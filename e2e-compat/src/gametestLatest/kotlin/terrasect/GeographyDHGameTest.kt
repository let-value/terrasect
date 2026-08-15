package terrasect

import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.sqrt
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.presets.GEOGRAPHY
import terrasect.presets.Presets

private val log = LoggerFactory.getLogger("GeographyDHGameTest")

private const val SEED = "geography-dh"
private const val DH_RENDER_DISTANCE = 128
private const val WARMUP_TICKS = 400
private const val POLL_INTERVAL_TICKS = 200
private const val MAX_WAIT_TICKS = 18000
private const val WARMUP_GIVEUP_TICKS = 2000
private const val IDLE_STREAK_REQUIRED = 3

private val SCREENSHOTS_DIR: Path by lazy {
  val configured = System.getProperty("terrasect.e2eDir")?.takeIf { it.isNotBlank() }
  val base =
    if (configured != null) Path.of(configured)
    else
      Path.of(object {}.javaClass.protectionDomain.codeSource.location.toURI())
        .parent
        .parent
        .parent
        .parent
  base.resolve("build/gametest-screenshots/GeographyDH")
}

private fun configureDH() {
  try {
    val configClass =
      Class.forName("com.seibel.distanthorizons.core.api.external.methods.config.DhApiConfig")
    val instance = configClass.getField("INSTANCE").get(null)

    val graphics = instance.javaClass.getMethod("graphics").invoke(instance)
    val renderDist = graphics.javaClass.getMethod("chunkRenderDistance").invoke(graphics)
    renderDist.javaClass
      .getMethod("setValue", Object::class.java)
      .invoke(renderDist, DH_RENDER_DISTANCE)
    log.info("dh config: chunkRenderDistance set to {}", DH_RENDER_DISTANCE)

    val worldGen = instance.javaClass.getMethod("worldGenerator").invoke(instance)
    val enableGen = worldGen.javaClass.getMethod("enableDistantWorldGeneration").invoke(worldGen)
    enableGen.javaClass.getMethod("setValue", Object::class.java).invoke(enableGen, true)

    val modeEnum =
      Class.forName(
        "com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode"
      )
    val surface = modeEnum.getField("SURFACE").get(null)
    val genMode = worldGen.javaClass.getMethod("distantGeneratorMode").invoke(worldGen)
    genMode.javaClass.getMethod("setValue", Object::class.java).invoke(genMode, surface)
    log.info("dh config: world gen enabled, mode=SURFACE")

    val fog = graphics.javaClass.getMethod("fog").invoke(graphics)
    val fogConfig = fog.javaClass.getMethod("drawMode").invoke(fog)
    val fogEnum = Class.forName("com.seibel.distanthorizons.api.enums.rendering.EDhApiFogDrawMode")
    val fogDisabled = fogEnum.getField("FOG_DISABLED").get(null)
    fogConfig.javaClass.getMethod("setValue", Object::class.java).invoke(fogConfig, fogDisabled)
    log.info("dh config: fog disabled for clear screenshot")

    val genericRendering = graphics.javaClass.getMethod("genericRendering").invoke(graphics)
    val cloudEnabled =
      genericRendering.javaClass.getMethod("cloudRenderingEnabled").invoke(genericRendering)
    cloudEnabled.javaClass.getMethod("setValue", Object::class.java).invoke(cloudEnabled, false)
    log.info("dh config: cloud rendering disabled")

    val hqEnum =
      Class.forName("com.seibel.distanthorizons.api.enums.config.EDhApiHorizontalQuality")
    val hqLowest = hqEnum.getField("LOWEST").get(null)
    val hqConfig = graphics.javaClass.getMethod("horizontalQuality").invoke(graphics)
    hqConfig.javaClass.getMethod("setValue", Object::class.java).invoke(hqConfig, hqLowest)

    val vqEnum = Class.forName("com.seibel.distanthorizons.api.enums.config.EDhApiVerticalQuality")
    val vqHeightMap = vqEnum.getField("HEIGHT_MAP").get(null)
    val vqConfig = graphics.javaClass.getMethod("verticalQuality").invoke(graphics)
    vqConfig.javaClass.getMethod("setValue", Object::class.java).invoke(vqConfig, vqHeightMap)

    val resEnum =
      Class.forName("com.seibel.distanthorizons.api.enums.config.EDhApiMaxHorizontalResolution")
    val resHalfChunk = resEnum.getField("HALF_CHUNK").get(null)
    val resConfig = graphics.javaClass.getMethod("maxHorizontalResolution").invoke(graphics)
    resConfig.javaClass.getMethod("setValue", Object::class.java).invoke(resConfig, resHalfChunk)
    log.info("dh config: quality=LOWEST, vertical=HEIGHT_MAP, resolution=HALF_CHUNK")
  } catch (e: Exception) {
    log.warn("dh config: failed to configure Distant Horizons — {}", e.message)
  }
}

private fun getDHDebugStrings(): List<String> {
  return try {
    val sharedApi = Class.forName("com.seibel.distanthorizons.core.api.internal.SharedApi")
    val instance = sharedApi.getField("INSTANCE").get(null)
    @Suppress("UNCHECKED_CAST")
    instance.javaClass.getMethod("getDebugMenuString").invoke(instance) as? ArrayList<String>
      ?: emptyList()
  } catch (_: Exception) {
    emptyList()
  }
}

private fun parseDHActiveTaskCount(): Int {
  var active = 0
  for (line in getDHDebugStrings()) {
    val lower = line.lowercase()
    if (lower.contains("chunk update queues") || lower.contains("queued chunk updates")) continue
    if (lower.contains("world gen/import tasks")) {
      val numbers: List<Int> = Regex("\\d+").findAll(line).map { it.value.toInt() }.toList()
      if (numbers.size >= 2 && (numbers[0] > 0 || numbers[1] > 0)) active += 1
      else if (numbers.size == 1 && numbers[0] > 0) active += 1
    } else if (lower.contains("world gen queue")) {
      val numbers: List<Int> = Regex("\\d+").findAll(line).map { it.value.toInt() }.toList()
      if (numbers.isNotEmpty() && numbers[0] > 0) active += 1
    } else if (lower.contains("generating") || lower.contains("running")) {
      val numbers: List<Int> = Regex("\\d+").findAll(line).map { it.value.toInt() }.toList()
      if (numbers.any { it > 0 }) active += 1
    }
  }
  return active
}

@Suppress("UnstableApiUsage")
object GeographyDHGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.isFocused(this::class)) return

    val screenshotDir = SCREENSHOTS_DIR
    screenshotDir.toFile().mkdirs()

    configureDH()

    PresetRegistry.presets[Presets.GEOGRAPHY.id] = GEOGRAPHY
    PresetRegistry.forcePresetId = Presets.GEOGRAPHY.id
    log.info("geography-dh: creating world seed={}", SEED)

    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client ->
        client.options.renderDistance().set(16)
        client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF)
      }
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

      val root = GEOGRAPHY.buildTree("overworld")
      val continent = root.children.first { it.name == "continent" }
      val apothem = sqrt(continent.budget / (2.0 * sqrt(3.0)))
      log.info("geography-dh: hex apothem={}", apothem.toInt())

      var surfaceY = 64
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          surfaceY = server.overworld().getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0)
          log.info("geography-dh: surface at origin = {}", surfaceY)
        }
      )

      val cameraY = (surfaceY + 1200).toDouble()
      val targetX = 0.5
      val targetZ = 0.5

      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          val player = server.playerList.players[0]
          player.teleportTo(targetX, cameraY, targetZ)
          player.xRot = 50f
          player.yRot = -25f
        }
      )

      context.waitFor { client ->
        val player = client.player ?: return@waitFor false
        abs(player.x - targetX) < 1.0 && abs(player.z - targetZ) < 1.0
      }
      context.waitTicks(5)

      context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client ->
          client.player?.let { player ->
            player.xRot = 50f
            player.yRot = -25f
            player.abilities.mayfly = true
            player.abilities.flying = true
            player.onUpdateAbilities()
          }
          if (!client.gui.hud.isHidden) client.gui.hud.toggle()
        }
      )

      log.info("geography-dh: camera at y={}, warming up DH...", cameraY.toInt())
      context.waitTicks(WARMUP_TICKS)

      log.info("geography-dh: polling DH generation progress...")
      var elapsed = 0
      var idleStreak = 0
      var sawActivity = false
      while (elapsed < MAX_WAIT_TICKS) {
        context.waitTicks(POLL_INTERVAL_TICKS)
        elapsed += POLL_INTERVAL_TICKS

        val debug = getDHDebugStrings()
        val activeTasks = parseDHActiveTaskCount()

        if (activeTasks > 0) {
          sawActivity = true
          idleStreak = 0
        } else {
          idleStreak++
        }

        log.info(
          "geography-dh: {}s elapsed, active={}, sawActivity={}, idle streak={}, debug={}",
          elapsed / 20,
          activeTasks,
          sawActivity,
          idleStreak,
          debug.take(5),
        )

        if (sawActivity && idleStreak >= IDLE_STREAK_REQUIRED) {
          log.info(
            "geography-dh: DH generation idle for {} consecutive polls after activity, proceeding",
            idleStreak,
          )
          break
        }

        if (!sawActivity && elapsed >= WARMUP_GIVEUP_TICKS) {
          log.warn("geography-dh: DH never became active, proceeding after {}s", elapsed / 20)
          break
        }
      }

      if (elapsed >= MAX_WAIT_TICKS) {
        log.warn(
          "geography-dh: max wait {}s reached, taking screenshot anyway",
          MAX_WAIT_TICKS / 20,
        )
      }

      log.info("geography-dh: DH generation settled; letting LODs render...")
      context.waitTicks(400)
      context.waitTicks(200)

      context.takeScreenshot(
        TestScreenshotOptions.of("geography_dh_aerial").withDestinationDir(screenshotDir)
      )
      log.info("geography-dh: screenshot captured")
    } finally {
      game.close()
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(Presets.GEOGRAPHY.id)
    }
  }
}
