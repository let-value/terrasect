package terrasect.handler

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import net.minecraft.world.level.levelgen.NoiseRouter
import org.slf4j.LoggerFactory
import terrasect.extender.ChunkAccessExtender
import terrasect.generation.ChunkContext
import terrasect.helpers.ChunkDensityFunction

private val LOGGER = LoggerFactory.getLogger("Terrasect/NoiseHandler")
private const val TRACE_BLOCK_X = 0
private const val TRACE_BLOCK_Z = 0
private const val TRACE_PER_KEY_LIMIT = 8

object NoiseHandler {
  @JvmField val pendingChunk: ThreadLocal<ChunkAccessExtender?> = ThreadLocal()
  @JvmField val currentChunk: ThreadLocal<ChunkContext?> = ThreadLocal()

  private val wrapCount = AtomicInteger()
  private val modifyHitCount = AtomicInteger()
  private val originTraceCounts = ConcurrentHashMap<String, AtomicInteger>()

  fun resetOriginTrace() {
    originTraceCounts.clear()
  }

  @JvmStatic
  fun wrapNoiseRouter(router: NoiseRouter, chunk: ChunkContext): NoiseRouter {
    val n = wrapCount.incrementAndGet()
    val hasRegistry = chunk.dimensionContext?.noiseRegistry != null
    val dim = chunk.dimensionContext?.dimensionId ?: "null"
    if (n <= 8 || n % 500 == 0) {
      LOGGER.info(
        "[NC-NoiseHandler] wrapNoiseRouter #{}: dim={} hasRegistry={} regionCount={}",
        n,
        dim,
        hasRegistry,
        chunk.dimensionContext?.noiseRegistry?.size() ?: 0,
      )
    }
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
    blockY: Int,
    blockZ: Int,
    chunk: ChunkContext,
  ): Double? {
    val traceOrigin = blockX == TRACE_BLOCK_X && blockZ == TRACE_BLOCK_Z

    val region = chunk.getRegion(blockX, blockZ)
    if (region == null) {
      logOriginSample(traceOrigin, key, blockX, blockY, blockZ, original, null, "region=NULL")
      return null
    }

    val noiseRegistry = chunk.dimensionContext?.noiseRegistry
    if (noiseRegistry == null) {
      logOriginSample(
        traceOrigin,
        key,
        blockX,
        blockY,
        blockZ,
        original,
        null,
        "region=${region.name} noiseRegistry=NULL",
      )
      return null
    }

    val constraints = noiseRegistry.get(region)
    if (constraints == null) {
      logOriginSample(
        traceOrigin,
        key,
        blockX,
        blockY,
        blockZ,
        original,
        null,
        "region=${region.name} constraints=NULL (region not in registry)",
      )
      return null
    }

    val transform =
      constraints.densityFunctions[key]
        ?: constraints.noises[key]
        ?: constraints.densityFunctions[key.substringAfterLast('/')]
        ?: constraints.noises[key.substringAfterLast('/')]
    if (transform == null) {
      logOriginSample(
        traceOrigin,
        key,
        blockX,
        blockY,
        blockZ,
        original,
        null,
        "region=${region.name} transform=NULL",
      )
      return null
    }

    val blendWidth = constraints.blendWidth
    val sdfDist = chunk.getDistance(blockX, blockZ)
    val strength = getStrength(blendWidth, sdfDist)

    if (strength <= 0f) {
      logOriginSample(
        traceOrigin,
        key,
        blockX,
        blockY,
        blockZ,
        original,
        original,
        "region=${region.name} strength=0 sdfDist=${"%.1f".format(sdfDist)}",
      )
      return original
    }

    val transformed = transform.apply(original)
    val hitNum = modifyHitCount.incrementAndGet()
    if (hitNum <= 24 || hitNum % 500_000 == 0) {
      LOGGER.info(
        "[NC-NoiseHandler] CONSTRAINT HIT #{}: key={} block=({}, {}, {}) region={} orig={} transformed={} strength={} sdfDist={}",
        hitNum,
        key,
        blockX,
        blockY,
        blockZ,
        region.name,
        "%.4f".format(original),
        "%.4f".format(transformed),
        "%.3f".format(strength),
        "%.1f".format(sdfDist),
      )
    }
    logOriginSample(
      traceOrigin,
      key,
      blockX,
      blockY,
      blockZ,
      original,
      transformed,
      "hit=$hitNum region=${region.name} strength=${"%.3f".format(strength)} sdfDist=${"%.1f".format(sdfDist)}",
    )

    if (strength >= 1f) return transformed
    return original + (transformed - original) * strength
  }

  private fun logOriginSample(
    traceOrigin: Boolean,
    key: String,
    blockX: Int,
    blockY: Int,
    blockZ: Int,
    original: Double,
    transformed: Double?,
    status: String,
  ) {
    if (!traceOrigin) return
    val bucket = if (status.startsWith("hit=")) "$key|hit" else "$key|$status"
    val count = originTraceCounts.computeIfAbsent(bucket) { AtomicInteger() }.incrementAndGet()
    if (count > TRACE_PER_KEY_LIMIT) return
    LOGGER.info(
      "[NC-OriginNoise] sample #{} key={} block=({}, {}, {}) original={} transformed={} {}",
      count,
      key,
      blockX,
      blockY,
      blockZ,
      "%.4f".format(original),
      transformed?.let { "%.4f".format(it) } ?: "unchanged",
      status,
    )
  }

  fun getStrength(blendWidth: Float, sdfDist: Float): Float {
    if (blendWidth <= 0f) return if (sdfDist < 0f) 1f else 0f
    if (sdfDist >= 0f) return 0f
    return (-sdfDist / blendWidth).coerceAtMost(1f)
  }
}
