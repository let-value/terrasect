package terrasect.handler

import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.core.SectionPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureSet
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.StructureConstraints
import terrasect.extender.ChunkAccessExtender
import terrasect.extender.ChunkGeneratorStructureStateExtender
import terrasect.generation.ChunkContext
import terrasect.generation.DimensionContext
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent

private val instr = TerrasectInstr.structure

object StructureHandler {
  /**
   * Returns filtered structure sets for chunk creation. When a pre-built [ChunkContext] is
   * available it reads the region from the pre-computed grid, avoiding a re-traversal per chunk.
   * [dimensionKey] is only consulted as a fallback when [chunkContext] is null; 1.21.1's
   * `createStructures` doesn't expose a dimension key to the injection site, so it's null there.
   */
  @JvmStatic
  fun getFilteredSets(
    state: ChunkGeneratorStructureState,
    chunkContext: ChunkContext?,
    dimensionKey: ResourceKey<Level>?,
    chunkX: Int,
    chunkZ: Int,
  ): List<Holder<StructureSet>>? {
    val ctx =
      chunkContext?.dimensionContext
        ?: (state as ChunkGeneratorStructureStateExtender).`terrasect$getDimensionContext`()
        ?: dimensionKey?.let { DimensionContext.get(ResourceKeyCompat.getKeyId(it)) }
        ?: return null
    val blockX = (chunkX shl 4) + 8
    val blockZ = (chunkZ shl 4) + 8
    if (chunkContext == null) {
      instr.count(TerrasectMetricEvent.STRUCTURE_CHUNK_MISSING)
    }
    val forced = ctx.forcedStructures
    val constraints: StructureConstraints?
    if (forced != null) {
      val decision =
        chunkContext?.getForcedDecision() ?: forced.query(ctx.traverser, ctx.cache, chunkX, chunkZ)
      if (decision.banned) {
        instr.count(TerrasectMetricEvent.STRUCTURE_BANNED)
        return emptyList()
      }
      constraints = decision.leaf.structures
    } else {
      val region =
        chunkContext?.getRegion(blockX, blockZ) ?: ctx.traverser.traverse(blockX, blockZ).region
      constraints = region.structures
    }
    if (constraints == null) return null
    val lookup = ctx.structureLookup ?: return null
    instr.count(TerrasectMetricEvent.STRUCTURE_APPLIED)
    return lookup.getFilteredSets(constraints)
  }

  /**
   * Generates StructureStarts for forced sites whose center falls in this chunk. Runs at the tail
   * of `createStructures`, after the (already banned) random pass, so a forced start never competes
   * with a random one. Calls Structure.generate with an always-true biome predicate: forcing must
   * succeed regardless of what biome the site landed on. [dimensionKey] is null on 1.21.1 where
   * `createStructures` has no dimension parameter.
   */
  @JvmStatic
  fun placeForcedStructures(
    generator: ChunkGenerator,
    registryAccess: RegistryAccess,
    state: ChunkGeneratorStructureState,
    structureManager: StructureManager,
    templateManager: StructureTemplateManager,
    chunk: ChunkAccess,
    dimensionKey: ResourceKey<Level>?,
  ) {
    val chunkContext = (chunk as ChunkAccessExtender).`terrasect$getContext`()
    val chunkPos = chunk.pos
    val ctx =
      chunkContext?.dimensionContext
        ?: (state as ChunkGeneratorStructureStateExtender).`terrasect$getDimensionContext`()
        ?: dimensionKey?.let { DimensionContext.get(ResourceKeyCompat.getKeyId(it)) }
        ?: return
    if (chunkContext == null) {
      instr.count(TerrasectMetricEvent.STRUCTURE_CHUNK_MISSING)
    }
    val forced = ctx.forcedStructures ?: return
    val decision =
      chunkContext?.getForcedDecision()
        ?: forced.query(ctx.traverser, ctx.cache, chunkPos.x, chunkPos.z)
    if (decision.starts.isEmpty()) return
    val sectionPos = SectionPos.bottomOf(chunk)
    for (start in decision.starts) {
      val structure = start.entry.holder.value()
      if (chunk.getStartForStructure(structure) != null) continue
      // spotless:off
      //? if >=1.21.11 {
      val structureStart =
        structure.generate(
          start.entry.holder,
          dimensionKey!!,
          registryAccess,
          generator,
          generator.biomeSource,
          state.randomState(),
          templateManager,
          state.levelSeed,
          chunkPos,
          0,
          chunk,
          { true },
        )
      //?} else {
      /*val structureStart =
        structure.generate(
          registryAccess,
          generator,
          generator.biomeSource,
          state.randomState(),
          templateManager,
          state.levelSeed,
          chunkPos,
          0,
          chunk,
          { true },
        )
      */
      //?}
      // spotless:on
      if (structureStart.isValid) {
        structureManager.setStartForStructure(sectionPos, structure, structureStart, chunk)
        instr.count(TerrasectMetricEvent.STRUCTURE_FORCED, "structure_id", { start.entry.id })
      } else {
        instr.count(
          TerrasectMetricEvent.STRUCTURE_FORCED_FAILED,
          "structure_id",
          { start.entry.id },
        )
      }
    }
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
    val ctx = DimensionContext.get(ResourceKeyCompat.getKeyId(levelReader.dimension()))
    if (ctx == null) {
      instr.count(TerrasectMetricEvent.STRUCTURE_CHUNK_MISSING)
      return structures
    }
    val lookup = ctx.structureLookup ?: return structures
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
