// Offline fixture for the descriptor-driven Ferium resolution slice.
//
// The real resolve path shells out to `ferium upgrade`, which needs network access to Modrinth /
// CurseForge — not something that can run in a CI unit/Kit test. This file therefore proves the two
// offline-controllable halves of the contract:
//
//  1. `writeFeriumConfig` renders a repository-owned, schema-valid Ferium profile for both Modrinth
//     and CurseForge fixtures (no credentials baked in). This is the exact config string the task
//     writes before it ever touches the network.
//  2. The `RuntimeTestFeriumResolveTask` in its dry-run mode (no download, no process spawn) writes
//     that config + a deterministic manifest into its output dir, verified end-to-end through
//     TestKit with the real default-package task on the fixture buildscript classpath.

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeTestFeriumResolveTest {

  private val rootDir = File(System.getProperty("user.dir"))

  /** Parent buildSrc compiled main classes (real default-package resolve task + helpers). */
  private val buildSrcMainClasses: File
    get() = rootDir.resolve("build/classes/kotlin/main")

  // --- writeFeriumConfig unit coverage -------------------------------------------------------

  @Test
  fun `config renders fabric + Modrinth identifier with no credentials`() {
    val mods = listOf(modEntry("MODRINTH", "glitchcore", "GlitchCore", "CO7NeLTt"))
    val cfg = writeFeriumConfig("/out/mods", "fabric", "26.2", mods)

    assertTrue(cfg.contains("\"mod_loader\": \"Fabric\""), "loader must be PascalCase Fabric")
    assertTrue(cfg.contains("\"game_version\": \"26.2\""), "must pin the MC version")
    assertTrue(
      cfg.contains("\"ModrinthProject\":\"glitchcore\""),
      "Modrinth identifier must be the slug",
    )
    // The human version id is review metadata only — it must NOT leak into the on-disk config.
    assertFalse(cfg.contains("CO7NeLTt"), "version id must not be written into the config")
    // No secret anywhere in the generated config.
    assertFalse(cfg.contains("API_KEY"), "no credential may be embedded in the config")
  }

  @Test
  fun `config renders NeoForge + numeric CurseForge identifier`() {
    val mods = listOf(modEntry("CURSEFORGE", "1615147", "Terrasect"))
    val cfg = writeFeriumConfig("/out/mods", "neoforge", "26.2", mods)

    assertTrue(cfg.contains("\"mod_loader\": \"NeoForge\""), "loader must be PascalCase NeoForge")
    assertTrue(
      cfg.contains("\"CurseForgeProject\":1615147"),
      "CF identifier must be the numeric id",
    )
    assertFalse(cfg.contains("\"ModrinthProject\""), "no Modrinth identifier for a CF-only lane")
  }

  @Test
  fun `malformed mod entry is rejected`() {
    val bad = listOf("NOT-ENOUGH-FIELDS")
    try {
      writeFeriumConfig("/out/mods", "fabric", "26.2", bad)
      throw AssertionError("expected GradleException for malformed mod entry")
    } catch (e: Exception) {
      assertTrue(
        e.message!!.contains("Malformed mod entry"),
        "failure must name the malformed entry",
      )
    }
  }

  // --- task-level dry-run coverage via TestKit ------------------------------------------------

  private fun runner(fixture: File, args: List<String>): GradleRunner =
    GradleRunner.create()
      .withProjectDir(fixture)
      .withArguments("--build-cache", *args.toTypedArray())
      .forwardOutput()

  private fun writeResolveScript(
    fixture: File,
    outDir: File,
    label: String,
    modsArg: String,
  ) {
    val outDirAbs = outDir.absolutePath.replace("\\", "\\\\")
    val mainClasses = buildSrcMainClasses.absolutePath.replace("\\", "\\\\")
    File(fixture, "settings.gradle.kts").writeText("rootProject.name = \"resolve-fix\"")
    File(fixture, "build.gradle.kts")
      .writeText(
        """
        import java.io.File

        buildscript {
          dependencies {
            classpath(files("$mainClasses"))
          }
        }

        val outDir = File("$outDirAbs")

        tasks.register("resolveFixture", RuntimeTestFeriumResolveTask::class.java) {
          group = "runtime test"
          loader.set("fabric")
          mcVersion.set("26.2")
          scenarioLabel.set("$label")
          mods.set($modsArg)
          dryRun.set(true)
          outputDirectory.set(outDir.resolve("resolve-out"))
          resolveManifest.set(outDir.resolve("$label.txt"))
        }
        """
          .trimIndent()
      )
  }

  @Test
  fun `dry-run resolve writes config + deterministic manifest, no download`() {
    val fixture = rootDir.resolve("build/build/resolve-fix-${System.nanoTime()}")
    try {
      assertTrue(fixture.mkdirs())
      val outDir = File(fixture, "out")
      val modsArg = """listOf(modEntry("MODRINTH", "glitchcore", "GlitchCore", "CO7NeLTt"))"""
      writeResolveScript(fixture, outDir, "compat-fabric-26.2", modsArg)

      val result = runner(fixture, listOf("resolveFixture")).build()
      assertEquals(TaskOutcome.SUCCESS, result.task(":resolveFixture")!!.outcome)

      // The isolated config was written into the deterministic output dir.
      val cfg = File(outDir, "resolve-out/ferium-profile.json")
      assertTrue(cfg.exists(), "Ferium config must be written to the output dir")
      val cfgText = cfg.readText()
      assertTrue(
        cfgText.contains("\"ModrinthProject\":\"glitchcore\""),
        "config must declare the mod",
      )
      assertTrue(cfgText.contains("GlitchCore"), "config must carry the human-readable name")

      // The deterministic manifest was written and names the lane + declared mod.
      val manifest = File(outDir, "compat-fabric-26.2.txt")
      assertTrue(manifest.exists(), "deterministic manifest must be written")
      val manifestText = manifest.readText()
      assertTrue(
        manifestText.contains("scenario=compat-fabric-26.2"),
        "manifest must name the lane",
      )
      assertTrue(
        manifestText.contains("GlitchCore (MODRINTH/glitchcore)"),
        "manifest must list the declared mod",
      )
      assertTrue(manifestText.contains("resolvedJars=[]"), "dry-run must resolve nothing")

      // Dry-run must NOT spawn Ferium: no resolve.log is produced.
      assertFalse(File(outDir, "resolve-out/resolve.log").exists(), "dry-run must not run Ferium")
    } finally {
      if (fixture.exists()) fixture.deleteRecursively()
    }
  }
}
