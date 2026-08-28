// Offline test for the deterministic dry-run path of RuntimeTestLaunchTask.
//
// The real launch path starts HeadlessMC and boots Minecraft — neither of which can run offline in
// a unit/Kit test. This test proves the *dry-run* branch the acceptance card calls out ("one
// selected
// lane has a verifiable dry-run or controlled execution path with exact output"): it wires a
// fixture
// build whose buildscript classpath includes the parent buildSrc main classes (so the real
// default-package `RuntimeTestLaunchTask` is runnable), registers `runtimeTestLaunch` with
// `dryRun = true`, runs it through TestKit, and asserts the exact assembled command line the live
// path would feed to ProcessBuilder lands in the dry-run output file byte-for-byte.
//
// No network, no Minecraft, no process spawn — the dry-run branch never starts HeadlessMC.

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeTestLaunchDryRunTest {

  private val rootDir = File(System.getProperty("user.dir"))

  /**
   * The parent buildSrc's compiled main classes (real default-package `RuntimeTestLaunchTask`).
   * When this test runs inside `:buildSrc:test`, `user.dir` is already the buildSrc project dir, so
   * the classes live at `build/classes/kotlin/main` relative to it (not `buildSrc/build/...`).
   */
  private val buildSrcMainClasses: File
    get() = rootDir.resolve("build/classes/kotlin/main")

  private fun runner(fixture: File, args: List<String>): GradleRunner =
    GradleRunner.create()
      .withProjectDir(fixture)
      .withArguments("--build-cache", *args.toTypedArray())
      .forwardOutput()

  /**
   * The exact command line the real path builds. It is `java -jar <hmcJar> launch <loader>:<mc>
   * -specifics -lwjgl -keep -quit` joined with single spaces — this mirrors `buildLaunchArgs` in
   * RuntimeTestTasks.kt and is the byte-for-byte expected dry-run output.
   */
  private fun expectedCommandLine(hmcJarAbs: String, loader: String, mc: String): String =
    listOf(
        "java",
        "-jar",
        hmcJarAbs,
        "launch",
        "$loader:$mc",
        "-offline",
        "-specifics",
        "-lwjgl",
        "-keep",
        "-quit",
      )
      .joinToString(" ")

  @Test
  fun `dry-run assembles the exact launch command line for fabric 2622`() {
    val fixture = rootDir.resolve("build/build/dryrun-${System.nanoTime()}")
    try {
      assertTrue(fixture.mkdirs())
      val outDir = File(fixture, "out")
      val mainClasses = buildSrcMainClasses.absolutePath.replace("\\", "\\\\")

      // The dry-run must not require bootstrap output. It uses the pinned HMC path when the
      // bootstrap directory is absent.
      val hmcZip = File(outDir, "bootstrap/hmc/headlessmc-launcher-2.10.0.jar")
      val terrasectJar = File(fixture, "terrasect-0.2.3.jar")
      terrasectJar.writeText("fake-terrasect")

      val hmcZipAbs = hmcZip.absolutePath.replace("\\", "\\\\")
      val terrasectJarAbs = terrasectJar.absolutePath.replace("\\", "\\\\")
      val outDirAbs = outDir.absolutePath.replace("\\", "\\\\")

      File(fixture, "settings.gradle.kts").writeText("rootProject.name = \"dryrun-fix\"")
      File(fixture, "build.gradle.kts")
        .writeText(
          """
        import java.io.File

        buildscript {
          dependencies {
            classpath(files("$mainClasses"))
          }
        }

        val hmcZip = File("$hmcZipAbs")
        val terrasectJar = File("$terrasectJarAbs")
        val outDir = File("$outDirAbs")

        tasks.register("runtimeTestLaunch", RuntimeTestLaunchTask::class.java) {
          group = "runtime test"
          mcVersion.set("26.2.x")
          loader.set("fabric")
          successMarker.set("Terrasect")
          launchTimeoutSeconds.set(900L)
          dryRun.set(true)
          toolsDir.from(File("$outDirAbs/bootstrap"))
          runtimeDir.set(File(outDir, "runtime-fabric-26.2.x"))
          dryRunOutput.set(File(outDir, "dryrun-fabric-26.2.x.cmd"))
          testJar.set(terrasectJar)
        }
        """
            .trimIndent()
        )

      val result = runner(fixture, listOf("runtimeTestLaunch")).build()

      val outcome = result.task(":runtimeTestLaunch")!!.outcome
      assertEquals(org.gradle.testkit.runner.TaskOutcome.SUCCESS, outcome)

      // The dry-run must write an output file (no process spawn, deterministic output).
      val outputFile = File(outDir, "dryrun-fabric-26.2.x.cmd")
      assertTrue(outputFile.exists(), "dry-run output file should be written")

      val expected = expectedCommandLine(hmcZip.absolutePath, "fabric", "26.2.x")
      assertEquals(
        expected,
        outputFile.readText().trim(),
        "exact launch command line should be written",
      )

      // The local Terrasect jar must be prepared in the runtime mods dir (shared side effect).
      val modsDir = File(outDir, "runtime-fabric-26.2.x/mods")
      assertTrue(
        File(modsDir, terrasectJar.name).exists(),
        "Terrasect jar should be copied into mods/",
      )

      // The live path must never have started: no HMC log should exist (dry-run bypasses it).
      val logFile = File(outDir, "runtime-fabric-26.2.x/hmc-launch.log")
      assertTrue(!logFile.exists(), "dry-run must not produce the live HMC launch log")
    } finally {
      if (fixture.exists()) fixture.deleteRecursively()
    }
  }

  /**
   * The exact-version identity gate must fail the launch (even in dry-run) when the resolved
   * published Terrasect jar does not match the requested version. This is the offline, controlled
   * proof of the acceptance contract's "exact-version mismatch fails" and "never silently fall back
   * to another version or local artifact" clauses: the launch task verifies before any process
   * starts, and a wrong-version resolved jar aborts the run.
   */
  @Test
  fun `launch dry-run fails when resolved Terrasect version does not match exact requested version`() {
    val fixture = rootDir.resolve("build/build/published-mismatch-${System.nanoTime()}")
    try {
      assertTrue(fixture.mkdirs())
      val outDir = File(fixture, "out")
      val mainClasses = buildSrcMainClasses.absolutePath.replace("\\", "\\\\")

      // Fake HeadlessMC jar (name must contain "headlessmc" for findHmcJar).
      val hmcZip = File(fixture, "headlessmc-launcher.jar")
      hmcZip.writeText("fake-hmc")

      // A resolved published Terrasect jar whose version (0.2.9) does NOT match the requested
      // 0.2.3.
      val resolvedMods = File(fixture, "resolved").apply { mkdirs() }
      File(resolvedMods, "terrasect-neoforge-0.2.9+26.2.jar").writeText("wrong-version")

      val hmcZipAbs = hmcZip.absolutePath.replace("\\", "\\\\")
      val resolvedDirAbs = resolvedMods.absolutePath.replace("\\", "\\\\")
      val outDirAbs = outDir.absolutePath.replace("\\", "\\\\")

      File(fixture, "settings.gradle.kts")
        .writeText("rootProject.name = \"published-mismatch-fix\"")
      File(fixture, "build.gradle.kts")
        .writeText(
          """
          import java.io.File

          buildscript {
            dependencies {
              classpath(files("$mainClasses"))
            }
          }

          val hmcZip = File("$hmcZipAbs")
          val resolvedMods = File("$resolvedDirAbs")
          val outDir = File("$outDirAbs")

          tasks.register("runtimeTestLaunch", RuntimeTestLaunchTask::class.java) {
            group = "runtime test"
            mcVersion.set("26.2.x")
            loader.set("neoforge")
            successMarker.set("Terrasect")
            launchTimeoutSeconds.set(1200L)
            dryRun.set(true)
            expectedTerrasectVersion.set("0.2.3")
            toolsDir.from(hmcZip.parentFile)
            runtimeDir.set(File(outDir, "runtime-neoforge-published-26.2.x"))
            dryRunOutput.set(File(outDir, "dryrun-neoforge-published-26.2.x.cmd"))
            resolvedModsDir.set(resolvedMods)
          }
          """
            .trimIndent()
        )

      val result = runner(fixture, listOf("runtimeTestLaunch")).buildAndFail()
      assertEquals(
        org.gradle.testkit.runner.TaskOutcome.FAILED,
        result.task(":runtimeTestLaunch")!!.outcome,
        "mismatched published version must fail the launch",
      )
      assertTrue(
        result.output.contains("Exact version mismatch"),
        "failure output must name the exact version mismatch",
      )
    } finally {
      if (fixture.exists()) fixture.deleteRecursively()
    }
  }

  /**
   * When the resolved published Terrasect jar version matches the requested exact version, the
   * dry-run launch must proceed past the identity gate and succeed — proving the gate is selective,
   * not a blanket failure.
   */
  @Test
  fun `launch dry-run succeeds when resolved Terrasect version matches exact requested version`() {
    val fixture = rootDir.resolve("build/build/published-match-${System.nanoTime()}")
    try {
      assertTrue(fixture.mkdirs())
      val outDir = File(fixture, "out")
      val mainClasses = buildSrcMainClasses.absolutePath.replace("\\", "\\\\")

      val hmcZip = File(fixture, "headlessmc-launcher.jar")
      hmcZip.writeText("fake-hmc")

      // A resolved published Terrasect jar whose version (0.2.3) DOES match the requested 0.2.3.
      val resolvedMods = File(fixture, "resolved").apply { mkdirs() }
      File(resolvedMods, "terrasect-neoforge-0.2.3+26.2.jar").writeText("right-version")

      val hmcZipAbs = hmcZip.absolutePath.replace("\\", "\\\\")
      val resolvedDirAbs = resolvedMods.absolutePath.replace("\\", "\\\\")
      val outDirAbs = outDir.absolutePath.replace("\\", "\\\\")

      File(fixture, "settings.gradle.kts").writeText("rootProject.name = \"published-match-fix\"")
      File(fixture, "build.gradle.kts")
        .writeText(
          """
          import java.io.File

          buildscript {
            dependencies {
              classpath(files("$mainClasses"))
            }
          }

          val hmcZip = File("$hmcZipAbs")
          val resolvedMods = File("$resolvedDirAbs")
          val outDir = File("$outDirAbs")

          tasks.register("runtimeTestLaunch", RuntimeTestLaunchTask::class.java) {
            group = "runtime test"
            mcVersion.set("26.2.x")
            loader.set("neoforge")
            successMarker.set("Terrasect")
            launchTimeoutSeconds.set(1200L)
            dryRun.set(true)
            expectedTerrasectVersion.set("0.2.3")
            toolsDir.from(hmcZip.parentFile)
            runtimeDir.set(File(outDir, "runtime-neoforge-published-26.2.x"))
            dryRunOutput.set(File(outDir, "dryrun-neoforge-published-26.2.x.cmd"))
            resolvedModsDir.set(resolvedMods)
          }
          """
            .trimIndent()
        )

      val result = runner(fixture, listOf("runtimeTestLaunch")).build()
      assertEquals(
        org.gradle.testkit.runner.TaskOutcome.SUCCESS,
        result.task(":runtimeTestLaunch")!!.outcome,
        "matching published version must pass the identity gate",
      )
    } finally {
      if (fixture.exists()) fixture.deleteRecursively()
    }
  }
}
