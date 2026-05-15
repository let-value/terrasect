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

private enum class ScenarioExpectation {
  DESERT,
  OCEAN,
  HIGHLANDS,
  HILLY_PLAINS,
  FLAT_PLAINS,
  LOWLANDS,
  RIDGE_VALLEY,
}

private data class Scenario(
  val name: String,
  val trace: String,
  val expectation: ScenarioExpectation,
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

private fun configureAerialScreenshotCamera(client: Minecraft) {
  client.player?.let { player ->
    player.xRot = 65f
    player.yRot = 0f
    player.abilities.mayfly = true
    player.abilities.flying = true
    player.onUpdateAbilities()
  }
}

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
  LOGGER.info("[NoiseNarrative][{}] START preset={} trace=[{}]", scenarioName, presetId, trace)
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
        server.playerList.players[0].teleportTo(8.0, 120.0, -16.0)
      }
    )
    context.waitTicks(10)
    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client -> configureAerialScreenshotCamera(client) }
    )

    context.waitTicks(10)
    game.clientWorld.waitForChunksRender()

    if (screenshotLabel != null) {
      val screenshotDir = SCREENSHOTS_BASE.resolve("NoiseNarrativeConstraintTest")
      LOGGER.info(
        "[NoiseNarrative][{}] screenshot -> {}/{}.png",
        scenarioName,
        screenshotDir,
        screenshotLabel,
      )
      context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client -> configureAerialScreenshotCamera(client) }
      )
      context.waitTicks(5)
      context.takeScreenshot(
        TestScreenshotOptions.of(screenshotLabel).withDestinationDir(screenshotDir)
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
          "[NoiseNarrative][{}] preset={} trace=[{}] surface={}-{} avg={} oceanFloor={}-{} avg={} ground=[{}] cover=[{}] biomes=[{}]",
          scenarioName,
          presetId,
          trace,
          surfaces.min(),
          surfaces.max(),
          average(surfaces).fmt1(),
          columns.values.minOf { it.oceanFloor },
          columns.values.maxOf { it.oceanFloor },
          average(columns.values.map { it.oceanFloor }).fmt1(),
          formatCounts(groundCounts, 4),
          formatCounts(countBlocks(columns) { it.coverBlock }, 4),
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

private fun average(values: Collection<Int>): Double = values.sum().toDouble() / values.size

private fun averageSurface(columns: Map<String, ColumnStats>): Double =
  average(columns.values.map { it.worldSurface })

private fun surfaceRelief(columns: Map<String, ColumnStats>): Int =
  columns.values.maxOf { it.worldSurface } - columns.values.minOf { it.worldSurface }

private fun Double.fmt1() = "%.1f".format(this)

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
            "noise-only: temperature pinned hot + inland/erosion/weirdness density pinned to middle-slice desert cell",
          expectation = ScenarioExpectation.DESERT,
        ) {
          region("overworld_root").noise {
            noise("temperature") {
              it.multiply(0.0)
              it.add(0.8)
            }
            densityFunction("continents") {
              it.multiply(0.0)
              it.add(0.2)
            }
            densityFunction("erosion") {
              it.multiply(0.0)
              it.add(0.2)
            }
            densityFunction("ridges") {
              it.multiply(0.0)
              it.add(-0.2)
            }
            densityFunction("finalDensity") { it.add(-0.15) }
          }
        },
        Scenario(
          name = "ocean",
          trace =
            "noise-only: deep-ocean continentalness + flooded aquifer noise + lowered finalDensity → visible water basin",
          expectation = ScenarioExpectation.OCEAN,
        ) {
          region("overworld_root").noise {
            densityFunction("continents") {
              it.multiply(0.0)
              it.add(-0.8)
            }
            densityFunction("fluidLevelFloodednessNoise") {
              it.multiply(0.0)
              it.add(1.0)
            }
            densityFunction("fluidLevelSpreadNoise") {
              it.multiply(0.0)
              it.add(1.0)
            }
            densityFunction("lavaNoise") {
              it.multiply(0.0)
              it.add(-1.0)
            }
            densityFunction("finalDensity") { it.add(-0.4) }
          }
        },
        Scenario(
          name = "highlands",
          trace =
            "noise-only: inland continentalness + raised finalDensity → elevated traversal plateau",
          expectation = ScenarioExpectation.HIGHLANDS,
        ) {
          region("overworld_root").noise {
            densityFunction("continents") {
              it.multiply(0.0)
              it.add(0.35)
            }
            densityFunction("erosion") {
              it.multiply(0.0)
              it.add(-0.65)
            }
            densityFunction("ridges") {
              it.multiply(0.0)
              it.add(0.6)
            }
            densityFunction("preliminarySurfaceLevel") { it.add(16.0) }
            densityFunction("finalDensity") { it.add(0.02) }
          }
        },
        Scenario(
          name = "hilly_plains",
          trace =
            "noise-only: dry-temperate inland + high erosion + softened density → same mountain frame transformed into rolling plains",
          expectation = ScenarioExpectation.HILLY_PLAINS,
        ) {
          region("overworld_root").noise {
            noise("temperature") {
              it.multiply(0.0)
              it.add(0.0)
            }
            noise("vegetation") {
              it.multiply(0.0)
              it.add(-0.2)
            }
            densityFunction("continents") {
              it.multiply(0.0)
              it.add(0.2)
            }
            densityFunction("erosion") {
              it.multiply(0.0)
              it.add(0.8)
            }
            densityFunction("ridges") {
              it.multiply(0.0)
              it.add(0.0)
            }
            densityFunction("preliminarySurfaceLevel") { it.add(-12.0) }
            densityFunction("finalDensity") { it.add(-0.12) }
          }
        },
        Scenario(
          name = "flat_plains",
          trace =
            "noise-only: dry-temperate inland + maximum erosion + neutral ridges + lowered surface → mountain frame flattened into plains",
          expectation = ScenarioExpectation.FLAT_PLAINS,
        ) {
          region("overworld_root").noise {
            noise("temperature") {
              it.multiply(0.0)
              it.add(0.0)
            }
            noise("vegetation") {
              it.multiply(0.0)
              it.add(-0.2)
            }
            densityFunction("continents") {
              it.multiply(0.0)
              it.add(0.2)
            }
            densityFunction("erosion") {
              it.multiply(0.0)
              it.add(0.85)
            }
            densityFunction("ridges") {
              it.multiply(0.0)
              it.add(0.0)
            }
            densityFunction("preliminarySurfaceLevel") { it.add(-24.0) }
            densityFunction("finalDensity") { it.add(-0.18) }
          }
        },
        Scenario(
          name = "lowlands",
          trace =
            "noise-only: inland continentalness + softened density → lower approach basin without becoming ocean",
          expectation = ScenarioExpectation.LOWLANDS,
        ) {
          region("overworld_root").noise {
            densityFunction("continents") {
              it.multiply(0.0)
              it.add(0.2)
            }
            densityFunction("erosion") {
              it.multiply(0.0)
              it.add(0.6)
            }
            densityFunction("finalDensity") { it.add(-0.18) }
          }
        },
        Scenario(
          name = "ridge_valley",
          trace =
            "noise-only: river-valley weirdness band + high erosion → traversable narrative corridor",
          expectation = ScenarioExpectation.RIDGE_VALLEY,
        ) {
          region("overworld_root").noise {
            densityFunction("continents") {
              it.multiply(0.0)
              it.add(0.1)
            }
            densityFunction("erosion") {
              it.multiply(0.0)
              it.add(0.8)
            }
            densityFunction("ridges") {
              it.multiply(0.0)
              it.add(0.0)
            }
            densityFunction("finalDensity") { it.add(-0.08) }
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

        val terrainChange =
          diff.heightDiffs > 0 || diff.groundBlockDiffs > 0 || diff.coverBlockDiffs > 0
        LOGGER.info(
          "[NoiseNarrative][{}] composition evidence: ground=[{}] cover=[{}] biomes=[{}]",
          scenario.name,
          formatCounts(groundCounts, 4),
          formatCounts(coverCounts, 4),
          formatCounts(biomeCounts, 4),
        )
        assertTrue(
          terrainChange,
          "[${scenario.name}] noise-only scenario did not change terrain vs vanilla " +
            "(height=${diff.heightDiffs} ground=${diff.groundBlockDiffs} cover=${diff.coverBlockDiffs} " +
            "biome=${diff.biomeDiffs} / ${diff.total} columns). Biome-only differences are not enough here; " +
            "check [NC-OriginNoise] and [NC-HolderKey] log lines to see whether constrained density keys reach the effective terrain-density graph.",
        )

        when (scenario.expectation) {
          ScenarioExpectation.DESERT -> {
            val sandGroundColumns = candidate.values.count { "sand" in it.groundBlock }
            val desertBiomeColumns = candidate.values.count { it.biome == "minecraft:desert" }
            assertTrue(
              sandGroundColumns > candidate.size / 2,
              "[desert] expected sand-dominant ground from noise-routed climate but only $sandGroundColumns/${candidate.size} columns were sand. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
            assertTrue(
              desertBiomeColumns > candidate.size / 2,
              "[desert] expected desert biome dominance from noise-routed climate but only $desertBiomeColumns/${candidate.size} columns were desert. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
          }
          ScenarioExpectation.OCEAN -> {
            val deepOceanColumns = candidate.values.count { it.biome == "minecraft:deep_ocean" }
            val waterColumns =
              candidate.values.count { "water" in it.groundBlock || "water" in it.coverBlock }
            assertTrue(
              deepOceanColumns > candidate.size / 2,
              "[ocean] expected deep-ocean biome dominance from noise-routed climate but only $deepOceanColumns/${candidate.size} columns were deep ocean. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
            assertTrue(
              waterColumns > candidate.size / 2,
              "[ocean] expected visible water-dominant basin but only $waterColumns/${candidate.size} columns contained water. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
          }
          ScenarioExpectation.HIGHLANDS -> {
            val baselineAverage = averageSurface(vanilla)
            val candidateAverage = averageSurface(candidate)
            assertTrue(
              candidateAverage >= baselineAverage + 2.0,
              "[highlands] expected average surface to rise by at least 2 blocks but vanilla=${baselineAverage.fmt1()} candidate=${candidateAverage.fmt1()}. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
          }
          ScenarioExpectation.HILLY_PLAINS -> {
            val baselineAverage = averageSurface(vanilla)
            val candidateAverage = averageSurface(candidate)
            val waterColumns =
              candidate.values.count { "water" in it.groundBlock || "water" in it.coverBlock }
            val relief = surfaceRelief(candidate)
            assertTrue(
              candidateAverage <= baselineAverage - 2.0,
              "[hilly_plains] expected the mountain frame to become lower rolling plains but vanilla=${baselineAverage.fmt1()} candidate=${candidateAverage.fmt1()}. " +
                "relief=$relief ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
            assertTrue(
              relief <= 18,
              "[hilly_plains] expected rolling plains relief, not intact mountains; relief=$relief. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
            assertTrue(
              waterColumns < candidate.size / 3,
              "[hilly_plains] expected mostly dry rolling plains, not a water basin; water=$waterColumns/${candidate.size}. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
          }
          ScenarioExpectation.FLAT_PLAINS -> {
            val baselineAverage = averageSurface(vanilla)
            val candidateAverage = averageSurface(candidate)
            val waterColumns =
              candidate.values.count { "water" in it.groundBlock || "water" in it.coverBlock }
            val relief = surfaceRelief(candidate)
            assertTrue(
              candidateAverage <= baselineAverage - 6.0,
              "[flat_plains] expected the mountain frame to flatten well below vanilla but vanilla=${baselineAverage.fmt1()} candidate=${candidateAverage.fmt1()}. " +
                "relief=$relief ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
            assertTrue(
              relief <= 15,
              "[flat_plains] expected near-flat plains relief, not remaining mountains; relief=$relief. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
            assertTrue(
              waterColumns < candidate.size / 4,
              "[flat_plains] expected dry flat plains, not a water basin; water=$waterColumns/${candidate.size}. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
          }
          ScenarioExpectation.LOWLANDS -> {
            val baselineAverage = averageSurface(vanilla)
            val candidateAverage = averageSurface(candidate)
            val waterColumns =
              candidate.values.count { "water" in it.groundBlock || "water" in it.coverBlock }
            assertTrue(
              candidateAverage <= baselineAverage - 4.0,
              "[lowlands] expected average surface to drop by at least 4 blocks but vanilla=${baselineAverage.fmt1()} candidate=${candidateAverage.fmt1()}. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
            assertTrue(
              waterColumns < candidate.size / 2,
              "[lowlands] expected a mostly dry basin, not an ocean; water=$waterColumns/${candidate.size}. " +
                "ground=${formatCounts(groundCounts, 4)} cover=${formatCounts(coverCounts, 4)} biomes=${formatCounts(biomeCounts, 4)}",
            )
          }
          ScenarioExpectation.RIDGE_VALLEY -> {
            val baselineAverage = averageSurface(vanilla)
            val candidateAverage = averageSurface(candidate)
            assertTrue(
              candidateAverage <= baselineAverage - 2.0,
              "[ridge_valley] expected valley corridor to sit below vanilla average surface but vanilla=${baselineAverage.fmt1()} candidate=${candidateAverage.fmt1()}. " +
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
