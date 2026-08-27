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
    projectRoot.set(root.file("."))
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

  val compatProviders =
    launchBySegment.values
      .map { reg ->
        registerCompatVariant(root, reg)
      }
      .toSet()

  val publishedModrinth =
    launchBySegment.values.map { registerPublishedVariant(root, it, Platform.MODRINTH) }
  val publishedCurseforge =
    launchBySegment.values.map { registerPublishedVariant(root, it, Platform.CURSEFORGE) }

  // --- root aggregates. ---
  root.tasks.register("runtimeTestBuild") {
    group = ROOT_TASKS
    description = "Boot locally built Terrasect jars through HeadlessMC across the full matrix."
    dependsOn(buildTasks)
  }

  root.tasks.register("runtimeTestCompat") {
    group = ROOT_TASKS
    description = "Boot compat-modpack scenarios (local jar + third-party mods) through HeadlessMC."
    dependsOn(compatProviders)
  }

  root.tasks.register("runtimeTestPublished") {
    group = ROOT_TASKS
    description =
      "Boot the requested published Terrasect artifact from Modrinth/CurseForge via Ferium."
    // Defer selection until this task runs (not at configuration) so it never fails registration.
    dependsOn(publishedModrinth + publishedCurseforge)
  }

  root.tasks.register("runtimeTestAll") {
    group = ROOT_TASKS
    description = "Build + published + compat runtime matrices."
    dependsOn("runtimeTestBuild", "runtimeTestPublished", "runtimeTestCompat")
  }
}

/** Registers per-version launch tasks on every supported fabric/neoforge lane, keyed by segment. */
private fun collectPerVersionLaunches(
  root: Project,
  tree: ProjectTree,
): Map<String, PerVersionLaunch> {
  val result = mutableMapOf<String, PerVersionLaunch>()
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
      result[node.project] = PerVersionLaunch(gradlePath, branchName, node.project, provider)
    }
  }
  return result
}

/** Registers a per-version COMPAT variant: the launch reads a Ferium-resolved mod dir. */
private fun registerCompatVariant(root: Project, reg: PerVersionLaunch): Task {
  val modProject = root.findProject(reg.gradlePath) ?: return reg.provider.get()
  val jarProvider: TaskProvider<Jar> = modProject.tasks.named("jar") as TaskProvider<Jar>
  val provider: TaskProvider<RuntimeTestLaunchTask> =
    modProject.tasks.register("runtimeTestCompat", RuntimeTestLaunchTask::class.java)
  provider.configure {
    group = ROOT_TASKS
    description =
      "Boot compat-modpack scenario for ${reg.loader} ${RuntimeTestPins.mcVersionOf(reg.segment)} " +
        "(local jar + third-party mods) through HeadlessMC."
    mcVersion.set(RuntimeTestPins.mcVersionOf(reg.segment))
    loader.set(reg.loader)
    successMarker.set(RuntimeTestPins.successMarkerFor(reg.loader))
    launchTimeoutSeconds.set(1200L)
    toolsDir.from(root.layout.buildDirectory.dir("$RUNTIME_TOOLS_DIR/bootstrap"))
    runtimeDir.set(
      root.layout.buildDirectory.dir(
        "$RUNTIME_TOOLS_DIR/runtime/${reg.loader}-compat-${reg.segment}"
      )
    )
    testJar.set(jarProvider.flatMap { it.archiveFile })
    dependsOn(reg.provider, modProject.tasks.named("jar"))
  }
  return provider.get()
}

/** Registers a per-version PUBLISHED variant: resolves Terrasect from the registry via Ferium. */
private fun registerPublishedVariant(
  root: Project,
  reg: PerVersionLaunch,
  platform: Platform,
): TaskProvider<RuntimeTestLaunchTask> {
  val modProject = root.findProject(reg.gradlePath) ?: return reg.provider
  val provider: TaskProvider<RuntimeTestLaunchTask> =
    modProject.tasks.register(
      "runtimeTest${platform.name}Published",
      RuntimeTestLaunchTask::class.java,
    )
  provider.configure {
    group = ROOT_TASKS
    description =
      "Boot the $platform-registered Terrasect jar for ${reg.loader} " +
        "${RuntimeTestPins.mcVersionOf(reg.segment)} through HeadlessMC."
    mcVersion.set(RuntimeTestPins.mcVersionOf(reg.segment))
    loader.set(reg.loader)
    successMarker.set(RuntimeTestPins.successMarkerFor(reg.loader))
    launchTimeoutSeconds.set(1200L)
    toolsDir.from(root.layout.buildDirectory.dir("$RUNTIME_TOOLS_DIR/bootstrap"))
    runtimeDir.set(
      root.layout.buildDirectory.dir(
        "$RUNTIME_TOOLS_DIR/runtime/${reg.loader}-${platform.name}-${reg.segment}"
      )
    )
    // Published scenarios use the Ferium-resolved dir, so we intentionally leave testJar unset and
    // provide resolvedModsDir (set from the resolved-pack task) for the launch to consume.
    dependsOn(reg.provider)
  }
  return provider
}
