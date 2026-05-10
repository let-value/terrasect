package terrasect

import java.nio.file.Path
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.client.player.LocalPlayer
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy

private val LOGGER = LoggerFactory.getLogger("NoiseNarrativeFabricGameTest")
private const val DISABLED_PRESET = "__disabled__"
private const val SEED = "noise-narrative"
private const val CUSTOM_PRESET_PREFIX = "noise_narrative_client_test"
private val PROBE_LOCATIONS = listOf(0 to 0, 512 to 0, 0 to 512, 512 to 512)

private val GAME_TEST_MODULE_DIR: Path by lazy {
  val classesRoot = Path.of(object {}.javaClass.protectionDomain.codeSource.location.toURI())
  classesRoot.parent.parent.parent.parent
}

private val GAME_TEST_SCREENSHOTS_BASE: Path by lazy {
  GAME_TEST_MODULE_DIR.resolve("build/gametest-screenshots/noise-narrative")
}

private data class Scenario(val name: String, val configure: RegionRegistry.() -> Unit)

@Suppress("UnstableApiUsage")
object TerrasectFabricClientGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val vanillaColumns = runWorld(context, DISABLED_PRESET, null)
    val scenarios =
      listOf(
        Scenario("desert") {
          region("border").noise {
            noise("temperature") { it.remap(-1.0, 1.0, 0.55, 1.0) }
            densityFunction("continents") { it.remap(-1.0, 1.0, -0.11, 0.55) }
          }
        },
        Scenario("river") {
          region("border").noise {
            densityFunction("depth") { it.squeeze() }
            noise("erosion") { it.remap(-1.0, 1.0, 0.55, 1.0) }
            noise("ridges") { it.clamp(-0.05, 0.05) }
          }
        },
        Scenario("lake") {
          region("border").noise {
            densityFunction("continents") { it.remap(-1.0, 1.0, -0.455, -0.11) }
            noise("erosion") { it.remap(-1.0, 1.0, -0.25, 0.25) }
          }
        },
        Scenario("ocean") {
          region("border").noise {
            densityFunction("continents") { it.remap(-1.0, 1.0, -1.05, -0.455) }
            densityFunction("depth") { it.multiply(1.5) }
          }
        },
      )

    val registeredPresetIds = mutableListOf<String>()
    val screenshotPaths = mutableListOf<String>()
    val requiredDiffScenarios = setOf("desert", "river", "lake", "ocean")
    var requiredScenarioPassed = false
    try {
      for (scenario in scenarios) {
        val presetId = "${CUSTOM_PRESET_PREFIX}_${scenario.name}"
        registeredPresetIds.add(presetId)
        registerPreset(presetId, scenario.configure)

        val screenshotsDir = GAME_TEST_SCREENSHOTS_BASE.resolve(scenario.name)
        val scenarioColumns = runWorld(context, presetId, screenshotsDir)
        val diffCount = countDifferences(vanillaColumns, scenarioColumns)
        LOGGER.info("[NoiseNarrative] {} changed {} sampled columns", scenario.name, diffCount)
        if (scenario.name in requiredDiffScenarios && diffCount > 0) {
          requiredScenarioPassed = true
        }

        for ((x, z) in PROBE_LOCATIONS) {
          screenshotPaths.add(screenshotsDir.resolve("aerial_${x}_${z}.png").toString())
        }
      }
      assertTrue(requiredScenarioPassed, "no worldgen-affecting noise-narrative case changed terrain")
    } finally {
      PresetRegistry.forcePresetId = null
      for (presetId in registeredPresetIds) {
        PresetRegistry.presets.remove(presetId)
      }
    }

    LOGGER.info("[NoiseNarrative] screenshots saved to {}", GAME_TEST_SCREENSHOTS_BASE)
    for (path in screenshotPaths) {
      LOGGER.info("[NoiseNarrative] screenshot: {}", path)
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
      FailableConsumer<Minecraft, Exception> { client -> client.player?.let { configureAerialCamera(it) } }
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
        context.runOnClient(
          FailableConsumer<Minecraft, Exception> { client -> client.player?.let { configureAerialCamera(it) } }
        )
        context.takeScreenshot(
          TestScreenshotOptions.of("aerial_${x}_${z}").withDestinationDir(screenshotsDir)
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

    LOGGER.info("[NoiseNarrative] {} sampled {} columns", presetId, columns.size)
    return columns
  } finally {
    game.close()
    PresetRegistry.forcePresetId = originalPresetId
  }
}

private fun configureAerialCamera(player: LocalPlayer) {
  player.xRot = 90f
  player.yRot = 0f
  player.abilities.mayfly = true
  player.abilities.flying = true
  player.onUpdateAbilities()
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
