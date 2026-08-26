package terrasect.lookup

import java.util.IdentityHashMap
import net.minecraft.core.Holder
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import terrasect.compat.ResourceKeyCompat
import terrasect.compat.TerraBlenderCompat
import terrasect.definition.Region
import terrasect.handler.NoiseLogger

private val log = NoiseLogger.registry

data class BiomeEntry(val id: String?, val tags: Set<String>)

class CompiledBiomeLookup
internal constructor(
  private val decisions: IdentityHashMap<Region, IdentityHashMap<Biome, Boolean>>,
  private val filteredParameters: IdentityHashMap<Region, Climate.ParameterList<Holder<Biome>>> =
    IdentityHashMap(),
) {
  fun isConstrained(region: Region): Boolean = decisions.containsKey(region)

  fun isAdmitted(region: Region, biome: Biome): Boolean {
    val regionDecisions = decisions[region] ?: return true
    return regionDecisions[biome] ?: true
  }

  fun select(region: Region, target: Climate.TargetPoint): Holder<Biome>? =
    filteredParameters[region]?.findValue(target)

  internal fun decision(region: Region, biome: Biome): Boolean? = decisions[region]?.get(biome)

  internal companion object {
    fun build(
      root: Region,
      parameters: Climate.ParameterList<Holder<Biome>>?,
      dimensionId: String,
    ): CompiledBiomeLookup? {
      val biomeRegions = ArrayList<Region>()
      collectBiomeRegions(root, biomeRegions)
      if (biomeRegions.isEmpty()) {
        log.debug { "build: no biome-constrained regions under root=${root.name}" }
        return null
      }
      if (parameters == null) {
        log.warn {
          "build: biome constraints under root=${root.name} in dimension=$dimensionId have no " +
            "MultiNoise parameter list; vanilla biome selection will be retained"
        }
        return null
      }
      val resolvedParameters = TerraBlenderCompat.expandBiomeParameters(parameters)
      val biomeIndex = buildBiomeIndex(resolvedParameters)
      val lookup = compile(biomeRegions, resolvedParameters, biomeIndex)
      log.debug {
        "build: ${lookup.size} biome-constrained region(s) under root=${root.name}; ${biomeIndex.size} biomes indexed"
      }
      for (region in biomeRegions) {
        if (!lookup.hasCandidate(region)) {
          log.warn {
            "build: biome constraint for region=${region.name} in dimension=$dimensionId " +
              "admits no biome entries from the MultiNoise parameter list; vanilla selection " +
              "will be retained for that region"
          }
        }
      }
      return lookup
    }

    internal fun compile(
      biomeRegions: List<Region>,
      parameters: Climate.ParameterList<Holder<Biome>>,
      biomeIndex: IdentityHashMap<Biome, BiomeEntry>,
    ): CompiledBiomeLookup {
      return CompiledBiomeLookup(
        collectDecisions(biomeRegions, biomeIndex),
        collectFilteredParameters(biomeRegions, parameters, biomeIndex),
      )
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

    private fun collectFilteredParameters(
      biomeRegions: List<Region>,
      parameters: Climate.ParameterList<Holder<Biome>>,
      biomeIndex: IdentityHashMap<Biome, BiomeEntry>,
    ): IdentityHashMap<Region, Climate.ParameterList<Holder<Biome>>> {
      val filtered = IdentityHashMap<Region, Climate.ParameterList<Holder<Biome>>>()
      for (region in biomeRegions) {
        val constraints = region.biomes ?: continue
        val values =
          parameters.values().filter { value ->
            val entry = biomeIndex[value.second.value()]
            entry != null && constraints.evaluate(entry.id, entry.tags)
          }
        if (values.isNotEmpty()) {
          filtered[region] = Climate.ParameterList(values)
        }
      }
      return filtered
    }

    private fun buildBiomeIndex(
      parameters: Climate.ParameterList<Holder<Biome>>
    ): IdentityHashMap<Biome, BiomeEntry> {
      val index = IdentityHashMap<Biome, BiomeEntry>()
      for (value in parameters.values()) {
        val holder = value.second
        if (index.containsKey(holder.value())) continue
        val key = holder.unwrapKey().orElse(null)
        val id = key?.let { ResourceKeyCompat.getKeyId(it) }
        val tags = tagsOf(holder)
        index[holder.value()] = BiomeEntry(id, tags)
      }
      return index
    }

    private fun collectBiomeRegions(region: Region, biomeRegions: MutableList<Region>) {
      if (region.biomes?.hasRules() == true) {
        biomeRegions += region
      }
      region.children.forEach { collectBiomeRegions(it, biomeRegions) }
    }
  }

  private val size: Int
    get() = decisions.size

  private fun hasCandidate(region: Region): Boolean = filteredParameters.containsKey(region)
}
