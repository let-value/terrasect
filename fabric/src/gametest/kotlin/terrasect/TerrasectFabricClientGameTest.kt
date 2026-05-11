package terrasect

import java.nio.file.Path
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.handler.ClimateHandler
import terrasect.handler.NoiseHandler

private val LOGGER = LoggerFactory.getLogger("NoiseNarrativeFabricGameTest")
private const val DISABLED_PRESET = "__disabled__"
private const val SEED = "noise-narrative"
private const val CUSTOM_PRESET_PREFIX = "noise_narrative_client_test"

private val SCREENSHOTS_BASE: Path by lazy {
  val classesRoot = Path.of(object {}.javaClass.protectionDomain.codeSource.location.toURI())
  classesRoot.parent.parent.parent.parent.resolve("build/gametest-screenshots")
}

private data class ColumnStats(
  val oceanFloor: Int,
  val worldSurface: Int,
  val groundBlock: String,
  val coverBlock: String,
  val biome: String,
)

private data class DiffSummary(
  val heightDiffs: Int,
  val groundBlockDiffs: Int,
  val coverBlockDiffs: Int,
  val biomeDiffs: Int,
  val total: Int,
)

private data class Scenario(
  val name: String,
  val trace: String,
  val configure: RegionRegistry.() -> Unit,
)

private fun registerSimpleRootPreset(id: String, configure: RegionRegistry.() -> Unit) {
  PresetRegistry.presets[id] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root")
      apply(configure)
    }
}

private fun scenarioPresetId(name: String) = "${CUSTOM_PRESET_PREFIX}_$name"

@Suppress("UnstableApiUsage")
private fun runSpawnChunk(
  context: ClientGameTestContext,
  presetId: String?,
  scenarioName: String,
  trace: String,
  screenshotLabel: String? = null,
): Map<String, ColumnStats> {
  val originalPresetId = PresetRegistry.forcePresetId
  PresetRegistry.forcePresetId = presetId
  NoiseHandler.resetOriginTrace()
  ClimateHandler.resetOriginTrace()
  LOGGER.info(
    "[NoiseNarrative][{}] reset origin diagnostics; detailed [NC-OriginNoise]/[NC-OriginClimate] logs are limited to block x=0 z=0",
    scenarioName,
  )
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
        server.playerList.players[0].teleportTo(8.0, 140.0, -16.0)
      }
    )
    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client ->
        client.player?.let { player ->
          player.xRot = 65f
          player.yRot = 180f
          player.abilities.mayfly = true
          player.abilities.flying = true
          player.onUpdateAbilities()
        }
      }
    )

    context.waitTicks(20)
    game.clientWorld.waitForChunksRender()

    if (screenshotLabel != null) {
      context.takeScreenshot(
        TestScreenshotOptions.of(screenshotLabel)
          .withDestinationDir(SCREENSHOTS_BASE.resolve("NoiseNarrativeConstraintTest"))
      )
    }

    val columns = LinkedHashMap<String, ColumnStats>()
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
        for (bx in 0 until 16) {
          for (bz in 0 until 16) {
            val oceanFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz)
            val worldSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz)
            val groundY = worldSurface - 1
            val groundBlock =
              BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos(bx, groundY, bz)).block)
                .toString()
            val coverBlock =
              BuiltInRegistries.BLOCK.getKey(
                  level.getBlockState(BlockPos(bx, worldSurface, bz)).block
                )
                .toString()
            val biome =
              level
                .getBiome(BlockPos(bx, worldSurface, bz))
                .unwrapKey()
                .map { it.identifier().toString() }
                .orElse("unknown")
            columns["$bx,$bz"] =
              ColumnStats(oceanFloor, worldSurface, groundBlock, coverBlock, biome)
          }
        }

        val surfaces = columns.values.map { it.worldSurface }
        val groundCounts = countBlocks(columns) { it.groundBlock }
        val biomeCounts = countBlocks(columns) { it.biome }
        LOGGER.info(
          "[NoiseNarrative][{}] preset={} trace=[{}] surface={}-{} ground=[{}] biomes=[{}]",
          scenarioName,
          presetId,
          trace,
          surfaces.min(),
          surfaces.max(),
          formatCounts(groundCounts, 4),
          formatCounts(biomeCounts, 3),
        )
      }
    )

    return columns
  } finally {
    game.close()
    PresetRegistry.forcePresetId = originalPresetId
  }
}

private fun compareDiffs(
  baseline: Map<String, ColumnStats>,
  candidate: Map<String, ColumnStats>,
): DiffSummary {
  var heightDiffs = 0
  var groundBlockDiffs = 0
  var coverBlockDiffs = 0
  var biomeDiffs = 0
  for ((key, c) in candidate) {
    val b = baseline[key] ?: continue
    if (b.oceanFloor != c.oceanFloor || b.worldSurface != c.worldSurface) heightDiffs++
    if (b.groundBlock != c.groundBlock) groundBlockDiffs++
    if (b.coverBlock != c.coverBlock) coverBlockDiffs++
    if (b.biome != c.biome) biomeDiffs++
  }
  return DiffSummary(heightDiffs, groundBlockDiffs, coverBlockDiffs, biomeDiffs, candidate.size)
}

private fun logColumnDiffs(
  baseline: Map<String, ColumnStats>,
  candidate: Map<String, ColumnStats>,
  scenarioName: String,
) {
  var logged = 0
  for ((key, c) in candidate) {
    val b = baseline[key] ?: continue
    if (b != c) {
      if (logged < 6) {
        LOGGER.info(
          "[NoiseNarrative][{}] column {} surface {}→{} ground {}→{} cover {}→{} biome {}→{}",
          scenarioName,
          key,
          b.worldSurface,
          c.worldSurface,
          b.groundBlock.substringAfterLast(':'),
          c.groundBlock.substringAfterLast(':'),
          b.coverBlock.substringAfterLast(':'),
          c.coverBlock.substringAfterLast(':'),
          b.biome.substringAfterLast('/'),
          c.biome.substringAfterLast('/'),
        )
      }
      logged++
    }
  }
  if (logged == 0) {
    LOGGER.warn(
      "[NoiseNarrative][{}] NO columns changed — check [NC-*] log lines; constraint pipeline may not be firing",
      scenarioName,
    )
  } else if (logged > 6) {
    LOGGER.info(
      "[NoiseNarrative][{}] … {} more columns changed (not shown)",
      scenarioName,
      logged - 6,
    )
  }
}

private fun countBlocks(
  columns: Map<String, ColumnStats>,
  selector: (ColumnStats) -> String,
): Map<String, Int> = columns.values.groupingBy(selector).eachCount()

private fun formatCounts(counts: Map<String, Int>, limit: Int): String =
  counts.entries
    .sortedByDescending { it.value }
    .take(limit)
    .joinToString { "${it.key.substringAfterLast(':')}×${it.value}" }

@Suppress("UnstableApiUsage")
object TerrasectFabricClientGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val scenarios =
      listOf(
        Scenario(
          name = "desert",
          trace =
            "temperature=10000 + humidity=-10000 + climate continentalness=0 + erosion=-3000 + weirdness=-3000, terrain continents=0.2..0.4 + depth=0 → forced vanilla desert cell/sand",
        ) {
          region("overworld_root").climate {
            temperature(10000)
            humidity(-10000)
            continentalness(0)
            erosion(-3000)
            depth(0)
            weirdness(-3000)
          }
          region("overworld_root").noise {
            densityFunction("continents") { it.remap(-1.0, 1.0, 0.2, 0.4) }
            densityFunction("depth") { it.multiply(0.0) }
          }
        },
        Scenario(
          name = "ocean",
          trace =
            "climate continentalness=-8000 + finalDensity-0.15 → depressed deep-ocean climate cell",
        ) {
          region("overworld_root").climate {
            continentalness(-8000)
          }
          region("overworld_root").noise {
            densityFunction("finalDensity") { it.add(-0.15) }
          }
        },
      )

    val registeredPresetIds = mutableListOf<String>()
    try {
      val vanilla =
        runSpawnChunk(context, DISABLED_PRESET, "vanilla", "baseline — no constraints", "vanilla")

      for (scenario in scenarios) {
        val presetId = scenarioPresetId(scenario.name)
        registeredPresetIds.add(presetId)
        registerSimpleRootPreset(presetId, scenario.configure)

        val candidate =
          runSpawnChunk(context, presetId, scenario.name, scenario.trace, scenario.name)
        val diff = compareDiffs(vanilla, candidate)
        val groundCounts = countBlocks(candidate) { it.groundBlock }
        val coverCounts = countBlocks(candidate) { it.coverBlock }
        val biomeCounts = countBlocks(candidate) { it.biome }

        LOGGER.info(
          "[NoiseNarrative][{}] diffs: height={} ground={} cover={} biome={} / {} columns",
          scenario.name,
          diff.heightDiffs,
          diff.groundBlockDiffs,
          diff.coverBlockDiffs,
          diff.biomeDiffs,
          diff.total,
        )
        logColumnDiffs(vanilla, candidate, scenario.name)

        val anyChange =
          diff.heightDiffs > 0 ||
            diff.groundBlockDiffs > 0 ||
            diff.coverBlockDiffs > 0 ||
            diff.biomeDiffs > 0
        assertTrue(
          anyChange,
          "[${scenario.name}] spawn-chunk terrain did not change vs vanilla " +
            "(height=${diff.heightDiffs} ground=${diff.groundBlockDiffs} cover=${diff.coverBlockDiffs} " +
            "biome=${diff.biomeDiffs} / ${diff.total} columns). Check [NC-*] log lines — constraint pipeline may not be firing.",
        )

        when (scenario.name) {
          "desert" -> {
            val sandGroundColumns = candidate.values.count { "sand" in it.groundBlock }
            val desertBiomeColumns = candidate.values.count { it.biome == "minecraft:desert" }
            val waterColumns =
              candidate.values.count { "water" in it.groundBlock || "water" in it.coverBlock }
            LOGGER.info(
              "[NoiseNarrative][desert] enforced composition: sandGround={}/{} desertBiome={}/{} water={} ground=[{}] cover=[{}] biomes=[{}]",
              sandGroundColumns,
              candidate.size,
              desertBiomeColumns,
              candidate.size,
              waterColumns,
              formatCounts(groundCounts, 4),
              formatCounts(coverCounts, 4),
              formatCounts(biomeCounts, 4),
            )
            assertTrue(
              sandGroundColumns > candidate.size / 2,
              "[desert] expected sand-dominant ground but only $sandGroundColumns/${candidate.size} columns were sand. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
            assertTrue(
              desertBiomeColumns > candidate.size / 2,
              "[desert] expected desert biome dominance but only $desertBiomeColumns/${candidate.size} columns were desert. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
            assertTrue(
              waterColumns == 0,
              "[desert] expected dry terrain but found $waterColumns water-bearing columns. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
          }
          "ocean" -> {
            val deepOceanColumns = candidate.values.count { it.biome == "minecraft:deep_ocean" }
            LOGGER.info(
              "[NoiseNarrative][ocean] enforced composition: deepOcean={}/{} ground=[{}] cover=[{}] biomes=[{}]",
              deepOceanColumns,
              candidate.size,
              formatCounts(groundCounts, 4),
              formatCounts(coverCounts, 4),
              formatCounts(biomeCounts, 4),
            )
            assertTrue(
              deepOceanColumns > candidate.size / 2,
              "[ocean] expected deep-ocean biome dominance but only $deepOceanColumns/${candidate.size} columns were deep ocean. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
          }
        }
      }
    } finally {
      PresetRegistry.forcePresetId = null
      for (presetId in registeredPresetIds) {
        PresetRegistry.presets.remove(presetId)
      }
    }
  }
}
