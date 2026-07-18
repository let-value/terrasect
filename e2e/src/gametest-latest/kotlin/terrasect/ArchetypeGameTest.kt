package terrasect

import java.nio.file.Path
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.Archetype
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry

private val log = LoggerFactory.getLogger("ArchetypeGameTest")

private const val SEED = "archetype"

// A single infinite region must read the same at every distance from spawn, so we sample at growing
// offsets (and a couple of negative quadrants) and require the archetype's property to hold at
// each.
private val PROBES = listOf(0 to 0, 1500 to 0, 0 to 3000, 5000 to 5000, -4000 to 2500)

private val SCREENSHOTS_BASE: Path by lazy { e2eScreenshotsBase(object {}.javaClass) }

// Terrain summary of a 16x16 sample at a probe.
private data class ProbeStats(
  val surfaceMin: Int,
  val surfaceMax: Int,
  // fraction of columns whose surface sits at or below sea level (open water present)
  val seaFraction: Double,
  // fraction of columns holding a deep water column (ocean, not a shallow river/lake)
  val deepWaterFraction: Double,
  // ocean-floor heightmap extremes: how deep the water column actually reaches
  val floorMin: Int,
  val floorMax: Int,
) {
  val spread: Int
    get() = surfaceMax - surfaceMin
}

private fun singleRegionPreset(archetype: Archetype): RegionRegistry =
  RegionRegistry().apply {
    setRoot("minecraft:overworld", "root")
    region("root").archetype(archetype)
  }

private fun configureAerialCamera(client: Minecraft) {
  client.player?.let { player ->
    player.xRot = 90f
    player.yRot = 0f
    player.abilities.mayfly = true
    player.abilities.flying = true
    player.onUpdateAbilities()
  }
}

private fun sampleProbe(server: MinecraftServer, x: Int, z: Int): ProbeStats {
  val level = server.overworld()
  val sea = level.seaLevel
  var min = Int.MAX_VALUE
  var max = Int.MIN_VALUE
  var seaColumns = 0
  var deepColumns = 0
  var total = 0
  var floorMin = Int.MAX_VALUE
  var floorMax = Int.MIN_VALUE
  for (bx in x until x + 16) {
    for (bz in z until z + 16) {
      val surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz)
      val floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz)
      min = minOf(min, surface)
      max = maxOf(max, surface)
      floorMin = minOf(floorMin, floor)
      floorMax = maxOf(floorMax, floor)
      if (surface <= sea) seaColumns++
      if (surface - floor >= 4) deepColumns++
      total++
    }
  }
  return ProbeStats(
    min,
    max,
    seaColumns.toDouble() / total,
    deepColumns.toDouble() / total,
    floorMin,
    floorMax,
  )
}

// Loads a world under a single-region preset carrying `archetype`, then at each probe screenshots
// the terrain and asserts `check` holds — proving the archetype is enforced consistently regardless
// of distance from spawn.
private fun runArchetype(
  context: ClientGameTestContext,
  label: String,
  archetype: Archetype,
  check: (String, ProbeStats) -> Unit,
) {
  val presetId = "archetype_$label"
  PresetRegistry.presets[presetId] = singleRegionPreset(archetype)
  PresetRegistry.forcePresetId = presetId
  try {
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
      val results = PROBES.map { (x, z) -> Triple(x, z, probeAt(context, game, x, z, label)) }
      writeResults(label, results)
      for ((x, z, stats) in results) {
        check("[$label] probe ($x,$z)", stats)
      }
    } finally {
      game.close()
    }
  } finally {
    PresetRegistry.forcePresetId = null
    PresetRegistry.presets.remove(presetId)
  }
}

private fun writeResults(label: String, results: List<Triple<Int, Int, ProbeStats>>) {
  val dir = SCREENSHOTS_BASE.resolve("ArchetypeTest")
  dir.toFile().mkdirs()
  val text = buildString {
    appendLine("x,z,surfaceMin,surfaceMax,spread,seaFraction,deepWaterFraction,floorMin,floorMax")
    for ((x, z, s) in results) {
      appendLine(
        "$x,$z,${s.surfaceMin},${s.surfaceMax},${s.spread},${s.seaFraction},${s.deepWaterFraction},${s.floorMin},${s.floorMax}"
      )
    }
  }
  dir.resolve("${label}_probes.csv").toFile().writeText(text)
  log.info("[{}] probe results -> {}", label, text)
}

private fun probeAt(
  context: ClientGameTestContext,
  game: TestSingleplayerContext,
  x: Int,
  z: Int,
  label: String,
): ProbeStats {
  game.server.runOnServer(
    FailableConsumer<MinecraftServer, Exception> { server ->
      server.playerList.players[0].teleportTo(x + 8.0, 160.0, z + 8.0)
    }
  )
  context.waitTicks(20)
  context.runOnClient(
    FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
  )
  context.waitTicks(5)
  game.clientLevel.waitForChunksDownload()
  game.clientLevel.waitForChunksRender()
  context.runOnClient(
    FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
  )
  context.waitTicks(5)

  val screenshotDir = SCREENSHOTS_BASE.resolve("ArchetypeTest")
  context.takeScreenshot(
    TestScreenshotOptions.of("${label}_${x}_${z}").withDestinationDir(screenshotDir)
  )

  var stats: ProbeStats? = null
  game.server.runOnServer(
    FailableConsumer<MinecraftServer, Exception> { server -> stats = sampleProbe(server, x, z) }
  )
  return stats ?: error("[$label] probe ($x,$z) failed to sample")
}

@Suppress("UnstableApiUsage")
object OceanArchetypeGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    runArchetype(context, "ocean", Archetype.ocean()) { where, stats ->
      // open ocean everywhere: almost every column drowns below sea level
      assertTrue(
        stats.seaFraction >= 0.85,
        "$where expected open ocean, seaFraction=${stats.seaFraction}",
      )
    }
  }
}

@Suppress("UnstableApiUsage")
object LandlockedArchetypeGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    runArchetype(context, "landlocked", Archetype.landlocked()) { where, stats ->
      // no ocean basins; rivers/lakes may leave a little shallow water but not deep columns
      assertTrue(
        stats.deepWaterFraction <= 0.2,
        "$where expected no ocean, deepWaterFraction=${stats.deepWaterFraction}",
      )
    }
  }
}

@Suppress("UnstableApiUsage")
object FlatlandsArchetypeGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    runArchetype(context, "flatlands", Archetype.flatlands()) { where, stats ->
      assertTrue(stats.spread <= 20, "$where expected low relief, surface spread=${stats.spread}")
    }
  }
}

@Suppress("UnstableApiUsage")
object HighlandsArchetypeGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    runArchetype(context, "highlands", Archetype.highlands()) { where, stats ->
      // a raised plateau enforced everywhere: the whole sample sits well above sea level
      assertTrue(
        stats.surfaceMin >= 82,
        "$where expected an elevated plateau, surfaceMin=${stats.surfaceMin}",
      )
    }
  }
}
