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

private val log = LoggerFactory.getLogger("TerraBlenderCompatGameTest")

private const val SEED = "terrasect-terrablender-compat"
private const val COMPAT_PRESET = "terrablender_compat_noise_constrained"
private const val MIN_DISTINCT_BIOMES = 5

// Terrasect's density-function rewriting operates on the same noise router TerraBlender's
// multi-noise region weighting reads from — unlike the BOP test's climate-constraint angle, this
// exercises the noise/scaffold mixin path directly.
private fun registerCompatPreset() {
  PresetRegistry.presets[COMPAT_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").noise {
        densityFunction("erosion") {
          it.multiply(0.0)
          it.add(0.2)
        }
      }
    }
}

// TerraBlender's own value-add (distinct from BOP, which just supplies biome content) is region
// *weighting* across the multi-noise biome layout — it decides how much of the map each mod's
// region claims. The compat risk here is that Terrasect's density-function rewrite collapses the
// noise space TerraBlender partitions on, flattening the world into a handful of biomes instead
// of the varied mix TerraBlender is meant to produce.
// Evidence: biome variety (distinct biome count) in a sampled area stays above a sane floor under
// a Terrasect noise constraint, proving TerraBlender's weighting still meaningfully partitions
// the world rather than degenerating to near-single-biome output.
@Suppress("UnstableApiUsage")
object TerraBlenderCompatGameTest : FabricClientGameTest {
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

      val distinctBiomes = HashSet<String>()
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          val level = server.overworld()
          val biomeSource = level.chunkSource.generator.biomeSource
          val sampler = level.chunkSource.randomState().sampler()
          for (qx in -512..512 step 16) {
            for (qz in -512..512 step 16) {
              val holder = biomeSource.getNoiseBiome(qx, 0, qz, sampler)
              holder.unwrapKey().ifPresent { distinctBiomes.add(it.identifier().toString()) }
            }
          }
          log.info(
            "terrablender compat: distinct biomes sampled={} sample={}",
            distinctBiomes.size,
            distinctBiomes.take(10),
          )
        }
      )

      assertTrue(
        distinctBiomes.size >= MIN_DISTINCT_BIOMES,
        "only ${distinctBiomes.size} distinct biome(s) found under a Terrasect noise constraint " +
          "(want >= $MIN_DISTINCT_BIOMES) — TerraBlender's region weighting may be getting " +
          "flattened by the density-function rewrite: $distinctBiomes",
      )
      log.info("terrablender compat: OK — {} distinct biomes", distinctBiomes.size)
    } finally {
      game.close()
      PresetRegistry.forcePresetId = originalPresetId
      PresetRegistry.presets.remove(COMPAT_PRESET)
    }
  }
}
