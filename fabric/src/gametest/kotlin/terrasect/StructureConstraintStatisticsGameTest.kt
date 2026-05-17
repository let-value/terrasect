package terrasect

import de.skuzzle.test.snapshots.SnapshotFile
import de.skuzzle.test.snapshots.SnapshotFile.SnapshotHeader
import java.nio.file.Path
import java.util.LinkedHashMap
import kotlin.math.sqrt
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.structure.Structure
import org.apache.commons.lang3.function.FailableConsumer
import org.slf4j.LoggerFactory
import org.junit.jupiter.api.Assertions.assertTrue
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry

private val LOGGER = LoggerFactory.getLogger("StructureConstraintStatisticsTest")

private const val SEED = "structure-constraints"
private const val DISABLED_PRESET = "__disabled__"
private const val DENSE_PRESET = "structure_constraint_statistics_dense"
private const val BANNED_VILLAGE_PRESET = "structure_constraint_statistics_banned_village"
private const val SAMPLE_CHUNKS = 32
private const val LOCATE_RADIUS = 300
private const val VILLAGE_TAG = "minecraft:village"

private val GAME_TEST_MODULE_DIR: Path by lazy {
  val classesRoot = Path.of(object {}.javaClass.protectionDomain.codeSource.location.toURI())
  classesRoot.parent.parent.parent.parent.also { LOGGER.info("[StructureConstraint][statistics] module dir = {}", it) }
}

private val GAME_TEST_RESOURCES_BASE: Path by lazy {
  GAME_TEST_MODULE_DIR.resolve("src/gametest/resources/terrasect")
}

private data class StatisticsSnapshot(
  val caseName: String,
  val sampleCount: Int,
  val hitCount: Int,
  val missCount: Int,
  val uniqueStructures: Int,
  val minDistance: Int,
  val maxDistance: Int,
  val averageDistance: Double,
  val topStructures: List<Pair<String, Int>>,
) {
  fun toSnapshotText(): String =
    buildString {
      appendLine("case,$caseName")
      appendLine("samples,$sampleCount")
      appendLine("hits,$hitCount")
      appendLine("misses,$missCount")
      appendLine("unique_structures,$uniqueStructures")
      appendLine("min_distance,$minDistance")
      appendLine("max_distance,$maxDistance")
      appendLine("average_distance,${"%.2f".format(averageDistance)}")
      appendLine("top_structures,id,count")
      for ((id, count) in topStructures) {
        appendLine("$id,$count")
      }
    }
}

private fun registerDensePreset() {
  PresetRegistry.presets[DENSE_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures {
        spacing(8)
        separation(4)
        frequency(1.0f)
      }
    }
}

private fun registerBannedVillagePreset() {
  PresetRegistry.presets[BANNED_VILLAGE_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures { blockTags(VILLAGE_TAG) }
    }
}

private fun snapshotPath(caseName: String): Path =
  GAME_TEST_RESOURCES_BASE.resolve("StructureConstraintStatisticsTest_snapshots/$caseName.snapshot")

private fun writeOrCompareSnapshot(caseName: String, text: String) {
  val snapshotFile = snapshotPath(caseName)
  val update = System.getProperty("updateSnapshots") != null
  if (update || !snapshotFile.toFile().exists()) {
    snapshotFile.parent.toFile().mkdirs()
    val header =
      SnapshotHeader.fromMap(
        mapOf(
          SnapshotHeader.TEST_CLASS to StructureConstraintStatisticsTest::class.qualifiedName!!,
          SnapshotHeader.TEST_METHOD to "runTest",
          SnapshotHeader.SNAPSHOT_NUMBER to "0",
          SnapshotHeader.SNAPSHOT_NAME to caseName,
          SnapshotHeader.DYNAMIC_DIRECTORY to "true",
        )
      )
    SnapshotFile.of(header, text).writeTo(snapshotFile)
    LOGGER.info("[StructureConstraint][statistics][{}] snapshot {}", caseName, snapshotFile)
  } else {
    val stored = SnapshotFile.fromSnapshotFile(snapshotFile).snapshot()
    if (stored != text) {
      throw AssertionError(
        "Statistics snapshot mismatch at $snapshotFile\n" +
          "Rerun with -PupdateSnapshots to accept the new snapshot, or inspect the diff:\n" +
          buildUnifiedDiff(stored, text)
      )
    }
    LOGGER.info("[StructureConstraint][statistics][{}] snapshot matches {}", caseName, snapshotFile)
  }
}

private fun buildUnifiedDiff(expected: String, actual: String): String {
  val exp = expected.lines()
  val act = actual.lines()
  val sb = StringBuilder()
  var matches = 0
  var diffs = 0
  for (i in 0 until maxOf(exp.size, act.size)) {
    val e = exp.getOrNull(i)
    val a = act.getOrNull(i)
    when {
      e == null -> {
        diffs++
        if (diffs <= 50) sb.appendLine("+ $a")
      }
      a == null -> {
        diffs++
        if (diffs <= 50) sb.appendLine("- $e")
      }
      e != a -> {
        diffs++
        if (diffs <= 50) {
          sb.appendLine("- $e")
          sb.appendLine("+ $a")
        }
      }
      else -> matches++
    }
  }
  if (diffs > 50) sb.appendLine("... (${diffs - 50} more differing lines)")
  sb.appendLine("Summary: $diffs differing line(s), $matches matching line(s)")
  return sb.toString()
}

private data class LocateStats(
  val samples: Int,
  val hits: Int,
  val misses: Int,
  val uniqueStructures: Int,
  val minDistance: Int,
  val maxDistance: Int,
  val averageDistance: Double,
  val topStructures: List<Pair<String, Int>>,
) {
  fun toSnapshot(caseName: String): StatisticsSnapshot =
    StatisticsSnapshot(
      caseName = caseName,
      sampleCount = samples,
      hitCount = hits,
      missCount = misses,
      uniqueStructures = uniqueStructures,
      minDistance = minDistance,
      maxDistance = maxDistance,
      averageDistance = averageDistance,
      topStructures = topStructures,
    )
}

private fun collectVillageStatistics(
  context: ClientGameTestContext,
  caseName: String,
  presetId: String,
): LocateStats {
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
        server.playerList.players[0].teleportTo(8.0, 100.0, 8.0)
      }
    )
    context.waitTicks(10)
    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client ->
        client.player?.let { player ->
          player.xRot = 60f
          player.yRot = 0f
          player.abilities.mayfly = true
          player.abilities.flying = true
          player.onUpdateAbilities()
        }
      }
    )
    context.waitTicks(5)
    game.clientWorld.waitForChunksRender()

    var stats: LocateStats? = null
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
        val generator = level.getChunkSource().getGenerator()
        val structReg = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
        val holders =
          structReg
            .listElements()
            .filter { holder ->
              holder.unwrapKey().map { key -> "village" in key.identifier().toString() }.orElse(false)
            }
            .toList()
        check(holders.isNotEmpty()) { "Expected at least one village structure holder" }
        val holderSet = HolderSet.direct(holders)

        val counts = LinkedHashMap<String, Int>()
        var samples = 0
        var hits = 0
        var misses = 0
        var minDistance = Int.MAX_VALUE
        var maxDistance = Int.MIN_VALUE
        var sumDistance = 0L

        for (chunkX in 0 until SAMPLE_CHUNKS) {
          for (chunkZ in 0 until SAMPLE_CHUNKS) {
            samples++
            val samplePos = BlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8)
            val found =
              generator.findNearestMapStructure(level, holderSet, samplePos, LOCATE_RADIUS, true)
            if (found == null) {
              misses++
              continue
            }
            hits++
            val id = found.second.unwrapKey().map { it.identifier().toString() }.orElse("unknown")
            counts[id] = (counts[id] ?: 0) + 1
            val pos = found.first
            val distance =
              sqrt(
                  (pos.x.toLong() * pos.x.toLong() + pos.z.toLong() * pos.z.toLong()).toDouble()
                )
                .toInt()
            minDistance = minOf(minDistance, distance)
            maxDistance = maxOf(maxDistance, distance)
            sumDistance += distance.toLong()
          }
        }

        val topStructures =
          counts.entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }
        val averageDistance = if (hits == 0) 0.0 else sumDistance.toDouble() / hits.toDouble()
        stats =
          LocateStats(
            samples = samples,
            hits = hits,
            misses = misses,
            uniqueStructures = counts.size,
            minDistance = if (hits == 0) -1 else minDistance,
            maxDistance = if (hits == 0) -1 else maxDistance,
            averageDistance = averageDistance,
            topStructures = topStructures,
          )

        LOGGER.info(
          "[StructureConstraint][statistics][{}] preset={} samples={} hits={} misses={} unique={} avgDist={}",
          caseName,
          presetId,
          samples,
          hits,
          misses,
          counts.size,
          "%.2f".format(averageDistance),
        )
      }
    )

    val result = stats ?: error("Statistics collection did not produce a result")
    writeOrCompareSnapshot(caseName, result.toSnapshot(caseName).toSnapshotText())
    return result
  } finally {
    game.close()
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintStatisticsTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanillaStats: LocateStats
    try {
      vanillaStats = collectVillageStatistics(context, "vanilla", DISABLED_PRESET)
    } finally {
      PresetRegistry.forcePresetId = null
    }

    registerDensePreset()
    PresetRegistry.forcePresetId = DENSE_PRESET
    val denseStats: LocateStats
    try {
      denseStats = collectVillageStatistics(context, "dense", DENSE_PRESET)
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(DENSE_PRESET)
    }

    registerBannedVillagePreset()
    PresetRegistry.forcePresetId = BANNED_VILLAGE_PRESET
    val bannedStats: LocateStats
    try {
      bannedStats = collectVillageStatistics(context, "banned_village", BANNED_VILLAGE_PRESET)
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(BANNED_VILLAGE_PRESET)
    }

    assertTrue(
      denseStats.hits > vanillaStats.hits,
      "Expected the dense preset to find more village hits than vanilla " +
        "(vanilla=${vanillaStats.hits}, dense=${denseStats.hits})",
    )
    assertTrue(
      denseStats.misses < vanillaStats.misses,
      "Expected the dense preset to miss fewer village samples than vanilla " +
        "(vanilla=${vanillaStats.misses}, dense=${denseStats.misses})",
    )
    assertTrue(
      bannedStats.hits == 0,
      "Expected the banned-village preset to suppress all village hits, but got ${bannedStats.hits}",
    )
  }
}
