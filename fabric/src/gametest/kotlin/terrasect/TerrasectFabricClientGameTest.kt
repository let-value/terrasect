package terrasect

import java.nio.file.Path
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy

private val LOGGER = LoggerFactory.getLogger("NoiseConstraintsFabricGameTest")
private const val DISABLED_PRESET = "__disabled__"
private const val SEED = "noise-constraints"
private const val CUSTOM_PRESET_PREFIX = "noise_constraints_client_test"
private val PROBE_LOCATIONS = listOf(0 to 0, 512 to 0, 0 to 512, 512 to 512)

private val GAME_TEST_MODULE_DIR: Path by lazy {
  val classesRoot = Path.of(object {}.javaClass.protectionDomain.codeSource.location.toURI())
  classesRoot.parent.parent.parent.parent
}

private val GAME_TEST_SCREENSHOTS_BASE: Path by lazy {
  GAME_TEST_MODULE_DIR.resolve("build/gametest-screenshots/noise-constraints")
}

private data class Scenario(val name: String, val configure: RegionRegistry.() -> Unit)

@Suppress("UnstableApiUsage")
object TerrasectFabricClientGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val vanillaColumns = runWorld(context, DISABLED_PRESET, null)
    val scenarios =
      listOf(
        Scenario("depth-multiply-add") {
          region("border").noise {
            noise("depth") {
              it.multiply(0.5)
              it.add(0.2)
            }
          }
        },
        Scenario("continents-clamp") {
          region("border").noise { densityFunction("continents") { it.clamp(-0.2, 0.2) } }
        },
        Scenario("erosion-abs") { region("border").noise { noise("erosion") { it.abs() } } },
        Scenario("depth-squeeze") {
          region("border").noise { densityFunction("depth") { it.squeeze() } }
        },
        Scenario("temperature-remap") {
          region("border").noise { noise("temperature") { it.remap(-1.0, 1.0, -0.5, 0.5) } }
        },
        Scenario("ridges-half-negative") {
          region("border").noise { noise("ridges") { it.halfNegative() } }
        },
        // Real-world worldgen-affecting cases: these change terrain shape directly
        Scenario("finalDensity-multiply") {
          region("border").noise { densityFunction("finalDensity") { it.multiply(0.5) } }
        },
        Scenario("depth-raise-clamp") {
          region("border").noise {
            densityFunction("depth") {
              it.add(0.4)
              it.clamp(-0.5, 1.5)
            }
          }
        },
        Scenario("vegetation-invert") {
          region("border").noise { noise("vegetation") { it.remap(-1.0, 1.0, 1.0, -1.0) } }
        },
        // Multi-constraint: depth AND erosion modified simultaneously in one region.
        // Verifies that multiple density-function entries each apply to their own key.
        Scenario("multi-constraint-depth-erosion") {
          region("border").noise {
            densityFunction("depth") { it.multiply(1.5) }
            noise("erosion") { it.multiply(-1.0) }
          }
        },
        // Remap continents to always be above ocean threshold: forces land everywhere.
        // Tests a real-world geography preset override (ocean → land transition).
        Scenario("continents-remap-all-land") {
          region("border").noise { densityFunction("continents") { it.remap(-1.0, 1.0, 0.5, 1.0) } }
        },
        // Negate finalDensity: solid blocks become air, air becomes solid.
        // The most extreme terrain inversion; verifies full-strength transform on terrain.
        Scenario("finalDensity-negate") {
          region("border").noise { densityFunction("finalDensity") { it.multiply(-1.0) } }
        },
      )

    val registeredPresetIds = mutableListOf<String>()
    val screenshotPaths = mutableListOf<String>()
    val requiredDiffScenarios =
      setOf(
        "finalDensity-multiply",
        "depth-raise-clamp",
        "vegetation-invert",
        "multi-constraint-depth-erosion",
        "continents-remap-all-land",
        "finalDensity-negate",
      )
    var requiredScenarioPassed = false
    try {
      for (scenario in scenarios) {
        val presetId = "${CUSTOM_PRESET_PREFIX}_${scenario.name}"
        registeredPresetIds.add(presetId)
        registerPreset(presetId, scenario.configure)

        val screenshotsDir = GAME_TEST_SCREENSHOTS_BASE.resolve(scenario.name)
        val scenarioColumns = runWorld(context, presetId, screenshotsDir)
        val diffCount = countDifferences(vanillaColumns, scenarioColumns)
        LOGGER.info("[NoiseConstraints] {} changed {} sampled columns", scenario.name, diffCount)
        if (scenario.name in requiredDiffScenarios && diffCount > 0) {
          requiredScenarioPassed = true
        }

        for ((x, z) in PROBE_LOCATIONS) {
          screenshotPaths.add("${screenshotsDir}/probe_${x}_${z}.png")
        }
      }
      assertTrue(
        requiredScenarioPassed,
        "no worldgen-affecting noise-constraints case changed terrain",
      )
    } finally {
      PresetRegistry.forcePresetId = null
      for (presetId in registeredPresetIds) {
        PresetRegistry.presets.remove(presetId)
      }
    }

    LOGGER.info("[NoiseConstraints] screenshots saved to {}", GAME_TEST_SCREENSHOTS_BASE)
    for (path in screenshotPaths) {
      LOGGER.info("[NoiseConstraints] screenshot: {}", path)
    }
  }
}

private fun registerPreset(id: String, configure: RegionRegistry.() -> Unit) {
  PresetRegistry.presets[id] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "hex")
      region("hex").radius(150).strategy(Strategy.hex().tiling(true))
      region("cell").parent("hex").strategy(Strategy.voronoi())
      region("border").radius(1024).parent("hex")
      apply(configure)
    }
}

@Suppress("UnstableApiUsage")
private fun runWorld(
  context: ClientGameTestContext,
  presetId: String?,
  screenshotsDir: Path?,
): Map<String, Pair<Int, Int>> {
  val originalPresetId = PresetRegistry.forcePresetId
  PresetRegistry.forcePresetId = presetId
  val game =
    context
      .worldBuilder()
      .setUseConsistentSettings(false)
      .adjustSettings { settings ->
        settings.seed = SEED
        settings.gameMode = WorldCreationUiState.SelectedGameMode.CREATIVE
      }
      .create()

  try {
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        server.playerList.players[0].teleportTo(0.0, 400.0, 0.0)
      }
    )

    context.waitTicks(10)

    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client ->
        client.player?.let { player ->
          player.xRot = 90f
          player.yRot = 0f
          player.abilities.mayfly = true
          player.abilities.flying = true
          player.onUpdateAbilities()
        }
      }
    )

    game.clientWorld.waitForChunksRender()

    val columns = LinkedHashMap<String, Pair<Int, Int>>()
    for ((x, z) in PROBE_LOCATIONS) {
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          server.playerList.players[0].teleportTo(x + 8.0, 200.0, z + 8.0)
        }
      )

      context.waitTicks(60)
      game.clientWorld.waitForChunksDownload()
      game.clientWorld.waitForChunksRender()

      if (screenshotsDir != null) {
        screenshotsDir.toFile().mkdirs()
        context.takeScreenshot(
          TestScreenshotOptions.of("probe_${x}_${z}").withDestinationDir(screenshotsDir)
        )
      }

      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          val level = server.overworld()
          for (bx in x until x + 16) {
            for (bz in z until z + 16) {
              val oceanFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz)
              val worldSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz)
              columns["$bx,$bz"] = oceanFloor to worldSurface
            }
          }
        }
      )
    }

    LOGGER.info("[NoiseConstraints] {} sampled {} columns", presetId, columns.size)
    return columns
  } finally {
    game.close()
    PresetRegistry.forcePresetId = originalPresetId
  }
}

private fun countDifferences(
  baseline: Map<String, Pair<Int, Int>>,
  candidate: Map<String, Pair<Int, Int>>,
): Int {
  var diffCount = 0
  for ((key, value) in candidate) {
    if (baseline[key] != value) {
      diffCount++
    }
  }
  return diffCount
}
