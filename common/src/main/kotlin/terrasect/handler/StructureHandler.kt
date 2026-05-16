package terrasect.handler

import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureSet
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.StructureConstraints
import terrasect.generation.DimensionContext

object StructureHandler {
  @JvmStatic
  fun getFilteredSets(
    dimensionKey: ResourceKey<Level>,
    chunkX: Int,
    chunkZ: Int,
  ): List<Holder<StructureSet>>? {
    val ctx = DimensionContext.get(ResourceKeyCompat.getKeyId(dimensionKey)) ?: return null
    val lookup = ctx.structureLookup ?: return null
    val constraints = constraintsAt(ctx, chunkX, chunkZ) ?: return null
    return lookup.getFilteredSets(constraints)
  }

  /**
   * Returns null → all structures filtered, caller should skip this chunk. Returns non-null
   * (possibly the original set) → use this set.
   */
  @JvmStatic
  fun resolveLocateSet(
    structures: Set<Holder<Structure>>,
    levelReader: LevelReader,
    chunkX: Int,
    chunkZ: Int,
  ): Set<Holder<Structure>>? {
    if (levelReader !is Level) return structures
    val ctx =
      DimensionContext.get(ResourceKeyCompat.getKeyId(levelReader.dimension())) ?: return structures
    val lookup = ctx.structureLookup ?: return structures
    val constraints = constraintsAt(ctx, chunkX, chunkZ) ?: return structures
    val filtered = lookup.filterStructuresForLocate(structures, constraints) ?: return structures
    return filtered.ifEmpty { null }
  }

  private fun constraintsAt(
    ctx: DimensionContext,
    chunkX: Int,
    chunkZ: Int,
  ): StructureConstraints? {
    val blockX = (chunkX shl 4) + 8
    val blockZ = (chunkZ shl 4) + 8
    return ctx.traverser.traverse(blockX, blockZ).region.structures
  }
}
