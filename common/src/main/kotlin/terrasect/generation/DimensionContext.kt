package terrasect.generation

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
import terrasect.lookup.CompiledNoiseRegistry
import java.util.concurrent.ConcurrentHashMap

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
      val registry = PresetRegistry.resolve(presetId) ?: return
      val name = registry.getRoot(dimensionId) ?: return
      val root = registry.buildTree(name)

      val dimensionContext =
          DimensionContext(presetId, dimensionId, seed, root, sampler, biomesClimate)
      map[dimensionId] = dimensionContext
    }

    fun get(dimensionId: String): DimensionContext? = map[dimensionId]
  }
}
