package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
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

private val log = LoggerFactory.getLogger("LootConstraintGameTest")

private const val SEED = "loot-constraints"
private const val DISABLED_PRESET = "__disabled__"
private const val BLOCK_ALL_PRESET = "loot_constraint_block_all"
private const val ROLL_COUNT = 200

private fun registerBlockAllPreset() {
  PresetRegistry.presets[BLOCK_ALL_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").loot { blockMods("minecraft") }
    }
}

private fun enableLootInstrumentation(): MetricsBackend {
  MetricsConfig.enabled = true
  MetricsConfig.countersEnabled = true
  MetricsConfig.clearScopeOverrides()
  for (scope in TerrasectInstrScope.entries) {
    if (scope != TerrasectInstrScope.LOOT) MetricsConfig.setScopeEnabled(scope, false)
  }
  for (event in TerrasectMetricEvent.entries) {
    if (event != TerrasectMetricEvent.LOOT_APPLIED)
      MetricsConfig.setEventCountersEnabled(event, false)
  }
  val previous = Instr.getBackend()
  Instr.setBackend(InMemoryBackend())
  return previous
}

private fun restoreLootInstrumentation(previous: MetricsBackend) {
  MetricsConfig.enabled = false
  MetricsConfig.countersEnabled = false
  MetricsConfig.clearScopeOverrides()
  Instr.setBackend(previous)
}

private fun lootAppliedCount(): Long =
  Instr.counterSnapshot()
    .filter {
      it.id.scope == TerrasectInstrScope.LOOT.id &&
        it.id.event == TerrasectMetricEvent.LOOT_APPLIED.id
    }
    .sumOf { it.value }

// Rolls the vanilla simple_dungeon loot table directly through LootTable.getRandomItems — the
// exact method LootTableMixin injects into — so this exercises the real filter path without
// depending on natural structure generation or container-open timing.
@Suppress("UnstableApiUsage")
private fun rollDungeonLoot(
  context: ClientGameTestContext,
  presetId: String?,
  scenarioName: String,
): Int {
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
    var totalItems = 0
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
        val lootTable = server.reloadableRegistries().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON)
        val params =
          LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3(8.0, 60.0, 8.0))
            .create(LootContextParamSets.CHEST)
        for (i in 0 until ROLL_COUNT) {
          val items = lootTable.getRandomItems(params, SEED.hashCode().toLong() + i)
          totalItems += items.sumOf { it.count }
        }
      }
    )
    log.info(
      "[{}] preset={} totalItems={} over {} rolls",
      scenarioName,
      presetId,
      totalItems,
      ROLL_COUNT,
    )
    return totalItems
  } finally {
    game.close()
    PresetRegistry.forcePresetId = originalPresetId
  }
}

// Vanilla baseline vs. blockMods("minecraft") on Region.loot: proves per-region loot filtering
// works on the real LootTable.getRandomItems path, using the exact same Region/SelectionConstraints
// machinery as mob spawning and structure selection (position -> Region via chunk/traverse lookup,
// then name/tag/mod allow-block evaluation) rather than a bespoke mechanism.
// Evidence: vanilla rolls produce items, LOOT_APPLIED > 0 (filter ran), constrained rolls = 0
// items.
@Suppress("UnstableApiUsage")
object LootConstraintBlockAllGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val previousBackend = enableLootInstrumentation()

    try {
      val vanillaItems = rollDungeonLoot(context, DISABLED_PRESET, "vanilla")

      Instr.reset()
      registerBlockAllPreset()
      val constrainedItems = rollDungeonLoot(context, BLOCK_ALL_PRESET, "block_all")

      val appliedCount = lootAppliedCount()

      log.info(
        "[block_all] summary: vanilla_items={} constrained_items={} LOOT_APPLIED={}",
        vanillaItems,
        constrainedItems,
        appliedCount,
      )

      assertTrue(
        vanillaItems > 0,
        "[block_all] vanilla simple_dungeon rolls must produce items to prove the loot table " +
          "isn't empty by construction (got $vanillaItems over $ROLL_COUNT rolls).",
      )
      assertTrue(
        appliedCount > 0,
        "[block_all] LOOT_APPLIED counter must be > 0 — the runtime loot filter path was " +
          "exercised in the constrained world (got $appliedCount).",
      )
      assertEquals(
        0,
        constrainedItems,
        "[block_all] blockMods(minecraft) on Region.loot must suppress every vanilla-namespaced " +
          "loot item; found $constrainedItems item(s) across $ROLL_COUNT rolls.",
      )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(BLOCK_ALL_PRESET)
      restoreLootInstrumentation(previousBackend)
    }
  }
}
