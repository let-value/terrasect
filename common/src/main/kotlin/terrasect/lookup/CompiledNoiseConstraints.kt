package terrasect.lookup

import java.util.IdentityHashMap
import terrasect.definition.NoiseConstraints
import terrasect.definition.Region
import terrasect.handler.NoiseLogger
import terrasect.helpers.NoiseTransform

private val log = NoiseLogger.registry

class ResolvedNoise(@JvmField val transform: NoiseTransform, @JvmField val blendWidth: Float)

class NoiseBinding(private val table: IdentityHashMap<Region, ResolvedNoise>) {
  fun get(region: Region): ResolvedNoise? = table[region]
}

class CompiledNoiseRegistry
private constructor(private val constraints: IdentityHashMap<Region, NoiseConstraints>) {
  fun get(region: Region): NoiseConstraints? = constraints[region]

  fun isEmpty(): Boolean = constraints.isEmpty()

  fun size(): Int = constraints.size

  fun bind(key: String, shortKey: String): NoiseBinding? {
    val table = IdentityHashMap<Region, ResolvedNoise>()
    for ((region, noise) in constraints) {
      val transform = noise.resolveTransform(key, shortKey) ?: continue
      table[region] = ResolvedNoise(transform, noise.blendWidth)
    }
    return if (table.isEmpty()) null else NoiseBinding(table)
  }

  companion object {
    fun build(root: Region): CompiledNoiseRegistry? {
      val map = IdentityHashMap<Region, NoiseConstraints>()
      collectRecursively(root, map)
      return if (map.isEmpty()) {
        log.debug { "build: no noise-constrained regions found under root=${root.name}" }
        null
      } else {
        log.debug {
          "build: ${map.size} noise-constrained region(s) under root=${root.name}: ${map.keys.joinToString { it.name }}"
        }
        CompiledNoiseRegistry(map)
      }
    }

    private fun collectRecursively(
      region: Region,
      map: IdentityHashMap<Region, NoiseConstraints>,
    ) {
      if (!map.containsKey(region)) {
        val noise = region.noise
        if (noise != null && noise.hasAnyConstraints()) {
          map[region] = noise
          log.debug {
            "collected region=${region.name} densityFunctions=[${noise.densityFunctions.keys.joinToString()}] noises=[${noise.noises.keys.joinToString()}] blendWidth=${noise.blendWidth}"
          }
        }
      }
      for (child in region.children) {
        collectRecursively(child, map)
      }
    }
  }
}
