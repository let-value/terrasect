package terrasect.lookup

import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EntityType
import terrasect.definition.Region
import terrasect.handler.NoiseLogger

private val log = NoiseLogger.registry

class CompiledMobLookup
private constructor(private val table: RegionSelectionTable<EntityType<*>>) {
  fun allowSpawn(region: Region, entityType: EntityType<*>): Boolean =
    table.allows(region, entityType)

  companion object {
    fun build(root: Region, registry: RegistryAccess.Frozen): CompiledMobLookup? {
      val table =
        RegionSelectionTable.build(root, registry.lookupOrThrow(Registries.ENTITY_TYPE)) { it.mobs }
      if (table == null) {
        log.debug { "build: no mob-constrained regions under root=${root.name}" }
        return null
      }
      log.debug { "build: mob-constrained regions compiled under root=${root.name}" }
      return CompiledMobLookup(table)
    }
  }
}
