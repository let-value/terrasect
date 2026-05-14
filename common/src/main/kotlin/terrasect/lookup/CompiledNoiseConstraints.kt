package terrasect.lookup

import terrasect.definition.NoiseConstraints
import terrasect.definition.Region
import terrasect.handler.NoiseScope

class CompiledNoiseRegistry
private constructor(private val constraints: IdentityHashMap<Region, NoiseConstraints>) {
  fun get(region: Region): NoiseConstraints? = constraints[region]

  fun isEmpty(): Boolean = constraints.isEmpty()

  fun size(): Int = constraints.size

  companion object {
    fun build(root: Region): CompiledNoiseRegistry? {
      val map = IdentityHashMap<Region, NoiseConstraints>()
      collectRecursively(root, map)
      return if (map.isEmpty()) {
        NoiseScope.registry.debug {
          "[NC-Registry] build: no noise-constrained regions found under root=${root.name}"
        }
        null
      } else {
        NoiseScope.registry.debug {
          "[NC-Registry] build: ${map.size} noise-constrained region(s) under root=${root.name}: ${map.keys.joinToString { it.name }}"
        }
        CompiledNoiseRegistry(map)
      }
    }

    private fun collectRecursively(region: Region, map: IdentityHashMap<Region, NoiseConstraints>) {
      if (!map.containsKey(region)) {
        val noise = region.noise
        if (noise != null && noise.hasAnyConstraints()) {
          map[region] = noise
          NoiseScope.registry.debug {
            "[NC-Registry] collected region=${region.name} densityFunctions=[${noise.densityFunctions.keys.joinToString()}] noises=[${noise.noises.keys.joinToString()}] blendWidth=${noise.blendWidth}"
          }
        }
      }
      for (child in region.children) {
        collectRecursively(child, map)
      }
    }
  }
}

private typealias IdentityHashMap<K, V> = java.util.IdentityHashMap<K, V>
