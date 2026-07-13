package terrasect.lookup

import java.util.IdentityHashMap
import net.minecraft.core.HolderLookup
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import terrasect.definition.SelectionConstraints

/**
 * Precomputed region×entry allow/deny table for a [SelectionConstraints]-gated registry (mobs, loot
 * items, ...). Shared by [CompiledMobLookup] and [CompiledLootLookup], which previously duplicated
 * this build/lookup logic verbatim aside from the registry type.
 */
class RegionSelectionTable<T : Any>
private constructor(private val decisions: IdentityHashMap<Region, IdentityHashMap<T, Boolean>>) {
  fun allows(region: Region, entry: T): Boolean {
    val regionDecisions = decisions[region] ?: return true
    return regionDecisions[entry] ?: true
  }

  companion object {
    fun <T : Any> build(
      root: Region,
      registryLookup: HolderLookup<T>,
      selectionOf: (Region) -> SelectionConstraints?,
    ): RegionSelectionTable<T>? {
      val constrainedRegions = ArrayList<Region>()
      collectConstrainedRegions(root, selectionOf, constrainedRegions)
      if (constrainedRegions.isEmpty()) return null

      val index = buildIndex(registryLookup)
      val decisions = IdentityHashMap<Region, IdentityHashMap<T, Boolean>>()
      for (region in constrainedRegions) {
        val selection = selectionOf(region) ?: continue
        val regionDecisions = IdentityHashMap<T, Boolean>()
        index.forEach { (entry, meta) ->
          regionDecisions[entry] = selection.evaluate(meta.id, meta.tags)
        }
        decisions[region] = regionDecisions
      }
      return RegionSelectionTable(decisions)
    }

    private fun collectConstrainedRegions(
      region: Region,
      selectionOf: (Region) -> SelectionConstraints?,
      out: MutableList<Region>,
    ) {
      if (selectionOf(region) != null) out += region
      region.children.forEach { collectConstrainedRegions(it, selectionOf, out) }
    }

    private fun <T : Any> buildIndex(
      registryLookup: HolderLookup<T>
    ): IdentityHashMap<T, EntryMeta> {
      val index = IdentityHashMap<T, EntryMeta>()
      registryLookup.listElements().forEach { holder ->
        val id = ResourceKeyCompat.getKeyId(holder.key())
        val tags = HashSet<String>()
        holder.tags().forEach { tag -> tags.add(tag.location().toString()) }
        index[holder.value()] = EntryMeta(id, tags)
      }
      return index
    }
  }
}

private data class EntryMeta(val id: String, val tags: Set<String>)
