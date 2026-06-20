package terrasect.lookup

import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import terrasect.handler.NoiseLogger

private val log = NoiseLogger.registry

class CompiledLootLookup
private constructor(
  private val decisions: java.util.IdentityHashMap<Region, java.util.IdentityHashMap<Item, Boolean>>
) {
  fun allowDrop(region: Region, item: Item): Boolean {
    val regionDecisions = decisions[region] ?: return true
    return regionDecisions[item] ?: true
  }

  companion object {
    fun build(root: Region, registry: RegistryAccess.Frozen): CompiledLootLookup? {
      val lootRegions = ArrayList<Region>()
      collectLootRegions(root, lootRegions)
      if (lootRegions.isEmpty()) {
        log.debug { "build: no loot-constrained regions under root=${root.name}" }
        return null
      }
      val itemIndex = buildItemIndex(registry)
      val decisions = collectDecisions(lootRegions, itemIndex)
      log.debug {
        "build: ${decisions.size} loot-constrained region(s) under root=${root.name}; ${itemIndex.size} items indexed"
      }
      return CompiledLootLookup(decisions)
    }

    private fun collectLootRegions(region: Region, lootRegions: MutableList<Region>) {
      if (region.loot != null) {
        lootRegions += region
      }
      region.children.forEach { collectLootRegions(it, lootRegions) }
    }

    private fun collectDecisions(
      lootRegions: List<Region>,
      itemIndex: java.util.IdentityHashMap<Item, ItemEntry>,
    ): java.util.IdentityHashMap<Region, java.util.IdentityHashMap<Item, Boolean>> {
      val map = java.util.IdentityHashMap<Region, java.util.IdentityHashMap<Item, Boolean>>()
      for (region in lootRegions) {
        val loot = region.loot ?: continue
        val regionDecisions = java.util.IdentityHashMap<Item, Boolean>()
        itemIndex.forEach { (item, entry) ->
          regionDecisions[item] = loot.evaluate(entry.id, entry.tags)
        }
        map[region] = regionDecisions
      }
      return map
    }

    private fun buildItemIndex(
      registry: RegistryAccess.Frozen
    ): java.util.IdentityHashMap<Item, ItemEntry> {
      val index = java.util.IdentityHashMap<Item, ItemEntry>()
      val itemRegistry = registry.lookupOrThrow(Registries.ITEM)
      itemRegistry.listElements().forEach { holder ->
        val id = ResourceKeyCompat.getKeyId(holder.key())
        val tags = HashSet<String>()
        holder.tags().forEach { tag -> tags.add(tag.location().toString()) }
        index[holder.value()] = ItemEntry(id, tags)
      }
      return index
    }
  }
}

private data class ItemEntry(val id: String, val tags: Set<String>)
