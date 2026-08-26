package terrasect.lookup

import com.mojang.datafixers.util.Pair as MojangPair
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import net.minecraft.core.Holder
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import terrasect.compat.ResourceKeyCompat
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
      val resolvedParameters = expandProviderParameters(parameters)
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

    private fun expandProviderParameters(
      parameters: Climate.ParameterList<Holder<Biome>>
    ): Climate.ParameterList<Holder<Biome>> {
      val treeCountMethod =
        parameters.javaClass.methods.firstOrNull {
          it.name == "getTreeCount" && it.parameterCount == 0
        } ?: return parameters
      val treeMethod =
        parameters.javaClass.methods.firstOrNull {
          it.name == "getTree" &&
            it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        } ?: return parameters
      return try {
        val values = ArrayList<MojangPair<Climate.ParameterPoint, Holder<Biome>>>()
        val treeCount = treeCountMethod.invoke(parameters) as Int
        for (treeIndex in 0 until treeCount) {
          val tree = treeMethod.invoke(parameters, treeIndex) ?: continue
          collectTreeValues(tree, values)
        }
        if (values.isEmpty()) parameters else Climate.ParameterList(values)
      } catch (exception: Exception) {
        log.warn {
          "build: could not read positional biome provider parameters from ${parameters.javaClass.name}; " +
            "vanilla parameter entries will be used (${exception.javaClass.simpleName})"
        }
        parameters
      }
    }

    private fun collectTreeValues(
      tree: Any,
      values: MutableList<MojangPair<Climate.ParameterPoint, Holder<Biome>>>,
    ) {
      val rootField =
        findField(tree.javaClass) { !Modifier.isStatic(it.modifiers) && it.name == "root" }
          ?: error("missing biome provider tree root")
      rootField.makeAccessible()
      collectTreeNode(rootField.get(tree), values)
    }

    private fun collectTreeNode(
      node: Any,
      values: MutableList<MojangPair<Climate.ParameterPoint, Holder<Biome>>>,
    ) {
      val childField =
        findField(node.javaClass) {
          it.type.isArray && it.type.componentType.isAssignableFrom(node.javaClass)
        }
      if (childField != null) {
        childField.makeAccessible()
        val children = childField.get(node)
        for (index in 0 until ReflectArray.getLength(children)) {
          collectTreeNode(ReflectArray.get(children, index), values)
        }
        return
      }

      val parameterField =
        findField(node.javaClass) {
          it.type.isArray && !it.type.componentType.isAssignableFrom(node.javaClass)
        } ?: error("missing biome provider leaf parameters")
      val valueField =
        node.javaClass.declaredFields.firstOrNull {
          !Modifier.isStatic(it.modifiers) && !it.type.isPrimitive && !it.type.isArray
        } ?: error("missing biome provider leaf value")
      parameterField.makeAccessible()
      valueField.makeAccessible()
      val parameters = parameterField.get(node)
      if (ReflectArray.getLength(parameters) < 7) {
        return
      }
      @Suppress("UNCHECKED_CAST") val holder = valueField.get(node) as? Holder<Biome> ?: return
      val point =
        Climate.ParameterPoint(
          ReflectArray.get(parameters, 0) as Climate.Parameter,
          ReflectArray.get(parameters, 1) as Climate.Parameter,
          ReflectArray.get(parameters, 2) as Climate.Parameter,
          ReflectArray.get(parameters, 3) as Climate.Parameter,
          ReflectArray.get(parameters, 4) as Climate.Parameter,
          ReflectArray.get(parameters, 5) as Climate.Parameter,
          (ReflectArray.get(parameters, 6) as Climate.Parameter).min(),
        )
      values += MojangPair(point, holder)
    }

    private fun findField(type: Class<*>, predicate: (Field) -> Boolean): Field? {
      var current: Class<*>? = type
      while (current != null) {
        current.declaredFields.firstOrNull(predicate)?.let {
          return it
        }
        current = current.superclass
      }
      return null
    }

    private fun Field.makeAccessible() {
      if (!trySetAccessible()) {
        error("cannot access biome provider field ${name}")
      }
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
        val tags = HashSet<String>()
        holder.tags().forEach { tag -> tags.add(tag.location().toString()) }
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
