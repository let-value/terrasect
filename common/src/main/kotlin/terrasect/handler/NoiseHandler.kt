package terrasect.handler

import net.minecraft.world.level.levelgen.NoiseRouter
import terrasect.ChunkAccessExtender
import terrasect.NoiseChunkExtender
import terrasect.generation.ChunkContext
import terrasect.helpers.ChunkDensityFunction

object NoiseHandler {
  @JvmField val pendingChunk: ThreadLocal<ChunkAccessExtender?> = ThreadLocal()

  @JvmStatic
  fun wrapNoiseRouter(router: NoiseRouter, noiseChunk: NoiseChunkExtender): NoiseRouter {
    return NoiseRouter(
            ChunkDensityFunction(router.barrierNoise, "barrierNoise", noiseChunk),
            ChunkDensityFunction(
                router.fluidLevelFloodednessNoise,
                "fluidLevelFloodednessNoise",
                noiseChunk,
            ),
            ChunkDensityFunction(
                router.fluidLevelSpreadNoise,
                "fluidLevelSpreadNoise",
                noiseChunk,
                scale = 16, // sampled at floorDiv(x,16)
            ),
            ChunkDensityFunction(
                router.lavaNoise,
                "lavaNoise",
                noiseChunk,
                scale = 64, // sampled at floorDiv(x,64)
            ),
            ChunkDensityFunction(router.temperature, "temperature", noiseChunk),
            ChunkDensityFunction(router.vegetation, "vegetation", noiseChunk),
            ChunkDensityFunction(router.continents, "continents", noiseChunk),
            ChunkDensityFunction(router.erosion, "erosion", noiseChunk),
            ChunkDensityFunction(router.depth, "depth", noiseChunk),
            ChunkDensityFunction(router.ridges, "ridges", noiseChunk),
            ChunkDensityFunction(
                router.preliminarySurfaceLevel,
                "preliminarySurfaceLevel",
                noiseChunk,
            ),
            ChunkDensityFunction(router.finalDensity, "finalDensity", noiseChunk),
            ChunkDensityFunction(router.veinToggle, "veinToggle", noiseChunk),
            ChunkDensityFunction(router.veinRidged, "veinRidged", noiseChunk),
            ChunkDensityFunction(router.veinGap, "veinGap", noiseChunk),
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
    val constraints = chunk.context?.noiseRegistry?.get(region) ?: return null
    val transform = constraints.densityFunctions[key] ?: return null

    val blendWidth = chunk.context!!.noiseRegistry!!.getBlendWidth(region)
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
