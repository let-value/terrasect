package terrasect.lookup

import java.util.IdentityHashMap
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EntityType
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import terrasect.handler.NoiseLogger

private val log = NoiseLogger.registry

class CompiledMobLookup
private constructor(
  private val decisions: IdentityHashMap<Region, IdentityHashMap<EntityType<*>, Boolean>>
) {
  fun allowSpawn(region: Region, entityType: EntityType<*>): Boolean {
    return decisions[region]?.get(entityType) ?: true
  }

  companion object {
    fun build(root: Region, registry: RegistryAccess.Frozen): CompiledMobLookup? {
      val mobRegions = ArrayList<Region>()
      collectMobRegions(root, mobRegions)
      if (mobRegions.isEmpty()) {
        log.debug { "build: no mob-constrained regions under root=${root.name}" }
        return null
      }
      val entries =
        registry
          .lookupOrThrow(Registries.ENTITY_TYPE)
          .listElements()
          .map { holder ->
            Triple(holder.value(), ResourceKeyCompat.getKeyId(holder.key()), tagsOf(holder))
          }
          .toList()
      val decisions = IdentityHashMap<Region, IdentityHashMap<EntityType<*>, Boolean>>()
      for (region in mobRegions) {
        val selection = checkNotNull(region.mobs)
        decisions[region] =
          IdentityHashMap<EntityType<*>, Boolean>().apply {
            for ((value, id, tags) in entries) put(value, selection.evaluate(id, tags))
          }
      }
      log.debug {
        "build: ${decisions.size} mob-constrained region(s) under root=${root.name}; ${entries.size} entity types indexed"
      }
      return CompiledMobLookup(decisions)
    }

    private fun collectMobRegions(region: Region, mobRegions: MutableList<Region>) {
      if (region.mobs != null) {
        mobRegions += region
      }
      region.children.forEach { collectMobRegions(it, mobRegions) }
    }
  }
}
