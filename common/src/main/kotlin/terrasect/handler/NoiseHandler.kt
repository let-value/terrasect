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
  private val noiseChunkCreations = ConcurrentHashMap<NoiseChunkKey, ChunkAccessExtender>()
  private val routerWrapCount = AtomicInteger()
  private val climateRouterWrapCount = AtomicInteger()
  private val modifyHitCount = AtomicInteger()
  private val holderKeyLogCount = AtomicInteger()
  private val missingHolderChunkLogCount = AtomicInteger()
  private val originTraceCounts = ConcurrentHashMap<String, AtomicInteger>()

  fun resetOriginTrace() {
    originTraceCounts.clear()
  }

  @JvmStatic
  fun beginNoiseChunkCreation(
    random: Any,
    firstNoiseX: Int,
    firstNoiseZ: Int,
    chunk: ChunkAccessExtender,
  ) {
    noiseChunkCreations[NoiseChunkKey(random, firstNoiseX, firstNoiseZ)] = chunk
  }

  @JvmStatic
  fun endNoiseChunkCreation(random: Any, firstNoiseX: Int, firstNoiseZ: Int) {
    noiseChunkCreations.remove(NoiseChunkKey(random, firstNoiseX, firstNoiseZ))
  }

  @JvmStatic
  fun getNoiseChunkCreation(random: Any, firstNoiseX: Int, firstNoiseZ: Int): ChunkAccessExtender? =
    noiseChunkCreations[NoiseChunkKey(random, firstNoiseX, firstNoiseZ)]

  @JvmStatic
  fun wrapNoiseRouter(router: NoiseRouter, chunk: ChunkContext?): NoiseRouter =
    wrapRouter("wrapNoiseRouter", routerWrapCount, router, chunk)

  @JvmStatic
  fun wrapClimateSamplerRouter(router: NoiseRouter, chunk: ChunkContext?): NoiseRouter =
    wrapRouter("wrapClimateSamplerRouter", climateRouterWrapCount, router, chunk)

  @JvmStatic
  fun logCapturedDensityKey(key: String) {
    val count = holderKeyLogCount.incrementAndGet()
    if (count <= 24) LOGGER.info("[NC-HolderKey] captured density holder key={}", key)
  }

  @JvmStatic
  fun logMissingDensityChunk(key: String) {
    val count = missingHolderChunkLogCount.incrementAndGet()
    if (count <= 24) {
      LOGGER.info("[NC-HolderKey] skipped keyed value key={} because chunk context is missing", key)
    }
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
    if (region == null) return trace(traceOrigin, key, blockX, blockY, blockZ, original, null, "region=NULL")

    val noiseRegistry = chunk.dimensionContext?.noiseRegistry
    if (noiseRegistry == null) {
      return trace(traceOrigin, key, blockX, blockY, blockZ, original, null, "region=${region.name} noiseRegistry=NULL")
    }

    val constraints = noiseRegistry.get(region)
    if (constraints == null) {
      return trace(traceOrigin, key, blockX, blockY, blockZ, original, null, "region=${region.name} constraints=NULL (region not in registry)")
    }

    val transform =
      constraints.densityFunctions[key]
        ?: constraints.noises[key]
        ?: constraints.densityFunctions[key.substringAfterLast('/')]
        ?: constraints.noises[key.substringAfterLast('/')]
    if (transform == null) {
      return trace(traceOrigin, key, blockX, blockY, blockZ, original, null, "region=${region.name} transform=NULL")
    }

    val blendWidth = constraints.blendWidth
    val sdfDist = chunk.getDistance(blockX, blockZ)
    val strength = getStrength(blendWidth, sdfDist)
    if (strength <= 0f) {
      trace(traceOrigin, key, blockX, blockY, blockZ, original, original, "region=${region.name} strength=0 sdfDist=${sdfDist.fmt1()}")
      return original
    }

    val transformed = transform.apply(original)
    val hitNum = modifyHitCount.incrementAndGet()
    if (hitNum <= 24 || hitNum % 5_000_000 == 0) {
      LOGGER.info(
        "[NC-NoiseHandler] CONSTRAINT HIT #{}: key={} block=({}, {}, {}) region={} orig={} transformed={} strength={} sdfDist={}",
        hitNum,
        key,
        blockX,
        blockY,
        blockZ,
        region.name,
        original.fmt4(),
        transformed.fmt4(),
        strength.fmt3(),
        sdfDist.fmt1(),
      )
    }
    trace(traceOrigin, key, blockX, blockY, blockZ, original, transformed, "hit=$hitNum region=${region.name} strength=${strength.fmt3()} sdfDist=${sdfDist.fmt1()}")

    if (strength >= 1f) return transformed
    return original + (transformed - original) * strength
  }

  fun getStrength(blendWidth: Float, sdfDist: Float): Float {
    if (blendWidth <= 0f) return if (sdfDist < 0f) 1f else 0f
    if (sdfDist >= 0f) return 0f
    return (-sdfDist / blendWidth).coerceAtMost(1f)
  }

  private fun wrapRouter(
    label: String,
    counter: AtomicInteger,
    router: NoiseRouter,
    chunk: ChunkContext?,
  ): NoiseRouter {
    val n = counter.incrementAndGet()
    if (chunk == null) {
      if (n <= 8 || n % 500 == 0) LOGGER.warn("[NC-NoiseHandler] {} #{} skipped: chunkContext=NULL", label, n)
      return router
    }

    val registry = chunk.dimensionContext?.noiseRegistry
    if (n <= 8 || n % 500 == 0) {
      LOGGER.info(
        "[NC-NoiseHandler] {} #{}: dim={} hasRegistry={} regionCount={}",
        label,
        n,
        chunk.dimensionContext?.dimensionId ?: "null",
        registry != null,
        registry?.size() ?: 0,
      )
    }
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
      wrapDensityFunction(router.preliminarySurfaceLevel, "preliminarySurfaceLevel", chunk),
      wrapDensityFunction(router.finalDensity, "finalDensity", chunk),
      wrapDensityFunction(router.veinToggle, "veinToggle", chunk),
      wrapDensityFunction(router.veinRidged, "veinRidged", chunk),
      wrapDensityFunction(router.veinGap, "veinGap", chunk),
    )
  }

  private fun wrapDensityFunction(
    function: net.minecraft.world.level.levelgen.DensityFunction,
    key: String,
    chunk: ChunkContext,
    scale: Int = 1,
  ): net.minecraft.world.level.levelgen.DensityFunction {
    if (function is ChunkDensityFunction) return function
    return ChunkDensityFunction(function, key, chunk, scale)
  }

  private fun trace(
    enabled: Boolean,
    key: String,
    blockX: Int,
    blockY: Int,
    blockZ: Int,
    original: Double,
    transformed: Double?,
    status: String,
  ): Double? {
    if (!enabled) return null
    val bucket = if (status.startsWith("hit=")) "$key|hit" else "$key|$status"
    val count = originTraceCounts.computeIfAbsent(bucket) { AtomicInteger() }.incrementAndGet()
    if (count <= TRACE_PER_KEY_LIMIT) {
      LOGGER.info(
        "[NC-OriginNoise] sample #{} key={} block=({}, {}, {}) original={} transformed={} {}",
        count,
        key,
        blockX,
        blockY,
        blockZ,
        original.fmt4(),
        transformed?.fmt4() ?: "unchanged",
        status,
      )
    }
    return null
  }
}

private data class NoiseChunkKey(
  val randomIdentity: Int,
  val firstNoiseX: Int,
  val firstNoiseZ: Int,
) {
  constructor(random: Any, firstNoiseX: Int, firstNoiseZ: Int) :
    this(System.identityHashCode(random), firstNoiseX, firstNoiseZ)
}

private fun Double.fmt4() = "%.4f".format(this)

private fun Float.fmt3() = "%.3f".format(this)

private fun Float.fmt1() = "%.1f".format(this)
