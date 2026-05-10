package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry

private val LOGGER = LoggerFactory.getLogger("NoiseNarrativeFabricGameTest")
private const val DISABLED_PRESET = "__disabled__"
private const val SEED = "noise-narrative"
private const val CUSTOM_PRESET_PREFIX = "noise_narrative_client_test"

// Registers a flat single-region preset: every overworld block belongs to the root region,
// so constraints are applied at full strength everywhere. Used for spawn-chunk isolation.
private fun registerSimpleRootPreset(id: String, configure: RegionRegistry.() -> Unit) {
  PresetRegistry.presets[id] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root")
      apply(configure)
    }
}

// Creates a world, waits for the spawn chunk to load, then reads one 16×16 chunk of column data.
@Suppress("UnstableApiUsage")
private fun runSpawnChunk(
  context: ClientGameTestContext,
  presetId: String?,
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
        server.playerList.players[0].teleportTo(8.0, 400.0, 8.0)
      }
    )

    context.waitTicks(20)
    game.clientWorld.waitForChunksRender()

    val columns = LinkedHashMap<String, Pair<Int, Int>>()
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
        for (bx in 0 until 16) {
          for (bz in 0 until 16) {
            val oceanFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz)
            val worldSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz)
            columns["$bx,$bz"] = oceanFloor to worldSurface
          }
        }
        LOGGER.info(
          "[SpawnChunkTest] preset={} sampled {} columns — surfaces: {}",
          presetId,
          columns.size,
          columns.values.map { it.second }.distinct().sorted().takeLast(5),
        )
      }
    )

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
    if (baseline[key] != value) diffCount++
  }
  return diffCount
}

// Single-scenario, single spawn-chunk test for rapid noise-constraint troubleshooting.
// Uses a simple root preset so the entire overworld is one constrained region.
// depth*3 is extreme enough to produce an unmissable height shift if the pipeline works.
@Suppress("UnstableApiUsage")
object TerrasectFabricClientGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val presetId = "${CUSTOM_PRESET_PREFIX}_depth_extreme"
    registerSimpleRootPreset(presetId) {
      region("overworld_root").noise { densityFunction("depth") { it.multiply(3.0) } }
    }

    try {
      val vanillaColumns = runSpawnChunk(context, DISABLED_PRESET)
      val constrainedColumns = runSpawnChunk(context, presetId)
      val diffCount = countDifferences(vanillaColumns, constrainedColumns)

      LOGGER.info(
        "[NoiseNarrative] depth_extreme: {} of {} spawn-chunk columns changed",
        diffCount,
        vanillaColumns.size,
      )
      LOGGER.info(
        "[NoiseNarrative] vanilla surface range: {}–{}",
        vanillaColumns.values.map { it.second }.min(),
        vanillaColumns.values.map { it.second }.max(),
      )
      LOGGER.info(
        "[NoiseNarrative] constrained surface range: {}–{}",
        constrainedColumns.values.map { it.second }.min(),
        constrainedColumns.values.map { it.second }.max(),
      )

      assertTrue(
        diffCount > 0,
        "TerrasectFabricClientGameTest: depth*3 must change spawn-chunk terrain " +
          "(got 0 diffs / ${vanillaColumns.size} columns). " +
          "Check [NC-*] log lines for which pipeline stage returned null.",
      )
    } finally {
      PresetRegistry.presets.remove(presetId)
    }
  }
}

// Separate object so it can be run in isolation via GameTestFilter.
@Suppress("UnstableApiUsage")
object SpawnChunkNoiseConstraintTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val presetId = "spawn_chunk_noise_constraint_test"
    registerSimpleRootPreset(presetId) {
      region("overworld_root").noise { densityFunction("depth") { it.multiply(3.0) } }
    }

    try {
      val vanillaColumns = runSpawnChunk(context, DISABLED_PRESET)
      val constrainedColumns = runSpawnChunk(context, presetId)

      val diffCount = countDifferences(vanillaColumns, constrainedColumns)
      LOGGER.info(
        "[SpawnChunkTest] depth*3 scenario: {} of {} spawn-chunk columns changed",
        diffCount,
        vanillaColumns.size,
      )
      LOGGER.info(
        "[SpawnChunkTest] vanilla surface range: {}–{}",
        vanillaColumns.values.map { it.second }.min(),
        vanillaColumns.values.map { it.second }.max(),
      )
      LOGGER.info(
        "[SpawnChunkTest] constrained surface range: {}–{}",
        constrainedColumns.values.map { it.second }.min(),
        constrainedColumns.values.map { it.second }.max(),
      )

      assertTrue(
        diffCount > 0,
        "SpawnChunkNoiseConstraintTest: depth*3 constraint must change spawn-chunk terrain " +
          "(got 0 diffs out of ${vanillaColumns.size} columns). " +
          "Check [NC-*] log lines for which pipeline stage returned null.",
      )
    } finally {
      PresetRegistry.presets.remove(presetId)
    }
  }
}
