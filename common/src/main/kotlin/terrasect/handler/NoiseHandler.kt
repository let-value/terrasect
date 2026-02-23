package terrasect.handler

import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.levelgen.DensityFunction
import terrasect.cache.ChunkCache

object NoiseHandler {

  @JvmStatic
  fun modifyDensityValue(
      key: ResourceKey<DensityFunction>,
      original: Double,
      blockX: Int,
      blockZ: Int,
      cache: ChunkCache,
  ): Double {
    val constraintCache = cache.noiseConstraintCache ?: return original
    val constraints = constraintCache.getConstraints(blockX, blockZ) ?: return original
    val transform = constraints.densityFunctions[key.identifier().toString()] ?: return original

    val strength = constraintCache.getStrength(blockX, blockZ)
    if (strength <= 0f) return original

    val transformed = transform.apply(original)
    if (strength >= 1f) return transformed

    return original + (transformed - original) * strength
  }
}
