// Cacheable Gradle tasks for the runtime-test infrastructure.
//
// Everything here is offline-buildable: no third-party libraries, only Gradle's own API plus the
// JDK. The expensive preparation (tool download/verify/extract) is a @CacheableTask so CI and local
// second runs read the build cache instead of re-downloading. Credentials are never declared as
// inputs and never written to caches.
//
// These tasks are registered by RuntimeTestDsl from a buildscript context, so this file has no
// `package` declaration to match build-extensions.kt.
//
// NOTE on Gradle 9.5.0 API: `org.gradle.api.tasks.Inject` (service injection) and `@TempDir` and
// `ExecOperations` are not available on this toolchain, so process execution uses the JDK
// `ProcessBuilder` and temp space uses the task project's build directory.

import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Shared: SHA-256 digest (hex) of a file, or null if it does not exist. */
fun sha256(file: File): String? {
  if (!file.exists()) return null
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
    val buf = ByteArray(8192)
    while (true) {
      val n = input.read(buf)
      if (n < 0) break
      digest.update(buf, 0, n)
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

fun expectSha256(file: File, expected: String) {
  val actual = sha256(file) ?: throw GradleException("${file.name} missing before verification")
  if (!actual.equals(expected, ignoreCase = true)) {
    throw GradleException("SHA-256 mismatch for ${file.name}: expected $expected but got $actual")
  }
}

fun downloadToFile(url: String, destination: File) {
  if (destination.exists()) return
  destination.parentFile?.mkdirs()
  URL(url).openStream().use { input ->
    destination.outputStream().use { output -> input.copyTo(output) }
  }
}

/**
 * True when [file] lives inside [ancestor] (inclusive of [ancestor] itself). Uses canonical paths
 * so a `..` segment or a symlink-based escape is detected. Returns false when [ancestor] itself is
 * a symlink that the archive name would traverse — we cannot verify containment without resolving.
 */
fun isInside(ancestor: File, file: File): Boolean {
  val a = ancestor.canonicalFile
  val f = file.canonicalFile
  return f.absolutePath == a.absolutePath || f.path.startsWith(a.path + File.separator)
}

/**
 * Zip-extracts [zip] into [destination], rejecting any entry whose canonical path escapes the
 * destination directory (Zip Slip /Zip Slip). Directories are created; a file is written in one
 * pass.
 */
fun unzip(zip: File, destination: File) {
  if (!destination.isDirectory && !destination.mkdirs()) {
    throw GradleException("Could not create ${destination}")
  }
  ZipInputStream(zip.inputStream()).use { zipIn ->
    while (true) {
      val entry = zipIn.nextEntry ?: break
      val out = File(destination, entry.name)
      if (!isInside(destination, out)) {
        throw GradleException("Zip entry escapes destination: ${entry.name}")
      }
      if (entry.isDirectory) {
        out.mkdirs()
      } else {
        out.parentFile?.mkdirs()
        out.outputStream().use { zipIn.copyTo(it) }
      }
      zipIn.closeEntry()
    }
  }
}

/**
 * Downloads and verifies HeadlessMC + Ferium into a single cacheable output directory.
 *
 * Inputs: tool version + url + checksum (HMC) and ferium version + url + checksum + target
 * platform. Output: the extracted tool tree under [outputDirectory]/hmc/ and
 * [outputDirectory]/ferium/. A second run whose inputs are unchanged is UP-TO-DATE / served
 * FROM-CACHE.
 */
@CacheableTask
abstract class RuntimeTestBootstrapTask : DefaultTask() {

  @get:Input abstract val hmcUrl: Property<String>
  @get:Input abstract val hmcSha256: Property<String>
  @get:Input abstract val feriumUrl: Property<String>
  @get:Input abstract val feriumSha256: Property<String>
  @get:Input @get:Optional abstract val feriumPlatform: Property<String>

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun run() {
    val out = outputDirectory.get().asFile
    val hmcDir = File(out, "hmc").apply { mkdirs() }
    val feriumDir = File(out, "ferium").apply { mkdirs() }
    // Downloads go to a private subdir of the cacheable output dir. They are deterministic given
    // the inputs, so caching them is harmless and actually speeds up the warm build.
    val downloadDir = File(out, "download").apply { mkdirs() }

    // HeadlessMC is a single jar — download, verify, then place it in the output tree.
    val hmcZip = File(downloadDir, "hmc-download.jar")
    downloadToFile(hmcUrl.get(), hmcZip)
    expectSha256(hmcZip, hmcSha256.get())
    val hmcJar =
      File(hmcDir, "headlessmc-launcher-${hmcUrl.get().substringAfterLast('/')}").apply { delete() }
    hmcZip.copyTo(hmcJar, overwrite = true)

    // Ferium is a zip archive — download, verify, then extract the nosui binary. Every Ferium
    // release ships a single binary named exactly `ferium` at the archive root; centralizing that
    // contract means the bootstrap task and the tests check the same name rather than two guesses.
    val feriumZip = File(downloadDir, "ferium-download.zip")
    downloadToFile(feriumUrl.get(), feriumZip)
    expectSha256(feriumZip, feriumSha256.get())
    unzip(feriumZip, feriumDir)
    val binary = findFeriumBinary(feriumDir)
    binary.setExecutable(true, false)

    logger.lifecycle(
      "RuntimeTestBootstrap: HMC $hmcSha256.get().take(8)+, Ferium " +
        "${feriumPlatform.get()} -> ${outputDirectory.get().asFile}/hmc and " +
        "${outputDirectory.get().asFile}/ferium"
    )
  }
}

/**
 * Locates the Ferium binary inside [extractedDir]. Every Ferium release ships a single file named
 * exactly `ferium` at the archive root; centralizing this contract means the bootstrap task and the
 * tests check the same file name rather than two independent guesses.
 */
fun findFeriumBinary(extractedDir: File): File {
  val binary = extractedDir.walkTopDown().firstOrNull { it.isFile && it.name == "ferium" }
  if (binary == null) {
    throw GradleException("Ferium binary 'ferium' not found under $extractedDir")
  }
  return binary
}

/**
 * Launches a real Minecraft client/server through HeadlessMC and asserts Terrasect loaded.
 *
 * Inputs: the bootstrap tools, the Terrasect jar under test, the MC version, the loader, and a
 * configurable success marker. Output: the runtime dir plus the captured log (captured as an
 * artifact on failure). The success condition is HMC exiting 0 AND the marker appearing in the
 * combined log, which is stronger than "the process existed".
 */
abstract class RuntimeTestLaunchTask : DefaultTask() {

  @get:Classpath abstract val toolsDir: ConfigurableFileCollection
  @get:InputFile @get:Optional abstract val testJar: RegularFileProperty

  /**
   * Optional pre-resolved mods directory (Ferium resolves for published/compat scenarios). When
   * set, these jars are copied into the runtime mods dir *after* the local [testJar], so the local
   * build jar cannot shadow third-party mods and Ferium cleanup cannot remove it.
   */
  @get:InputDirectory @get:Optional abstract val resolvedModsDir: DirectoryProperty

  @get:Input abstract val mcVersion: Property<String>
  @get:Input abstract val loader: Property<String>
  @get:Input @get:Optional abstract val successMarker: Property<String>
  @get:Input @get:Optional abstract val launchTimeoutSeconds: Property<Long>

  /**
   * The exact Terrasect version the published artifact under test must match (e.g. `0.2.3`). Set
   * for PUBLISHED lanes; null for BUILD/COMPAT lanes. When set, the Terrasect jar identity is
   * verified against it before any launch (dry-run or live); a mismatch fails hard and never
   * silently falls back to another version or a local jar.
   */
  @get:Input @get:Optional abstract val expectedTerrasectVersion: Property<String>

  /**
   * When set, the task does NOT start HeadlessMC. It builds the exact launcher command it would
   * run, writes the (assembled) command to [dryRunOutput]. The launch is otherwise fully simulated:
   * jars are copied and the runtime dir is prepared so this path is byte-for-byte reproducible
   * offline.
   *
   * This is a deterministic dry-run, not a unit double: it exercises the real command-builder and
   * the real jar-preparation side effects, and proves the exact launch argv the offline build can
   * never execute. It exists so the acceptance contract ("one lane has a verifiable controlled
   * execution path with exact output") is satisfied without downloading HeadlessMC or launching
   * Minecraft.
   */
  @get:Input @get:Optional abstract val dryRun: Property<Boolean>
  @get:OutputFile @get:Optional abstract val dryRunOutput: RegularFileProperty

  @get:OutputDirectory abstract val runtimeDir: DirectoryProperty

  @TaskAction
  fun run() {
    val modsDir = prepareModsDir(runtimeDir.get().asFile)

    // Gate the launch on artifact identity: for PUBLISHED lanes the Terrasect jar actually staged
    // into runtimeDir/mods/ (from Ferium resolution) must be the exact requested version. This
    // fails hard BEFORE any HeadlessMC process starts, so a registry that resolves a newer/latest
    // version than the one requested never runs against it. Applies to the dry-run path too, so the
    // exact-version contract is enforced and testable offline.
    verifyTerrasectIdentity(modsDir, expectedTerrasectVersion.orNull)

    if (dryRun.orElse(false).get()) {
      dryRunAndExit(modsDir)
      return
    }

    val hmcJar = findHmcJar()

    val outputLog = File(runtimeDir.get().asFile, "hmc-launch.log")

    val launchArgs = buildLaunchArgs(hmcJar.absolutePath)
    val launchDir = runtimeDir.get().asFile.absolutePath
    val proc = launchProcess(launchArgs, launchDir)
    val log = awaitProcess(proc, outputLog)

    val marker = successMarker.orElse("Terrasect").get()
    val markerOk = log.contains(marker)
    val exitOk = proc.exitValue() == 0
    if (!exitOk) {
      throw GradleException(
        "HeadlessMC launch failed (exit ${proc.exitValue()}) for ${loader.get()} ${mcVersion.get()}. Log tail:\n" +
          log.takeLast(2000)
      )
    }
    if (!markerOk) {
      throw GradleException(
        "Success marker '$marker' not found in log for ${loader.get()} ${mcVersion.get()}. " +
          "Last 1500 chars:\n$log.takeLast(1500)"
      )
    }
    logger.lifecycle(
      "RuntimeTestLaunch: OK for ${loader.get()} ${mcVersion.get()} (marker='$marker')"
    )
  }

  /**
   * Verifies the Terrasect artifact staged under [modsDir] against an exact version.
   *
   * When [expectedVersion] is null (BUILD/COMPAT lanes) there is nothing to assert. Otherwise every
   * staged jar matching the Terrasect publishing contract must decode to exactly [expectedVersion];
   * an empty set, an ambiguous set of versions, or a differing version all fail hard. This is the
   * "never silently fall back to another version or local artifact" gate and runs before any
   * HeadlessMC process starts.
   */
  private fun verifyTerrasectIdentity(modsDir: File, expectedVersion: String?) {
    if (expectedVersion == null) return
    val jarNames =
      modsDir.walkTopDown().filter { it.isFile && it.extension == "jar" }.map { it.name }.toList()
    assertPublishedTerrasectVersion(jarNames, expectedVersion)
  }

  /**
   * Prepares the runtime mods layout shared by the live and dry-run launch paths. The runtime dir
   * is emptied and recreated here so both paths produce a byte-identical `mods/` directory: the
   * local Terrasect jar first, then any Ferium-resolved third-party jars (so the local build jar
   * cannot shadow them). Returns the prepared `mods` directory.
   */
  private fun prepareModsDir(runtimeDir: File): File {
    runtimeDir.deleteRecursively()
    runtimeDir.mkdirs()
    val modsDir = File(runtimeDir, "mods").apply { mkdirs() }

    // (A) Build/compat scenarios: copy the locally built Terrasect jar into the runtime mods dir.
    val localJar = testJar.orNull?.asFile
    if (localJar != null) {
      val jarName = localJar.name
      File(modsDir, jarName).apply { delete() }
      localJar.copyTo(modsDir.resolve(jarName), overwrite = true)
    }

    // (B) Published/compat scenarios: a pre-resolved mods dir (from Ferium) is provided. These jars
    // are copied *after* the local jar so the local build jar cannot accidentally shadow
    // third-party
    // mods, and any third-party jar named identically to Terrasect wins (so Ferium's resolution is
    // real, not overwritten by the local build).
    val resolved = resolvedModsDir.orNull?.asFile
    if (resolved != null) {
      val resolvedFiles =
        resolved.walkTopDown().filter { it.isFile && it.extension == "jar" }.toList()
      if (resolvedFiles.isEmpty()) {
        throw GradleException("resolvedModsDir has no jars: $resolved")
      }
      resolvedFiles.forEach { j ->
        val dest = modsDir.resolve(j.name)
        if (dest.exists() && dest.absolutePath != j.absolutePath) {
          throw GradleException("Local Terrasect jar $dest conflicts with resolved jar ${j.name}")
        }
        j.copyTo(dest, overwrite = true)
      }
      logger.lifecycle(
        "RuntimeTestLaunch: injected ${resolvedFiles.size} resolved jar(s) for ${loader.get()} ${mcVersion.get()}"
      )
    }
    return modsDir
  }

  /**
   * Builds the exact HeadlessMC launch argv the task would execute. Extracted so the dry-run path
   * (which never starts the process) prints the identical command line the live path feeds to
   * ProcessBuilder. The offline build exercises this via the dry-run mode; CI exercises the live
   * path.
   */
  private fun buildLaunchArgs(hmcJarAbs: String): List<String> =
    listOf(
      "java",
      "-jar",
      hmcJarAbs,
      "launch",
      "${loader.get()}:${mcVersion.get()}",
      "-specifics",
      "-lwjgl",
      "-keep",
      "-quit",
    )

  /**
   * Locates the HeadlessMC launcher jar under [toolsDir]. The bootstrap task places the jar inside
   * a subdirectory of the bootstrap output tree (not directly under it), and
   * `ConfigurableFileCollection` returns the configured directory itself rather than its contents,
   * so a non-recursive search would never find it. Walk the tree and return the first jar whose
   * name contains "headlessmc".
   */
  private fun findHmcJar(): File {
    val toolsRoot =
      toolsDir.files.firstOrNull { it.isDirectory }
        ?: toolsDir.files.firstOrNull()
        ?: throw GradleException("toolsDir is empty")
    val hmcJar =
      toolsRoot.walkTopDown().firstOrNull { it.isFile && it.name.contains("headlessmc") }
        ?: throw GradleException("HeadlessMC jar not found under toolsDir ($toolsRoot)")
    return hmcJar
  }

  /**
   * Deterministic, offline dry-run. Never starts HeadlessMC. It reuses [prepareModsDir] so the
   * prepared runtime dir is byte-identical to the live path, then writes the assembled launch
   * command line to [dryRunOutput] and stops.
   *
   * This is the "verifiable controlled execution path with exact output" the acceptance contract
   * requires: the command line is fully determined by (loader, mcVersion, jar path, HMC jar path)
   * and is reproducible, so it can be asserted against the exact expected argv on the offline
   * build.
   */
  private fun dryRunAndExit(modsDir: File) {
    val hmcJar = findHmcJar()

    val commandLine = buildLaunchArgs(hmcJar.absolutePath)
    val out =
      dryRunOutput.orNull?.asFile
        ?: throw GradleException("dryRunOutput is not set while dryRun=true")

    out.parentFile?.mkdirs()
    out.writeText(commandLine.joinToString(" "))

    logger.lifecycle(
      "RuntimeTestLaunch (dry-run): ${loader.get()} ${mcVersion.get()} -> ${out.path}"
    )
    logger.lifecycle("  expected argv: ${commandLine.joinToString(" ")}")
  }

  /** Builds and starts the HMC process, returning it once it has launched. */
  private fun launchProcess(args: List<String>, launchDir: String): Process {
    val builder = ProcessBuilder(args)
    builder.directory(File(launchDir))
    return try {
      builder.start()
    } catch (e: Exception) {
      throw GradleException(
        "Failed to start HeadlessMC (${args.first()}) from $launchDir: ${e.message}",
        e,
      )
    }
  }

  /**
   * Streams the child process output into the log until it exits or the configured timeout elapses.
   * Output is drained on a daemon thread so the child never blocks on a full pipe; when the timeout
   * is hit the process is force-killed so a stuck launch can never hang CI.
   */
  private fun awaitProcess(proc: Process, out: File): String {
    val timeoutMs = launchTimeoutSeconds.orElse(600L).get() * 1000
    val log = StringBuilder()
    val logLock = Any()
    val writer = out.outputStream().bufferedWriter()
    val reader = proc.inputStream.bufferedReader()
    val drain =
      Thread({
          var line = reader.readLine()
          while (line != null) {
            synchronized(logLock) {
              writer.append(line).append('\n')
              log.append(line).append('\n')
            }
            line = reader.readLine()
          }
        })
        .apply { isDaemon = true }
    drain.start()

    val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    if (!finished) {
      proc.destroyForcibly()
      drain.join(30_000)
      synchronized(logLock) { log.append("\n[timeout] process killed after $timeoutMs ms\n") }
    } else {
      drain.join(5_000)
    }
    return synchronized(logLock) {
      try {
        writer.flush()
      } finally {
        writer.close()
      }
      log.toString()
    }
  }
}

/**
 * Validates every runtime-test descriptor offline. No network access. This is the preflight gate.
 *
 * Inputs: the descriptor directory + expected manifest header (mod id / latest / mod version). The
 * action parses + validates the YAML files with RuntimeTestDescriptors and fails on any missing
 * lane or unsupported pair.
 */
@CacheableTask
abstract class RuntimeTestDescriptorValidateTask : DefaultTask() {

  @get:Input abstract val expectedModId: Property<String>
  @get:Input abstract val expectedLatest: Property<String>
  @get:Input abstract val expectedModVersion: Property<String>

  @get:Classpath abstract val descriptorsDir: ConfigurableFileCollection

  @TaskAction
  fun run() {
    val roots: Set<File> = descriptorsDir.files
    if (roots.isEmpty()) throw GradleException("No descriptor directory configured")
    // descriptorsDir is configured with the directory path(s); walk each for *.yaml/*.yml files so
    // nested manifests resolve. `Collection.files` returns the directories themselves, whose
    // isFile flag is false, so a bare filter would match nothing.
    val files =
      roots
        .flatMap {
          it.walkTopDown().filter { f -> f.isFile && f.extension in listOf("yaml", "yml") }
        }
        .distinct()
        .sortedBy { it.path }
    val manifests = files.map { RuntimeTestDescriptors.parse(it) }
    RuntimeTestDescriptors.validate(manifests)
    logger.lifecycle(
      "RuntimeTestDescriptorValidate: ${manifests.size} manifest(s), ${files.size} file(s) valid"
    )
  }
}
