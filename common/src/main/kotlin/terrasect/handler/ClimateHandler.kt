package terrasect.handler

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.biome.Climate
import org.slf4j.LoggerFactory
import terrasect.definition.ClimateRange
import terrasect.extender.ClimateTargetPointExtender
import terrasect.generation.ChunkContext
import terrasect.generation.DimensionContext

private val LOGGER = LoggerFactory.getLogger("Terrasect/ClimateHandler")
private const val TRACE_BLOCK_X = 0
private const val TRACE_BLOCK_Z = 0

object ClimateHandler {
  private val nullRegionCount = AtomicInteger()
  private val nullConstraintsCount = AtomicInteger()
  private val appliedCount = AtomicInteger()

  fun resetOriginTrace() {
    nullRegionCount.set(0)
    nullConstraintsCount.set(0)
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
    chunk: ChunkContext,
  ) {
    val blockX = quadX shl 2
    val blockZ = quadZ shl 2
    val traceOrigin = blockX == TRACE_BLOCK_X && blockZ == TRACE_BLOCK_Z
    val region = chunk.regions?.get(blockX, blockZ)
    if (region == null) {
      if (traceOrigin) {
        val count = nullRegionCount.incrementAndGet()
        if (count <= 3) {
          LOGGER.info(
            "[NC-OriginClimate] sample #{} block=({}, {}) quad=({}, {}, {}) region=NULL",
            count,
            blockX,
            blockZ,
            quadX,
            quadY,
            quadZ,
          )
        }
      }
      return
    }
    val constraints = region.climate
    if (constraints == null) {
      if (traceOrigin) {
        val count = nullConstraintsCount.incrementAndGet()
        if (count <= 3) {
          LOGGER.info(
            "[NC-OriginClimate] sample #{} block=({}, {}) quad=({}, {}, {}) region={} climateConstraints=NULL",
            count,
            blockX,
            blockZ,
            quadX,
            quadY,
            quadZ,
            region.name,
          )
        }
      }
      return
    }

    @Suppress("CAST_NEVER_SUCCEEDS") val extender = climate as ClimateTargetPointExtender
    val details = StringBuilder()
    var changed = false

    constraints.temperature?.let { range ->
      val value = climate.temperature.coerceIn(range.min, range.max)
      changed = appendAxis(details, "temperature", climate.temperature, value, range) || changed
      extender.`terrasect$setTemperature`(value)
    }
    constraints.humidity?.let { range ->
      val value = climate.humidity.coerceIn(range.min, range.max)
      changed = appendAxis(details, "humidity", climate.humidity, value, range) || changed
      extender.`terrasect$setHumidity`(value)
    }
    constraints.continentalness?.let { range ->
      val value = climate.continentalness.coerceIn(range.min, range.max)
      changed =
        appendAxis(details, "continentalness", climate.continentalness, value, range) || changed
      extender.`terrasect$setContinentalness`(value)
    }
    constraints.erosion?.let { range ->
      val value = climate.erosion.coerceIn(range.min, range.max)
      changed = appendAxis(details, "erosion", climate.erosion, value, range) || changed
      extender.`terrasect$setErosion`(value)
    }
    constraints.depth?.let { range ->
      val value = climate.depth.coerceIn(range.min, range.max)
      changed = appendAxis(details, "depth", climate.depth, value, range) || changed
      extender.`terrasect$setDepth`(value)
    }
    constraints.weirdness?.let { range ->
      val value = climate.weirdness.coerceIn(range.min, range.max)
      changed = appendAxis(details, "weirdness", climate.weirdness, value, range) || changed
      extender.`terrasect$setWeirdness`(value)
    }

    if (traceOrigin) {
      val count = appliedCount.incrementAndGet()
      if (count <= 8) {
        LOGGER.info(
          "[NC-OriginClimate] APPLIED #{} block=({}, {}) quad=({}, {}, {}) region={} changed={} axes=[{}]",
          count,
          blockX,
          blockZ,
          quadX,
          quadY,
          quadZ,
          region.name,
          changed,
          details.toString(),
        )
      }
    }
  }

  private fun appendAxis(
    details: StringBuilder,
    name: String,
    original: Long,
    value: Long,
    range: ClimateRange,
  ): Boolean {
    if (details.isNotEmpty()) details.append(", ")
    details
      .append(name)
      .append('=')
      .append(original)
      .append("→")
      .append(value)
      .append(" in ")
      .append(range.min)
      .append("..")
      .append(range.max)
    return original != value
  }
}
