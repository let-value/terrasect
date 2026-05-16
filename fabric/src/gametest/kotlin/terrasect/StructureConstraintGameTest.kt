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
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry

private val LOGGER = LoggerFactory.getLogger("StructureConstraintGameTest")

private const val SEED = "structure-constraints"
private const val DISABLED_PRESET = "__disabled__"
private const val HIGH_DENSITY_PRESET = "structure_constraint_high_density"

// Probe positions spaced ~512 blocks apart to sample different vanilla structure grid cells.
// Village spacing is 34 chunks = 544 blocks, so these probes hit distinct grid cells.
private val PROBE_POSITIONS = listOf(0 to 0, 512 to 0, 0 to 512, 512 to 512)

private val SCREENSHOTS_BASE: Path by lazy {
  val classesRoot = Path.of(object {}.javaClass.protectionDomain.codeSource.location.toURI())
  classesRoot.parent.parent.parent.parent.resolve("build/gametest-screenshots")
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
