package terrasect.lookup

import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EntityType
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import terrasect.handler.NoiseLogger

private val log = NoiseLogger.registry

class CompiledMobLookup
private constructor(
  private val decisions:
    java.util.IdentityHashMap<Region, java.util.IdentityHashMap<EntityType<*>, Boolean>>
) {
  fun allowSpawn(region: Region, entityType: EntityType<*>): Boolean {
    val regionDecisions = decisions[region] ?: return true
    return regionDecisions[entityType] ?: true
  }

  companion object {
    fun build(root: Region, registry: RegistryAccess.Frozen): CompiledMobLookup? {
      val mobRegions = ArrayList<Region>()
      collectMobRegions(root, mobRegions)
      if (mobRegions.isEmpty()) {
        log.debug { "build: no mob-constrained regions under root=${root.name}" }
        return null
      }
      val entityTypeIndex = buildEntityTypeIndex(registry)
      val decisions = collectDecisions(mobRegions, entityTypeIndex)
      log.debug {
        "build: ${decisions.size} mob-constrained region(s) under root=${root.name}; ${entityTypeIndex.size} entity types indexed"
      }
      return CompiledMobLookup(decisions)
    }

    private fun collectMobRegions(region: Region, mobRegions: MutableList<Region>) {
      if (region.mobs != null) {
        mobRegions += region
      }
      region.children.forEach { collectMobRegions(it, mobRegions) }
    }

    private fun collectDecisions(
      mobRegions: List<Region>,
      entityTypeIndex: java.util.IdentityHashMap<EntityType<*>, EntityTypeEntry>,
    ): java.util.IdentityHashMap<Region, java.util.IdentityHashMap<EntityType<*>, Boolean>> {
      val map =
        java.util.IdentityHashMap<Region, java.util.IdentityHashMap<EntityType<*>, Boolean>>()
      for (region in mobRegions) {
        val mobs = region.mobs ?: continue
        val regionDecisions = java.util.IdentityHashMap<EntityType<*>, Boolean>()
        entityTypeIndex.forEach { (entityType, entry) ->
          regionDecisions[entityType] = mobs.evaluate(entry.id, entry.tags)
        }
        map[region] = regionDecisions
      }
      return map
    }

    private fun buildEntityTypeIndex(
      registry: RegistryAccess.Frozen
    ): java.util.IdentityHashMap<EntityType<*>, EntityTypeEntry> {
      val index = java.util.IdentityHashMap<EntityType<*>, EntityTypeEntry>()
      val entityTypeRegistry = registry.lookupOrThrow(Registries.ENTITY_TYPE)
      entityTypeRegistry.listElements().forEach { holder ->
        val id = ResourceKeyCompat.getKeyId(holder.key())
        val tags = HashSet<String>()
        holder.tags().forEach { tag -> tags.add(tag.location().toString()) }
        index[holder.value()] = EntityTypeEntry(id, tags)
      }
      return index
    }
  }
}

private data class EntityTypeEntry(val id: String, val tags: Set<String>)
