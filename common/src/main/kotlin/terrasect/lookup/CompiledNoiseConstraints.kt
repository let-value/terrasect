package terrasect.lookup

import org.slf4j.LoggerFactory
import terrasect.definition.NoiseConstraints
import terrasect.definition.Region

private val LOGGER = LoggerFactory.getLogger("Terrasect/CompiledNoiseRegistry")

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
        LOGGER.info(
          "[NC-Registry] build: no noise-constrained regions found under root={}",
          root.name,
        )
        null
      } else {
        LOGGER.info(
          "[NC-Registry] build: {} noise-constrained region(s) under root={}: {}",
          map.size,
          root.name,
          map.keys.joinToString { it.name },
        )
        CompiledNoiseRegistry(map)
      }
    }

    private fun collectRecursively(region: Region, map: IdentityHashMap<Region, NoiseConstraints>) {
      if (!map.containsKey(region)) {
        val noise = region.noise
        if (noise != null && noise.hasAnyConstraints()) {
          map[region] = noise
          LOGGER.info(
            "[NC-Registry] collected region={} densityFunctions=[{}] noises=[{}] blendWidth={}",
            region.name,
            noise.densityFunctions.keys.joinToString(),
            noise.noises.keys.joinToString(),
            noise.blendWidth,
          )
        }
      }
      for (child in region.children) {
        collectRecursively(child, map)
      }
    }
  }
}

private typealias IdentityHashMap<K, V> = java.util.IdentityHashMap<K, V>
