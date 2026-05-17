package terrasect

import java.nio.file.Path
import kotlin.math.sqrt
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry

private val LOGGER = LoggerFactory.getLogger("StructureConstraintGameTest")

private const val SEED = "structure-constraints"
private const val DISABLED_PRESET = "__disabled__"
private const val HIGH_DENSITY_PRESET = "structure_constraint_high_density"
private const val LOCATE_BLOCKED_PRESET = "structure_constraint_locate_blocked"
private const val RUINED_PORTAL_DISTANCE = 385

// Probe positions spaced ~512 blocks apart to sample different vanilla structure grid cells.
// Village spacing is 34 chunks = 544 blocks, so these probes hit distinct grid cells.
private val PROBE_POSITIONS = listOf(0 to 0, 512 to 0, 0 to 512, 512 to 512)

private val SCREENSHOTS_BASE: Path by lazy {
  val classesRoot = Path.of(object {}.javaClass.protectionDomain.codeSource.location.toURI())
  classesRoot.parent.parent.parent.parent.resolve("build/gametest-screenshots")
}

private data class LocateResult(val structureId: String?, val pos: BlockPos?) {
  val distance: Int
    get() {
      if (pos == null) return -1
      val dx = pos.x.toLong()
      val dz = pos.z.toLong()
      return sqrt((dx * dx + dz * dz).toDouble()).toInt()
    }
}

private fun registerHighDensityPreset() {
  PresetRegistry.presets[HIGH_DENSITY_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures {
        // spacing=8 chunks (128 blocks), separation=4 chunks — ~18x denser than vanilla villages.
        // frequency=1.0 ensures every eligible grid cell actually places a structure.
        spacing(8)
        separation(4)
        frequency(1.0f)
      }
    }
}

private fun registerLocateBlockedPreset() {
  PresetRegistry.presets[LOCATE_BLOCKED_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures { blockMods("minecraft") }
    }
}

private fun configureAerialCamera(client: Minecraft) {
  client.player?.let { player ->
    player.xRot = 60f
    player.yRot = 0f
    player.abilities.mayfly = true
    player.abilities.flying = true
    player.onUpdateAbilities()
  }
}

@Suppress("UnstableApiUsage")
private fun runStructureProbes(
  context: ClientGameTestContext,
  label: String,
  screenshotDir: Path,
) {
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
      FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
    )
    context.waitTicks(5)
    game.clientWorld.waitForChunksRender()

    for ((x, z) in PROBE_POSITIONS) {
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          server.playerList.players[0].teleportTo(x + 8.0, 100.0, z + 8.0)
        }
      )
      context.waitTicks(60)
      game.clientWorld.waitForChunksDownload()
      game.clientWorld.waitForChunksRender()

      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          val level = server.overworld()
          val blockCounts = LinkedHashMap<String, Int>()
          val surfaces = mutableListOf<Int>()
          for (bx in x until x + 16) {
            for (bz in z until z + 16) {
              val s = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz)
              surfaces.add(s)
              val block = level.getBlockState(BlockPos(bx, s - 1, bz)).block
              val name = BuiltInRegistries.BLOCK.getKey(block).toString()
              blockCounts[name] = (blockCounts[name] ?: 0) + 1
            }
          }
          val top3 =
            blockCounts.entries
              .sortedByDescending { it.value }
              .take(3)
              .joinToString { "${it.key.substringAfterLast(':')}×${it.value}" }
          LOGGER.info(
            "[StructureConstraint][{}] probe ({},{}) surface={}-{} blocks=[{}]",
            label,
            x,
            z,
            surfaces.min(),
            surfaces.max(),
            top3,
          )
        }
      )

      context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
      )
      context.waitTicks(5)
      context.takeScreenshot(
        TestScreenshotOptions.of("probe_${x}_${z}").withDestinationDir(screenshotDir)
      )
    }

    LOGGER.info("[StructureConstraint][{}] done — screenshots in {}", label, screenshotDir)
  } finally {
    game.close()
  }
}

@Suppress("UnstableApiUsage")
private fun runLocateTest(
  context: ClientGameTestContext,
  label: String,
  screenshotDir: Path? = null,
  structureFilter: (String) -> Boolean = { "village" in it },
  screenshotName: String = "located_village",
): LocateResult {
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
    var result: LocateResult? = null
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
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
          LOGGER.warn(
            "[StructureConstraint][locate][{}] no matching structure holders in registry",
            label,
          )
          result = LocateResult(null, null)
          return@FailableConsumer
        }
        val holderSet = HolderSet.direct(holders)
        val found =
          generator.findNearestMapStructure(level, holderSet, BlockPos(0, 64, 0), 100, true)
        if (found != null) {
          val id = found.second.unwrapKey().map { it.identifier().toString() }.orElse("unknown")
          val pos = found.first
          LOGGER.info(
            "[StructureConstraint][locate][{}] found type={} at ({},{}) distance={}",
            label,
            id,
            pos.x,
            pos.z,
            sqrt((pos.x.toLong().let { it * it } + pos.z.toLong().let { it * it }).toDouble())
              .toInt(),
          )
          result = LocateResult(id, pos)
        } else {
          LOGGER.info("[StructureConstraint][locate][{}] not found within radius=100", label)
          result = LocateResult(null, null)
        }
      }
    )
    val locateResult = result ?: LocateResult(null, null)
    if (screenshotDir != null && locateResult.pos != null) {
      val pos = locateResult.pos
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          server.playerList.players[0].teleportTo(pos.x + 0.5, 100.0, pos.z + 0.5)
        }
      )
      context.waitTicks(10)
      context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client ->
          client.player?.let { player ->
            player.xRot = 90f
            player.yRot = 0f
            player.abilities.mayfly = true
            player.abilities.flying = true
            player.onUpdateAbilities()
          }
        }
      )
      context.waitTicks(5)
      game.clientWorld.waitForChunksDownload()
      game.clientWorld.waitForChunksRender()
      context.takeScreenshot(
        TestScreenshotOptions.of(screenshotName).withDestinationDir(screenshotDir)
      )
      LOGGER.info("[StructureConstraint][locate][{}] screenshot saved to {}", label, screenshotDir)
    }
    return locateResult
  } finally {
    game.close()
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintVanillaGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    PresetRegistry.forcePresetId = DISABLED_PRESET
    try {
      runStructureProbes(
        context,
        "vanilla",
        SCREENSHOTS_BASE.resolve("StructureConstraintVanillaTest"),
      )
    } finally {
      PresetRegistry.forcePresetId = null
    }
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintHighDensityGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    registerHighDensityPreset()
    PresetRegistry.forcePresetId = HIGH_DENSITY_PRESET
    try {
      runStructureProbes(
        context,
        "high_density",
        SCREENSHOTS_BASE.resolve("StructureConstraintHighDensityTest"),
      )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(HIGH_DENSITY_PRESET)
    }
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintLocateGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val locateScreenshotsBase = SCREENSHOTS_BASE.resolve("StructureConstraintLocateTest")

    // Vanilla baseline: locate finds nearest village with no Terrasect constraints active.
    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanillaResult: LocateResult
    try {
      vanillaResult =
        runLocateTest(context, "vanilla", locateScreenshotsBase.resolve("vanilla"))
    } finally {
      PresetRegistry.forcePresetId = null
    }

    // High-density: tighter grid (spacing=8, separation=4) produces a closer village than vanilla
    // for this seed — asserted below as highDensityResult.distance < vanillaResult.distance.
    registerHighDensityPreset()
    PresetRegistry.forcePresetId = HIGH_DENSITY_PRESET
    val highDensityResult: LocateResult
    try {
      highDensityResult =
        runLocateTest(context, "high_density", locateScreenshotsBase.resolve("high_density"))
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(HIGH_DENSITY_PRESET)
    }

    // Selection-blocked: all minecraft structures denied by region selection constraints.
    // findNearestMapStructure must return null, proving the locate mixin selection filter works.
    // No screenshot: the result is null by design.
    registerLocateBlockedPreset()
    PresetRegistry.forcePresetId = LOCATE_BLOCKED_PRESET
    val blockedResult: LocateResult
    try {
      blockedResult = runLocateTest(context, "blocked")
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(LOCATE_BLOCKED_PRESET)
    }

    LOGGER.info(
      "[StructureConstraint][locate] summary: vanilla={} dist={} | high_density={} dist={} | blocked={}",
      vanillaResult.structureId,
      vanillaResult.distance,
      highDensityResult.structureId,
      highDensityResult.distance,
      if (blockedResult.pos == null) "null (correct)" else "FOUND (unexpected: ${blockedResult.structureId})",
    )

    assertNotNull(
      vanillaResult.pos,
      "[locate] vanilla should find a village within radius=100 but got null — " +
        "check that village structures exist in the test world registry.",
    )
    assertNotNull(
      highDensityResult.pos,
      "[locate] high-density should find a village within radius=100 but got null — " +
        "placement-only preset must not suppress locate results (no selection constraint set).",
    )
    assertTrue(
      vanillaResult.structureId?.startsWith("minecraft:village_") == true,
      "[locate] vanilla should return a village-type structure id, got ${vanillaResult.structureId}",
    )
    assertTrue(
      highDensityResult.structureId?.startsWith("minecraft:village_") == true,
      "[locate] high-density should return a village-type structure id, got ${highDensityResult.structureId}",
    )
    assertEquals(
      vanillaResult.structureId,
      highDensityResult.structureId,
      "[locate] vanilla and high-density should locate the same closest village type for the fixed seed",
    )
    assertTrue(
      highDensityResult.distance < vanillaResult.distance,
      "[locate] high-density preset should find a closer village than vanilla " +
        "(vanilla dist=${vanillaResult.distance}, high_density dist=${highDensityResult.distance})",
    )
    assertTrue(
      vanillaResult.distance >= 0,
      "[locate] vanilla distance should be non-negative but was ${vanillaResult.distance}",
    )
    assertNull(
      blockedResult.pos,
      "[locate] selection-blocked preset (blockMods=minecraft) must not find any village " +
        "within radius=100, but found ${blockedResult.structureId} at ${blockedResult.pos} — " +
        "the locate mixin selection filter did not apply.",
    )
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintLocateRuinedPortalGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val screenshotDir = SCREENSHOTS_BASE.resolve("StructureConstraintLocateRuinedPortalTest")

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val result: LocateResult
    try {
      result =
        runLocateTest(
          context,
          "ruined_portal",
          screenshotDir,
          structureFilter = { "ruined_portal" in it },
          screenshotName = "located_ruined_portal",
        )
    } finally {
      PresetRegistry.forcePresetId = null
    }

    LOGGER.info(
      "[StructureConstraint][locate][ruined_portal] id={} dist={}",
      result.structureId,
      result.distance,
    )

    assertNotNull(
      result.pos,
      "[locate][ruined_portal] expected a ruined portal within radius=100 but got null",
    )
    assertTrue(
      result.structureId?.contains("ruined_portal") == true,
      "[locate][ruined_portal] expected id containing ruined_portal, got ${result.structureId}",
    )
    assertEquals(
      RUINED_PORTAL_DISTANCE,
      result.distance,
      "[locate][ruined_portal] horizontal distance from origin mismatch for fixed seed",
    )
  }
}
