// Reproducible, offline fixture proving the cacheable-bootstrap caching contract at the Gradle
// task level: warm UP-TO-DATE, then FROM-CACHE after clearing outputs, plus a checksum-mismatch
// failure and a missing-binary failure. It spins up a tiny fixture build (via TestKit) whose
// buildscript classpath includes the parent buildSrc — with the parent buildSrc main classes added
// explicitly so the real default-package `RuntimeTestBootstrapTask` is runnable — and runs the
// @CacheableTask against a local file:// fixture (no network).

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeTestBootstrapCacheTest {

  private val rootDir = File(System.getProperty("user.dir"))

  /**
   * The parent buildSrc's compiled main classes (real default-package `RuntimeTestBootstrapTask`).
   * When this test runs inside `:buildSrc:test`, `user.dir` is already the buildSrc project dir, so
   * the classes live at `build/classes/kotlin/main` relative to it (not `buildSrc/build/...`).
   */
  private val buildSrcMainClasses: File
    get() = rootDir.resolve("build/classes/kotlin/main")

  /** sha256 of [file]'s bytes. */
  private fun sha256(file: File): String {
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

  /** Builds a zip containing a single entry named exactly `ferium` (a valid "binary"). */
  private fun buildBinaryZip(dir: File, name: String, entryName: String = "ferium"): String {
    val zip = File(dir, name)
    ZipOutputStream(zip.outputStream()).use { z ->
      z.putNextEntry(ZipEntry(entryName))
      z.write("fake-binary-bytes".toByteArray())
      z.closeEntry()
    }
    return sha256(zip)
  }

  private fun writeScript(
    fixture: File,
    hmcZip: String,
    hmcSha: String,
    feriumSha: String,
    outDir: File,
  ) {
    val hmcZipAbs = hmcZip
    val outDirAbs = outDir.absolutePath.replace("\\", "\\\\")
    val mainClasses = buildSrcMainClasses.absolutePath.replace("\\", "\\\\")
    File(fixture, "settings.gradle.kts").writeText("rootProject.name = \"bootstrap-fix\"")
    File(fixture, "build.gradle.kts")
      .writeText(
        """
      import java.io.File

      buildscript {
        dependencies {
          classpath(files("$mainClasses"))
        }
      }

      val hmcSha = "$hmcSha"
      val feriumSha = "$feriumSha"
      val outDir = File("$outDirAbs")

      tasks.register("bootstrapFixture", RuntimeTestBootstrapTask::class.java) {
        group = "runtime test"
        hmcUrl.set("${File(hmcZipAbs).toURI().toString()}")
        hmcSha256.set(hmcSha)
        feriumUrl.set("${File(hmcZipAbs).toURI().toString()}")
        feriumSha256.set(feriumSha)
        feriumPlatform.set("linux")
        outputDirectory.set(outDir)
      }
      """
          .trimIndent()
      )
  }

  private fun runner(fixture: File, args: List<String>): GradleRunner =
    GradleRunner.create()
      .withProjectDir(fixture)
      .withArguments("--build-cache", *args.toTypedArray())
      .forwardOutput()

  @Test
  fun `cacheable bootstrap runs once, then UP-TO-DATE, then FROM-CACHE`() {
    val fixture = rootDir.resolve("build/build/cache-fix-${System.nanoTime()}")
    try {
      assertTrue(fixture.mkdirs())
      val outDir = File(fixture, "out")
      val hmcZip = File(fixture, "hmc.zip").absolutePath
      val sha = buildBinaryZip(fixture, "hmc.zip")
      writeScript(fixture, hmcZip, sha, sha, outDir)

      // 1) First run executes the real @CacheableTask and caches its output.
      val first = runner(fixture, listOf("bootstrapFixture")).build()
      assertEquals(TaskOutcome.SUCCESS, first.task(":bootstrapFixture")!!.outcome)
      assertTrue(File(outDir, "hmc/hmc.zip").exists(), "HMC should retain the URL basename")
      assertTrue(File(outDir, "ferium/ferium").exists(), "ferium binary should be extracted")

      // 2) Re-running with inputs unchanged and outputs present -> UP-TO-DATE.
      val second = runner(fixture, listOf("bootstrapFixture")).build()
      assertEquals(TaskOutcome.UP_TO_DATE, second.task(":bootstrapFixture")!!.outcome)

      // 3) Clear the outputs but keep inputs identical -> served FROM-CACHE (no exec).
      outDir.deleteRecursively()
      val cached = runner(fixture, listOf("bootstrapFixture")).build()
      assertEquals(TaskOutcome.FROM_CACHE, cached.task(":bootstrapFixture")!!.outcome)
      // FROM-CACHE restores the outputs, so the binary should be back.
      assertTrue(File(outDir, "hmc/hmc.zip").exists(), "FROM-CACHE should restore the HMC jar")
      assertTrue(File(outDir, "ferium/ferium").exists(), "FROM-CACHE should restore outputs")
    } finally {
      if (fixture.exists()) fixture.deleteRecursively()
    }
  }

  @Test
  fun `bootstrap fails on a checksum mismatch`() {
    val fixture = rootDir.resolve("build/build/cache-mismatch-${System.nanoTime()}")
    try {
      assertTrue(fixture.mkdirs())
      val outDir = File(fixture, "out")
      val hmcZip = File(fixture, "hmc.zip").absolutePath
      val sha = buildBinaryZip(fixture, "hmc.zip")
      val wrongSha = "0000000000000000000000000000000000000000000000000000000000000000"
      writeScript(fixture, hmcZip, wrongSha, sha, outDir)

      val result = runner(fixture, listOf("bootstrapFixture")).buildAndFail()
      assertEquals(TaskOutcome.FAILED, result.task(":bootstrapFixture")?.outcome)
      assertEquals(1, result.tasks?.count { it.outcome == TaskOutcome.FAILED })
    } finally {
      if (fixture.exists()) fixture.deleteRecursively()
    }
  }

  @Test
  fun `bootstrap fails when the Ferium binary is missing after extract`() {
    val fixture = rootDir.resolve("build/build/cache-missing-${System.nanoTime()}")
    try {
      assertTrue(fixture.mkdirs())
      val outDir = File(fixture, "out")
      // A valid zip whose only entry is NOT named `ferium`, so missing-binary detection must fire.
      val sha = buildBinaryZip(fixture, "hmc.zip", "notes.txt")
      writeScript(fixture, sha, sha, sha, outDir)

      val result = runner(fixture, listOf("bootstrapFixture")).buildAndFail()
      assertEquals(TaskOutcome.FAILED, result.task(":bootstrapFixture")?.outcome)
      assertEquals(1, result.tasks?.count { it.outcome == TaskOutcome.FAILED })
    } finally {
      if (fixture.exists()) fixture.deleteRecursively()
    }
  }
}
