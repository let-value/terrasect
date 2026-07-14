package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry

private val log = LoggerFactory.getLogger("BiomesOPlentyCompatGameTest")

private const val SEED = "terrasect-bop-compat"
private const val COMPAT_PRESET = "bop_compat_climate_constrained"

// Same climate window the smoke preset uses to force the noise/climate pipeline to actually
// reshape terrain, rather than passing through untouched vanilla generation.
private fun registerCompatPreset() {
  PresetRegistry.presets[COMPAT_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").climate {
        temperature(-200, 400)
        humidity(0, 800)
        precipitation("rain")
      }
    }
}

// Biomes O' Plenty's unique surface area, from Terrasect's perspective, is the ~70 overworld
// biomes it registers via TerraBlender. The real compat risk isn't "does BOP load" (covered by
// CompatSmokeGameTest) — it's whether Terrasect's own climate-constraint mixins, which reshape
// the same temperature/humidity/continentalness inputs TerraBlender's regions are keyed on, end
// up starving BOP's regions out of the generated world entirely.
// Evidence: a biomesoplenty-namespaced biome is actually reachable via the real biome source
// under a Terrasect climate constraint, sampled without materializing chunks.
@Suppress("UnstableApiUsage")
object BiomesOPlentyCompatGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val originalPresetId = PresetRegistry.forcePresetId
    registerCompatPreset()
    PresetRegistry.forcePresetId = COMPAT_PRESET

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

      var found: String? = null
      var sampled = 0
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          val level = server.overworld()
          val biomeSource = level.chunkSource.generator.biomeSource
          val sampler = level.chunkSource.randomState().sampler()
          // Quart-position grid (1 sample per 64 blocks) out to +/-2048 blocks: cheap noise-only
          // sampling, no chunk generation, wide enough that TerraBlender's region weighting
          // should place at least one Biomes O' Plenty region within range.
          outer@ for (qx in -512..512 step 16) {
            for (qz in -512..512 step 16) {
              sampled++
              val holder = biomeSource.getNoiseBiome(qx, 0, qz, sampler)
              val id = holder.unwrapKey().map { it.identifier() }.orElse(null)
              if (id?.namespace == "biomesoplenty") {
                found = id.toString()
                break@outer
              }
            }
          }
          log.info("bop compat: sampled={} biomesoplenty biome found={}", sampled, found ?: "none")
        }
      )

      assertTrue(
        found != null,
        "no biomesoplenty-namespaced biome found within +/-2048 blocks of spawn under a Terrasect " +
          "climate constraint (sampled $sampled points) — TerraBlender's region injection may be " +
          "getting starved out by Terrasect's climate-constraint pipeline",
      )
      log.info("bop compat: OK — found {}", found)
    } finally {
      game.close()
      PresetRegistry.forcePresetId = originalPresetId
      PresetRegistry.presets.remove(COMPAT_PRESET)
    }
  }
}
