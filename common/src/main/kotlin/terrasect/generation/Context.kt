package terrasect.generation

import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import net.minecraft.world.level.levelgen.structure.StructureSet
import terrasect.Terrasect
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import java.util.concurrent.ConcurrentHashMap

class Context(
    val dimensionId: String,
    override val seed: Long,
    override val root: Region,
    val sampler: Climate.Sampler,
    val biomesClimate: Climate.ParameterList<Holder<Biome>>?,
) : Traverse, Locate {
  companion object {
    val byDimension = ConcurrentHashMap<String, Context>()

    fun register(
        dimension: ResourceKey<Level>,
        seed: Long,
        sampler: Climate.Sampler,
        structureSets: MutableList<Holder<StructureSet>>,
        registry: RegistryAccess.Frozen,
        biomesClimate: Climate.ParameterList<Holder<Biome>>?,
    ) {
      val dimensionId = ResourceKeyCompat.getKeyId(dimension)

      val name = Terrasect.registry.getRoot(dimensionId) ?: return
      val root = Terrasect.registry.buildTree(name)

      val context = Context(dimensionId, seed, root, sampler, biomesClimate)
      byDimension[dimensionId] = context
    }

    fun get(dimensionId: String): Context? = byDimension[dimensionId]
  }
}
