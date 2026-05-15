package terrasect.handler

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.biome.Climate
import terrasect.definition.ClimateRange
import terrasect.extender.ClimateTargetPointExtender
import terrasect.extender.NoiseChunkExtender
import terrasect.generation.ChunkContext
import terrasect.generation.DimensionContext

private const val TRACE_BLOCK_X = 0
private const val TRACE_BLOCK_Z = 0

object ClimateHandler {
  private val nullRegionCount = AtomicInteger()
  private val nullConstraintsCount = AtomicInteger()
  private val samplerSkipCount = AtomicInteger()
  private val appliedCount = AtomicInteger()

  fun resetOriginTrace() {
    nullRegionCount.set(0)
    nullConstraintsCount.set(0)
    samplerSkipCount.set(0)
    appliedCount.set(0)
  }

  fun getInfluence(dimensionContext: DimensionContext, x: Int, z: Int): Pair<Float, Float> {
    if (dimensionContext.biomesClimate == null) return 0f to 0f

    val target = dimensionContext.sampler.sample(x shr 2, 16, z shr 2)

    val biome = dimensionContext.biomesClimate.findValue(target)

    val river = if (biome.`is`(BiomeTags.IS_RIVER)) 1.0f else 0.0f

    val weirdness = target.weirdness()
    val normalized = (weirdness + 10000.0f) / 20000.0f
    val ridge = max(0.0f, min(1.0f, normalized))

    return Pair(river, ridge)
  }

  fun modifyClimate(
    quadX: Int,
    quadY: Int,
    quadZ: Int,
    climate: Climate.TargetPoint,
    noiseChunk: NoiseChunkExtender?,
  ) {
    val blockX = quadX shl 2
    val blockZ = quadZ shl 2

    val chunk: ChunkContext? = noiseChunk?.`terrasect$getChunk`()?.`terrasect$getContext`()

    if (chunk == null) {
      ifOriginTrace(blockX, blockZ) {
        traceSkip(samplerSkipCount, blockX, blockZ, quadX, quadY, quadZ, "context=NULL")
      }
      return
    }

    val region = chunk.regions?.get(blockX, blockZ)
    if (region == null) {
      ifOriginTrace(blockX, blockZ) {
        traceSkip(nullRegionCount, blockX, blockZ, quadX, quadY, quadZ, "region=NULL")
      }
      return
    }

    val constraints = region.climate
    if (constraints == null) {
      ifOriginTrace(blockX, blockZ) {
        traceSkip(
          nullConstraintsCount,
          blockX,
          blockZ,
          quadX,
          quadY,
          quadZ,
          "region=${region.name} climateConstraints=NULL",
        )
      }
      return
    }

    @Suppress("CAST_NEVER_SUCCEEDS") val extender = climate as ClimateTargetPointExtender

    var originals: LongArray? = null
    if (blockX == TRACE_BLOCK_X && blockZ == TRACE_BLOCK_Z) {
      NoiseScope.originClimate.traceBlock {
        originals =
          longArrayOf(
            climate.temperature,
            climate.humidity,
            climate.continentalness,
            climate.erosion,
            climate.depth,
            climate.weirdness,
          )
      }
    }

    constraints.temperature?.let { range ->
      extender.`terrasect$setTemperature`(climate.temperature.coerceIn(range.min, range.max))
    }
    constraints.humidity?.let { range ->
      extender.`terrasect$setHumidity`(climate.humidity.coerceIn(range.min, range.max))
    }
    constraints.continentalness?.let { range ->
      extender.`terrasect$setContinentalness`(
        climate.continentalness.coerceIn(range.min, range.max)
      )
    }
    constraints.erosion?.let { range ->
      extender.`terrasect$setErosion`(climate.erosion.coerceIn(range.min, range.max))
    }
    constraints.depth?.let { range ->
      extender.`terrasect$setDepth`(climate.depth.coerceIn(range.min, range.max))
    }
    constraints.weirdness?.let { range ->
      extender.`terrasect$setWeirdness`(climate.weirdness.coerceIn(range.min, range.max))
    }

    originals?.let { o ->
      val count = appliedCount.incrementAndGet()
      if (count <= 8) {
        NoiseScope.originClimate.trace {
          val axes = buildString {
            constraints.temperature?.let { range ->
              appendAxis("temperature", o[0], climate.temperature, range)
            }
            constraints.humidity?.let { range ->
              appendAxis("humidity", o[1], climate.humidity, range)
            }
            constraints.continentalness?.let { range ->
              appendAxis("continentalness", o[2], climate.continentalness, range)
            }
            constraints.erosion?.let { range ->
              appendAxis("erosion", o[3], climate.erosion, range)
            }
            constraints.depth?.let { range ->
              appendAxis("depth", o[4], climate.depth, range)
            }
            constraints.weirdness?.let { range ->
              appendAxis("weirdness", o[5], climate.weirdness, range)
            }
          }
          "APPLIED #$count block=($blockX, $blockZ) quad=($quadX, $quadY, $quadZ) region=${region.name} axes=[$axes]"
        }
      }
    }
  }

  private inline fun ifOriginTrace(blockX: Int, blockZ: Int, action: () -> Unit) {
    if (blockX == TRACE_BLOCK_X && blockZ == TRACE_BLOCK_Z) NoiseScope.originClimate.traceBlock(action)
  }

  private fun traceSkip(
    counter: AtomicInteger,
    blockX: Int,
    blockZ: Int,
    quadX: Int,
    quadY: Int,
    quadZ: Int,
    reason: String,
  ) {
    val count = counter.incrementAndGet()
    if (count <= 3) {
      NoiseScope.originClimate.trace {
        "sample #$count block=($blockX, $blockZ) quad=($quadX, $quadY, $quadZ) $reason"
      }
    }
  }

  private fun StringBuilder.appendAxis(
    name: String,
    original: Long,
    value: Long,
    range: ClimateRange,
  ) {
    if (isNotEmpty()) append(", ")
    append(name)
      .append('=')
      .append(original)
      .append("→")
      .append(value)
      .append(" in ")
      .append(range.min)
      .append("..")
      .append(range.max)
  }
}
