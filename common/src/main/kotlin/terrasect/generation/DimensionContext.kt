package terrasect.generation

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import net.minecraft.world.level.levelgen.structure.StructureSet
import org.slf4j.LoggerFactory
import terrasect.Terrasect
import terrasect.cache.RegionsCache
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.PresetRegistry
import terrasect.definition.Region
import terrasect.handler.NoiseDebug
import terrasect.lookup.CompiledNoiseRegistry

private val LOGGER = LoggerFactory.getLogger("Terrasect/DimensionContext")

class DimensionContext(
  val presetId: String?,
  val dimensionId: String,
  val seed: Long,
  val root: Region,
  val sampler: Climate.Sampler,
  val biomesClimate: Climate.ParameterList<Holder<Biome>>?,
) {
  val cache = RegionsCache(200, Terrasect.cache)
  val traverser = Traverser(seed, root)
  val locator = Locator(seed, root)

  val noiseRegistry: CompiledNoiseRegistry? = CompiledNoiseRegistry.build(root)

  init {
    NoiseDebug.ifEnabled {
      LOGGER.info(
        "[NC-DimensionContext] built preset={} dim={} noiseRegistry={}",
        presetId,
        dimensionId,
        if (noiseRegistry != null) "ACTIVE" else "NULL (no noise constraints)",
      )
    }
  }

  companion object {
    val map = ConcurrentHashMap<String, DimensionContext>()

    @JvmStatic
    fun register(
      presetId: String?,
      dimension: ResourceKey<Level>,
      structureSets: MutableList<Holder<StructureSet>>,
      registry: RegistryAccess.Frozen,
      seed: Long,
      sampler: Climate.Sampler,
      biomesClimate: Climate.ParameterList<Holder<Biome>>?,
    ) {
      val dimensionId = ResourceKeyCompat.getKeyId(dimension)
      NoiseDebug.ifEnabled {
        LOGGER.info(
          "[NC-DimensionContext] register called: preset={} force={} dim={}",
          presetId,
          PresetRegistry.forcePresetId,
          dimensionId,
        )
      }
      val resolvedRegistry = PresetRegistry.resolve(presetId)
      if (resolvedRegistry == null) {
        NoiseDebug.ifEnabled {
          LOGGER.warn(
            "[NC-DimensionContext] no preset resolved — noise constraints disabled for {}",
            dimensionId,
          )
        }
        return
      }
      val name = resolvedRegistry.getRoot(dimensionId)
      if (name == null) {
        NoiseDebug.ifEnabled {
          LOGGER.warn(
            "[NC-DimensionContext] no root region for dim={} in preset={}",
            dimensionId,
            presetId,
          )
        }
        return
      }
      val root = resolvedRegistry.buildTree(name)

      val dimensionContext =
        DimensionContext(presetId, dimensionId, seed, root, sampler, biomesClimate)
      map[dimensionId] = dimensionContext
      NoiseDebug.ifEnabled { LOGGER.info("[NC-DimensionContext] registered dim={}", dimensionId) }
    }

    fun get(dimensionId: String): DimensionContext? = map[dimensionId]
  }
}
