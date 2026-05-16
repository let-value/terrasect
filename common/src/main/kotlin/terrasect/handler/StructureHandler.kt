package terrasect.handler

import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureSet
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.StructureConstraints
import terrasect.generation.DimensionContext
import terrasect.lookup.CompiledStructureLookup

object StructureHandler {
  @JvmStatic
  fun getFilteredSets(
    dimensionKey: ResourceKey<Level>,
    chunkX: Int,
    chunkZ: Int,
  ): List<Holder<StructureSet>>? {
    val (lookup, constraints) = resolve(dimensionKey, chunkX, chunkZ) ?: return null
    return lookup.getFilteredSets(constraints)
  }

  @JvmStatic
  fun filterStructuresForLocate(
    structures: Set<Holder<Structure>>,
    dimensionKey: ResourceKey<Level>,
    chunkX: Int,
    chunkZ: Int,
  ): Set<Holder<Structure>>? {
    val (lookup, constraints) = resolve(dimensionKey, chunkX, chunkZ) ?: return null
    return lookup.filterStructuresForLocate(structures, constraints)
  }

  private fun resolve(
    dimensionKey: ResourceKey<Level>,
    chunkX: Int,
    chunkZ: Int,
  ): Pair<CompiledStructureLookup, StructureConstraints>? {
    val ctx = DimensionContext.get(ResourceKeyCompat.getKeyId(dimensionKey)) ?: return null
    val lookup = ctx.structureLookup ?: return null
    val blockX = (chunkX shl 4) + 8
    val blockZ = (chunkZ shl 4) + 8
    val region = ctx.traverser.traverse(blockX, blockZ).region
    val constraints = region.structures ?: return null
    return Pair(lookup, constraints)
  }
}
