package terrasect

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.server.MinecraftServer
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry
import terrasect.generation.DimensionContext

private val log = LoggerFactory.getLogger("ModdedBiomeConstraintGameTest")

private const val SEED = "terrasect-modded-biome-constraints"
private const val ALLOW_PRESET = "modded_biome_allow"
private const val BLOCK_PRESET = "modded_biome_block"
private const val BOP_NAMESPACE = "biomesoplenty"

private fun registerBiomePreset(id: String, configure: RegionRegistry.() -> Unit) {
  PresetRegistry.presets[id] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root")
      configure()
    }
}

@Suppress("UnstableApiUsage")
private fun sampleBiomeConstraint(
  context: ClientGameTestContext,
  presetId: String,
  configure: RegionRegistry.() -> Unit,
): Set<String> {
  val originalPresetId = PresetRegistry.forcePresetId
  registerBiomePreset(presetId, configure)
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
    val sampled = LinkedHashSet<String>()
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
        val dimensionId = ResourceKeyCompat.getKeyId(level.dimension())
        val dimensionContext =
          checkNotNull(DimensionContext.get(dimensionId)) {
            "no DimensionContext registered for $dimensionId with modded biome preset=$presetId"
          }
        val lookup =
          checkNotNull(dimensionContext.biomeLookup) {
            "no biome lookup compiled for modded biome preset=$presetId"
          }
        val biomeSource = level.chunkSource.generator.biomeSource
        val sampler = level.chunkSource.randomState().sampler()
        for (qx in -512..512 step 16) {
          for (qz in -512..512 step 16) {
            val holder = biomeSource.getNoiseBiome(qx, 0, qz, sampler)
            val id = holder.unwrapKey().map { ResourceKeyCompat.getKeyId(it) }.orElse("unknown")
            sampled += id
            val region =
              dimensionContext.traverser.traverse(qx shl 2, qz shl 2, dimensionContext.cache).region
            check(lookup.isAdmitted(region, holder.value())) {
              "source returned biome $id rejected by its region lookup at quart=($qx,$qz)"
            }
          }
        }
        log.info("modded biome preset={} types={}", presetId, sampled)
      }
    )
    return sampled
  } finally {
    game.close()
    PresetRegistry.forcePresetId = originalPresetId
    PresetRegistry.presets.remove(presetId)
  }
}

@Suppress("UnstableApiUsage")
object ModdedBiomeConstraintGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val requiredMods = listOf("biomesoplenty", "terrablender")
    val modStatus = requiredMods.associateWith { FabricLoader.getInstance().isModLoaded(it) }
    assertTrue(
      modStatus.values.all { it },
      "modded biome constraint test requires Biomes O' Plenty and TerraBlender: " +
        modStatus.filterValues { !it }.keys,
    )

    val allowed =
      sampleBiomeConstraint(context, ALLOW_PRESET) {
        region("overworld_root").biomes { allowMods(BOP_NAMESPACE) }
      }
    assertTrue(
      allowed.isNotEmpty() && allowed.all { it.substringBefore(':') == BOP_NAMESPACE },
      "allowMods($BOP_NAMESPACE) returned non-BOP biomes: $allowed",
    )

    val blocked =
      sampleBiomeConstraint(context, BLOCK_PRESET) {
        region("overworld_root").biomes { blockMods(BOP_NAMESPACE) }
      }
    assertTrue(
      blocked.none { it.substringBefore(':') == BOP_NAMESPACE },
      "blockMods($BOP_NAMESPACE) returned a Biomes O' Plenty biome: $blocked",
    )
    log.info("modded biome constraint: allow={} block={}", allowed, blocked)
  }
}
