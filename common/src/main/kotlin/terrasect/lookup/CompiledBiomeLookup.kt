package terrasect.lookup

import java.util.IdentityHashMap
import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.biome.Biome
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import terrasect.handler.NoiseLogger

private val log = NoiseLogger.registry

data class BiomeEntry(val id: String, val tags: Set<String>)

class CompiledBiomeLookup
internal constructor(
  private val decisions: IdentityHashMap<Region, IdentityHashMap<Biome, Boolean>>
) {
  fun isAdmitted(region: Region, biome: Biome): Boolean {
    val regionDecisions = decisions[region] ?: return true
    return regionDecisions[biome] ?: true
  }

  internal fun decision(region: Region, biome: Biome): Boolean? = decisions[region]?.get(biome)

  internal companion object {
    fun build(root: Region, registry: RegistryAccess.Frozen): CompiledBiomeLookup? {
      val biomeRegions = ArrayList<Region>()
      collectBiomeRegions(root, biomeRegions)
      if (biomeRegions.isEmpty()) {
        log.debug { "build: no biome-constrained regions under root=${root.name}" }
        return null
      }
      val biomeIndex = buildBiomeIndex(registry)
      val decisions = collectDecisions(biomeRegions, biomeIndex)
      log.debug {
        "build: ${decisions.size} biome-constrained region(s) under root=${root.name}; ${biomeIndex.size} biomes indexed"
      }
      return CompiledBiomeLookup(decisions)
    }

    fun collectDecisions(
      biomeRegions: List<Region>,
      biomeIndex: IdentityHashMap<Biome, BiomeEntry>,
    ): IdentityHashMap<Region, IdentityHashMap<Biome, Boolean>> {
      val map = IdentityHashMap<Region, IdentityHashMap<Biome, Boolean>>()
      for (region in biomeRegions) {
        val biomes = region.biomes ?: continue
        val regionDecisions = IdentityHashMap<Biome, Boolean>()
        biomeIndex.forEach { (biome, entry) ->
          regionDecisions[biome] = biomes.evaluate(entry.id, entry.tags)
        }
        map[region] = regionDecisions
      }
      return map
    }

    internal fun biomesFrom(registry: RegistryAccess.Frozen): List<Holder<Biome>> =
      registry.lookupOrThrow(Registries.BIOME).listElements().toList()

    private fun buildBiomeIndex(
      registry: RegistryAccess.Frozen
    ): IdentityHashMap<Biome, BiomeEntry> {
      val index = IdentityHashMap<Biome, BiomeEntry>()
      for (holder in biomesFrom(registry)) {
        val id = ResourceKeyCompat.getKeyId(holder.unwrapKey().get())
        val tags = HashSet<String>()
        holder.tags().forEach { tag -> tags.add(tag.toString()) }
        index[holder.value()] = BiomeEntry(id, tags)
      }
      return index
    }

    private fun collectBiomeRegions(region: Region, biomeRegions: MutableList<Region>) {
      if (region.biomes != null) {
        biomeRegions += region
      }
      region.children.forEach { collectBiomeRegions(it, biomeRegions) }
    }
  }
}
