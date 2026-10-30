package terrasect.lookup

import java.util.IdentityHashMap
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import terrasect.handler.NoiseLogger

private val log = NoiseLogger.registry

class CompiledLootLookup
private constructor(
  private val decisions: IdentityHashMap<Region, IdentityHashMap<Item, Boolean>>
) {
  fun allowDrop(region: Region, item: Item): Boolean {
    return decisions[region]?.get(item) ?: true
  }

  companion object {
    fun build(root: Region, registry: RegistryAccess.Frozen): CompiledLootLookup? {
      val lootRegions = ArrayList<Region>()
      collectLootRegions(root, lootRegions)
      if (lootRegions.isEmpty()) {
        log.debug { "build: no loot-constrained regions under root=${root.name}" }
        return null
      }
      val entries =
        registry
          .lookupOrThrow(Registries.ITEM)
          .listElements()
          .map { holder ->
            Triple(holder.value(), ResourceKeyCompat.getKeyId(holder.key()), tagsOf(holder))
          }
          .toList()
      val decisions = IdentityHashMap<Region, IdentityHashMap<Item, Boolean>>()
      for (region in lootRegions) {
        val selection = checkNotNull(region.loot)
        decisions[region] =
          IdentityHashMap<Item, Boolean>().apply {
            for ((value, id, tags) in entries) put(value, selection.evaluate(id, tags))
          }
      }
      log.debug {
        "build: ${decisions.size} loot-constrained region(s) under root=${root.name}; ${entries.size} items indexed"
      }
      return CompiledLootLookup(decisions)
    }

    private fun collectLootRegions(region: Region, lootRegions: MutableList<Region>) {
      if (region.loot != null) {
        lootRegions += region
      }
      region.children.forEach { collectLootRegions(it, lootRegions) }
    }
  }
}
