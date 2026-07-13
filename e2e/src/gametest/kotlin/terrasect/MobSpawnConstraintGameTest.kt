package terrasect

import java.nio.file.Path
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.Mob
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
private const val SPAWN_SETTLE_TICKS = 300

private val MOB_COUNT_AABB = AABB(-128.0, -64.0, -128.0, 128.0, 320.0, 128.0)

private val SCREENSHOTS_BASE: Path by lazy {
  e2eScreenshotsBase(object {}.javaClass)
}

private data class MobProbeResult(val zombieCount: Int, val mobCount: Int)

private fun registerBlockedZombiePreset() {
  PresetRegistry.presets[BLOCKED_ZOMBIE_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").mobs { blockNames("minecraft:zombie") }
    }
}

private fun configureAerialCamera(client: Minecraft) {
  client.player?.let { player ->
    player.xRot = 65f
    player.yRot = 0f
    player.abilities.mayfly = true
    player.abilities.flying = true
    player.onUpdateAbilities()
  }
}

@Suppress("UnstableApiUsage")
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
        server.playerList.players[0].teleportTo(8.0, 120.0, 8.0)
      }
    )
    context.waitTicks(10)
    game.server.runCommand("time set midnight")
    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
    )
    context.waitTicks(10)
    game.clientLevel.waitForChunksRender()
    context.waitTicks(SPAWN_SETTLE_TICKS)

    var result = MobProbeResult(0, 0)
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
        val mobs = level.getEntitiesOfClass(Mob::class.java, MOB_COUNT_AABB) { true }
        var zombieCount = 0
        for (mob in mobs) {
          if (BuiltInRegistries.ENTITY_TYPE.getKey(mob.type).toString() == "minecraft:zombie") {
            zombieCount++
          }
        }
        result = MobProbeResult(zombieCount = zombieCount, mobCount = mobs.size)
        val top5 =
          mobs
            .groupingBy { BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString { "${it.key.substringAfterLast(':')}×${it.value}" }
        log.info(
          "[{}] zombieCount={} mobCount={} types=[{}]",
          scenarioName,
          zombieCount,
          mobs.size,
          top5.ifEmpty { "none" },
        )
      }
    )

    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
    )
    context.waitTicks(5)
    context.takeScreenshot(
      TestScreenshotOptions.of(screenshotLabel)
        .withDestinationDir(SCREENSHOTS_BASE.resolve("MobSpawnConstraintGameTest"))
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

    PresetRegistry.presets.remove(BLOCKED_ZOMBIE_PRESET)

    assertTrue(
      vanilla.zombieCount > 0,
      "Vanilla baseline should spawn at least one zombie (got ${vanilla.zombieCount} zombies, " +
        "${vanilla.mobCount} total mobs) — check seed '$SEED' and difficulty/time settings.",
    )
    assertEquals(
      0,
      blocked.zombieCount,
      "blockNames(minecraft:zombie) must suppress all naturally-spawned zombies; " +
        "found ${blocked.zombieCount} in the constrained world (${blocked.mobCount} total mobs).",
    )
    assertTrue(
      vanilla.zombieCount > blocked.zombieCount,
      "Constrained world should spawn fewer zombies than vanilla " +
        "(vanilla=${vanilla.zombieCount}, blocked=${blocked.zombieCount})",
    )
  }
}
