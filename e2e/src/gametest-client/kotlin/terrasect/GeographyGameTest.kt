//? if latest {
package terrasect

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.generation.DimensionContext
import terrasect.generation.Traverser
import terrasect.presets.GEOGRAPHY
import terrasect.presets.GEOGRAPHY_REGIONS
import terrasect.presets.Presets

private const val CHUNK_TIMEOUT = 6000

private val log = LoggerFactory.getLogger("GeographyGameTest")

private const val SEED = "geography"

private val SCREENSHOTS_BASE: Path by lazy { e2eScreenshotsBase(object {}.javaClass) }

private val REGION_COLORS: Map<String, Int> by lazy {
  val golden = 0.618033988749895
  GEOGRAPHY_REGIONS.mapIndexed { i, name ->
    val hue = (i * golden) % 1.0
    val sat = 0.65f + (i % 3) * 0.1f
    val bri = 0.75f + (i % 2) * 0.15f
    name to (Color.getHSBColor(hue.toFloat(), sat, bri).rgb and 0xFFFFFF)
  }.toMap()
}

private fun generateRegionMap(screenshotDir: Path) {
  val root = GEOGRAPHY.buildTree("overworld")
  val traverser = Traverser(seed = SEED.hashCode().toLong(), root = root)

  val continent = root.children.first { it.name == "continent" }
  val apothem = sqrt(continent.budget / (2.0 * sqrt(3.0)))
  val range = (apothem * 3).toInt()
  val step = 4
  val size = range * 2 / step

  val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)

  for (py in 0 until size) {
    val worldZ = -range + py * step
    for (px in 0 until size) {
      val worldX = -range + px * step
      val result = traverser.traverse(worldX, worldZ)
      val color = REGION_COLORS[result.region.name] ?: 0x808080
      image.setRGB(px, py, color)
    }
  }

  val outFile = screenshotDir.resolve("geography_map.png").toFile()
  ImageIO.write(image, "PNG", outFile)
  log.info("region map written to {} ({}x{})", outFile, size, size)
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

private fun visitRegion(
  context: ClientGameTestContext,
  game: TestSingleplayerContext,
  regionName: String,
  screenshotDir: Path,
) {
  var centerX = 0
  var centerZ = 0
  game.server.runOnServer(
    FailableConsumer<MinecraftServer, Exception> { _ ->
      val ctx = DimensionContext.get("minecraft:overworld")
      assertNotNull(ctx, "DimensionContext must be active")
      val located = ctx!!.locator.query(".$regionName")
      assertNotNull(located, "must locate .$regionName")
      centerX = located!!.centerX
      centerZ = located.centerZ
      log.info("located .{} at ({}, {})", regionName, centerX, centerZ)
    }
  )

  val targetX = centerX + 0.5
  val targetZ = centerZ + 0.5

  game.server.runOnServer(
    FailableConsumer<MinecraftServer, Exception> { server ->
      server.playerList.players[0].teleportTo(targetX, 320.0, targetZ)
    }
  )

  context.waitFor { client ->
    val player = client.player ?: return@waitFor false
    abs(player.x - targetX) < 1.0 && abs(player.z - targetZ) < 1.0
  }
  context.waitTicks(5)

  game.clientLevel.waitForChunksDownload(CHUNK_TIMEOUT)

  game.server.runOnServer(
    FailableConsumer<MinecraftServer, Exception> { server ->
      val level = server.overworld()
      val surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ)
      val cameraY = (surfaceY + 40).toDouble()
      log.info(".{}: surface={}, camera={}", regionName, surfaceY, cameraY.toInt())
      val player = server.playerList.players[0]
      player.teleportTo(targetX, cameraY, targetZ)
      player.xRot = 90f
      player.yRot = 0f
    }
  )
  context.waitTicks(5)

  context.runOnClient(
    FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
  )

  game.clientLevel.waitForChunksRender(CHUNK_TIMEOUT)
  context.waitTicks(10)
  game.clientLevel.waitForChunksRender(false, CHUNK_TIMEOUT)

  context.takeScreenshot(
    TestScreenshotOptions.of(regionName).withDestinationDir(screenshotDir)
  )
  log.info("screenshot taken for .{}", regionName)
}

@Suppress("UnstableApiUsage")
object GeographyGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val screenshotDir = SCREENSHOTS_BASE.resolve("GeographyTest")
    screenshotDir.toFile().mkdirs()

    generateRegionMap(screenshotDir)

    PresetRegistry.presets[Presets.GEOGRAPHY.id] = GEOGRAPHY
    PresetRegistry.forcePresetId = Presets.GEOGRAPHY.id
    log.info("geography: creating world seed={}", SEED)

    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client -> client.options.renderDistance().set(6) }
    )

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
      context.waitTicks(40)

      for (regionName in GEOGRAPHY_REGIONS) {
        visitRegion(context, game, regionName, screenshotDir)
      }

      log.info("geography: all {} regions visited and screenshotted", GEOGRAPHY_REGIONS.size)
    } finally {
      game.close()
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(Presets.GEOGRAPHY.id)
    }
  }
}
//?}
