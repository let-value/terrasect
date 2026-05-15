package terrasect.handler

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import net.minecraft.world.level.levelgen.DensityFunction
import net.minecraft.world.level.levelgen.NoiseRouter
import terrasect.extender.ChunkAccessExtender
import terrasect.generation.ChunkContext
import terrasect.helpers.ChunkDensityFunction

private const val TRACE_BLOCK_X = 0
private const val TRACE_BLOCK_Z = 0
private const val TRACE_PER_KEY_LIMIT = 8

private val log = NoiseScope.handler
private val logDf = NoiseScope.densityFunction
private val logOrigin = NoiseScope.originNoise

object NoiseHandler {
  private val pendingNoiseChunkCreation = ThreadLocal<ChunkAccessExtender?>()
  private val routerWrapCount = AtomicInteger()
  private val climateRouterWrapCount = AtomicInteger()
  private val modifyHitCount = AtomicInteger()
  private val holderKeyLogCount = AtomicInteger()
  private val missingHolderChunkLogCount = AtomicInteger()
  private val originTraceCounts = ConcurrentHashMap<String, AtomicInteger>()

  fun resetOriginTrace() {
    originTraceCounts.clear()
    modifyHitCount.set(0)
    holderKeyLogCount.set(0)
    missingHolderChunkLogCount.set(0)
  }

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
    wrapRouter("wrapNoiseRouter", routerWrapCount, router, chunk)

  @JvmStatic
  fun wrapClimateSamplerRouter(router: NoiseRouter, chunk: ChunkContext?): NoiseRouter =
    wrapRouter("wrapClimateSamplerRouter", climateRouterWrapCount, router, chunk)

  @JvmStatic
  fun logCapturedDensityKey(key: String) {
    logDf.traceBlock {
      val count = holderKeyLogCount.incrementAndGet()
      if (count <= 24) logDf.trace { "captured density holder key=$key" }
    }
  }

  @JvmStatic
  fun logMissingDensityChunk(key: String) {
    logDf.traceBlock {
      val count = missingHolderChunkLogCount.incrementAndGet()
      if (count <= 24) logDf.trace { "skipped keyed value key=$key: chunk context missing" }
    }
  }

  @JvmStatic
  fun wrapDensity(function: DensityFunction, key: String?, chunk: ChunkContext?): DensityFunction {
    if (key == null || function is ChunkDensityFunction) return function
    if (chunk == null) {
      logMissingDensityChunk(key)
      return function
    }
    return ChunkDensityFunction(function, key, chunk, 1)
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
    val region = chunk.getRegion(blockX, blockZ)
    if (region == null) {
      ifOriginTrace(blockX, blockZ) {
        trace(key, blockX, blockY, blockZ, original, null, "region=NULL")
      }
      return null
    }

    val noiseRegistry = chunk.dimensionContext?.noiseRegistry
    if (noiseRegistry == null) {
      ifOriginTrace(blockX, blockZ) {
        trace(
          key,
          blockX,
          blockY,
          blockZ,
          original,
          null,
          "region=${region.name} noiseRegistry=NULL",
        )
      }
      return null
    }

    val constraints = noiseRegistry.get(region)
    if (constraints == null) {
      ifOriginTrace(blockX, blockZ) {
        trace(
          key,
          blockX,
          blockY,
          blockZ,
          original,
          null,
          "region=${region.name} constraints=NULL (region not in registry)",
        )
      }
      return null
    }

    val transform =
      constraints.densityFunctions[key]
        ?: constraints.noises[key]
        ?: constraints.densityFunctions[key.substringAfterLast('/')]
        ?: constraints.noises[key.substringAfterLast('/')]
    if (transform == null) {
      ifOriginTrace(blockX, blockZ) {
        trace(key, blockX, blockY, blockZ, original, null, "region=${region.name} transform=NULL")
      }
      return null
    }

    val blendWidth = constraints.blendWidth
    val sdfDist = chunk.getDistance(blockX, blockZ)
    val strength = getStrength(blendWidth, sdfDist)
    if (strength <= 0f) {
      ifOriginTrace(blockX, blockZ) {
        trace(
          key,
          blockX,
          blockY,
          blockZ,
          original,
          original,
          "region=${region.name} strength=0 sdfDist=${sdfDist.fmt1()}",
        )
      }
      return original
    }

    val transformed = transform.apply(original)

    logDf.traceBlock {
      val hitNum = modifyHitCount.incrementAndGet()
      if (hitNum <= 24 || hitNum % 5_000_000 == 0) {
        logDf.trace {
          "CONSTRAINT HIT #$hitNum: key=$key block=($blockX, $blockY, $blockZ) region=${region.name} orig=${original.fmt4()} transformed=${transformed.fmt4()} strength=${strength.fmt3()} sdfDist=${sdfDist.fmt1()}"
        }
      }
      if (blockX == TRACE_BLOCK_X && blockZ == TRACE_BLOCK_Z) {
        trace(
          key,
          blockX,
          blockY,
          blockZ,
          original,
          transformed,
          "hit=$hitNum region=${region.name} strength=${strength.fmt3()} sdfDist=${sdfDist.fmt1()}",
        )
      }
    }

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
    if (chunk == null) {
      log.debugBlock {
        val n = counter.incrementAndGet()
        if (n <= 8 || n % 500 == 0) log.debug { "$label #$n skipped: chunkContext=NULL" }
      }
      return router
    }

    log.debugBlock {
      val registry = chunk.dimensionContext?.noiseRegistry
      val n = counter.incrementAndGet()
      if (n <= 8 || n % 500 == 0) {
        log.debug {
          "$label #$n: dim=${chunk.dimensionContext?.dimensionId ?: "null"} hasRegistry=${registry != null} regionCount=${registry?.size() ?: 0}"
        }
      }
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
    function: DensityFunction,
    key: String,
    chunk: ChunkContext,
    scale: Int = 1,
  ): DensityFunction {
    if (function is ChunkDensityFunction) return function
    return ChunkDensityFunction(function, key, chunk, scale)
  }

  private inline fun ifOriginTrace(blockX: Int, blockZ: Int, action: () -> Unit) {
    if (blockX == TRACE_BLOCK_X && blockZ == TRACE_BLOCK_Z) logOrigin.traceBlock(action)
  }

  private fun trace(
    key: String,
    blockX: Int,
    blockY: Int,
    blockZ: Int,
    original: Double,
    transformed: Double?,
    status: String,
  ) {
    val bucket = if (status.startsWith("hit=")) "$key|hit" else "$key|$status"
    val count = originTraceCounts.computeIfAbsent(bucket) { AtomicInteger() }.incrementAndGet()
    if (count <= TRACE_PER_KEY_LIMIT) {
      logOrigin.trace {
        "sample #$count key=$key block=($blockX, $blockY, $blockZ) original=${original.fmt4()} transformed=${transformed?.fmt4() ?: "unchanged"} $status"
      }
    }
  }
}

private fun Double.fmt4() = "%.4f".format(this)

private fun Float.fmt3() = "%.3f".format(this)

private fun Float.fmt1() = "%.1f".format(this)
