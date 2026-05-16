package terrasect.lookup

import net.minecraft.core.Holder
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureSet
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement
import terrasect.definition.Region
import terrasect.definition.StructureConstraints
import terrasect.extender.StructurePlacementExtender
import terrasect.handler.NoiseLogger

private val structureLog = NoiseLogger.registry

class CompiledStructureLookup
private constructor(
  private val allSets: List<Holder<StructureSet>>,
  private val index: java.util.IdentityHashMap<Structure, StructureEntry>,
) {
  private val filteredCache =
    java.util.concurrent.ConcurrentHashMap<StructureConstraints, List<Holder<StructureSet>>>()

  fun getFilteredSets(constraints: StructureConstraints): List<Holder<StructureSet>> =
    filteredCache.computeIfAbsent(constraints) { computeFilteredSets(it) }

  fun filterStructuresForLocate(
    structures: Set<Holder<Structure>>,
    constraints: StructureConstraints,
  ): Set<Holder<Structure>>? {
    val selection = constraints.selection ?: return null
    val allowed = HashSet<Holder<Structure>>(structures.size)
    for (holder in structures) {
      val meta = index[holder.value()]
      if (meta == null || selection.evaluate(meta.id, meta.tags)) allowed.add(holder)
    }
    return if (allowed.size == structures.size) null else allowed
  }

  private fun computeFilteredSets(constraints: StructureConstraints): List<Holder<StructureSet>> {
    val selection = constraints.selection
    val hasPlacementOverrides =
      constraints.spacing != null || constraints.separation != null || constraints.frequency != null
    if (selection == null && !hasPlacementOverrides) return allSets

    val result = ArrayList<Holder<StructureSet>>(allSets.size)
    for (setHolder in allSets) {
      val set = setHolder.value()
      val entries = set.structures()

      val filtered =
        if (selection != null) {
          entries.filter { entry ->
            val meta = index[entry.structure().value()]
            meta == null || selection.evaluate(meta.id, meta.tags)
          }
        } else entries

      if (filtered.isEmpty()) continue

      val placement =
        if (hasPlacementOverrides) applyPlacementOverrides(set.placement(), constraints)
        else set.placement()

      if (filtered.size == entries.size && placement === set.placement()) result.add(setHolder)
      else result.add(Holder.direct(StructureSet(filtered, placement)))
    }
    structureLog.debug { "computeFilteredSets: ${allSets.size} → ${result.size} structure sets" }
    return result
  }

  private fun applyPlacementOverrides(
    placement: StructurePlacement,
    constraints: StructureConstraints,
  ): StructurePlacement {
    if (placement !is RandomSpreadStructurePlacement) return placement
    val ext = placement as StructurePlacementExtender
    val newSpacing = constraints.spacing ?: placement.spacing()
    val newSeparation = minOf(constraints.separation ?: placement.separation(), newSpacing - 1)
    val newFrequency = constraints.frequency ?: ext.`terrasect$frequency`()
    if (
      newSpacing == placement.spacing() &&
        newSeparation == placement.separation() &&
        newFrequency == ext.`terrasect$frequency`()
    )
      return placement
    return RandomSpreadStructurePlacement(
      ext.`terrasect$locateOffset`(),
      ext.`terrasect$frequencyReductionMethod`(),
      newFrequency,
      ext.`terrasect$salt`(),
      ext.`terrasect$exclusionZone`(),
      newSpacing,
      newSeparation,
      placement.spreadType(),
    )
  }

  companion object {
    fun build(allSets: List<Holder<StructureSet>>, root: Region): CompiledStructureLookup? {
      if (!anyRegionHasStructures(root)) {
        structureLog.debug { "build: no structure-constrained regions under root=${root.name}" }
        return null
      }
      val index = buildIndex(allSets)
      structureLog.debug {
        "build: ${index.size} structures indexed; structure constraints active under root=${root.name}"
      }
      return CompiledStructureLookup(allSets, index)
    }

    private fun buildIndex(
      allSets: List<Holder<StructureSet>>
    ): java.util.IdentityHashMap<Structure, StructureEntry> {
      val index = java.util.IdentityHashMap<Structure, StructureEntry>()
      for (setHolder in allSets) {
        for (entry in setHolder.value().structures()) {
          val holder = entry.structure()
          val structure = holder.value()
          if (!index.containsKey(structure)) {
            val id = holder.unwrapKey().map { it.identifier().toString() }.orElse(null)
            val tags = HashSet<String>()
            try {
              holder.tags().forEach { tags.add(it.location().toString()) }
            } catch (_: IllegalStateException) {}
            index[structure] = StructureEntry(id, tags)
          }
        }
      }
      return index
    }

    private fun anyRegionHasStructures(region: Region): Boolean {
      if (region.structures != null) return true
      return region.children.any { anyRegionHasStructures(it) }
    }
  }
}

private data class StructureEntry(val id: String?, val tags: Set<String>)
