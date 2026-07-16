package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.generation.DimensionContext

private val log = LoggerFactory.getLogger("ForcedStructureGameTest")

private const val SEED = "forced-structures"
private const val FORCED_PRESET = "forced_structure_village"
private const val FORCED_ID = "minecraft:village_plains"

private fun registerForcedPreset() {
  PresetRegistry.presets[FORCED_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures { force(FORCED_ID) }
    }
}

// Forces a plains village on the overworld root region and verifies, at STRUCTURE_STARTS
// resolution (no full generation needed), that:
//  1. the deterministic forced site's origin chunk holds a StructureStart for exactly that
//     structure, and
//  2. every other chunk touching the site's collision radius holds no structure start at all
//     (the collision ban suppressed the random pass).
@Suppress("UnstableApiUsage")
object ForcedStructureGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    registerForcedPreset()
    PresetRegistry.forcePresetId = FORCED_PRESET
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
        game.server.runOnServer(
          FailableConsumer<MinecraftServer, Exception> { server -> runAssertions(server) }
        )
      } finally {
        game.close()
      }
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(FORCED_PRESET)
    }
  }

  private fun runAssertions(server: MinecraftServer) {
    val level = server.overworld()
    val ctx = DimensionContext.get("minecraft:overworld")
    assertNotNull(ctx, "DimensionContext must be active for the forced preset")
    val forced = ctx!!.forcedStructures
    assertNotNull(forced, "forced structure lookup must be compiled for the forced preset")

    val sites = forced!!.sitesAt(ctx.traverser, ctx.cache, 0, 0)
    assertEquals(1, sites.size, "root region must plan exactly one forced site")
    val start = sites.single()
    val site = start.site
    log.info(
      "forced site for {} at block ({},{}) chunk ({},{}) radius={} budget={}",
      start.entry.id,
      site.blockX,
      site.blockZ,
      site.chunkX,
      site.chunkZ,
      site.radius,
      start.entry.budget,
    )

    val structure =
      server
        .registryAccess()
        .lookupOrThrow(Registries.STRUCTURE)
        .listElements()
        .filter { h ->
          h.unwrapKey().map { it.identifier().toString() == FORCED_ID }.orElse(false)
        }
        .findFirst()
        .orElseThrow()
        .value()

    val originChunk = level.getChunk(site.chunkX, site.chunkZ, ChunkStatus.STRUCTURE_STARTS, true)!!
    val forcedStart = originChunk.getStartForStructure(structure)
    assertNotNull(
      forcedStart,
      "forced site chunk (${site.chunkX},${site.chunkZ}) must hold a $FORCED_ID StructureStart",
    )
    assertTrue(forcedStart!!.isValid, "forced StructureStart must be valid")

    val extraStarts = mutableListOf<String>()
    val radiusSq = site.radius.toDouble() * site.radius.toDouble()
    val reachChunks = (site.radius.toInt() shr 4) + 1
    for (dz in -reachChunks..reachChunks) {
      for (dx in -reachChunks..reachChunks) {
        val chunkX = site.chunkX + dx
        val chunkZ = site.chunkZ + dz
        val nearestX = site.blockX.coerceIn(chunkX shl 4, (chunkX shl 4) + 15) - site.blockX
        val nearestZ = site.blockZ.coerceIn(chunkZ shl 4, (chunkZ shl 4) + 15) - site.blockZ
        if (nearestX.toDouble() * nearestX + nearestZ.toDouble() * nearestZ > radiusSq) continue
        val chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.STRUCTURE_STARTS, true)!!
        for ((other, otherStart) in chunk.getAllStarts()) {
          if (!otherStart.isValid) continue
          if (chunkX == site.chunkX && chunkZ == site.chunkZ && other === structure) continue
          extraStarts.add("${other.javaClass.simpleName}@($chunkX,$chunkZ)")
        }
      }
    }
    assertTrue(
      extraStarts.isEmpty(),
      "collision ban must suppress all random structure starts around the forced site, found: " +
        extraStarts.joinToString(),
    )
    log.info(
      "forced start confirmed at ({},{}), no random starts in ban radius",
      site.chunkX,
      site.chunkZ,
    )
  }
}
