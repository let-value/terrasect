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
import org.junit.jupiter.api.Assertions.assertNull
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
  fun `prepare workspace stages a local build jar in Ferium user directory`() {
    val workspace = rootDir.resolve("build/build/ferium-prepare-${System.nanoTime()}")
    val localJar = File(workspace.parentFile, "terrasect-build.jar")
    try {
      assertTrue(workspace.mkdirs())
      localJar.writeText("local-terrasect")

      val profile =
        prepareFeriumWorkspace(
          workspace,
          File(workspace, "mods"),
          "fabric",
          "26.2",
          listOf(modEntry("MODRINTH", "glitchcore", "GlitchCore")),
          localJar,
        )

      assertTrue(profile.exists(), "Ferium profile should be written")
      assertTrue(File(workspace, "mods/user/${localJar.name}").exists())
      assertTrue(
        profile.readText().contains("\"output_dir\": \"${File(workspace, "mods").absolutePath}")
      )
    } finally {
      workspace.deleteRecursively()
      localJar.delete()
    }
  }

  @Test
  fun `Ferium download command uses the bootstrapped binary and isolated config`() {
    assertEquals(
      listOf("/tools/ferium", "--config-file", "/workspace/ferium-profile.json", "upgrade"),
      feriumUpgradeArgs(File("/tools/ferium"), File("/workspace/ferium-profile.json")),
    )
  }

  @Test
  fun `Ferium download times out instead of hanging`() {
    val fixture = rootDir.resolve("build/build/ferium-timeout-${System.nanoTime()}")
    try {
      assertTrue(fixture.mkdirs())
      val workspace = File(fixture, "workspace")
      prepareFeriumWorkspace(workspace, File(workspace, "mods"), "fabric", "26.2", emptyList())

      val tools = File(fixture, "tools").apply { mkdirs() }
      File(tools, "ferium").apply {
        writeText("#!/bin/sh\nsleep 5\n")
        setExecutable(true)
      }

      val mainClasses = buildSrcMainClasses.absolutePath.replace("\\", "\\\\")
      val fixturePath = fixture.absolutePath.replace("\\", "\\\\")
      File(fixture, "settings.gradle.kts").writeText("rootProject.name = \"ferium-timeout-fix\"")
      File(fixture, "build.gradle.kts")
        .writeText(
          """
          import java.io.File

          buildscript {
            dependencies { classpath(files("$mainClasses")) }
          }

          val root = File("$fixturePath")
          tasks.register("downloadFixture", RuntimeTestFeriumDownloadTask::class.java) {
            toolsDir.from(root.resolve("tools"))
            profileFile.set(root.resolve("workspace/ferium-profile.json"))
            userDirectory.set(root.resolve("workspace/mods/user"))
            loader.set("fabric")
            mcVersion.set("26.2")
            scenarioLabel.set("compat-fabric-26.2")
            mods.set(emptyList())
            dryRun.set(false)
            timeoutSeconds.set(1L)
            outputDirectory.set(root.resolve("workspace/mods"))
            resolveManifest.set(root.resolve("workspace/manifest.txt"))
          }
          """
            .trimIndent()
        )

      val result = runner(fixture, listOf("downloadFixture")).buildAndFail()
      assertEquals(TaskOutcome.FAILED, result.task(":downloadFixture")!!.outcome)
      assertTrue(result.output.contains("timed out"), "failure must report the Ferium timeout")
    } finally {
      fixture.deleteRecursively()
    }
  }

  @Test
  fun `Ferium manifests only include active output jars`() {
    val output = rootDir.resolve("build/build/ferium-output-${System.nanoTime()}")
    try {
      assertTrue(output.mkdirs())
      val active = File(output, "active.jar")
      val stale = File(output, ".old/stale.jar")
      val user = File(output, "user/local.jar")
      active.writeText("active")
      stale.parentFile.mkdirs()
      stale.writeText("stale")
      user.parentFile.mkdirs()
      user.writeText("user")

      assertEquals(listOf(active), activeFeriumOutputJars(output))
    } finally {
      output.deleteRecursively()
    }
  }

  @Test
  fun `download task runs after preparation and consumes the staged user jar`() {
    val fixture = rootDir.resolve("build/build/ferium-download-${System.nanoTime()}")
    try {
      assertTrue(fixture.mkdirs())
      val workspace = File(fixture, "workspace")
      val localJar = File(fixture, "terrasect-local.jar").apply { writeText("local") }
      prepareFeriumWorkspace(
        workspace,
        File(workspace, "mods"),
        "fabric",
        "26.2",
        emptyList(),
        localJar,
      )

      val tools = File(fixture, "tools").apply { mkdirs() }
      val downloaded = File(workspace, "mods/downloaded.jar")
      val script =
        File(tools, "ferium").apply {
          writeText(
            """
            #!/bin/sh
            mkdir -p '${downloaded.parentFile.absolutePath}'
            printf downloaded > '${downloaded.absolutePath}'
            cp "${'$'}PWD/mods/user/"*.jar '${downloaded.parentFile.absolutePath}/' 2>/dev/null || true
            """
              .trimIndent()
          )
          setExecutable(true)
        }
      assertTrue(script.canExecute())

      val mainClasses = buildSrcMainClasses.absolutePath.replace("\\", "\\\\")
      val fixturePath = fixture.absolutePath.replace("\\", "\\\\")
      File(fixture, "settings.gradle.kts").writeText("rootProject.name = \"ferium-download-fix\"")
      File(fixture, "build.gradle.kts")
        .writeText(
          """
          import java.io.File

          buildscript {
            dependencies { classpath(files("$mainClasses")) }
          }

          val root = File("$fixturePath")
          tasks.register("downloadFixture", RuntimeTestFeriumDownloadTask::class.java) {
            toolsDir.from(root.resolve("tools"))
            profileFile.set(root.resolve("workspace/ferium-profile.json"))
            userDirectory.set(root.resolve("workspace/mods/user"))
            loader.set("fabric")
            mcVersion.set("26.2")
            scenarioLabel.set("compat-fabric-26.2")
            mods.set(emptyList())
            dryRun.set(false)
            outputDirectory.set(root.resolve("workspace/mods"))
            resolveManifest.set(root.resolve("workspace/manifest.txt"))
          }
          """
            .trimIndent()
        )

      val result = runner(fixture, listOf("downloadFixture")).build()
      assertEquals(TaskOutcome.SUCCESS, result.task(":downloadFixture")!!.outcome)
      assertTrue(downloaded.exists(), "Ferium should produce a downloaded jar")
      assertTrue(
        File(workspace, "mods/${localJar.name}").exists(),
        "Ferium should copy the local user jar into output_dir",
      )
      assertTrue(File(workspace, "resolve.log").exists())
      assertTrue(File(workspace, "manifest.txt").readText().contains("resolvedJars="))
    } finally {
      fixture.deleteRecursively()
    }
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

  // --- exact-version identity helpers (pure, fully offline) ---------------------------------

  @Test
  fun `version parses from both registry jar names`() {
    // Modrinth and CurseForge share the same published naming contract.
    assertEquals("0.2.3", terrasectVersionFromJarName("terrasect-neoforge-0.2.3+26.2.jar"))
    assertEquals("0.2.3", terrasectVersionFromJarName("terrasect-fabric-0.2.3+1.21.1.jar"))
  }

  @Test
  fun `non-terrasect jars parse to null`() {
    assertNull(terrasectVersionFromJarName("create-fly-1.2.jar"))
    assertNull(terrasectVersionFromJarName("no-plus-segment.jar"))
    assertFalse(isTerrasectJar("create-fly-1.2.jar"))
  }

  @Test
  fun `exact version matches for both platforms`() {
    // Modrinth lane (NeoForge) and CurseForge lane (NeoForge): a resolved 0.2.3 artifact matches.
    assertPublishedTerrasectVersion(
      listOf("terrasect-neoforge-0.2.3+26.2.jar"),
      "0.2.3",
    )
  }

  @Test
  fun `exact version mismatch fails hard`() {
    try {
      // Ferium resolved the latest 0.2.9, but the task pinned 0.2.3 — must refuse to run.
      assertPublishedTerrasectVersion(
        listOf("terrasect-neoforge-0.2.9+26.2.jar"),
        "0.2.3",
      )
      throw AssertionError("expected GradleException for exact version mismatch")
    } catch (e: Exception) {
      assertTrue(
        e.message!!.contains("Exact version mismatch"),
        "failure must name the exact version mismatch",
      )
    }
  }

  @Test
  fun `empty resolution and ambiguity fail hard`() {
    try {
      assertPublishedTerrasectVersion(listOf("create-fly-1.2.jar"), "0.2.3")
      throw AssertionError("expected GradleException when no Terrasect artifact resolved")
    } catch (e: Exception) {
      assertTrue(
        e.message!!.contains("No Terrasect artifact"),
        "failure must report no Terrasect artifact resolved",
      )
    }

    try {
      assertPublishedTerrasectVersion(
        listOf("terrasect-neoforge-0.2.3+26.2.jar", "terrasect-fabric-0.2.9+26.2.jar"),
        "0.2.3",
      )
      throw AssertionError("expected GradleException for ambiguous resolution")
    } catch (e: Exception) {
      assertTrue(
        e.message!!.contains("Ambiguous Terrasect"),
        "failure must report ambiguous Terrasect artifacts",
      )
    }
  }

  @Test
  fun `null expected version skips verification (compat lane)`() {
    // COMPAT lanes resolve third-party mods and carry no Terrasect artifact; verification is a
    // no-op so it must never throw.
    assertPublishedTerrasectVersion(listOf("create-fly-1.2.jar"), null)
  }
}
