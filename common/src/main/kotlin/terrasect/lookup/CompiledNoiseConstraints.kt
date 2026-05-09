package terrasect.lookup

import terrasect.definition.NoiseConstraints
import terrasect.definition.Region

class CompiledNoiseRegistry
private constructor(private val constraints: IdentityHashMap<Region, NoiseConstraints>) {
  fun get(region: Region): NoiseConstraints? = constraints[region]

  fun isEmpty(): Boolean = constraints.isEmpty()

  companion object {
    fun build(root: Region): CompiledNoiseRegistry? {
      val map = IdentityHashMap<Region, NoiseConstraints>()
      collectRecursively(root, map)
      return if (map.isEmpty()) null else CompiledNoiseRegistry(map)
    }

    private fun collectRecursively(region: Region, map: IdentityHashMap<Region, NoiseConstraints>) {
      if (!map.containsKey(region)) {
        val noise = region.noise
        if (noise != null && noise.hasAnyConstraints()) {
          map[region] = noise
        }
      }
      for (child in region.children) {
        collectRecursively(child, map)
      }
    }
  }
}

private typealias IdentityHashMap<K, V> = java.util.IdentityHashMap<K, V>
