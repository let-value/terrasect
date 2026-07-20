//? if latest {
package terrasect

import kotlin.math.abs
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy
import terrasect.generation.DimensionContext

private val log = LoggerFactory.getLogger("OriginAnchorGameTest")

private const val SEED = "origin-anchor"
private const val OFF_PRESET = "origin_anchor_off"
private const val ON_PRESET = "origin_anchor_on"
private const val TARGET = "target"

// The anchored region's reported center is exactly the origin in principle; allow a chunk of slack
// so the assertion documents intent ("near zero") rather than pinning an exact value.
private const val NEAR_ZERO = 16

// Without the anchor the same region's canonical center falls somewhere on the scattered plane. For
// this seed it lands well outside the near-zero window; requiring a clear gap proves the anchor is
// what moved it, not that every run happens to sit near origin.
private const val CLEARLY_AWAY = 256

private fun preset(anchor: Boolean): RegionRegistry =
  RegionRegistry().apply {
    setRoot("minecraft:overworld", "root")
    region("root").radius(2000).strategy(Strategy.voronoi())
    region(TARGET).parent("root").radius(500).apply { if (anchor) originAnchor() }
    region("filler").parent("root").radius(500)
  }

// Two-run origin-anchor test. Both runs use the same two-region preset and locate the same region
// (`.target`); they differ only in whether that region is marked `originAnchor`.
//   Run 1 (no anchor): the region's center lands wherever the seed puts it — we don't care where.
//   Run 2 (anchored):  loading the world shifts every coordinate so the region's center sits at
//                      world (0, 0), so locating the same region now reports a near-zero center.
@Suppress("UnstableApiUsage")
object OriginAnchorGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    PresetRegistry.presets[OFF_PRESET] = preset(anchor = false)
    PresetRegistry.presets[ON_PRESET] = preset(anchor = true)
    try {
      val unanchored = locateTargetCenter(context, OFF_PRESET)
      val anchored = locateTargetCenter(context, ON_PRESET)

      log.info("unanchored .{} center = {}", TARGET, unanchored)
      log.info("anchored   .{} center = {}", TARGET, anchored)

      assertTrue(
        abs(anchored.first) <= NEAR_ZERO && abs(anchored.second) <= NEAR_ZERO,
        "anchored .$TARGET center should sit near world origin, was $anchored",
      )
      assertTrue(
        abs(unanchored.first) >= CLEARLY_AWAY || abs(unanchored.second) >= CLEARLY_AWAY,
        "unanchored .$TARGET center should be clearly away from origin (else the anchor proved " +
          "nothing), was $unanchored",
      )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(OFF_PRESET)
      PresetRegistry.presets.remove(ON_PRESET)
    }
  }

  // Loads a world under the given preset and asks the live DimensionContext to locate the target
  // region, returning its reported center. Locating by name (no player context) hits the same
  // canonical resolution the anchor itself is derived from, so an active anchor drives the result
  // to origin.
  private fun locateTargetCenter(
    context: ClientGameTestContext,
    presetId: String,
  ): Pair<Int, Int> {
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
      context.waitTicks(20)

      var center = 0 to 0
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { _ ->
          val ctx = DimensionContext.get("minecraft:overworld")
          assertNotNull(ctx, "[$presetId] DimensionContext must be active for the forced preset")
          val located = ctx!!.locator.query(".$TARGET")
          assertNotNull(located, "[$presetId] must locate .$TARGET")
          center = located!!.centerX to located.centerZ
        }
      )
      return center
    } finally {
      game.close()
    }
  }
}
//?}