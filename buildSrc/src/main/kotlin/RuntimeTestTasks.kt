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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
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

fun unzip(zip: File, destination: File) {
  if (!destination.isDirectory && !destination.mkdirs()) {
    throw GradleException("Could not create ${destination}")
  }
  ZipInputStream(zip.inputStream()).use { zipIn ->
    while (true) {
      val entry = zipIn.nextEntry ?: break
      val out = File(destination, entry.name)
      // Guard against path traversal inside the archive.
      if (!out.absolutePath.startsWith(destination.absolutePath)) {
        throw GradleException("Zip entry escapes destination: ${entry.name}")
      }
      if (entry.isDirectory) {
        out.mkdirs()
      } else {
        out.parentFile?.mkdirs()
        out.outputStream().use { zipIn.copyTo(it) }
        if (entry.name.endsWith("ferium") || entry.name.endsWith("ferium-nogui")) {
          out.setExecutable(true, false)
        }
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

    // Ferium is a zip archive — download, verify, extract the nosui binary.
    val feriumZip = File(downloadDir, "ferium-download.zip")
    downloadToFile(feriumUrl.get(), feriumZip)
    expectSha256(feriumZip, feriumSha256.get())
    unzip(feriumZip, feriumDir)
    val binary =
      feriumDir.walkTopDown().firstOrNull {
        it.isFile && (it.name.startsWith("ferium") || it.name.contains("gui"))
      } ?: throw GradleException("Ferium binary not found after extracting ${feriumZip}")
    binary.setExecutable(true, false)
    logger.lifecycle(
      "RuntimeTestBootstrap: HMC ${hmcSha256.get().take(8)}+, Ferium ${feriumSha256.get().take(8)}+"
    )
  }
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
  @get:Input @get:Optional abstract val testJar: RegularFileProperty

  /**
   * Optional pre-resolved mods directory (Ferium resolves for published/compat scenarios). When
   * set, these jars are copied into the runtime mods dir *after* the local [testJar], so the local
   * build jar cannot shadow third-party mods and Ferium cleanup cannot remove it.
   */
  @get:InputDirectory abstract val resolvedModsDir: DirectoryProperty

  @get:Input abstract val mcVersion: Property<String>
  @get:Input abstract val loader: Property<String>
  @get:Input @get:Optional abstract val successMarker: Property<String>
  @get:Input @get:Optional abstract val launchTimeoutSeconds: Property<Long>

  @get:OutputDirectory abstract val runtimeDir: DirectoryProperty

  @TaskAction
  fun run() {
    val runtimeDir = runtimeDir.get().asFile
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

    val hmcJar =
      toolsDir.files.firstOrNull { it.isFile && it.name.contains("headlessmc") }
        ?: throw GradleException("HeadlessMC jar not found under toolsDir")

    val outputLog = File(runtimeDir, "hmc-launch.log")

    val launchArgs =
      listOf(
        "java",
        "-jar",
        hmcJar.absolutePath,
        "launch",
        "${loader.get()}:${mcVersion.get()}",
        "-specifics",
        "-lwjgl",
        "-keep",
        "-quit",
      )
    val launchDir = runtimeDir.absolutePath
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
