package terrasect

import de.skuzzle.test.snapshots.SnapshotFile
import de.skuzzle.test.snapshots.SnapshotFile.SnapshotHeader
import java.nio.file.Path
import java.util.LinkedHashMap
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.instrumentation.CounterSnapshot
import terrasect.instrumentation.InMemoryBackend
import terrasect.instrumentation.Instr
import terrasect.instrumentation.MetricTag
import terrasect.instrumentation.MetricsConfig
import terrasect.instrumentation.TerrasectInstrScope
import terrasect.instrumentation.TerrasectMetricEvent

private val log = LoggerFactory.getLogger("StructureConstraintStatisticsTest")

private const val SEED = "structure-constraints"
private const val DISABLED_PRESET = "__disabled__"
private const val DENSE_PRESET = "structure_constraint_statistics_dense"
private const val BANNED_VILLAGE_PRESET = "structure_constraint_statistics_banned_village"
private const val GENERATED_CHUNK_BASE = 64
private const val GENERATED_CHUNK_SIZE = 10
private const val VILLAGE_TAG = "minecraft:village"

private val GAME_TEST_MODULE_DIR: Path by lazy {
  val classesRoot = Path.of(object {}.javaClass.protectionDomain.codeSource.location.toURI())
  classesRoot.parent.parent.parent.parent.also { log.info("module dir = {}", it) }
}

private val GAME_TEST_RESOURCES_BASE: Path by lazy {
  GAME_TEST_MODULE_DIR.resolve("src/gametest/resources/terrasect")
}

private data class GeneratedStructureStats(
  val caseName: String,
  val chunkArea: String,
  val totalGenerated: Long,
  val structureCounts: List<Pair<String, Long>>,
) {
  fun toSnapshotText(): String = buildString {
    appendLine("case,$caseName")
    appendLine("chunk_area,$chunkArea")
    appendLine("total_generated,$totalGenerated")
    appendLine("structure_id,count")
    for ((id, count) in structureCounts) {
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

private const val TOTAL_GENERATED_TOLERANCE = 100L
private const val STRUCTURE_COUNT_TOLERANCE = 50L

private fun parseGeneratedStructureSnapshot(text: String): GeneratedStructureStats {
  val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
  require(lines.size >= 4) { "Invalid generated-structure snapshot: expected at least 4 lines, got ${lines.size}" }

  fun parsePair(line: String): Pair<String, String> {
    val idx = line.indexOf(',')
    require(idx > 0 && idx < line.lastIndex) { "Invalid snapshot line: '$line'" }
    return line.substring(0, idx) to line.substring(idx + 1)
  }

  val (caseKey, caseName) = parsePair(lines[0])
  require(caseKey == "case") { "Expected first snapshot line to start with 'case', got '${lines[0]}'" }
  val (areaKey, chunkArea) = parsePair(lines[1])
  require(areaKey == "chunk_area") { "Expected second snapshot line to start with 'chunk_area', got '${lines[1]}'" }
  val (totalKey, totalValue) = parsePair(lines[2])
  require(totalKey == "total_generated") {
    "Expected third snapshot line to start with 'total_generated', got '${lines[2]}'"
  }
  require(lines[3] == "structure_id,count") {
    "Expected fourth snapshot line to be 'structure_id,count', got '${lines[3]}'"
  }

  val counts =
    lines.drop(4).map { line ->
      val (id, countText) = parsePair(line)
      id to countText.toLong()
    }

  return GeneratedStructureStats(
    caseName = caseName,
    chunkArea = chunkArea,
    totalGenerated = totalValue.toLong(),
    structureCounts = counts,
  )
}

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
    log.info("[{}] snapshot {}", caseName, snapshotFile)
  } else {
    val expected = parseGeneratedStructureSnapshot(SnapshotFile.fromSnapshotFile(snapshotFile).snapshot())
    val actual = parseGeneratedStructureSnapshot(text)
    val errors = mutableListOf<String>()

    if (expected.caseName != actual.caseName) {
      errors += "case mismatch: expected=${expected.caseName} actual=${actual.caseName}"
    }
    if (expected.chunkArea != actual.chunkArea) {
      errors += "chunk_area mismatch: expected=${expected.chunkArea} actual=${actual.chunkArea}"
    }

    val totalDelta = kotlin.math.abs(expected.totalGenerated - actual.totalGenerated)
    if (totalDelta > TOTAL_GENERATED_TOLERANCE) {
      errors +=
        "total_generated differs by $totalDelta (expected=${expected.totalGenerated} actual=${actual.totalGenerated}, tolerance=$TOTAL_GENERATED_TOLERANCE)"
    }

    val expectedCounts = expected.structureCounts.toMap()
    val actualCounts = actual.structureCounts.toMap()
    val missing = expectedCounts.keys - actualCounts.keys
    val extra = actualCounts.keys - expectedCounts.keys
    if (missing.isNotEmpty()) errors += "missing structure ids: ${missing.sorted().joinToString()}"
    if (extra.isNotEmpty()) errors += "unexpected structure ids: ${extra.sorted().joinToString()}"

    for (structureId in expectedCounts.keys.intersect(actualCounts.keys)) {
      val delta = kotlin.math.abs(expectedCounts.getValue(structureId) - actualCounts.getValue(structureId))
      if (delta > STRUCTURE_COUNT_TOLERANCE) {
        errors +=
          "$structureId differs by $delta (expected=${expectedCounts.getValue(structureId)} actual=${actualCounts.getValue(structureId)}, tolerance=$STRUCTURE_COUNT_TOLERANCE)"
      }
    }

    if (errors.isNotEmpty()) {
      throw AssertionError(
        "Statistics snapshot mismatch at $snapshotFile\n" +
          errors.joinToString(prefix = "- ", separator = "\n- ") +
          "\nRerun with -PupdateSnapshots to accept the new snapshot, or inspect the diff:\n" +
          buildUnifiedDiff(expected.toSnapshotText(), actual.toSnapshotText())
      )
    }
    log.info("[{}] snapshot matches {} (within tolerance)", caseName, snapshotFile)
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

private fun collectGeneratedStructureStats(
  context: ClientGameTestContext,
  caseName: String,
  presetId: String,
): GeneratedStructureStats {
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
    context.waitTicks(120)
    game.clientWorld.waitForChunksRender()
    Instr.reset()
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
        for (chunkX in GENERATED_CHUNK_BASE until GENERATED_CHUNK_BASE + GENERATED_CHUNK_SIZE) {
          for (chunkZ in GENERATED_CHUNK_BASE until GENERATED_CHUNK_SIZE + GENERATED_CHUNK_BASE) {
            level.getChunk(chunkX, chunkZ)
          }
        }
      }
    )

    val stats = awaitStableGeneratedStructureStats(context, caseName)
    writeOrCompareSnapshot(caseName, stats.toSnapshotText())

    log.info(
      "[{}] preset={} total={} types={} first={}",
      caseName,
      presetId,
      stats.totalGenerated,
      stats.structureCounts.size,
      stats.structureCounts.firstOrNull()?.first ?: "none",
    )
    return stats
  } finally {
    game.close()
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
  val generatedSnapshots =
    snapshots.filter {
      it.id.scope == TerrasectInstrScope.STRUCTURE.id &&
        it.id.event == TerrasectMetricEvent.STRUCTURE_GENERATED.id
    }
  check(generatedSnapshots.isNotEmpty()) {
    "Expected generated-structure counters for $caseName, but the instrumentation snapshot was empty"
  }

  // Filter to the target chunk area; each snapshot key in InMemoryBackend is unique per
  // (structure_id, location), so +1L correctly counts each distinct placement once.
  val filteredSnapshots =
    generatedSnapshots.filter { snapshot ->
      val location =
        snapshot.id.tags.firstOrNull { it.key == "location" }?.value ?: return@filter false
      isLocationInTargetArea(location)
    }

  val groupedCounts = LinkedHashMap<String, Long>()
  for (snapshot in filteredSnapshots) {
    val structureId =
      snapshot.id.tags.firstOrNull { tag -> tag.key == "structure_id" }?.value
        ?: error("Missing structure_id tag in $caseName snapshot: ${snapshot.id}")
    groupedCounts[structureId] = (groupedCounts[structureId] ?: 0L) + 1L
  }

  val orderedCounts =
    groupedCounts.entries
      .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
      .map { it.key to it.value }

  return GeneratedStructureStats(
    caseName = caseName,
    chunkArea = "${GENERATED_CHUNK_SIZE}x${GENERATED_CHUNK_SIZE}",
    totalGenerated = filteredSnapshots.size.toLong(),
    structureCounts = orderedCounts,
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
      if (scope != TerrasectInstrScope.STRUCTURE) {
        MetricsConfig.setScopeEnabled(scope, false)
      }
    }
    Instr.setBackend(InMemoryBackend())

    try {
      PresetRegistry.forcePresetId = DISABLED_PRESET
      val vanillaStats: GeneratedStructureStats
      try {
        vanillaStats = collectGeneratedStructureStats(context, "vanilla", DISABLED_PRESET)
      } finally {
        PresetRegistry.forcePresetId = null
      }

      registerDensePreset()
      PresetRegistry.forcePresetId = DENSE_PRESET
      val denseStats: GeneratedStructureStats
      try {
        denseStats = collectGeneratedStructureStats(context, "dense", DENSE_PRESET)
      } finally {
        PresetRegistry.forcePresetId = null
        PresetRegistry.presets.remove(DENSE_PRESET)
      }

      registerBannedVillagePreset()
      PresetRegistry.forcePresetId = BANNED_VILLAGE_PRESET
      val bannedStats: GeneratedStructureStats
      try {
        bannedStats = collectGeneratedStructureStats(context, "banned_village", BANNED_VILLAGE_PRESET)
      } finally {
        PresetRegistry.forcePresetId = null
        PresetRegistry.presets.remove(BANNED_VILLAGE_PRESET)
      }

      val vanillaVillageCount = vanillaStats.countForStructure("minecraft:village")
      val denseVillageCount = denseStats.countForStructure("minecraft:village")
      val bannedVillageCount = bannedStats.countForStructure("minecraft:village")

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
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(DENSE_PRESET)
      PresetRegistry.presets.remove(BANNED_VILLAGE_PRESET)
      MetricsConfig.enabled = false
      MetricsConfig.countersEnabled = false
      MetricsConfig.clearScopeOverrides()
      Instr.setBackend(previousBackend)
    }
  }
}

private fun GeneratedStructureStats.countForStructure(structureId: String): Long =
  structureCounts.firstOrNull { it.first == structureId }?.second ?: 0L
