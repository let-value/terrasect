package terrasect.lookup

import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureSet
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import terrasect.definition.StructureConstraints
import terrasect.extender.StructurePlacementExtender
import terrasect.handler.NoiseLogger

private val structureLog = NoiseLogger.registry

class CompiledStructureLookup
private constructor(
  private val allSets: List<Holder<StructureSet>>,
  private val index: IdentityHashMap<Structure, StructureEntry>,
) {
  private val filteredCache = ConcurrentHashMap<StructureConstraints, List<Holder<StructureSet>>>()

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
      val id = meta?.id ?: holder.unwrapKey().map { it.identifier().toString() }.orElse(null)
      val tags = meta?.tags ?: emptySet()
      if (selection.evaluate(id, tags)) {
        allowed.add(holder)
      }
    }
    return when {
      allowed.isEmpty() -> emptySet()
      allowed.size == structures.size -> null
      else -> allowed
    }
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
            val holder = entry.structure()
            val meta = index[holder.value()]
            val id = meta?.id ?: holder.unwrapKey().map { it.identifier().toString() }.orElse(null)
            val tags = meta?.tags ?: emptySet()
            selection.evaluate(id, tags)
          }
        } else entries

      if (filtered.isEmpty()) continue

      val originalPlacement = set.placement()
      val placement =
        if (hasPlacementOverrides) placementWithOverrides(originalPlacement, constraints)
        else originalPlacement

      if (filtered.size == entries.size && placement === originalPlacement) result.add(setHolder)
      else result.add(Holder.direct(StructureSet(filtered, placement)))
    }
    structureLog.debug { "computeFilteredSets: ${allSets.size} → ${result.size} structure sets" }
    return result
  }

  // Returns the original placement when no override is needed, otherwise a copied placement with
  // Terrasect's per-region spacing/separation/frequency applied. Do not mutate vanilla registry
  // placements in place: the same StructureSet instances are reused across worlds and presets, so
  // a dense test/world would otherwise leak its placement into later vanilla or banned scenarios.
  private fun placementWithOverrides(
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
    fun build(
      allSets: List<Holder<StructureSet>>,
      root: Region,
      registry: RegistryAccess.Frozen,
    ): CompiledStructureLookup? {
      if (!anyRegionHasStructures(root)) {
        structureLog.debug { "build: no structure-constrained regions under root=${root.name}" }
        return null
      }
      val index = buildIndex(allSets, registry)
      val lookup = CompiledStructureLookup(allSets, index)
      // Pre-bake filtered sets for every unique StructureConstraints in the region tree so that
      // the per-chunk hot path (getFilteredSets) always hits the cache and never recomputes.
      collectAllConstraints(root).forEach { lookup.getFilteredSets(it) }
      structureLog.debug {
        "build: ${index.size} structures indexed; structure constraints active under root=${root.name}"
      }
      return lookup
    }

    private fun collectAllConstraints(root: Region): Set<StructureConstraints> {
      val result = mutableSetOf<StructureConstraints>()
      val queue = ArrayDeque<Region>()
      queue.add(root)
      while (queue.isNotEmpty()) {
        val region = queue.removeFirst()
        region.structures?.let { result.add(it) }
        queue.addAll(region.children)
      }
      return result
    }

    private fun buildIndex(
      allSets: List<Holder<StructureSet>>,
      registry: RegistryAccess.Frozen,
    ): IdentityHashMap<Structure, StructureEntry> {
      val index = IdentityHashMap<Structure, StructureEntry>()
      val structureRegistry = registry.lookupOrThrow(Registries.STRUCTURE)
      for (setHolder in allSets) {
        for (entry in setHolder.value().structures()) {
          val holder = entry.structure()
          val structure = holder.value()
          if (!index.containsKey(structure)) {
            val key = holder.unwrapKey().or { structureRegistry.getResourceKey(structure) }
            val id = key.map { ResourceKeyCompat.getKeyId(it) }.orElse(null)
            val tags = HashSet<String>()
            key.ifPresent { resourceKey ->
              structureRegistry.get(resourceKey).ifPresent { taggedHolder ->
                taggedHolder.tags().forEach { tag -> tags.add(tag.location().toString()) }
              }
            }
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
