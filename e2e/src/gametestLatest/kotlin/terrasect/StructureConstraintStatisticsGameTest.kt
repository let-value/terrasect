package terrasect.gametest

import java.nio.file.Path
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import terrasect.definition.RegionRegistry
import terrasect.instrumentation.CounterSnapshot
import terrasect.instrumentation.InMemoryBackend
import terrasect.instrumentation.Instr
import terrasect.instrumentation.MetricsConfig
import terrasect.instrumentation.TerrasectInstrScope
import terrasect.instrumentation.TerrasectMetricEvent

private const val SEED = "structure-constraints"
private const val GENERATED_CHUNK_BASE = 64
private const val GENERATED_CHUNK_SIZE = 10
private const val VILLAGE_TAG = "minecraft:village"
private val VILLAGE_STRUCTURE_IDS =
  arrayOf(
    "minecraft:village_desert",
    "minecraft:village_plains",
    "minecraft:village_savanna",
    "minecraft:village_snowy",
    "minecraft:village_taiga",
  )

private val GAME_TEST_MODULE_DIR: Path by lazy {
  e2eModuleDir(object {}.javaClass)
}

private val GAME_TEST_RESOURCES_BASE: Path by lazy {
  GAME_TEST_MODULE_DIR.resolve("src/gametest/resources/terrasect")
}

private data class GeneratedStructureStats(
  val caseName: String,
  val chunkArea: String,
  val totalGenerated: Long,
  val structureCounts: List<Pair<String, Long>>,
  val structureOrigins: List<Pair<String, String>>,
) {
  fun toSnapshotText(): String = buildString {
    appendLine("case,$caseName")
    appendLine("chunk_area,$chunkArea")
    appendLine("total_generated,$totalGenerated")
    appendLine("structure_id,count")
    for ((id, count) in structureCounts) {
      appendLine("$id,$count")
    }
    appendLine("structure_id,origin")
    for ((id, origin) in structureOrigins) {
      appendLine("$id,$origin")
    }
  }
}

private fun snapshotPath(caseName: String): Path =
  GAME_TEST_RESOURCES_BASE.resolve("StructureConstraintStatisticsTest_snapshots/$caseName.snapshot")

private fun writeOrCompareSnapshot(caseName: String, text: String) {
  val expected =
    testSnapshot(
      snapshotPath(caseName),
      text,
      StructureConstraintStatisticsTest::class.qualifiedName!!,
      caseName,
    )
  assertEquals(expected.trimEnd(), text.trimEnd(), "$caseName: generated structures")
}

private fun collectGeneratedStructureStats(
  context: ClientGameTestContext,
  caseName: String,
  preset: RegionRegistry? = null,
): GeneratedStructureStats =
  context.withTestWorld(SEED, "structure_statistics_test", preset) { game ->
    context.waitTicks(120)
    Instr.reset()
    game.onServer { server ->
      for (x in GENERATED_CHUNK_BASE until GENERATED_CHUNK_BASE + GENERATED_CHUNK_SIZE) {
        for (z in GENERATED_CHUNK_BASE until GENERATED_CHUNK_BASE + GENERATED_CHUNK_SIZE) {
          server.overworld().getChunk(x, z)
        }
      }
    }
    awaitStableGeneratedStructureStats(context, caseName).also {
      game.screenshot(
        context,
        "StructureConstraintStatisticsTest/$caseName",
        (GENERATED_CHUNK_BASE shl 4) + 8,
        (GENERATED_CHUNK_BASE shl 4) + 8,
      )
      writeOrCompareSnapshot(caseName, it.toSnapshotText())
    }
  }

private fun awaitStableGeneratedStructureStats(
  context: ClientGameTestContext,
  caseName: String,
  maxTicks: Int = 600,
  stableTicksRequired: Int = 20,
): GeneratedStructureStats {
  var lastStats: GeneratedStructureStats? = null
  var stableTicks = 0
  repeat(maxTicks) {
    val current = summarizeGeneratedStructureStats(caseName, Instr.counterSnapshot())
    if (current == lastStats && current.totalGenerated > 0L) {
      stableTicks++
    } else {
      stableTicks = 0
    }
    if (stableTicks >= stableTicksRequired) return current
    lastStats = current
    context.waitTicks(1)
  }
  error(
    "Timed out waiting for generated-structure counters to settle in $caseName after " +
      "$maxTicks ticks; last observed stats=$lastStats"
  )
}

private fun isLocationInTargetArea(location: String): Boolean {
  if (!location.startsWith("chunk=")) return false
  val coords = location.removePrefix("chunk=").split(",")
  if (coords.size != 2) return false
  val x = coords[0].toIntOrNull() ?: return false
  val z = coords[1].toIntOrNull() ?: return false
  return x in GENERATED_CHUNK_BASE until GENERATED_CHUNK_BASE + GENERATED_CHUNK_SIZE &&
    z in GENERATED_CHUNK_BASE until GENERATED_CHUNK_BASE + GENERATED_CHUNK_SIZE
}

private fun summarizeGeneratedStructureStats(
  caseName: String,
  snapshots: List<CounterSnapshot>,
): GeneratedStructureStats {
  val generated = snapshots.filter {
    it.value > 0 &&
      it.id.scope == TerrasectInstrScope.STRUCTURE.id &&
      it.id.event == TerrasectMetricEvent.STRUCTURE_GENERATED.id
  }
  check(generated.isNotEmpty()) { "No generated-structure counters for $caseName" }
  val origins =
    generated
      .mapNotNull { snapshot ->
        val tags = snapshot.id.tags.associate { it.key to it.value }
        val location = tags["location"] ?: return@mapNotNull null
        if (!isLocationInTargetArea(location)) return@mapNotNull null
        checkNotNull(tags["structure_id"]) to (tags["origin"] ?: location)
      }
      .distinct()
      .sortedWith(compareBy({ it.first }, { it.second }))
  val counts =
    origins
      .groupingBy { it.first }
      .eachCount()
      .entries
      .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
      .map { it.key to it.value.toLong() }
  return GeneratedStructureStats(
    caseName,
    "${GENERATED_CHUNK_SIZE}x${GENERATED_CHUNK_SIZE}",
    origins.size.toLong(),
    counts,
    origins,
  )
}

@Suppress("UnstableApiUsage")
object StructureConstraintStatisticsTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val previousBackend = Instr.getBackend()
    MetricsConfig.enabled = true
    MetricsConfig.countersEnabled = true
    MetricsConfig.clearScopeOverrides()
    for (scope in TerrasectInstrScope.entries) {
      MetricsConfig.setScopeEnabled(scope, scope == TerrasectInstrScope.STRUCTURE)
    }
    for (event in TerrasectMetricEvent.entries) {
      MetricsConfig.setEventCountersEnabled(
        event,
        event == TerrasectMetricEvent.STRUCTURE_GENERATED,
      )
    }
    Instr.setBackend(InMemoryBackend())

    try {
      val vanillaStats = collectGeneratedStructureStats(context, "vanilla")
      val denseStats =
        collectGeneratedStructureStats(
          context,
          "dense",
          testPreset {
            structures {
              spacing(8)
              separation(4)
              frequency(1f)
            }
          },
        )
      val bannedStats =
        collectGeneratedStructureStats(
          context,
          "banned_village",
          testPreset {
            structures {
              blockTags(VILLAGE_TAG)
              blockNames(*VILLAGE_STRUCTURE_IDS)
            }
          },
        )

      val vanillaVillageCount = vanillaStats.countForStructurePrefix("minecraft:village_")
      val denseVillageCount = denseStats.countForStructurePrefix("minecraft:village_")
      val bannedVillageCount = bannedStats.countForStructurePrefix("minecraft:village_")

      assertTrue(
        denseStats.totalGenerated > vanillaStats.totalGenerated,
        "Expected the dense preset to generate more structures than vanilla " +
          "(vanilla=${vanillaStats.totalGenerated}, dense=${denseStats.totalGenerated})",
      )
      assertTrue(
        denseVillageCount >= vanillaVillageCount,
        "Expected the dense preset to generate at least as many villages as vanilla " +
          "(vanilla=$vanillaVillageCount, dense=$denseVillageCount)",
      )
      assertTrue(
        bannedVillageCount == 0L,
        "Expected the banned-village preset to suppress village generation, but got $bannedVillageCount",
      )
      assertTrue(
        bannedStats.totalGenerated <= denseStats.totalGenerated,
        "Expected banned-village generation to stay at or below the dense preset total " +
          "(dense=${denseStats.totalGenerated}, banned=${bannedStats.totalGenerated})",
      )
    } finally {
      MetricsConfig.enabled = false
      MetricsConfig.countersEnabled = false
      MetricsConfig.clearScopeOverrides()
      Instr.setBackend(previousBackend)
    }
  }
}

private fun GeneratedStructureStats.countForStructurePrefix(prefix: String): Long =
  structureCounts.filter { (id, _) -> id.startsWith(prefix) }.sumOf { it.second }
