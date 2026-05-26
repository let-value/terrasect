package terrasect.handler

import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureSet
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.StructureConstraints
import terrasect.generation.ChunkContext
import terrasect.generation.DimensionContext
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent

private val instr = TerrasectInstr.structure

object StructureHandler {
  /**
   * Returns filtered structure sets for chunk creation. When a pre-built [ChunkContext] is
   * available it reads the region from the pre-computed grid, avoiding a re-traversal per chunk.
   */
  @JvmStatic
  fun getFilteredSets(
    chunkContext: ChunkContext?,
    dimensionKey: ResourceKey<Level>,
    chunkX: Int,
    chunkZ: Int,
  ): List<Holder<StructureSet>>? {
    val ctx =
      chunkContext?.dimensionContext
        ?: DimensionContext.get(ResourceKeyCompat.getKeyId(dimensionKey))
        ?: return null
    val lookup = ctx.structureLookup ?: return null
    val blockX = (chunkX shl 4) + 8
    val blockZ = (chunkZ shl 4) + 8
    if (chunkContext == null) {
      instr.count(TerrasectMetricEvent.STRUCTURE_CHUNK_MISSING)
    }
    val region =
      chunkContext?.getRegion(blockX, blockZ) ?: ctx.traverser.traverse(blockX, blockZ).region
    val constraints = region.structures ?: return null
    instr.count(TerrasectMetricEvent.STRUCTURE_APPLIED)
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
    instr.count(TerrasectMetricEvent.STRUCTURE_CHUNK_MISSING)
    val constraints = constraintsAt(ctx, chunkX, chunkZ) ?: return structures
    instr.count(TerrasectMetricEvent.STRUCTURE_APPLIED)
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

  @JvmStatic
  fun recordGeneratedStructure(structureId: String, location: String, origin: String? = null) {
    if (origin == null) {
      instr.count(
        TerrasectMetricEvent.STRUCTURE_GENERATED,
        "structure_id",
        { structureId },
        "location",
        { location },
      )
    } else {
      instr.count(
        TerrasectMetricEvent.STRUCTURE_GENERATED,
        "structure_id",
        { structureId },
        "location",
        { location },
        "origin",
        { origin },
      )
    }
  }
}
