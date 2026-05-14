package terrasect.generation

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import net.minecraft.world.level.levelgen.structure.StructureSet
import terrasect.Terrasect
import terrasect.cache.RegionsCache
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.PresetRegistry
import terrasect.definition.Region
import terrasect.handler.NoiseScope
import terrasect.lookup.CompiledNoiseRegistry

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
    NoiseScope.context.debug {
      "[NC-DimensionContext] built preset=$presetId dim=$dimensionId noiseRegistry=${if (noiseRegistry != null) "ACTIVE" else "NULL (no noise constraints)"}"
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
      NoiseScope.context.debug {
        "[NC-DimensionContext] register called: preset=$presetId force=${PresetRegistry.forcePresetId} dim=$dimensionId"
      }
      val resolvedRegistry = PresetRegistry.resolve(presetId)
      if (resolvedRegistry == null) {
        NoiseScope.context.debug {
          "[NC-DimensionContext] no preset resolved — noise constraints disabled for $dimensionId"
        }
        return
      }
      val name = resolvedRegistry.getRoot(dimensionId)
      if (name == null) {
        NoiseScope.context.debug {
          "[NC-DimensionContext] no root region for dim=$dimensionId in preset=$presetId"
        }
        return
      }
      val root = resolvedRegistry.buildTree(name)

      val dimensionContext =
        DimensionContext(presetId, dimensionId, seed, root, sampler, biomesClimate)
      map[dimensionId] = dimensionContext
      NoiseScope.context.debug { "[NC-DimensionContext] registered dim=$dimensionId" }
    }

    fun get(dimensionId: String): DimensionContext? = map[dimensionId]
  }
}
