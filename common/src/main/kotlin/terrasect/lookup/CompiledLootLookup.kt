package terrasect.lookup

import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import terrasect.definition.Region
import terrasect.handler.NoiseLogger

private val log = NoiseLogger.registry

class CompiledLootLookup private constructor(private val table: RegionSelectionTable<Item>) {
  fun allowDrop(region: Region, item: Item): Boolean = table.allows(region, item)

  companion object {
    fun build(root: Region, registry: RegistryAccess.Frozen): CompiledLootLookup? {
      val table =
        RegionSelectionTable.build(root, registry.lookupOrThrow(Registries.ITEM)) { it.loot }
      if (table == null) {
        log.debug { "build: no loot-constrained regions under root=${root.name}" }
        return null
      }
      log.debug { "build: loot-constrained regions compiled under root=${root.name}" }
      return CompiledLootLookup(table)
    }
  }
}
