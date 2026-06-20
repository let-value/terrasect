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
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.Mob

import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry

private val log = LoggerFactory.getLogger("MobSpawnConstraintGameTest")

private const val SEED = "mob-spawn-constraints"
private const val DISABLED_PRESET = "__disabled__"
private const val BLOCKED_ZOMBIE_PRESET = "mob_spawn_constraint_blocked_zombie"
private const val ROOM_MIN_X = -8
private const val ROOM_MAX_X = 8
private const val ROOM_MIN_Y = 96
private const val ROOM_MAX_Y = 102
private const val ROOM_MIN_Z = -8
private const val ROOM_MAX_Z = 8
private const val PLAYER_Y_OFFSET = 40.0
private const val SPAWN_SETTLE_TICKS = 400

private val SCREENSHOTS_BASE: Path by lazy {
  val classesRoot = Path.of(object {}.javaClass.protectionDomain.codeSource.location.toURI())
  classesRoot.parent.parent.parent.parent.resolve("build/gametest-screenshots")
}

private data class MobProbeResult(val zombieCount: Int, val mobCount: Int)

private fun roomBounds() =
  AABB(
    ROOM_MIN_X + 0.5,
    ROOM_MIN_Y + 1.0,
    ROOM_MIN_Z + 0.5,
    ROOM_MAX_X - 0.5,
    ROOM_MAX_Y - 0.5,
    ROOM_MAX_Z - 0.5,
  )

private fun registerBlockedZombiePreset() {
  PresetRegistry.presets[BLOCKED_ZOMBIE_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").mobs { blockNames("minecraft:zombie") }
    }
}

private fun configureAerialCamera(client: Minecraft) {
  client.player?.let { player ->
    player.xRot = 82f
    player.yRot = 0f
    player.abilities.mayfly = true
    player.abilities.flying = true
    player.onUpdateAbilities()
  }
}

private fun buildSpawnChamber(server: MinecraftServer) {
  val level = server.overworld()
  for (x in ROOM_MIN_X..ROOM_MAX_X) {
    for (y in ROOM_MIN_Y..ROOM_MAX_Y) {
      for (z in ROOM_MIN_Z..ROOM_MAX_Z) {
        val shell =
          x == ROOM_MIN_X ||
            x == ROOM_MAX_X ||
            y == ROOM_MIN_Y ||
            y == ROOM_MAX_Y ||
            z == ROOM_MIN_Z ||
            z == ROOM_MAX_Z
        level.setBlockAndUpdate(
          BlockPos(x, y, z),
          if (shell) Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState(),
        )
      }
    }
  }
}

private fun teleportPlayerAboveChamber(server: MinecraftServer) {
  server.playerList.players[0].teleportTo(0.5, ROOM_MAX_Y + PLAYER_Y_OFFSET, 0.5)
}

private fun countProbe(level: MinecraftServer): MobProbeResult {
  val chamber = roomBounds()
  val world = level.overworld()
  var zombieCount = 0
  val mobs = world.getEntitiesOfClass(Mob::class.java, chamber)
  for (mob in mobs) {
    if (BuiltInRegistries.ENTITY_TYPE.getKey(mob.type).toString() == "minecraft:zombie") {
      zombieCount++
    }
  }
  return MobProbeResult(zombieCount = zombieCount, mobCount = mobs.size)
}

private fun runMobScenario(
  context: ClientGameTestContext,
  presetId: String?,
  scenarioName: String,
  screenshotLabel: String,
): MobProbeResult {
  val originalPresetId = PresetRegistry.forcePresetId
  PresetRegistry.forcePresetId = presetId
  log.info("[{}] START preset={}", scenarioName, presetId)
  val game =
    context
      .worldBuilder()
      .setUseConsistentSettings(false)
      .adjustSettings { settings ->
        settings.seed = SEED
        settings.gameMode = WorldCreationUiState.SelectedGameMode.CREATIVE
        settings.difficulty = Difficulty.HARD
      }
      .create()

  try {
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        buildSpawnChamber(server)
        teleportPlayerAboveChamber(server)
      }
    )
    context.waitTicks(20)
    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
    )
    context.waitTicks(10)
    game.clientWorld.waitForChunksRender()
    context.waitTicks(SPAWN_SETTLE_TICKS)

    var result = MobProbeResult(0, 0)
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        result = countProbe(server)
        log.info(
          "[{}] zombieCount={} mobCount={} bounds={}",
          scenarioName,
          result.zombieCount,
          result.mobCount,
          roomBounds(),
        )
      }
    )

    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
    )
    context.waitTicks(5)
    context.takeScreenshot(
      TestScreenshotOptions.of(screenshotLabel).withDestinationDir(SCREENSHOTS_BASE.resolve("MobSpawnConstraintGameTest"))
    )
    return result
  } finally {
    game.close()
    PresetRegistry.forcePresetId = originalPresetId
  }
}

@Suppress("UnstableApiUsage")
object MobSpawnConstraintGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    registerBlockedZombiePreset()

    val vanilla =
      runMobScenario(
        context,
        DISABLED_PRESET,
        scenarioName = "vanilla",
        screenshotLabel = "vanilla",
      )
    val blocked =
      runMobScenario(
        context,
        BLOCKED_ZOMBIE_PRESET,
        scenarioName = "blocked_zombie",
        screenshotLabel = "blocked_zombie",
      )

    assertTrue(
      vanilla.zombieCount > 0,
      "Vanilla baseline should spawn at least one zombie in the chamber",
    )
    assertEquals(
      0,
      blocked.zombieCount,
      "Blocked scenario should suppress zombies in the chamber",
    )
    assertTrue(
      vanilla.zombieCount > blocked.zombieCount,
      "Constrained world should spawn fewer zombies than vanilla",
    )
  }
}
