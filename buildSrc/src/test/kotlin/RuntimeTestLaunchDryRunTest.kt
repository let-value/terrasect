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

      // Fake HeadlessMC jar + a fake Terrasect jar. The task searches toolsDir for a jar whose
      // name contains "headlessmc", so the fake must carry that substring.
      val hmcZip = File(fixture, "headlessmc-launcher.jar")
      hmcZip.writeText("fake-hmc")
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
          toolsDir.from(hmcZip.parentFile)
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
}
