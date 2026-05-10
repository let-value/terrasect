package terrasect.handler

import java.util.concurrent.atomic.AtomicInteger
import net.minecraft.world.level.levelgen.NoiseRouter
import org.slf4j.LoggerFactory
import terrasect.extender.ChunkAccessExtender
import terrasect.generation.ChunkContext
import terrasect.helpers.ChunkDensityFunction

private val LOGGER = LoggerFactory.getLogger("Terrasect/NoiseHandler")

object NoiseHandler {
  @JvmField val pendingChunk: ThreadLocal<ChunkAccessExtender?> = ThreadLocal()

  private val wrapCount = AtomicInteger()
  private val modifyCallCount = AtomicInteger()
  private val modifyHitCount = AtomicInteger()

  @JvmStatic
  fun wrapNoiseRouter(router: NoiseRouter, chunk: ChunkContext): NoiseRouter {
    val n = wrapCount.incrementAndGet()
    val hasRegistry = chunk.dimensionContext?.noiseRegistry != null
    val dim = chunk.dimensionContext?.dimensionId ?: "null"
    LOGGER.info(
      "[NC-NoiseHandler] wrapNoiseRouter #{}: dim={} hasRegistry={} regionCount={}",
      n,
      dim,
      hasRegistry,
      chunk.dimensionContext?.noiseRegistry?.size() ?: 0,
    )
    return NoiseRouter(
      ChunkDensityFunction(router.barrierNoise, "barrierNoise", chunk),
      ChunkDensityFunction(router.fluidLevelFloodednessNoise, "fluidLevelFloodednessNoise", chunk),
      ChunkDensityFunction(
        router.fluidLevelSpreadNoise,
        "fluidLevelSpreadNoise",
        chunk,
        scale = 16,
      ),
      ChunkDensityFunction(router.lavaNoise, "lavaNoise", chunk, scale = 64),
      ChunkDensityFunction(router.temperature, "temperature", chunk),
      ChunkDensityFunction(router.vegetation, "vegetation", chunk),
      ChunkDensityFunction(router.continents, "continents", chunk),
      ChunkDensityFunction(router.erosion, "erosion", chunk),
      ChunkDensityFunction(router.depth, "depth", chunk),
      ChunkDensityFunction(router.ridges, "ridges", chunk),
      ChunkDensityFunction(router.preliminarySurfaceLevel, "preliminarySurfaceLevel", chunk),
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
    val callNum = modifyCallCount.incrementAndGet()

    val region = chunk.getRegion(blockX, blockZ)
    if (region == null) {
      if (callNum <= 3)
        LOGGER.info("[NC-NoiseHandler] modifyDensityValue #{}: key={} region=NULL", callNum, key)
      return null
    }

    val noiseRegistry = chunk.dimensionContext?.noiseRegistry
    if (noiseRegistry == null) {
      if (callNum <= 3)
        LOGGER.info(
          "[NC-NoiseHandler] modifyDensityValue #{}: key={} region={} noiseRegistry=NULL",
          callNum,
          key,
          region.name,
        )
      return null
    }

    val constraints = noiseRegistry.get(region)
    if (constraints == null) {
      if (callNum <= 3)
        LOGGER.info(
          "[NC-NoiseHandler] modifyDensityValue #{}: key={} region={} constraints=NULL (region not in registry)",
          callNum,
          key,
          region.name,
        )
      return null
    }

    val transform = constraints.densityFunctions[key] ?: constraints.noises[key]
    if (transform == null) {
      if (callNum <= 3)
        LOGGER.info(
          "[NC-NoiseHandler] modifyDensityValue #{}: key={} region={} no transform for this key",
          callNum,
          key,
          region.name,
        )
      return null
    }

    val blendWidth = constraints.blendWidth
    val sdfDist = chunk.getDistance(blockX, blockZ)
    val strength = getStrength(blendWidth, sdfDist)

    if (strength <= 0f) {
      if (callNum <= 3)
        LOGGER.info(
          "[NC-NoiseHandler] modifyDensityValue #{}: key={} region={} strength=0 sdfDist={}",
          callNum,
          key,
          region.name,
          sdfDist,
        )
      return original
    }

    val transformed = transform.apply(original)
    val hitNum = modifyHitCount.incrementAndGet()
    if (hitNum <= 5 || hitNum % 5000 == 0) {
      LOGGER.info(
        "[NC-NoiseHandler] CONSTRAINT HIT #{}: key={} region={} orig={} transformed={} strength={} sdfDist={}",
        hitNum,
        key,
        region.name,
        "%.4f".format(original),
        "%.4f".format(transformed),
        "%.3f".format(strength),
        "%.1f".format(sdfDist),
      )
    }

    if (strength >= 1f) return transformed
    return original + (transformed - original) * strength
  }

  fun getStrength(blendWidth: Float, sdfDist: Float): Float {
    if (blendWidth <= 0f) return if (sdfDist < 0f) 1f else 0f
    if (sdfDist >= 0f) return 0f
    return (-sdfDist / blendWidth).coerceAtMost(1f)
  }
}
