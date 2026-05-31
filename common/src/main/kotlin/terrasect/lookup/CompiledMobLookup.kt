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
      if (!anyRegionHasMobs(root)) {
        log.debug { "build: no mob-constrained regions under root=${root.name}" }
        return null
      }
      val entityTypeIndex = buildEntityTypeIndex(registry)
      val decisions = collectDecisions(root, entityTypeIndex)
      log.debug {
        "build: ${decisions.size} mob-constrained region(s) under root=${root.name}; ${entityTypeIndex.size} entity types indexed"
      }
      return CompiledMobLookup(decisions)
    }

    private fun collectDecisions(
      root: Region,
      entityTypeIndex: java.util.IdentityHashMap<EntityType<*>, EntityTypeEntry>,
    ): java.util.IdentityHashMap<Region, java.util.IdentityHashMap<EntityType<*>, Boolean>> {
      val map =
        java.util.IdentityHashMap<Region, java.util.IdentityHashMap<EntityType<*>, Boolean>>()
      val queue = ArrayDeque<Region>()
      queue.add(root)
      while (queue.isNotEmpty()) {
        val region = queue.removeFirst()
        val mobs = region.mobs
        if (mobs != null) {
          val regionDecisions = java.util.IdentityHashMap<EntityType<*>, Boolean>()
          entityTypeIndex.forEach { (entityType, entry) ->
            regionDecisions[entityType] = mobs.evaluate(entry.id, entry.tags)
          }
          map[region] = regionDecisions
        }
        queue.addAll(region.children)
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

    private fun anyRegionHasMobs(region: Region): Boolean {
      if (region.mobs != null) return true
      return region.children.any { anyRegionHasMobs(it) }
    }
  }
}

private data class EntityTypeEntry(val id: String, val tags: Set<String>)
