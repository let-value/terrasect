package terrasect.handler

import net.minecraft.world.level.levelgen.NoiseRouter
import terrasect.extender.ChunkAccessExtender
import terrasect.generation.ChunkContext
import terrasect.helpers.ChunkDensityFunction

object NoiseHandler {
  @JvmField val pendingChunk: ThreadLocal<ChunkAccessExtender?> = ThreadLocal()

  @JvmStatic
  fun wrapNoiseRouter(router: NoiseRouter, chunk: ChunkContext): NoiseRouter {
    return NoiseRouter(
        ChunkDensityFunction(router.barrierNoise, "barrierNoise", chunk),
        ChunkDensityFunction(
            router.fluidLevelFloodednessNoise,
            "fluidLevelFloodednessNoise",
            chunk,
        ),
        ChunkDensityFunction(
            router.fluidLevelSpreadNoise,
            "fluidLevelSpreadNoise",
            chunk,
            scale = 16,
        ),
        ChunkDensityFunction(
            router.lavaNoise,
            "lavaNoise",
            chunk,
            scale = 64,
        ),
        ChunkDensityFunction(router.temperature, "temperature", chunk),
        ChunkDensityFunction(router.vegetation, "vegetation", chunk),
        ChunkDensityFunction(router.continents, "continents", chunk),
        ChunkDensityFunction(router.erosion, "erosion", chunk),
        ChunkDensityFunction(router.depth, "depth", chunk),
        ChunkDensityFunction(router.ridges, "ridges", chunk),
        ChunkDensityFunction(
            router.preliminarySurfaceLevel,
            "preliminarySurfaceLevel",
            chunk,
        ),
        ChunkDensityFunction(router.finalDensity, "finalDensity", chunk),
        ChunkDensityFunction(router.veinToggle, "veinToggle", chunk),
        ChunkDensityFunction(router.veinRidged, "veinRidged", chunk),
        ChunkDensityFunction(router.veinGap, "veinGap", chunk),
    )
  }

  @JvmStatic
  fun modifyDensityValue(
      key: String,
      original: Double,
      blockX: Int,
      blockZ: Int,
      chunk: ChunkContext,
  ): Double? {
    val region = chunk.getRegion(blockX, blockZ) ?: return null
    val constraints = chunk.dimensionContext?.noiseRegistry?.get(region) ?: return null
    val transform = constraints.densityFunctions[key] ?: return null

    val blendWidth = chunk.dimensionContext!!.noiseRegistry!!.getBlendWidth(region)
    val sdfDist = chunk.getDistance(blockX, blockZ)

    val strength = getStrength(blendWidth, sdfDist)
    if (strength <= 0f) return original

    val transformed = transform.apply(original)
    if (strength >= 1f) return transformed

    return original + (transformed - original) * strength
  }

  fun getStrength(blendWidth: Float, sdfDist: Float): Float {
    if (blendWidth <= 0f) return if (sdfDist < 0f) 1f else 0f
    if (sdfDist >= 0f) return 0f
    return (-sdfDist / blendWidth).coerceAtMost(1f)
  }
}
