//? if latest {
package terrasect

import java.nio.file.Path
import kotlin.math.sqrt
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.generation.DimensionContext

private val log = LoggerFactory.getLogger("OtherDimensionGameTest")

private const val SEED = "other-dimensions"
private const val DISABLED_PRESET = "__disabled__"
private const val NETHER_DIM = "minecraft:the_nether"
private const val END_DIM = "minecraft:the_end"
private const val OVERWORLD_DIM = "minecraft:overworld"
private const val SPAWN_WAIT_TICKS = 300

private val SCREENSHOTS_BASE: Path by lazy { e2eScreenshotsBase(object {}.javaClass) }

// Two probes far apart so the constraint is checked in distinct region-grid cells, not just around
// the origin.
private val PROBES = listOf(0 to 0, 700 to 700)

private data class SurfaceStats(val min: Int, val max: Int)

private fun sampleSurfaces(level: ServerLevel, x: Int, z: Int): SurfaceStats {
  // getHeight returns minY for unloaded chunks instead of generating them; force full generation
  // of the probe area first (safe on the server thread — the chunk system blocks until done).
  for (cx in (x shr 4)..((x + 15) shr 4)) {
    for (cz in (z shr 4)..((z + 15) shr 4)) {
      level.getChunk(cx, cz)
    }
  }
  var min = Int.MAX_VALUE
  var max = Int.MIN_VALUE
  for (bx in x until x + 16) {
    for (bz in z until z + 16) {
      val surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz)
      min = minOf(min, surface)
      max = maxOf(max, surface)
    }
  }
  return SurfaceStats(min, max)
}

private fun createWorld(context: ClientGameTestContext): TestSingleplayerContext =
  context
    .worldBuilder()
    .setUseConsistentSettings(false)
    .adjustSettings { settings ->
      settings.seed = SEED
      settings.gameMode = WorldCreationUiState.SelectedGameMode.CREATIVE
    }
    .create()

private fun <T> onServer(game: TestSingleplayerContext, action: (MinecraftServer) -> T): T {
  var result: T? = null
  game.server.runOnServer(
    FailableConsumer<MinecraftServer, Exception> { server -> result = action(server) }
  )
  @Suppress("UNCHECKED_CAST")
  return result as T
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

private fun teleportInto(
  context: ClientGameTestContext,
  game: TestSingleplayerContext,
  dimensionId: String,
  x: Int,
  y: Int,
  z: Int,
) {
  game.server.runCommand("execute in $dimensionId run tp @a $x $y $z")
  context.waitTicks(20)
  context.runOnClient(
    FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
  )
  context.waitTicks(5)
  game.clientLevel.waitForChunksDownload()
  game.clientLevel.waitForChunksRender()
}

private fun screenshotHere(
  context: ClientGameTestContext,
  name: String,
  dir: Path,
) {
  context.runOnClient(
    FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
  )
  context.waitTicks(5)
  context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(dir))
}

private fun probeDimensionSurfaces(
  game: TestSingleplayerContext,
  dimension: ResourceKey<Level>,
  label: String,
): List<Triple<Int, Int, SurfaceStats>> =
  onServer(game) { server ->
    val level = server.getLevel(dimension) ?: error("[$label] level $dimension missing")
    PROBES.map { (x, z) ->
      val stats = sampleSurfaces(level, x, z)
      log.info("[{}] probe ({},{}) surface={}..{}", label, x, z, stats.min, stats.max)
      Triple(x, z, stats)
    }
  }

private fun dimensionContextIds(): Set<String> = DimensionContext.map.keys.toSet()

private fun locateStructure(
  game: TestSingleplayerContext,
  dimension: ResourceKey<Level>,
  label: String,
  structureFilter: (String) -> Boolean,
): String? =
  onServer(game) { server ->
    val level = server.getLevel(dimension) ?: error("[$label] level $dimension missing")
    val generator = level.getChunkSource().getGenerator()
    val structReg = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
    val holders =
      structReg
        .listElements()
        .filter { h ->
          h.unwrapKey().map { structureFilter(it.identifier().toString()) }.orElse(false)
        }
        .toList()
    if (holders.isEmpty()) {
      log.warn("[{}] no matching structure holders in registry", label)
      return@onServer null
    }
    val found =
      generator.findNearestMapStructure(
        level,
        HolderSet.direct(holders),
        BlockPos(0, 64, 0),
        1000,
        true,
      )
    if (found != null) {
      val id = found.second.unwrapKey().map { it.identifier().toString() }.orElse("unknown")
      val pos = found.first
      val dist =
        sqrt((pos.x.toLong().let { it * it } + pos.z.toLong().let { it * it }).toDouble()).toInt()
      log.info("[{}] found {} at ({},{}) distance={}", label, id, pos.x, pos.z, dist)
      id
    } else {
      log.info("[{}] nothing found within radius=1000", label)
      null
    }
  }

// The nether has a bedrock roof: WORLD_SURFACE reads ~128 everywhere in vanilla. Pinning
// finalDensity to a constant negative carves the whole dimension to air (plus the default lava sea
// below y=32), so a working noise pipeline drops the surface by ~100 blocks. That asymmetry is a
// binary, biome-independent signal that the density-function wrapping runs for nether chunks.
@Suppress("UnstableApiUsage")
object NetherNoiseConstraintGameTest : FabricClientGameTest {
  private const val PRESET = "nether_noise_void"

  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val screenshotDir = SCREENSHOTS_BASE.resolve("NetherNoiseConstraintTest")

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanilla: List<Triple<Int, Int, SurfaceStats>>
    try {
      val game = createWorld(context)
      try {
        vanilla = probeDimensionSurfaces(game, Level.NETHER, "nether_vanilla")
        assertNull(
          DimensionContext.get(NETHER_DIM),
          "[nether_vanilla] disabled preset must not register a nether DimensionContext",
        )
        teleportInto(context, game, NETHER_DIM, 8, 90, 8)
        screenshotHere(context, "vanilla_nether", screenshotDir)
      } finally {
        game.close()
      }
    } finally {
      PresetRegistry.forcePresetId = null
    }

    PresetRegistry.presets[PRESET] =
      RegionRegistry().apply {
        setRoot(NETHER_DIM, "nether_root")
        // Structures (fortresses, bastions) generate independently of terrain and would leave
        // solid pieces in the carved void, so block them to measure pure noise output.
        region("nether_root")
          .noise { densityFunction("finalDensity") { it.multiply(0.0).add(-1.0) } }
          .structures { blockMods("minecraft") }
      }
    PresetRegistry.forcePresetId = PRESET
    val constrained: List<Triple<Int, Int, SurfaceStats>>
    try {
      val game = createWorld(context)
      try {
        assertNotNull(
          DimensionContext.get(NETHER_DIM),
          "[nether_void] preset roots $NETHER_DIM — a DimensionContext must be registered for it " +
            "(registered: ${dimensionContextIds()})",
        )
        constrained = probeDimensionSurfaces(game, Level.NETHER, "nether_void")
        teleportInto(context, game, NETHER_DIM, 8, 90, 8)
        screenshotHere(context, "void_nether", screenshotDir)
      } finally {
        game.close()
      }
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(PRESET)
    }

    for ((x, z, stats) in vanilla) {
      assertTrue(
        stats.max >= 100,
        "[nether_vanilla] probe ($x,$z) expected the vanilla bedrock roof (surface >= 100), " +
          "got max=${stats.max}",
      )
    }
    for ((x, z, stats) in constrained) {
      assertTrue(
        stats.max <= 60,
        "[nether_void] probe ($x,$z) finalDensity pinned to -1 must carve the nether to air/lava " +
          "sea (surface <= 60), got max=${stats.max} — noise constraints did not apply in the " +
          "nether",
      )
    }
  }
}

// The end has no MultiNoiseBiomeSource, so DimensionContext is built with a null climate list —
// this covers that path end-to-end. Vanilla generates the main island under (0,0); pinning
// finalDensity negative must erase it entirely.
@Suppress("UnstableApiUsage")
object EndNoiseConstraintGameTest : FabricClientGameTest {
  private const val PRESET = "end_noise_void"

  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val screenshotDir = SCREENSHOTS_BASE.resolve("EndNoiseConstraintTest")

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanilla: SurfaceStats
    try {
      val game = createWorld(context)
      try {
        vanilla =
          onServer(game) { server ->
            val level = server.getLevel(Level.END) ?: error("[end_vanilla] end level missing")
            sampleSurfaces(level, 64, 64)
          }
        log.info("[end_vanilla] island probe surface={}..{}", vanilla.min, vanilla.max)
      } finally {
        game.close()
      }
    } finally {
      PresetRegistry.forcePresetId = null
    }

    PresetRegistry.presets[PRESET] =
      RegionRegistry().apply {
        setRoot(END_DIM, "end_root")
        region("end_root")
          .noise { densityFunction("finalDensity") { it.multiply(0.0).add(-1.0) } }
          .structures { blockMods("minecraft") }
      }
    PresetRegistry.forcePresetId = PRESET
    val constrained: SurfaceStats
    try {
      val game = createWorld(context)
      try {
        assertNotNull(
          DimensionContext.get(END_DIM),
          "[end_void] preset roots $END_DIM — a DimensionContext must be registered for it " +
            "(registered: ${dimensionContextIds()}). The end has no MultiNoiseBiomeSource, so " +
            "registration must survive a null climate parameter list.",
        )
        constrained =
          onServer(game) { server ->
            val level = server.getLevel(Level.END) ?: error("[end_void] end level missing")
            sampleSurfaces(level, 64, 64)
          }
        log.info("[end_void] island probe surface={}..{}", constrained.min, constrained.max)
        teleportInto(context, game, END_DIM, 8, 90, 8)
        screenshotHere(context, "void_end", screenshotDir)
      } finally {
        game.close()
      }
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(PRESET)
    }

    assertTrue(
      vanilla.max >= 50,
      "[end_vanilla] expected the main end island under the origin (surface >= 50), " +
        "got max=${vanilla.max}",
    )
    assertTrue(
      constrained.max <= 10,
      "[end_void] finalDensity pinned to -1 must erase the main island (surface <= 10), " +
        "got max=${constrained.max} — noise constraints did not apply in the end",
    )
  }
}

// Mob constraints on the real natural-spawn path in the nether: vanilla spawns freely around the
// player; blockMods("minecraft") on the nether root must suppress every vanilla mob.
@Suppress("UnstableApiUsage")
object NetherMobConstraintGameTest : FabricClientGameTest {
  private const val PRESET = "nether_mob_block_all"
  private val MOB_COUNT_AABB = AABB(-120.0, 0.0, -120.0, 136.0, 256.0, 136.0)

  private fun runNetherSpawnProbe(
    context: ClientGameTestContext,
    label: String,
    screenshotName: String,
    screenshotDir: Path,
  ): Map<String, Int> {
    val game = createWorld(context)
    try {
      teleportInto(context, game, NETHER_DIM, 8, 90, 8)
      context.waitTicks(SPAWN_WAIT_TICKS)
      screenshotHere(context, screenshotName, screenshotDir)

      return onServer(game) { server ->
        val level = server.getLevel(Level.NETHER) ?: error("[$label] nether level missing")
        val counts = LinkedHashMap<String, Int>()
        for (mob in level.getEntitiesOfClass(Mob::class.java, MOB_COUNT_AABB) { true }) {
          val id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.type).toString()
          counts[id] = (counts[id] ?: 0) + 1
        }
        log.info("[{}] nether mobs: total={} types={}", label, counts.values.sum(), counts)
        counts
      }
    } finally {
      game.close()
    }
  }

  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val screenshotDir = SCREENSHOTS_BASE.resolve("NetherMobConstraintTest")

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanillaCounts: Map<String, Int>
    try {
      vanillaCounts = runNetherSpawnProbe(context, "vanilla", "vanilla_mobs", screenshotDir)
    } finally {
      PresetRegistry.forcePresetId = null
    }

    PresetRegistry.presets[PRESET] =
      RegionRegistry().apply {
        setRoot(NETHER_DIM, "nether_root")
        region("nether_root").mobs { blockMods("minecraft") }
      }
    PresetRegistry.forcePresetId = PRESET
    val blockedCounts: Map<String, Int>
    try {
      blockedCounts = runNetherSpawnProbe(context, "block_all", "blocked_mobs", screenshotDir)
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(PRESET)
    }

    val vanillaTotal = vanillaCounts.values.sum()
    val blockedTotal = blockedCounts.values.sum()
    log.info("[nether_mobs] summary: vanilla={} blocked={}", vanillaTotal, blockedTotal)

    assertTrue(
      vanillaTotal > 0,
      "[nether_mobs] vanilla nether must naturally spawn mobs around the player within " +
        "$SPAWN_WAIT_TICKS ticks, got none — the baseline is invalid",
    )
    assertEquals(
      0,
      blockedTotal,
      "[nether_mobs] blockMods(minecraft) on the nether root must suppress all natural spawns, " +
        "found $blockedCounts — mob constraints did not apply in the nether",
    )
  }
}

// Structure constraints in the nether via the locate path: vanilla finds a fortress or bastion
// within the search radius; blockMods("minecraft") on the nether root must make locate come up
// empty.
@Suppress("UnstableApiUsage")
object NetherStructureConstraintGameTest : FabricClientGameTest {
  private const val PRESET = "nether_structure_block_all"
  private val filter: (String) -> Boolean = { "fortress" in it || "bastion" in it }

  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanillaId: String?
    try {
      val game = createWorld(context)
      try {
        vanillaId = locateStructure(game, Level.NETHER, "nether_structs_vanilla", filter)
      } finally {
        game.close()
      }
    } finally {
      PresetRegistry.forcePresetId = null
    }

    PresetRegistry.presets[PRESET] =
      RegionRegistry().apply {
        setRoot(NETHER_DIM, "nether_root")
        region("nether_root").structures { blockMods("minecraft") }
      }
    PresetRegistry.forcePresetId = PRESET
    val blockedId: String?
    try {
      val game = createWorld(context)
      try {
        blockedId = locateStructure(game, Level.NETHER, "nether_structs_blocked", filter)
      } finally {
        game.close()
      }
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(PRESET)
    }

    assertNotNull(
      vanillaId,
      "[nether_structs] vanilla must locate a fortress or bastion within radius=1000",
    )
    assertNull(
      blockedId,
      "[nether_structs] blockMods(minecraft) on the nether root must suppress fortress/bastion " +
        "locate, but found $blockedId — structure constraints did not apply in the nether",
    )
  }
}

// A preset that roots only the nether must leave every other dimension untouched — including not
// inheriting a stale DimensionContext left behind by a previous world whose preset did root the
// overworld. A stale context carries the old world's seed and regions, so terrain there would be
// silently wrong.
@Suppress("UnstableApiUsage")
object DimensionContextIsolationGameTest : FabricClientGameTest {
  private const val OVERWORLD_PRESET = "isolation_overworld_only"
  private const val NETHER_PRESET = "isolation_nether_only"

  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    PresetRegistry.presets[OVERWORLD_PRESET] =
      RegionRegistry().apply {
        setRoot(OVERWORLD_DIM, "overworld_root")
        region("overworld_root").mobs { blockNames("minecraft:creeper") }
      }
    PresetRegistry.presets[NETHER_PRESET] =
      RegionRegistry().apply {
        setRoot(NETHER_DIM, "nether_root")
        region("nether_root").mobs { blockNames("minecraft:ghast") }
      }

    try {
      PresetRegistry.forcePresetId = OVERWORLD_PRESET
      val first = createWorld(context)
      try {
        assertNotNull(
          DimensionContext.get(OVERWORLD_DIM),
          "[isolation] world 1 roots the overworld — its context must be registered",
        )
        assertNull(
          DimensionContext.get(NETHER_DIM),
          "[isolation] world 1 has no nether root — no nether context may be registered " +
            "(registered: ${dimensionContextIds()})",
        )
      } finally {
        first.close()
      }

      PresetRegistry.forcePresetId = NETHER_PRESET
      val second = createWorld(context)
      try {
        assertNotNull(
          DimensionContext.get(NETHER_DIM),
          "[isolation] world 2 roots the nether — its context must be registered",
        )
        assertNull(
          DimensionContext.get(OVERWORLD_DIM),
          "[isolation] world 2 has no overworld root, but an overworld DimensionContext is still " +
            "registered — a stale context from world 1 leaked across worlds (registered: " +
            "${dimensionContextIds()})",
        )
      } finally {
        second.close()
      }
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(OVERWORLD_PRESET)
      PresetRegistry.presets.remove(NETHER_PRESET)
    }
  }
}
//?}