package terrasect.lookup

import net.minecraft.core.Holder
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureSet
import terrasect.definition.Region
import terrasect.definition.SelectionConstraints
import terrasect.definition.StructureConstraints
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

  private fun computeFilteredSets(constraints: StructureConstraints): List<Holder<StructureSet>> {
    val selection = constraints.selection ?: return allSets
    val result = ArrayList<Holder<StructureSet>>(allSets.size)
    for (setHolder in allSets) {
      val set = setHolder.value()
      val entries = set.structures()
      val filtered =
        entries.filter { entry ->
          val meta = index[entry.structure().value()]
          meta == null || selection.evaluate(meta.id, meta.tags)
        }
      when {
        filtered.isEmpty() -> {} // drop entirely
        filtered.size == entries.size -> result.add(setHolder)
        else -> result.add(Holder.direct(StructureSet(filtered, set.placement())))
      }
    }
    structureLog.debug { "computeFilteredSets: ${allSets.size} → ${result.size} structure sets" }
    return result
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
