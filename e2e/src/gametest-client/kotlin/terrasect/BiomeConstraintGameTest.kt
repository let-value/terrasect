//? if latest {
package terrasect

import java.nio.file.Path
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
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

private val log = LoggerFactory.getLogger("BiomeConstraintGameTest")

private const val SEED = "biome-constraints"
private const val DISABLED_PRESET = "__disabled__"
private const val BLOCK_DESERT_PRESET = "biome_constraint_block_desert"
private const val SETTLE_TICKS = 40

private val SCAN_RADIUS = 128
private val SAMPLE_STEP = 4

private val SCREENSHOTS_BASE: Path by lazy {
  e2eScreenshotsBase(object {}.javaClass)
}

private fun registerBlockDesertPreset() {
  PresetRegistry.presets[BLOCK_DESERT_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").biomes { blockNames("minecraft:desert") }
    }
}

private fun configureOverheadBiomeCamera(client: Minecraft) {
  client.player?.let { player ->
    player.xRot = 90f
    player.yRot = 0f
    player.abilities.mayfly = true
    player.abilities.flying = true
    player.onUpdateAbilities()
  }
}

private fun enableBiomeInstrumentation(): MetricsBackend {
  MetricsConfig.enabled = true
  MetricsConfig.countersEnabled = true
  MetricsConfig.clearScopeOverrides()
  for (scope in TerrasectInstrScope.entries) {
    if (scope != TerrasectInstrScope.BIOME) MetricsConfig.setScopeEnabled(scope, false)
  }
  for (event in TerrasectMetricEvent.entries) {
    if (event != TerrasectMetricEvent.BIOME_APPLIED &&
      event != TerrasectMetricEvent.BIOME_REJECTED &&
      event != TerrasectMetricEvent.BIOME_FALLBACK_APPLIED &&
      event != TerrasectMetricEvent.BIOME_REJECTED_NO_FALLBACK
    ) {
      MetricsConfig.setEventCountersEnabled(event, false)
    }
  }
  val previous = Instr.getBackend()
  Instr.setBackend(InMemoryBackend())
  return previous
}

private fun restoreBiomeInstrumentation(previous: MetricsBackend) {
  MetricsConfig.enabled = false
  MetricsConfig.countersEnabled = false
  MetricsConfig.clearScopeOverrides()
  Instr.setBackend(previous)
}

@Suppress("UnstableApiUsage")
private fun runBiomeProbe(
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
    context.waitTicks(SETTLE_TICKS)

    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client -> configureOverheadBiomeCamera(client) }
    )
    context.waitTicks(10)
    game.clientLevel.waitForChunksRender()

    if (screenshotLabel != null && screenshotDir != null) {
      context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client -> configureOverheadBiomeCamera(client) }
      )
      context.waitTicks(5)
      context.takeScreenshot(
        TestScreenshotOptions.of(screenshotLabel).withDestinationDir(screenshotDir)
      )
      log.info("[{}] screenshot -> {}/{}.png", scenarioName, screenshotDir, screenshotLabel)
    }

    val biomeCounts = LinkedHashMap<String, Int>()
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
        for (bx in -SCAN_RADIUS..SCAN_RADIUS step SAMPLE_STEP) {
          for (bz in -SCAN_RADIUS..SCAN_RADIUS step SAMPLE_STEP) {
            val biome =
              level
                .getBiome(BlockPos(bx, 64, bz))
                .unwrapKey()
                .map { it.toString() }
                .orElse("unknown")
            biomeCounts[biome] = (biomeCounts[biome] ?: 0) + 1
          }
        }
        val top5 =
          biomeCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString { "${it.key.substringAfterLast(':')}×${it.value}" }
        log.info(
          "[{}] preset={} biomes: total={} types=[{}]",
          scenarioName,
          presetId,
          biomeCounts.values.sum(),
          top5.ifEmpty { "none" },
        )
      }
    )

    return biomeCounts
  } finally {
    game.close()
    PresetRegistry.forcePresetId = originalPresetId
  }
}

private fun biomeEventCount(event: TerrasectMetricEvent): Long =
  Instr.counterSnapshot()
    .filter {
      it.id.scope == TerrasectInstrScope.BIOME.id && it.id.event == event.id
    }
    .sumOf { it.value }

// Vanilla baseline vs. blockNames("minecraft:desert"): proves per-name biome blocking on the real
// climate read path. Desert biomes are replaced by the first admitted substitute; other biomes
// remain unaffected. Evidence: BIOME_APPLIED > 0 (admission ran) + constrained desert count = 0.
@Suppress("UnstableApiUsage")
object BiomeConstraintBlockByNameGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val screenshotDir = SCREENSHOTS_BASE.resolve("BiomeConstraintBlockByNameTest")
    val previousBackend = enableBiomeInstrumentation()

    try {
      val vanillaCounts =
        runBiomeProbe(
          context,
          DISABLED_PRESET,
          "vanilla",
          "vanilla_biomes",
          screenshotDir.resolve("vanilla"),
        )

      Instr.reset()
      registerBlockDesertPreset()
      val constrainedCounts =
        runBiomeProbe(
          context,
          BLOCK_DESERT_PRESET,
          "block_desert",
          "blocked_desert",
          screenshotDir.resolve("blocked"),
        )

      val appliedCount = biomeEventCount(TerrasectMetricEvent.BIOME_APPLIED)
      val rejectedCount = biomeEventCount(TerrasectMetricEvent.BIOME_REJECTED)
      val fallbackCount = biomeEventCount(TerrasectMetricEvent.BIOME_FALLBACK_APPLIED)
      val vanillaDesert = vanillaCounts["minecraft:desert"] ?: 0
      val constrainedDesert = constrainedCounts["minecraft:desert"] ?: 0

      log.info(
        "[block_by_name] summary: vanilla_desert={} constrained_desert={} BIOME_APPLIED={} BIOME_REJECTED={} BIOME_FALLBACK={}",
        vanillaDesert,
        constrainedDesert,
        appliedCount,
        rejectedCount,
        fallbackCount,
      )

      assertTrue(
        appliedCount > 0,
        "[block_by_name] BIOME_APPLIED counter must be > 0 — the biome admission read path was " +
          "exercised in the constrained world (got $appliedCount). Vanilla biomes: $vanillaCounts.",
      )
      assertEquals(
        0,
        constrainedDesert,
        "[block_by_name] blockNames(minecraft:desert) must produce zero desert biomes in the " +
          "sampled area; found $constrainedDesert (rejected=$rejectedCount, " +
          "fallback=$fallbackCount). Other biomes in constrained world: $constrainedCounts.",
      )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(BLOCK_DESERT_PRESET)
      restoreBiomeInstrumentation(previousBackend)
    }
  }
}
//?}
