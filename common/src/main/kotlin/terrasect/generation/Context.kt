package terrasect.generation

import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import net.minecraft.world.level.levelgen.structure.StructureSet
import terrasect.VanillaSamplerAccessor
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import terrasect.definition.RegionRegistry
import terrasect.utils.Packer
import kotlin.math.max
import kotlin.math.min

class Context(
    val dimensionId: String,
    val seed: Long,
    val region: Region,
    val sampler: Climate.Sampler,
    val biomesClimate: Climate.ParameterList<Holder<Biome>>?,
) {

  private val vanillaSampler = sampler as VanillaSamplerAccessor

  fun getInfluence(x: Int, z: Int): Long {
    if (biomesClimate == null) return 0

    val target = vanillaSampler.`terrasect$sampleVanilla`(x shr 2, 16, z shr 2)

    val biome = biomesClimate.findValue(target)

    val river = if (biome.`is`(BiomeTags.IS_RIVER)) 1.0f else 0.0f

    val weirdness = target.weirdness()
    val normalized = (weirdness + 10000.0f) / 20000.0f
    val ridge = max(0.0f, min(1.0f, normalized))

    return Packer.packPair(river, ridge)
  }

  companion object {
    val byDimension = mutableMapOf<String, Context>()
    val bySampler = mutableMapOf<Climate.Sampler, Context>()

    fun register(
        dimension: ResourceKey<Level>,
        seed: Long,
        sampler: Climate.Sampler,
        possibleSets: MutableList<Holder<StructureSet>>,
        registry: RegistryAccess.Frozen,
        biomesClimate: Climate.ParameterList<Holder<Biome>>?,
    ) {
      val dimensionId = ResourceKeyCompat.getKeyId(dimension)

      val root = RegionRegistry.getRoot(dimensionId) ?: return
      val region = RegionRegistry.build(root)

      val context = Context(dimensionId, seed, region, sampler, biomesClimate)
      byDimension[dimensionId] = context
      bySampler[sampler] = context
    }
  }
}
