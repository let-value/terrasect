package terrasect.lookup

import terrasect.definition.NoiseConstraints
import terrasect.definition.Region

class CompiledNoiseRegistry
private constructor(
    private val constraints: IdentityHashMap<Region, NoiseConstraints>,
    private val blendWidths: IdentityHashMap<Region, Float>,
) {
  fun get(region: Region): NoiseConstraints? = constraints[region]

  fun getBlendWidth(region: Region): Float =
      blendWidths[region] ?: NoiseConstraints.DEFAULT_BLEND_WIDTH

  fun isEmpty(): Boolean = constraints.isEmpty()

  companion object {
    fun build(root: Region): CompiledNoiseRegistry? {
      val map = IdentityHashMap<Region, NoiseConstraints>()
      val widths = IdentityHashMap<Region, Float>()
      collectRecursively(root, map, widths)
      return if (map.isEmpty()) null else CompiledNoiseRegistry(map, widths)
    }

    private fun collectRecursively(
        region: Region,
        map: IdentityHashMap<Region, NoiseConstraints>,
        widths: IdentityHashMap<Region, Float>,
    ) {
      if (!map.containsKey(region)) {
        val noise = region.noise
        if (noise != null && noise.hasAnyConstraints()) {
          map[region] = noise
          widths[region] = noise.blendWidth
        }
      }
      for (child in region.children) {
        collectRecursively(child, map, widths)
      }
    }
  }
}

private typealias IdentityHashMap<K, V> = java.util.IdentityHashMap<K, V>
