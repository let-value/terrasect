package terrasect.cache

import terrasect.definition.NoiseConstraints
import terrasect.lookup.CompiledNoiseRegistry

class NoiseConstraintCache(
    private val chunkCache: ChunkCache,
    private val registry: CompiledNoiseRegistry,
) {
  fun getConstraints(blockX: Int, blockZ: Int): NoiseConstraints? {
    val region = chunkCache.regions?.get(blockX, blockZ) ?: return null
    return registry.get(region)
  }

  fun getStrength(blockX: Int, blockZ: Int): Float {
    val region = chunkCache.regions?.get(blockX, blockZ) ?: return 0f
    val blendWidth = registry.getBlendWidth(region)
    val sdfDist = chunkCache.getDistance(blockX, blockZ)
    if (blendWidth <= 0f) return if (sdfDist < 0f) 1f else 0f
    if (sdfDist >= 0f) return 0f
    return (-sdfDist / blendWidth).coerceAtMost(1f)
  }
}
