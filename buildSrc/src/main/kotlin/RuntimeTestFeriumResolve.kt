// Descriptor-driven Ferium profile preparation and resolution.
//
// The explicit Gradle pipeline prepares an isolated Ferium profile, stages a local build artifact
// in
// Ferium's `user/` directory when needed, downloads registry fixtures non-interactively, and passes
// the resulting mods directory to the downstream HeadlessMC launch task. The older combined resolve
// task remains below as a compatibility/test seam while callers use the split tasks.
//
// This file has no `package` declaration to match build-extensions.kt / RuntimeTestTasks.kt, and it
// uses only the JDK + Gradle API (no third-party libraries on the buildSrc classpath), so
// `./gradlew tasks` / `build` / `spotlessCheck` stay offline — resolution only ever happens behind
// an explicit runtime-test download task that carries real secrets.

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Delimiter joining a single mod's fields into one @Input string. */
private const val MOD_DELIM = "|"

/**
 * One mod to resolve via Ferium, encoded as a single pipe-delimited @Input string:
 * `PLATFORM|ID|DISPLAY_NAME`. [versionId] (if present) is review metadata recorded in the manifest
 * only — Ferium 4.7.1's on-disk config identifier is a slug/project-id string, so the version id
 * does not round-trip into the config; it is retained for deterministic, reviewable diagnostics.
 */
fun modEntry(platform: String, id: String, displayName: String, versionId: String? = null): String =
  buildString {
    append(platform)
    append(MOD_DELIM)
    append(id)
    append(MOD_DELIM)
    append(displayName)
    if (versionId != null) {
      append(MOD_DELIM)
      append(versionId)
    }
  }

fun splitModEntry(entry: String): Triple<String, String, String> {
  val parts = entry.split(MOD_DELIM)
  if (parts.size < 3) {
    throw GradleException("Malformed mod entry '$entry' (expected platform|id|name)")
  }
  return Triple(parts[0], parts[1], parts[2])
}

/**
 * Writes a repository-owned Ferium profile config as a string. The config uses the shape Ferium
 * 4.7.1 actually deserializes: the legacy `game_version` / `mod_loader` fields (PascalCase loader
 * name — "Fabric" / "NeoForge"), NOT the newer `filters` array (which 4.7.1 rejects at load time).
 * Each mod is `{"name":..., "identifier":{"ModrinthProject":"slug"}}` or `{"name":...,
 * "identifier":{"CurseForgeProject":<number>}}`. No credentials are embedded here — the CurseForge
 * key is supplied to the process via CURSEFORGE_API_KEY at execution time.
 */
fun writeFeriumConfig(
  outputDir: String,
  loader: String,
  mcVersion: String,
  mods: List<String>,
): String {
  val loaderName =
    when (loader.lowercase()) {
      "quilt" -> "Quilt"
      "neo-forged",
      "neoforge" -> "NeoForge"
      "forge" -> "Forge"
      "fabric" -> "Fabric"
      else -> throw GradleException("Unsupported Ferium mod loader '$loader'")
    }
  val modsJson = buildString {
    append('[')
    mods.forEachIndexed { i, raw ->
      if (i > 0) append(',')
      val (platform, id, displayName) = splitModEntry(raw)
      append("{\"name\":\"")
      append(escapeJson(displayName))
      append("\",\"identifier\":{")
      when (platform.uppercase()) {
        "MODRINTH" -> {
          append("\"ModrinthProject\":\"")
          append(escapeJson(id))
          append('"')
        }
        "CURSEFORGE" -> {
          if (!id.all { it.isDigit() }) {
            throw GradleException("CurseForge mod id must be numeric, got '$id'")
          }
          append("\"CurseForgeProject\":")
          append(id)
        }
        else -> throw GradleException("Unsupported Ferium platform '$platform'")
      }
      append("}}")
    }
    append(']')
  }
  return buildString {
    append("{\n")
    append("  \"active_profile\": 0,\n")
    append("  \"active_modpack\": 0,\n")
    append("  \"profiles\": [\n")
    append("    {\n")
    append("      \"name\": \"terrasect-runtime-test\",\n")
    append("      \"output_dir\": \"")
    append(escapeJson(outputDir))
    append("\",\n")
    append("      \"game_version\": \"")
    append(escapeJson(mcVersion))
    append("\",\n")
    append("      \"mod_loader\": \"")
    append(escapeJson(loaderName))
    append("\",\n")
    append("      \"mods\": ")
    append(modsJson)
    append("\n")
    append("    }\n")
    append("  ],\n")
    append("  \"modpacks\": []\n")
    append("}\n")
  }
}

private fun escapeJson(s: String): String = buildString {
  for (c in s) {
    when (c) {
      '\\' -> append("\\\\")
      '"' -> append("\\\"")
      '\n' -> append("\\n")
      '\r' -> append("\\r")
      '\t' -> append("\\t")
      else -> append(c)
    }
  }
}

/** Returns the exact command used by the download stage, with no shell interpolation. */
fun feriumUpgradeArgs(binary: File, profile: File): List<String> =
  listOf(binary.absolutePath, "--config-file", profile.absolutePath, "upgrade")

/**
 * Creates the isolated Ferium workspace used by the two-stage pipeline. Ferium's `user/` convention
 * is relative to the configured `output_dir`, so the local jar is staged at `mods/user/` and Ferium
 * copies it into the actual mod output during `upgrade`.
 */
fun prepareFeriumWorkspace(
  workspace: File,
  outputDir: File,
  loader: String,
  mcVersion: String,
  mods: List<String>,
  localJar: File? = null,
): File {
  workspace.mkdirs()
  val userDir = File(outputDir, "user").apply { mkdirs() }
  localJar?.copyTo(File(userDir, localJar.name), overwrite = true)
  val profile = File(workspace, "ferium-profile.json")
  profile.writeText(writeFeriumConfig(outputDir.absolutePath, loader, mcVersion, mods))
  return profile
}

/** Prepares one isolated Ferium profile and optionally stages a locally built Terrasect jar. */
abstract class RuntimeTestFeriumPrepareTask : DefaultTask() {

  @get:Input abstract val loader: Property<String>
  @get:Input abstract val mcVersion: Property<String>
  @get:Input abstract val mods: ListProperty<String>
  @get:Input abstract val outputDirectoryPath: Property<String>
  @get:InputFile @get:Optional abstract val localJar: RegularFileProperty

  @get:OutputFile abstract val profileFile: RegularFileProperty
  @get:OutputDirectory abstract val userDirectory: DirectoryProperty

  @TaskAction
  fun run() {
    val profile = profileFile.get().asFile
    val staged = localJar.orNull?.asFile
    prepareFeriumWorkspace(
      profile.parentFile,
      File(outputDirectoryPath.get()),
      loader.get(),
      mcVersion.get(),
      mods.get(),
      staged,
    )
    logger.lifecycle(
      "RuntimeTestFeriumPrepare: ${loader.get()} ${mcVersion.get()} -> ${profile.path}" +
        if (staged == null) "" else " (staged ${staged.name} in user/)"
    )
  }
}

/**
 * Runs Ferium against a prepared isolated profile. This is deliberately separate from preparation
 * so the profile and local-artifact staging are inspectable before any registry access occurs.
 */
abstract class RuntimeTestFeriumDownloadTask : DefaultTask() {

  @get:Classpath abstract val toolsDir: ConfigurableFileCollection
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val profileFile: RegularFileProperty
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val userDirectory: DirectoryProperty
  @get:Input abstract val loader: Property<String>
  @get:Input abstract val mcVersion: Property<String>
  @get:Input abstract val scenarioLabel: Property<String>
  @get:Input abstract val mods: ListProperty<String>
  @get:Input @get:Optional abstract val expectedTerrasectVersion: Property<String>
  @get:Input @get:Optional abstract val dryRun: Property<Boolean>

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
  @get:OutputFile abstract val resolveManifest: RegularFileProperty
  @get:Internal abstract val curseforgeApiKey: Property<String>

  @TaskAction
  fun run() {
    val profile = profileFile.get().asFile
    val resolvedMods = outputDirectory.get().asFile
    if (!profile.isFile) throw GradleException("Prepared Ferium profile is missing: $profile")
    if (!userDirectory.get().asFile.isDirectory) {
      throw GradleException(
        "Prepared Ferium user directory is missing: ${userDirectory.get().asFile}"
      )
    }

    val dry = dryRun.orElse(false).get()
    if (dry) {
      val text = profile.readText()
      if (!text.contains("\"mod_loader\"") || !text.contains("\"mods\"")) {
        throw GradleException("Prepared Ferium config is malformed: $profile")
      }
      writeFeriumManifest(resolveManifest.get().asFile, resolvedMods, profile, this)
      logger.lifecycle(
        "RuntimeTestFeriumDownload (dry-run): ${scenarioLabel.get()} -> ${profile.name} " +
          "(${mods.get().size} mod(s) declared), no download"
      )
      return
    }

    if (!resolvedMods.isDirectory && !resolvedMods.mkdirs()) {
      throw GradleException("Could not create Ferium output directory $resolvedMods")
    }
    val toolsRoot =
      toolsDir.files.firstOrNull { it.isDirectory }
        ?: toolsDir.files.firstOrNull()
        ?: throw GradleException("toolsDir is empty")
    val binary = findFeriumBinary(toolsRoot)
    val command = feriumUpgradeArgs(binary, profile)
    val environment = linkedMapOf("FERIUM_CONFIG_FILE" to profile.absolutePath)
    curseforgeApiKey.orNull?.let { environment["CURSEFORGE_API_KEY"] = it }
    val logFile = File(profile.parentFile, "resolve.log")
    val process =
      ProcessBuilder(command)
        .directory(profile.parentFile)
        .redirectErrorStream(true)
        .apply { environment().putAll(environment) }
        .start()
    process.inputStream.use { input ->
      logFile.outputStream().use { output -> input.copyTo(output) }
    }
    val exit = process.waitFor()
    writeFeriumManifest(resolveManifest.get().asFile, resolvedMods, profile, this)
    if (exit != 0) {
      val diagnostic = File(profile.parentFile, "resolve-failure.log")
      diagnostic.writeText(
        "Ferium exited with code $exit for ${scenarioLabel.get()}\n" + logFile.readText()
      )
      throw GradleException(
        "Ferium resolution failed (exit $exit) for ${scenarioLabel.get()}: ${diagnostic.name}"
      )
    }
    assertPublishedTerrasectVersion(
      resolvedMods
        .walkTopDown()
        .filter { it.isFile && it.extension == "jar" }
        .map { it.name }
        .toList(),
      expectedTerrasectVersion.orNull,
    )
    logger.lifecycle(
      "RuntimeTestFeriumDownload: ${scenarioLabel.get()} -> " +
        "${resolvedMods.walkTopDown().count { it.isFile && it.extension == "jar" }} jar(s)"
    )
  }
}

private fun writeFeriumManifest(
  manifestFile: File,
  resolvedMods: File,
  profile: File,
  task: RuntimeTestFeriumDownloadTask,
) {
  val jars =
    resolvedMods
      .walkTopDown()
      .filter { it.isFile && it.extension == "jar" }
      .map { it.name }
      .toList()
      .sorted()
  val declared =
    task.mods.get().map { splitModEntry(it).let { (p, i, n) -> "$n ($p/$i)" } }.sorted()
  val lines = buildList {
    add("scenario=${task.scenarioLabel.get()}")
    add("loader=${task.loader.get()}")
    add("mcVersion=${task.mcVersion.get()}")
    add("dryRun=${task.dryRun.orElse(false).get()}")
    add("configFile=${profile.name}")
    if (task.expectedTerrasectVersion.isPresent) {
      add("terrasectVersion=${resolvedTerrasectVersion(resolvedMods)}")
    }
    add("declaredMods=$declared")
    add("resolvedJars=$jars")
  }
  manifestFile.parentFile?.mkdirs()
  manifestFile.writeText(
    "Terrasect Ferium resolve manifest for ${task.scenarioLabel.get()}\n" +
      lines.joinToString("\n") +
      "\n"
  )
}

/**
 * Terrasect's published jar naming contract, shared by both registries:
 * `terrasect-<loader>-<version>+<mcSegment>.jar` (e.g. `terrasect-neoforge-0.2.3+26.2.jar`).
 * [loader] is the branch id (fabric/neoforge); [version] is the Terrasect version; the trailing
 * segment after `+` is the Minecraft Stonecutter segment. This naming lets us recover the exact
 * Terrasect version from a resolved jar without any registry round-trip.
 */
fun terrasectVersionFromJarName(name: String): String? {
  val base = name.removeSuffix(".jar")
  val plus = base.lastIndexOf('+')
  if (plus < 0) return null
  // Left half is `terrasect-<loader>-<version>`; the Terrasect version follows the last `-`.
  val left = base.substring(0, plus)
  val minus = left.lastIndexOf('-')
  if (minus < 0) return null
  return left.substring(minus + 1)
}

/** True when [name] is a Terrasect artifact (matches the published naming contract above). */
fun isTerrasectJar(name: String): Boolean = terrasectVersionFromJarName(name) != null

/**
 * The Terrasect version recovered from a set of resolved jar names, or null when none match the
 * published naming contract. Used to record the resolved identity in the deterministic manifest.
 */
fun resolvedTerrasectVersion(resolvedMods: java.io.File): String? =
  resolvedMods
    .walkTopDown()
    .filter { it.isFile && it.extension == "jar" }
    .map { it.name }
    .filter(::isTerrasectJar)
    .mapNotNull { terrasectVersionFromJarName(it) }
    .firstOrNull()

/**
 * Verifies that the resolved Terrasect artifact identity matches an exact requested version.
 *
 * [expected] is the exact Terrasect version to assert (e.g. `0.2.3`); when null, verification is
 * skipped (used by COMPAT lanes, which resolve third-party mods and carry no Terrasect artifact).
 *
 * The check is deliberate and unforgiving: an ambiguous set of resolved Terrasect versions, an
 * empty resolution, or a version that differs from [expected] all fail — we never silently fall
 * back to another version or a local artifact. Pure and side-effect free so it is fully testable
 * offline, independent of Ferium's network resolution.
 */
fun assertPublishedTerrasectVersion(jarNames: List<String>, expected: String?) {
  if (expected == null) return
  val versions = jarNames.filter(::isTerrasectJar).mapNotNull { terrasectVersionFromJarName(it) }
  val distinct = versions.toSet()
  if (distinct.isEmpty()) {
    throw GradleException("No Terrasect artifact resolved; requested exact version $expected")
  }
  if (distinct.size > 1) {
    throw GradleException(
      "Ambiguous Terrasect artifacts resolved (${distinct.joinToString(", ")}); requested exactly $expected"
    )
  }
  val resolved = distinct.first()
  if (resolved != expected) {
    throw GradleException(
      "Exact version mismatch: resolved Terrasect $resolved but requested exact version $expected"
    )
  }
}

/**
 * Resolves third-party mods with Ferium from a repository-owned profile config.
 *
 * The task writes an isolated config (pointed at via FERIUM_CONFIG_FILE), then either:
 * - real mode: `ferium upgrade` downloads the latest compatible files into [outputDirectory] (the
 *   resolvedModsDir the launch task consumes), or
 * - dry-run mode: only writes the isolated config + manifest and asserts it parses — no download,
 *   no process spawn. This is the deterministic offline-controlled execution path the acceptance
 *   card calls out ("explicit compat task has a dry-run or controlled fixture test").
 *
 * A deterministic manifest (loader/mc/mods + resolved jars) is always written to [resolveManifest].
 * On failure the stderr is captured to a deterministic diagnostic file and surfaced as a
 * GradleException. The CurseForge API key is injected into the process env only — never declared as
 * an @Input, so it never lands in descriptors, task inputs, the build cache, or logs.
 */
abstract class RuntimeTestFeriumResolveTask : DefaultTask() {

  @get:Input abstract val loader: Property<String>
  @get:Input abstract val mcVersion: Property<String>

  /** Human-friendly lane label used for the deterministic manifest / diagnostic file names. */
  @get:Input abstract val scenarioLabel: Property<String>

  /** Pipe-delimited mod entries (see [modEntry]); order is preserved into the config + manifest. */
  @get:Input abstract val mods: ListProperty<String>

  /**
   * The exact Terrasect version the resolved artifact must match (e.g. `0.2.3`). Set for PUBLISHED
   * lanes; null for COMPAT lanes (which resolve third-party mods and carry no Terrasect artifact).
   * When set, the resolved Terrasect jar identity is verified against it and any mismatch fails
   * hard — never silently resolved to another version or a local jar.
   */
  @get:Input @get:Optional abstract val expectedTerrasectVersion: Property<String>

  /** When set, only validates the config (no download / no resolve of file contents). */
  @get:Input @get:Optional abstract val dryRun: Property<Boolean>

  /**
   * The resolved mods output directory. In real mode this is the Ferium upgrade output_dir and
   * becomes the launch task's [resolvedModsDir]. In dry-run mode it is a scratch dir that stays
   * empty (the config-validity assertion is the whole point).
   */
  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  /** Deterministic, reviewable manifest of what was resolved (or would be). */
  @get:OutputFile abstract val resolveManifest: RegularFileProperty

  /**
   * Optional read-only CurseForge API key, injected into the process env only (never a task input).
   */
  @get:Internal abstract val curseforgeApiKey: Property<String>

  @TaskAction
  fun run() {
    val workDir = outputDirectory.get().asFile
    if (!workDir.isDirectory && !workDir.mkdirs()) {
      throw GradleException("Could not create resolve output dir $workDir")
    }
    val resolvedMods = File(workDir, "mods").apply { mkdirs() }

    // A fresh config per run keeps the output dir self-contained and avoids mutating any shared
    // user profile. FERIUM_CONFIG_FILE points Ferium at it; the key (if any) is injected into the
    // process env, never written here.
    val configFile = File(workDir, "ferium-profile.json")
    val configText =
      writeFeriumConfig(resolvedMods.absolutePath, loader.get(), mcVersion.get(), mods.get())
    configFile.writeText(configText)

    val dry = dryRun.orElse(false).get()
    if (dry) {
      // Deterministic offline branch: no process spawn, no download. Assert the generated config is
      // well-formed and complete so a regression in writeFeriumConfig fails fast here.
      if (!configText.contains("\"mod_loader\"") || !configText.contains("\"mods\"")) {
        throw GradleException("Generated Ferium config is malformed: $configFile")
      }
      writeManifest(resolvedMods, configFile)
      logger.lifecycle(
        "RuntimeTestFeriumResolve (dry-run): ${scenarioLabel.get()} -> ${configFile.name} " +
          "(${mods.get().size} mod(s) declared), no download"
      )
      return
    }

    val cmd = listOf("ferium", "-c", configFile.absolutePath, "upgrade")
    // FERIUM_CONFIG_FILE is the canonical way to point Ferium at the isolated config; set it on the
    // process env in addition to the flag so any nested Ferium behavior sees the same isolation.
    val env = linkedMapOf<String, String>()
    env["FERIUM_CONFIG_FILE"] = configFile.absolutePath
    val keyCurse = curseforgeApiKey.orNull
    if (keyCurse != null) env["CURSEFORGE_API_KEY"] = keyCurse

    val builder = ProcessBuilder(cmd).directory(workDir).redirectErrorStream(false)
    // Populate the child env before start() so the variables actually reach Ferium.
    builder.environment().putAll(env)
    val proc = builder.start()

    // Read stderr first (small; carries the failure reason), then drain stdout to the log file on a
    // daemon thread so a large download log can never deadlock the pipe.
    val errOut = StringBuilder()
    errOut.append(proc.errorStream.bufferedReader().readText())
    val logFile = File(workDir, "resolve.log")
    val drain =
      Thread { proc.inputStream.bufferedReader().use { logFile.writeText(it.readText()) } }
        .apply { isDaemon = true }
    drain.start()
    val exit = proc.waitFor()
    drain.join(30_000)

    // Deterministic manifest regardless of outcome.
    writeManifest(resolvedMods, configFile)

    if (exit != 0) {
      val diag = File(workDir, "resolve-failure.log")
      diag.writeText("Ferium exited with code $exit for ${scenarioLabel.get()}\n" + errOut)
      throw GradleException(
        "Ferium resolution failed (exit $exit) for ${scenarioLabel.get()}: ${diag.name}. " +
          "Stderr captured; config written to ${configFile.name}."
      )
    }

    // Exact-version identity gate for PUBLISHED lanes: Ferium resolves the latest-compatible
    // artifact from the registry, so we must assert the installed Terrasect jar is exactly the
    // requested version. Any mismatch fails here — before the launch task ever feeds it to
    // HeadlessMC — and never silently falls back to another version or a local jar.
    assertPublishedTerrasectVersion(
      resolvedMods
        .walkTopDown()
        .filter { it.isFile && it.extension == "jar" }
        .map { it.name }
        .toList(),
      expectedTerrasectVersion.orNull,
    )

    val jarCount = resolvedMods.walkTopDown().filter { it.isFile && it.extension == "jar" }.count()
    logger.lifecycle(
      "RuntimeTestFeriumResolve: ${scenarioLabel.get()} -> $jarCount jar(s) in $resolvedMods"
    )
  }

  /** Writes the deterministic manifest: lane identity + declared mods + resolved jars (sorted). */
  private fun writeManifest(resolvedMods: File, configFile: File) {
    val jars =
      resolvedMods
        .walkTopDown()
        .filter { it.isFile && it.extension == "jar" }
        .map { it.name }
        .toList()
        .sorted()
    val declared = mods.get().map { splitModEntry(it).let { (p, i, n) -> "$n ($p/$i)" } }.sorted()
    val lines = buildList {
      add("scenario=${scenarioLabel.get()}")
      add("loader=${loader.get()}")
      add("mcVersion=${mcVersion.get()}")
      add("dryRun=${dryRun.orElse(false).get()}")
      add("configFile=${configFile.name}")
      if (expectedTerrasectVersion.isPresent) {
        add("terrasectVersion=${resolvedTerrasectVersion(resolvedMods)}")
      }
      add("declaredMods=$declared")
      add("resolvedJars=$jars")
    }
    val manifest =
      "Terrasect Ferium resolve manifest for ${scenarioLabel.get()}\n" +
        lines.joinToString("\n") +
        "\n"
    resolveManifest.get().asFile.writeText(manifest)
  }
}
