package terrasect.handler

import net.minecraft.world.level.levelgen.DensityFunction
import net.minecraft.world.level.levelgen.NoiseRouter
import terrasect.compat.NoiseRouterCompat
import terrasect.extender.ChunkAccessExtender
import terrasect.generation.ChunkContext
import terrasect.helpers.ChunkDensityFunction
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent

private val instr = TerrasectInstr.noise

object NoiseHandler {
  private val pendingNoiseChunkCreation = ThreadLocal<ChunkAccessExtender?>()

  @JvmStatic
  fun beginNoiseChunkCreation(chunk: ChunkAccessExtender) {
    pendingNoiseChunkCreation.set(chunk)
  }

  @JvmStatic
  fun endNoiseChunkCreation() {
    pendingNoiseChunkCreation.remove()
  }

  @JvmStatic fun getNoiseChunkCreation(): ChunkAccessExtender? = pendingNoiseChunkCreation.get()

  @JvmStatic
  fun wrapNoiseRouter(router: NoiseRouter, chunk: ChunkContext?): NoiseRouter =
    wrapRouter(router, chunk)

  @JvmStatic
  fun wrapClimateSamplerRouter(router: NoiseRouter, chunk: ChunkContext?): NoiseRouter =
    wrapRouter(router, chunk)

  @JvmStatic
  fun wrapDensity(function: DensityFunction, key: String?, chunk: ChunkContext?): DensityFunction {
    if (key == null || function is ChunkDensityFunction) return function
    if (chunk == null) {
      instr.count(TerrasectMetricEvent.NOISE_CHUNK_MISSING, "noise_key") { key }
      return function
    }
    return wrap(function, key, chunk, 1)
  }

  @JvmStatic
  fun getStrength(blendWidth: Float, sdfDist: Float): Float {
    if (blendWidth <= 0f) return if (sdfDist < 0f) 1f else 0f
    if (sdfDist >= 0f) return 0f
    return (-sdfDist / blendWidth).coerceAtMost(1f)
  }

  private fun wrapRouter(router: NoiseRouter, chunk: ChunkContext?): NoiseRouter {
    if (chunk == null) {
      instr.count(TerrasectMetricEvent.NOISE_CHUNK_MISSING)
      return router
    }

    instr.count(TerrasectMetricEvent.NOISE_ROUTER_WRAP)

    return NoiseRouter(
      wrapDensityFunction(router.barrierNoise, "barrierNoise", chunk),
      wrapDensityFunction(router.fluidLevelFloodednessNoise, "fluidLevelFloodednessNoise", chunk),
      wrapDensityFunction(router.fluidLevelSpreadNoise, "fluidLevelSpreadNoise", chunk, scale = 16),
      wrapDensityFunction(router.lavaNoise, "lavaNoise", chunk, scale = 64),
      wrapDensityFunction(router.temperature, "temperature", chunk),
      wrapDensityFunction(router.vegetation, "vegetation", chunk),
      wrapDensityFunction(router.continents, "continents", chunk),
      wrapDensityFunction(router.erosion, "erosion", chunk),
      wrapDensityFunction(router.depth, "depth", chunk),
      wrapDensityFunction(router.ridges, "ridges", chunk),
      wrapDensityFunction(
        NoiseRouterCompat.surfaceFunction(router),
        NoiseRouterCompat.SURFACE_FUNCTION_KEY,
        chunk,
      ),
      wrapDensityFunction(router.finalDensity, "finalDensity", chunk),
      wrapDensityFunction(router.veinToggle, "veinToggle", chunk),
      wrapDensityFunction(router.veinRidged, "veinRidged", chunk),
      wrapDensityFunction(router.veinGap, "veinGap", chunk),
    )
  }

  private fun wrapDensityFunction(
    function: DensityFunction,
    key: String,
    chunk: ChunkContext,
    scale: Int = 1,
  ): DensityFunction = wrap(function, key, chunk, scale)

  private fun wrap(
    function: DensityFunction,
    key: String,
    chunk: ChunkContext,
    scale: Int,
  ): DensityFunction {
    if (function is ChunkDensityFunction) return function
    val registry = chunk.dimensionContext?.noiseRegistry ?: return function
    val binding = registry.bind(key, key.substringAfterLast('/')) ?: return function
    instr.count(TerrasectMetricEvent.NOISE_FUNCTION_WRAP, "noise_key") { key }
    return ChunkDensityFunction(function, binding, chunk, scale)
  }
}
