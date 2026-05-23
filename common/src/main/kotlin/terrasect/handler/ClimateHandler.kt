package terrasect.handler

import kotlin.math.max
import kotlin.math.min
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.biome.Climate
import terrasect.extender.ClimateTargetPointExtender
import terrasect.extender.NoiseChunkExtender
import terrasect.generation.ChunkContext
import terrasect.generation.DimensionContext
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent

private val instr = TerrasectInstr.climate

object ClimateHandler {
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
      instr.count(TerrasectMetricEvent.CLIMATE_CHUNK_MISSING)
      return
    }

    val region = chunk.regions?.get(blockX, blockZ)
    if (region == null) {
      return
    }

    val constraints = region.climate
    if (constraints == null) {
      return
    }

    @Suppress("CAST_NEVER_SUCCEEDS") val extender = climate as ClimateTargetPointExtender

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

    instr.count(TerrasectMetricEvent.CLIMATE_APPLIED)
  }
}
