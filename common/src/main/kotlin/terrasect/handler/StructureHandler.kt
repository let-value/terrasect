package terrasect.handler

import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.structure.StructureSet
import terrasect.compat.ResourceKeyCompat
import terrasect.generation.DimensionContext

object StructureHandler {
  @JvmStatic
  fun getFilteredSets(
    dimensionContext: DimensionContext,
    chunkX: Int,
    chunkZ: Int,
  ): List<Holder<StructureSet>>? {
    val lookup = dimensionContext.structureLookup ?: return null
    val blockX = (chunkX shl 4) + 8
    val blockZ = (chunkZ shl 4) + 8
    val region = dimensionContext.traverser.traverse(blockX, blockZ).region
    val constraints = region?.structures ?: return null
    return lookup.getFilteredSets(constraints)
  }

  @JvmStatic
  fun getFilteredSets(
    dimensionKey: ResourceKey<Level>,
    chunkX: Int,
    chunkZ: Int,
  ): List<Holder<StructureSet>>? {
    val ctx = DimensionContext.get(ResourceKeyCompat.getKeyId(dimensionKey)) ?: return null
    return getFilteredSets(ctx, chunkX, chunkZ)
  }
}
