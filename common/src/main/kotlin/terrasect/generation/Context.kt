package terrasect.generation

import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import net.minecraft.world.level.levelgen.structure.StructureSet
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import terrasect.definition.RegionRegistry

class Context(
    val dimensionId: String,
    val seed: Long,
    val region: Region,
    val sampler: Climate.Sampler,
    val biomesClimate: Climate.ParameterList<Holder<Biome>>?,
) {
  companion object {
    val byDimension = mutableMapOf<String, Context>()

    fun register(
        dimension: ResourceKey<Level>,
        seed: Long,
        sampler: Climate.Sampler,
        structureSets: MutableList<Holder<StructureSet>>,
        registry: RegistryAccess.Frozen,
        biomesClimate: Climate.ParameterList<Holder<Biome>>?,
    ) {
      val dimensionId = ResourceKeyCompat.getKeyId(dimension)

      val root = RegionRegistry.getRoot(dimensionId) ?: return
      val region = RegionRegistry.buildTree(root)

      val context = Context(dimensionId, seed, region, sampler, biomesClimate)
      byDimension[dimensionId] = context
    }

    fun get(dimensionId: String): Context? = byDimension[dimensionId]
  }
}
