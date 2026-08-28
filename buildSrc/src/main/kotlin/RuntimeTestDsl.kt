// Root orchestration for the runtime-test infrastructure.
//
// Invoked once from the root controller (stonecutter.gradle.kts). It walks the Stonecutter tree,
// registers a shared tool-bootstrap task, registers a per-version launch task for every supported
// (loader, MC) lane, and wires the root aggregate tasks (build / published / compat / all /
// infrastructure check). Per-version tasks stay addressable (e.g. :fabric:26.2.x:runtimeTestLaunch)
// so a single lane can be run without the whole matrix.
//
// Everything is opt-in: none of these tasks is invoked by the normal build, spotlessCheck, or
// unit tests, and they touch Modrinth/CurseForge only behind an explicit task with real secrets.

import dev.kikugie.stonecutter.controller.StonecutterControllerExtension
import dev.kikugie.stonecutter.data.tree.ProjectTree
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.getByType

private const val RUNTIME_TOOLS_DIR = "runtime-tools"
private val RUNTIME_REL = "runtime-tests"
private const val RUNTIME_EXPECTATIONS = "runtime-tests-expectations"
private const val ROOT_TASKS = "runtime test"

/**
 * Root exposes the Stonecutter controller extension; the controller holds the shared [ProjectTree].
 */
fun Project.controller(): StonecutterControllerExtension =
  extensions.getByType<StonecutterControllerExtension>()

/**
 * Reads a root-level structured property (e.g. `mod.id`, `mod.version`) from the shared
 * `stonecutter.properties.toml` via the controller. Avoids the per-version build extension, which
 * only exists on versioned branches, not the root controller.
 */
fun Project.runtimeProp(key: String): String = controller().properties.getOrNull<String>(key) ?: ""

/** Like [runtimeProp] but returns null when unset, so an optional key never forces a value. */
fun Project.runtimePropOrNull(key: String): String? = controller().properties.getOrNull<String>(key)

/** Collects per-version (loader, segment) launch-task providers for later filtering. */
data class PerVersionLaunch(
  val gradlePath: String,
  val loader: String,
  val segment: String,
  val provider: TaskProvider<RuntimeTestLaunchTask>,
)

/** Root entrypoint called from the root controller (stonecutter.gradle.kts). */
fun RuntimeTestDsl(root: Project) {
  val tree = root.controller().tree
  val bootstrapDir = root.layout.buildDirectory.dir("$RUNTIME_TOOLS_DIR/bootstrap")

  // --- shared tool bootstrap: one task downloads + verifies HMC + Ferium. ---
  val bootstrapTask: TaskProvider<RuntimeTestBootstrapTask> =
    root.tasks.register("runtimeTestBootstrap", RuntimeTestBootstrapTask::class.java)
  bootstrapTask.configure {
    group = ROOT_TASKS
    description = "Download + verify HeadlessMC and Ferium into the runtime-tools cache dir."
    hmcUrl.set(RuntimeTestPins.HMC_JAR_URL)
    hmcSha256.set(RuntimeTestPins.HMC_JAR_SHA256)
    feriumUrl.set(RuntimeTestPins.feriumUrl())
    feriumSha256.set(RuntimeTestPins.feriumSha256())
    feriumPlatform.set(RuntimeTestPins.feriumPlatform())
    outputDirectory.set(bootstrapDir)
  }

  // --- shared descriptor validation (preflight gate): no network. ---
  val descriptorValidateTask: TaskProvider<RuntimeTestDescriptorValidateTask> =
    root.tasks.register(
      "runtimeTestDescriptorValidate",
      RuntimeTestDescriptorValidateTask::class.java,
    )
  descriptorValidateTask.configure {
    group = ROOT_TASKS
    description = "Validate every runtime-test descriptor offline (preflight gate)."
    expectedModId.set(root.runtimeProp("mod.id"))
    expectedLatest.set(RuntimeTestPins.LATEST_MC)
    expectedModVersion.set(root.runtimeProp("mod.version"))
    descriptorsDir.from(root.file(RUNTIME_REL))
  }

  // --- offline validation-expectations: proves the SAME parse()/validate() pipeline rejects the
  // malformed / unsupported / missing-lane / header-mismatch shapes, and that the real matrix still
  // passes. Drives RuntimeTestDescriptors.loadAll() directly — no network, no launch tooling. Not
  // wired into CI or the launch pipeline, so it cannot alter release behaviour. ---
  val validationExpectationsTask: TaskProvider<RuntimeTestValidationExpectationsTask> =
    root.tasks.register(
      "runtimeTestValidationExpectations",
      RuntimeTestValidationExpectationsTask::class.java,
    )
  validationExpectationsTask.configure {
    group = ROOT_TASKS
    description =
      "Offline: assert the descriptor parser rejects malformed/unsupported/missing-lane cases and accepts the real matrix."
    expectationsFile.set(root.file("$RUNTIME_EXPECTATIONS/expectations.properties"))
    positiveDir.set(root.file(RUNTIME_REL))
  }

  // --- infrastructure check: infrastructure + descriptors + bootstrap pipeline. ---
  root.tasks.register("runtimeTestInfrastructureCheck") {
    group = ROOT_TASKS
    description = "Preflight: validate descriptors and warm the tool/bootstrap + cache pipeline."
    dependsOn(descriptorValidateTask, bootstrapTask)
  }

  // --- tree enumeration: per-version launch tasks + scenario-variant tasks. ---
  val launchBySegment = collectPerVersionLaunches(root, tree)

  val buildTasks = launchBySegment.values.map { it.provider.get() }

  // --- per-version offline dry-run tasks: never launch, only assemble + print the exact launch
  // argv.
  val dryRunProviders = collectPerVersionDryRuns(root, tree)
  val dryRunTasks = dryRunProviders.values.map { it.get() }

  // --- descriptor-driven Ferium resolution (offline parse; real network only behind explicit
  // tasks). ---
  val manifests = RuntimeTestDescriptors.loadAll(root.file(RUNTIME_REL))
  // Terrasect's CurseForge project id is a public coordinate (like the Modrinth slug), so it can be
  // baked here — but kept overridable via -Pterrseaect.curseForgeTerrasectProjectId. The read-only
  // CurseForge API key is NOT baked anywhere; it is injected into the process env at execution
  // time.
  val curseForgeTerrasectId =
    (root.findProperty("terrseaect.curseForgeTerrasectProjectId") as? String) ?: "1615147"
  // Exact Terrasect version to verify the published artifact against. Defaults to mod.version from
  // stonecutter.properties.toml (the current release) and is overridable via -Pterrseaect.
  // runtimeTestPublishedVersion so a specific published version can be pinned and verified end to
  // end. Resolved once here (not per-lane) so every PUBLISHED lane asserts the same coordinate.
  val terrasectPublishedVersion =
    (root.findProperty("terrseaect.runtimeTestPublishedVersion") as? String)?.ifBlank { null }
      ?: root.runtimeProp("mod.version").ifBlank { RuntimeTestPins.MOD_VERSION_PROP }
  val resolveProviders = mutableListOf<TaskProvider<RuntimeTestFeriumResolveTask>>()

  val compatProviders =
    launchBySegment.values
      .map { reg ->
        registerCompatVariant(root, manifests, reg, resolveProviders)
      }
      .toSet()

  val publishedModrinth =
    launchBySegment.values.map { reg ->
      registerPublishedVariant(
        root,
        manifests,
        reg,
        Platform.MODRINTH,
        curseForgeTerrasectId,
        resolveProviders,
        terrasectPublishedVersion,
      )
    }
  val publishedCurseforge =
    launchBySegment.values.map { reg ->
      registerPublishedVariant(
        root,
        manifests,
        reg,
        Platform.CURSEFORGE,
        curseForgeTerrasectId,
        resolveProviders,
        terrasectPublishedVersion,
      )
    }

  // --- root aggregates. ---
  root.tasks.register("runtimeTestBuild") {
    group = ROOT_TASKS
    description = "Boot locally built Terrasect jars through HeadlessMC across the full matrix."
    dependsOn(buildTasks)
  }

  root.tasks.register("runtimeTestLaunchDryRun") {
    group = ROOT_TASKS
    description =
      "Offline dry-run: assemble the exact HeadlessMC launch argv for every lane " +
        "(no download, no process). The printed command is the verifiable controlled execution path."
    dependsOn(dryRunTasks)
  }

  root.tasks.register("runtimeTestCompat") {
    group = ROOT_TASKS
    description =
      "Boot compat-modpack scenarios (local jar + Ferium-resolved third-party mods) through HeadlessMC."
    dependsOn(compatProviders)
  }

  root.tasks.register("runtimeTestPublished") {
    group = ROOT_TASKS
    description =
      "Boot the requested published Terrasect artifact from Modrinth/CurseForge via Ferium."
    dependsOn(publishedModrinth + publishedCurseforge)
  }

  // Descriptor-driven Ferium resolution. Pass -Pterrseaect.runtimeTestResolveDryRun=true to run
  // every resolve task in dry-run mode (writes + validates the isolated config, no download).
  root.tasks.register("runtimeTestResolve") {
    group = ROOT_TASKS
    description =
      "Resolve Modrinth/CurseForge fixtures with Ferium (repository-owned FERIUM_CONFIG_FILE), " +
        "preserving a deterministic manifest; the launch task then boots via HeadlessMC."
    dependsOn(resolveProviders)
  }

  root.tasks.register("runtimeTestAll") {
    group = ROOT_TASKS
    description = "Build + published + compat runtime matrices."
    dependsOn("runtimeTestBuild", "runtimeTestPublished", "runtimeTestCompat", "runtimeTestResolve")
  }
}

/** Registers per-version launch tasks on every supported fabric/neoforge lane, keyed by segment. */
private fun collectPerVersionLaunches(
  root: Project,
  tree: ProjectTree,
): Map<Pair<String, String>, PerVersionLaunch> {
  val result = mutableMapOf<Pair<String, String>, PerVersionLaunch>()
  tree.entries.forEach { (branchName, branch) ->
    if (branchName !in listOf("fabric", "neoforge")) return@forEach
    branch.versions.forEach { node ->
      val gradlePath = ":$branchName:${node.project}"
      val modProject = root.findProject(gradlePath) ?: return@forEach
      val mcVersionId = RuntimeTestPins.mcVersionOf(node.project)

      val jarProvider: TaskProvider<Jar> = modProject.tasks.named("jar") as TaskProvider<Jar>
      val provider: TaskProvider<RuntimeTestLaunchTask> =
        modProject.tasks.register("runtimeTestLaunch", RuntimeTestLaunchTask::class.java)
      provider.configure {
        group = ROOT_TASKS
        description =
          "Boot the built Terrasect jar for $branchName $mcVersionId through HeadlessMC."
        mcVersion.set(mcVersionId)
        loader.set(branchName)
        successMarker.set(RuntimeTestPins.successMarkerFor(branchName))
        launchTimeoutSeconds.set(900L)
        toolsDir.from(root.layout.buildDirectory.dir("$RUNTIME_TOOLS_DIR/bootstrap"))
        runtimeDir.set(
          root.layout.buildDirectory.dir("$RUNTIME_TOOLS_DIR/runtime/$branchName-${node.project}")
        )
        testJar.set(jarProvider.flatMap { it.archiveFile })
        dependsOn(modProject.tasks.named("jar"))
      }
      result[Pair(branchName, mcVersionId)] =
        PerVersionLaunch(gradlePath, branchName, node.project, provider)
    }
  }
  return result
}

/**
 * Registers a per-version DRY-RUN variant of [collectPerVersionLaunches]'s launch task. The dry-run
 * never starts HeadlessMC or launches Minecraft: it performs the exact same jar preparation as the
 * live launch, then writes the assembled HMC launch command line to an output file. This is the
 * deterministic, offline-controlled execution path the acceptance contract requires ("one lane has
 * a verifiable dry-run or controlled execution path with exact output"). It exercises the real
 * command-builder and jar-preparation side effects, only minus the real process spawn.
 */
private fun collectPerVersionDryRuns(
  root: Project,
  tree: ProjectTree,
): Map<Pair<String, String>, TaskProvider<RuntimeTestLaunchTask>> {
  val result = mutableMapOf<Pair<String, String>, TaskProvider<RuntimeTestLaunchTask>>()
  tree.entries.forEach { (branchName, branch) ->
    if (branchName !in listOf("fabric", "neoforge")) return@forEach
    branch.versions.forEach { node ->
      val gradlePath = ":$branchName:${node.project}"
      val modProject = root.findProject(gradlePath) ?: return@forEach
      val mcVersionId = RuntimeTestPins.mcVersionOf(node.project)

      val jarProvider: TaskProvider<Jar> = modProject.tasks.named("jar") as TaskProvider<Jar>
      val provider: TaskProvider<RuntimeTestLaunchTask> =
        modProject.tasks.register("runtimeTestLaunchDryRun", RuntimeTestLaunchTask::class.java)
      provider.configure {
        group = ROOT_TASKS
        description =
          "Offline dry-run: assemble the exact HeadlessMC launch argv for $branchName " +
            "$mcVersionId without launching (no download, no process). Prints the command to " +
            "a file."
        mcVersion.set(mcVersionId)
        loader.set(branchName)
        successMarker.set(RuntimeTestPins.successMarkerFor(branchName))
        launchTimeoutSeconds.set(900L)
        dryRun.set(true)
        toolsDir.from(root.layout.buildDirectory.dir("$RUNTIME_TOOLS_DIR/bootstrap"))
        runtimeDir.set(
          root.layout.buildDirectory.dir(
            "$RUNTIME_TOOLS_DIR/runtime-dryrun/$branchName-${node.project}"
          )
        )
        dryRunOutput.set(
          root.layout.buildDirectory.file(
            "$RUNTIME_TOOLS_DIR/dryrun-output/$branchName-${node.project}.cmd"
          )
        )
        testJar.set(jarProvider.flatMap { it.archiveFile })
        dependsOn(modProject.tasks.named("jar"))
      }
      result[Pair(branchName, mcVersionId)] = provider
    }
  }
  return result
}

/** Resolves third-party mods for one lane via Ferium, registering a deterministic resolve task. */
private fun registerResolveTask(
  root: Project,
  name: String,
  loaderName: String,
  mcVer: String,
  label: String,
  modList: List<String>,
  resolveProviders: MutableList<TaskProvider<RuntimeTestFeriumResolveTask>>,
  versionToVerify: String? = null,
): TaskProvider<RuntimeTestFeriumResolveTask> {
  val workDir = root.layout.buildDirectory.dir("$RUNTIME_TOOLS_DIR/resolve/$label")
  val manifest = root.layout.buildDirectory.file("$RUNTIME_TOOLS_DIR/resolve/$label.txt")
  val provider = root.tasks.register(name, RuntimeTestFeriumResolveTask::class.java)
  provider.configure {
    group = ROOT_TASKS
    description = "Resolve $label via Ferium (repository-owned FERIUM_CONFIG_FILE)."
    loader.set(loaderName)
    mcVersion.set(mcVer)
    scenarioLabel.set(label)
    mods.set(modList)
    // Dry-run is toggled by a root gradle property so the SAME tasks can run fully offline, proving
    // the config is valid without any Modrinth/CurseForge network access.
    dryRun.set(
      root.providers
        .gradleProperty("terrseaect.runtimeTestResolveDryRun")
        .map { it.toBoolean() }
        .orElse(false)
    )
    outputDirectory.set(workDir)
    resolveManifest.set(manifest)
    // PUBLISHED lanes pass the exact version to verify against; COMPAT lanes leave it null (they
    // resolve third-party mods, not a Terrasect artifact).
    if (expectedTerrasectVersion != null) {
      expectedTerrasectVersion.set(expectedTerrasectVersion)
    }
  }
  resolveProviders.add(provider)
  return provider
}

/**
 * Registers a per-version COMPAT variant: the launch boots local jar + optional Ferium-resolved
 * mods.
 */
private fun registerCompatVariant(
  root: Project,
  manifests: List<RuntimeManifest>,
  reg: PerVersionLaunch,
  resolveProviders: MutableList<TaskProvider<RuntimeTestFeriumResolveTask>>,
): Task {
  val modProject = root.findProject(reg.gradlePath) ?: return reg.provider.get()
  val mcVersionId = RuntimeTestPins.mcVersionOf(reg.segment)
  val jarProvider: TaskProvider<Jar> = modProject.tasks.named("jar") as TaskProvider<Jar>

  // Third-party mods are read from the descriptor for this lane. Deferred lanes (e.g. NeoForge,
  // which has no e2e-compat pins) resolve to an empty list and boot the local jar only.
  val mods = resolveModsFor(manifests, reg.loader, mcVersionId)

  val resolveProvider =
    if (mods.isEmpty()) null
    else
      registerResolveTask(
        root,
        "runtimeTest${reg.loader}-${mcVersionId}CompatResolve",
        reg.loader,
        mcVersionId,
        "compat-${reg.loader}-${mcVersionId}",
        mods,
        resolveProviders,
      )

  val provider: TaskProvider<RuntimeTestLaunchTask> =
    modProject.tasks.register("runtimeTestCompat", RuntimeTestLaunchTask::class.java)
  provider.configure {
    group = ROOT_TASKS
    description =
      "Boot compat-modpack scenario for ${reg.loader} $mcVersionId (local jar + " +
        if (mods.isEmpty()) "no third-party mods (deferred)"
        else "Ferium-resolved third-party mods" + ") through HeadlessMC."
    mcVersion.set(mcVersionId)
    loader.set(reg.loader)
    successMarker.set(RuntimeTestPins.successMarkerFor(reg.loader))
    launchTimeoutSeconds.set(1200L)
    toolsDir.from(root.layout.buildDirectory.dir("$RUNTIME_TOOLS_DIR/bootstrap"))
    runtimeDir.set(
      root.layout.buildDirectory.dir(
        "$RUNTIME_TOOLS_DIR/runtime/${reg.loader}-compat-${mcVersionId}"
      )
    )
    testJar.set(jarProvider.flatMap { it.archiveFile })
    if (resolveProvider != null) {
      // Inject the local Terrasect jar first, then resolveModsDir after (so a same-named resolved
      // jar wins over the local build, per prepareModsDir()).
      resolvedModsDir.set(resolveProvider.flatMap { it.outputDirectory })
      dependsOn(reg.provider, resolveProvider.get(), modProject.tasks.named("jar"))
    } else {
      dependsOn(reg.provider, modProject.tasks.named("jar"))
    }
  }
  return provider.get()
}

/**
 * Registers a per-version PUBLISHED variant: resolves Terrasect from Modrinth/CurseForge via
 * Ferium.
 */
private fun registerPublishedVariant(
  root: Project,
  manifests: List<RuntimeManifest>,
  reg: PerVersionLaunch,
  platform: Platform,
  curseForgeTerrasectId: String?,
  resolveProviders: MutableList<TaskProvider<RuntimeTestFeriumResolveTask>>,
  versionToVerify: String,
): TaskProvider<RuntimeTestLaunchTask> {
  val modProject = root.findProject(reg.gradlePath) ?: return reg.provider
  val mcVersionId = RuntimeTestPins.mcVersionOf(reg.segment)
  val jarProvider: TaskProvider<Jar> = modProject.tasks.named("jar") as TaskProvider<Jar>

  // Terrasect's own registry coordinate. Modrinth uses the project slug; CurseForge a numeric id
  // supplied by the operator as -Pterrseaect.curseForgeTerrasectProjectId (never baked in).
  //
  // Resolution only happens when the descriptor actually declares this (loader, mc) lane on this
  // platform. CurseForge is Forge-only (Terrasect never publishes Fabric there), so the descriptor
  // omits fabric lanes; those are deferred to local-jar boot for build-artifact coverage rather
  // than attempting an invalid CurseForge resolve.
  val terrasectMod =
    when (platform) {
      Platform.MODRINTH ->
        if (publishedDeclared(manifests, reg.loader, mcVersionId, platform))
          modEntry("MODRINTH", "terrasect", "Terrasect")
        else null
      Platform.CURSEFORGE ->
        if (
          publishedDeclared(manifests, reg.loader, mcVersionId, platform) &&
            curseForgeTerrasectId != null
        )
          modEntry("CURSEFORGE", curseForgeTerrasectId, "Terrasect")
        else null
    }

  val resolveProvider =
    if (terrasectMod == null) null
    else
      registerResolveTask(
        root,
        "runtimeTest${reg.loader}-${mcVersionId}${platform.name}PublishedResolve",
        reg.loader,
        mcVersionId,
        "published-${reg.loader}-${mcVersionId}-${platform.name}",
        listOf(terrasectMod),
        resolveProviders,
        versionToVerify,
      )

  val provider: TaskProvider<RuntimeTestLaunchTask> =
    modProject.tasks.register(
      "runtimeTest${platform.name}Published",
      RuntimeTestLaunchTask::class.java,
    )
  provider.configure {
    group = ROOT_TASKS
    description =
      "Boot the $platform-registered Terrasect jar for ${reg.loader} $mcVersionId through HeadlessMC."
    mcVersion.set(mcVersionId)
    loader.set(reg.loader)
    successMarker.set(RuntimeTestPins.successMarkerFor(reg.loader))
    // Verify the resolved published artifact is exactly this version before any launch.
    expectedTerrasectVersion.set(versionToVerify)
    launchTimeoutSeconds.set(1200L)
    toolsDir.from(root.layout.buildDirectory.dir("$RUNTIME_TOOLS_DIR/bootstrap"))
    runtimeDir.set(
      root.layout.buildDirectory.dir(
        "$RUNTIME_TOOLS_DIR/runtime/${reg.loader}-${platform.name}-${mcVersionId}"
      )
    )
    if (resolveProvider != null) {
      // Boot the resolved published Terrasect artifact (no local injection — verifies the registry
      // form).
      resolvedModsDir.set(resolveProvider.flatMap { it.outputDirectory })
      dependsOn(reg.provider, resolveProvider.get(), modProject.tasks.named("jar"))
    } else {
      // Deferred (e.g. CurseForge without an operator-supplied id): boot the local build jar so the
      // lane still retains build-artifact coverage.
      testJar.set(jarProvider.flatMap { it.archiveFile })
      dependsOn(reg.provider, modProject.tasks.named("jar"))
    }
  }
  return provider
}

/**
 * Collects the pipe-delimited mod entries declared for a lane's COMPAT scenario in the descriptors.
 */
private fun resolveModsFor(
  manifests: List<RuntimeManifest>,
  loader: String,
  mcVersion: String,
): List<String> {
  val result = mutableListOf<String>()
  manifests.forEach { m ->
    m.scenarios.forEach { s ->
      if (s.loader != loader) return@forEach
      if (RuntimeTestPins.matrixKey(s.mc) != RuntimeTestPins.matrixKey(mcVersion)) return@forEach
      if (s.scenario != Scenario.COMPAT) return@forEach
      s.externalMods.forEach { em ->
        result += modEntry(em.platform.name, em.project, em.name, em.version)
      }
    }
  }
  return result
}

/**
 * Whether the descriptors declare a PUBLISHED scenario for this (loader, mc) lane on [platform].
 */
private fun publishedDeclared(
  manifests: List<RuntimeManifest>,
  loader: String,
  mcVersion: String,
  platform: Platform,
): Boolean = manifests.any { m ->
  m.scenarios.any { s ->
    s.scenario == Scenario.PUBLISHED &&
      s.source == platform &&
      s.loader == loader &&
      RuntimeTestPins.matrixKey(s.mc) == RuntimeTestPins.matrixKey(mcVersion)
  }
}
