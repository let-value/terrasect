package terrasect

import java.nio.file.Path
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.AABB
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.instrumentation.InMemoryBackend
import terrasect.instrumentation.Instr
import terrasect.instrumentation.MetricsBackend
import terrasect.instrumentation.MetricsConfig
import terrasect.instrumentation.TerrasectInstrScope
import terrasect.instrumentation.TerrasectMetricEvent

private val log = LoggerFactory.getLogger("MobConstraintGameTest")

private const val SEED = "mob-constraints"
private const val DISABLED_PRESET = "__disabled__"
private const val BLOCK_ZOMBIE_PRESET = "mob_constraint_block_zombie"
private const val SPAWN_WAIT_TICKS = 300

private val MOB_COUNT_AABB = AABB(-128.0, -64.0, -128.0, 128.0, 320.0, 128.0)

private val SCREENSHOTS_BASE: Path by lazy {
  e2eScreenshotsBase(object {}.javaClass)
}

private fun registerBlockZombiePreset() {
  PresetRegistry.presets[BLOCK_ZOMBIE_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").mobs { blockNames("minecraft:zombie") }
    }
}

private fun configureAerialMobCamera(client: Minecraft) {
  client.player?.let { player ->
    player.xRot = 65f
    player.yRot = 0f
    player.abilities.mayfly = true
    player.abilities.flying = true
    player.onUpdateAbilities()
  }
}

private fun enableMobInstrumentation(): MetricsBackend {
  MetricsConfig.enabled = true
  MetricsConfig.countersEnabled = true
  MetricsConfig.clearScopeOverrides()
  for (scope in TerrasectInstrScope.entries) {
    if (scope != TerrasectInstrScope.MOB) MetricsConfig.setScopeEnabled(scope, false)
  }
  for (event in TerrasectMetricEvent.entries) {
    if (event != TerrasectMetricEvent.MOB_APPLIED)
      MetricsConfig.setEventCountersEnabled(event, false)
  }
  val previous = Instr.getBackend()
  Instr.setBackend(InMemoryBackend())
  return previous
}

private fun restoreMobInstrumentation(previous: MetricsBackend) {
  MetricsConfig.enabled = false
  MetricsConfig.countersEnabled = false
  MetricsConfig.clearScopeOverrides()
  Instr.setBackend(previous)
}

@Suppress("UnstableApiUsage")
private fun runMobSpawnProbe(
  context: ClientGameTestContext,
  presetId: String?,
  scenarioName: String,
  screenshotLabel: String?,
  screenshotDir: Path?,
): Map<String, Int> {
  val originalPresetId = PresetRegistry.forcePresetId
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
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        server.playerList.players[0].teleportTo(8.0, 120.0, 8.0)
      }
    )
    context.waitTicks(10)

    // Midnight so sky-light drops to zero and hostile mobs can spawn on the surface
    game.server.runCommand("time set night")

    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client -> configureAerialMobCamera(client) }
    )
    context.waitTicks(10)
    game.clientLevel.waitForChunksRender()

    // Natural-spawning window: server ticks loaded chunks, NaturalSpawner runs each tick
    context.waitTicks(SPAWN_WAIT_TICKS)

    if (screenshotLabel != null && screenshotDir != null) {
      context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client -> configureAerialMobCamera(client) }
      )
      context.waitTicks(5)
      context.takeScreenshot(
        TestScreenshotOptions.of(screenshotLabel).withDestinationDir(screenshotDir)
      )
      log.info("[{}] screenshot -> {}/{}.png", scenarioName, screenshotDir, screenshotLabel)
    }

    val entityCounts = LinkedHashMap<String, Int>()
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
        val mobs = level.getEntitiesOfClass(Mob::class.java, MOB_COUNT_AABB) { true }
        for (mob in mobs) {
          val id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.type).toString()
          entityCounts[id] = (entityCounts[id] ?: 0) + 1
        }
        val top5 =
          entityCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString { "${it.key.substringAfterLast(':')}×${it.value}" }
        log.info(
          "[{}] preset={} mobs: total={} types=[{}]",
          scenarioName,
          presetId,
          mobs.size,
          top5.ifEmpty { "none" },
        )
      }
    )

    return entityCounts
  } finally {
    game.close()
    PresetRegistry.forcePresetId = originalPresetId
  }
}

private fun mobAppliedCount(): Long =
  Instr.counterSnapshot()
    .filter {
      it.id.scope == TerrasectInstrScope.MOB.id &&
        it.id.event == TerrasectMetricEvent.MOB_APPLIED.id
    }
    .sumOf { it.value }

// Vanilla baseline vs. blockNames("minecraft:zombie"): proves per-name blocking on the real
// natural-spawn path. Only zombies are suppressed; other mob types remain unaffected.
// Evidence: MOB_APPLIED > 0 (filter ran) + constrained zombie count = 0.
@Suppress("UnstableApiUsage")
object MobConstraintBlockByNameGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val screenshotDir = SCREENSHOTS_BASE.resolve("MobConstraintBlockByNameTest")
    val previousBackend = enableMobInstrumentation()

    try {
      val vanillaCounts =
        runMobSpawnProbe(
          context,
          DISABLED_PRESET,
          "vanilla",
          "vanilla_mobs",
          screenshotDir.resolve("vanilla"),
        )

      Instr.reset()
      registerBlockZombiePreset()
      val constrainedCounts =
        runMobSpawnProbe(
          context,
          BLOCK_ZOMBIE_PRESET,
          "block_zombie",
          "blocked_zombie",
          screenshotDir.resolve("blocked"),
        )

      val appliedCount = mobAppliedCount()
      val vanillaZombies = vanillaCounts["minecraft:zombie"] ?: 0
      val constrainedZombies = constrainedCounts["minecraft:zombie"] ?: 0

      log.info(
        "[block_by_name] summary: vanilla_zombie={} constrained_zombie={} MOB_APPLIED={}",
        vanillaZombies,
        constrainedZombies,
        appliedCount,
      )

      assertTrue(
        appliedCount > 0,
        "[block_by_name] MOB_APPLIED counter must be > 0 — the runtime natural-spawn filter path " +
          "was exercised in the constrained world (got $appliedCount after $SPAWN_WAIT_TICKS ticks). " +
          "Vanilla observed $vanillaZombies zombie(s) / all mobs: $vanillaCounts.",
      )
      assertEquals(
        0,
        constrainedZombies,
        "[block_by_name] blockNames(minecraft:zombie) must produce zero naturally-spawned zombie " +
          "entities; found $constrainedZombies. Other mobs in constrained world: $constrainedCounts.",
      )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(BLOCK_ZOMBIE_PRESET)
      restoreMobInstrumentation(previousBackend)
    }
  }
}
