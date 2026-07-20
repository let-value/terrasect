//? if latest {
package terrasect

import java.nio.file.Path
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
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
import terrasect.generation.ForcedSite

private val log = LoggerFactory.getLogger("ForcedStructureGameTest")

private const val SEED = "forced-structures"
private const val FORCED_PRESET = "forced_structure_village"
private const val DENSE_PRESET = "forced_structure_village_dense"
private const val FORCED_ID = "minecraft:village_plains"
private const val DENSE_FILLER_ID = "minecraft:ruined_portal"
private const val OUTSIDE_RING_CHUNKS = 3
private const val CAMERA_YAW = 0f
private const val CAMERA_PITCH = 90f

private val SCREENSHOTS_BASE: Path by lazy { e2eScreenshotsBase(object {}.javaClass) }

private fun configureAerialCamera(client: Minecraft) {
  client.player?.let { player ->
    player.xRot = CAMERA_PITCH
    player.yRot = CAMERA_YAW
    player.abilities.mayfly = true
    player.abilities.flying = true
    player.onUpdateAbilities()
  }
}

private fun registerPresets() {
  PresetRegistry.presets[FORCED_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures { force(FORCED_ID) }
    }
  PresetRegistry.presets[DENSE_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures {
        force(FORCED_ID)
        // Density maxed but restricted to ruined portals: they can start broadly enough that this
        // proves the random pass still runs outside the forced ban radius without relying on the
        // forced site's biome. Without the restriction every set generates in every chunk, which
        // hangs world generation.
        allowNames(DENSE_FILLER_ID)
        spacing(1)
        separation(0)
        frequency(1f)
      }
    }
}

// Forces a plains village on the overworld root region and verifies, at STRUCTURE_STARTS
// resolution (no full generation needed), that:
//  1. the deterministic forced site's origin chunk holds a StructureStart for exactly that
//     structure, and
//  2. every other chunk touching the site's collision radius holds no structure start at all
//     (the collision ban suppressed the random pass).
// A second world maxes random density (ruined portals at spacing 1 / separation 0 / frequency 1,
// i.e. a placement attempt in every chunk) and re-checks the ban zone stays clean while the
// configured filler remains locatable outside it.
@Suppress("UnstableApiUsage")
object ForcedStructureGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    registerPresets()
    try {
      runScenario(context, FORCED_PRESET, expectDenseOutside = false, "forced_village")
      runScenario(context, DENSE_PRESET, expectDenseOutside = true, "forced_village_dense")
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(FORCED_PRESET)
      PresetRegistry.presets.remove(DENSE_PRESET)
    }
  }

  private fun runScenario(
    context: ClientGameTestContext,
    presetId: String,
    expectDenseOutside: Boolean,
    screenshotLabel: String,
  ) {
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
      var siteX = 0
      var siteZ = 0
      var siteRadius = 0f
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          val site = runAssertions(server, presetId, expectDenseOutside)
          siteX = site.blockX
          siteZ = site.blockZ
          siteRadius = site.radius
        }
      )
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          server.commands.performPrefixedCommand(
            server.createCommandSourceStack().withSuppressedOutput(),
            "time set noon",
          )
        }
      )
      // Three aerial shots along +X: the forced site itself, halfway to the ban border, and the
      // border itself, so the transition from forced structure to suppressed zone is visible.
      captureAerial(context, game, siteX, siteZ, "${screenshotLabel}_site")
      captureAerial(
        context,
        game,
        siteX + (siteRadius / 2).toInt(),
        siteZ,
        "${screenshotLabel}_midway",
      )
      captureAerial(context, game, siteX + siteRadius.toInt(), siteZ, "${screenshotLabel}_ban_edge")
    } finally {
      game.close()
    }
  }

  private fun captureAerial(
    context: ClientGameTestContext,
    game: TestSingleplayerContext,
    blockX: Int,
    blockZ: Int,
    label: String,
  ) {
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        server.playerList.players[0].teleportTo(blockX + 0.5, 100.0, blockZ + 0.5)
      }
    )
    context.waitTicks(10)
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
    val screenshotDir = SCREENSHOTS_BASE.resolve("ForcedStructureTest")
    context.takeScreenshot(TestScreenshotOptions.of(label).withDestinationDir(screenshotDir))
    log.info("screenshot -> {}/{}.png", screenshotDir, label)
  }

  private fun runAssertions(
    server: MinecraftServer,
    presetId: String,
    expectDenseOutside: Boolean,
  ): ForcedSite {
    val level = server.overworld()
    val ctx = DimensionContext.get("minecraft:overworld")
    assertNotNull(ctx, "[$presetId] DimensionContext must be active for the forced preset")
    val forced = ctx!!.forcedStructures
    assertNotNull(
      forced,
      "[$presetId] forced structure lookup must be compiled for the forced preset",
    )

    val sites = forced!!.sitesAt(ctx.traverser, ctx.cache, 0, 0)
    assertEquals(1, sites.size, "[$presetId] root region must plan exactly one forced site")
    val start = sites.single()
    val site = start.site
    log.info(
      "[{}] forced site for {} at block ({},{}) chunk ({},{}) radius={} budget={}",
      presetId,
      start.entry.id,
      site.blockX,
      site.blockZ,
      site.chunkX,
      site.chunkZ,
      site.radius,
      start.entry.budget,
    )

    val structureRegistry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
    val structure =
      structureRegistry
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
      "[$presetId] forced site chunk (${site.chunkX},${site.chunkZ}) must hold a " +
        "$FORCED_ID StructureStart",
    )
    assertTrue(forcedStart!!.isValid, "[$presetId] forced StructureStart must be valid")

    val extraStarts = mutableListOf<String>()
    val radiusSq = site.radius.toDouble() * site.radius.toDouble()
    val reachChunks = (site.radius.toInt() shr 4) + 1 + OUTSIDE_RING_CHUNKS
    log.info(
      "[{}] scanning {}x{} chunks around the forced site",
      presetId,
      2 * reachChunks + 1,
      2 * reachChunks + 1,
    )
    for (dz in -reachChunks..reachChunks) {
      for (dx in -reachChunks..reachChunks) {
        val chunkX = site.chunkX + dx
        val chunkZ = site.chunkZ + dz
        val nearestX = site.blockX.coerceIn(chunkX shl 4, (chunkX shl 4) + 15) - site.blockX
        val nearestZ = site.blockZ.coerceIn(chunkZ shl 4, (chunkZ shl 4) + 15) - site.blockZ
        val banned = nearestX.toDouble() * nearestX + nearestZ.toDouble() * nearestZ <= radiusSq
        if (!banned && !expectDenseOutside) continue
        val chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.STRUCTURE_STARTS, true)!!
        for ((other, otherStart) in chunk.getAllStarts()) {
          if (!otherStart.isValid) continue
          if (chunkX == site.chunkX && chunkZ == site.chunkZ && other === structure) continue
          if (banned) {
            extraStarts.add("${other.javaClass.simpleName}@($chunkX,$chunkZ)")
          }
        }
      }
    }
    assertTrue(
      extraStarts.isEmpty(),
      "[$presetId] collision ban must suppress all random structure starts around the forced " +
        "site, found: " +
        extraStarts.joinToString(),
    )
    if (expectDenseOutside) {
      val fillerHolders =
        structureRegistry
          .listElements()
          .filter { h ->
            h.unwrapKey().map { it.identifier().toString() == DENSE_FILLER_ID }.orElse(false)
          }
          .toList()
      assertTrue(fillerHolders.isNotEmpty(), "[$presetId] $DENSE_FILLER_ID must exist")
      val found =
        level.chunkSource.generator.findNearestMapStructure(
          level,
          HolderSet.direct(fillerHolders),
          BlockPos(site.blockX, 64, site.blockZ),
          reachChunks + OUTSIDE_RING_CHUNKS,
          true,
        )
      assertNotNull(
        found,
        "[$presetId] maxed density must locate a $DENSE_FILLER_ID outside the forced ban radius",
      )
      val foundPos = found!!.first
      val foundDx = foundPos.x.toLong() - site.blockX
      val foundDz = foundPos.z.toLong() - site.blockZ
      val foundDistanceSq = foundDx * foundDx + foundDz * foundDz
      log.info(
        "[{}] nearest dense filler {} at block ({},{}) distanceSq={} radiusSq={}",
        presetId,
        DENSE_FILLER_ID,
        foundPos.x,
        foundPos.z,
        foundDistanceSq,
        radiusSq,
      )
      assertTrue(
        foundDistanceSq > radiusSq,
        "[$presetId] nearest $DENSE_FILLER_ID must be outside the forced ban radius",
      )
    }
    log.info(
      "[{}] forced start confirmed at ({},{}), no random starts in ban radius",
      presetId,
      site.chunkX,
      site.chunkZ,
    )
    return site
  }
}
//?}