package terrasect.handler

import net.minecraft.world.level.biome.Climate
import terrasect.extender.ClimateTargetPointExtender
import terrasect.extender.NoiseChunkExtender
import terrasect.generation.ChunkContext
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent

private val instr = TerrasectInstr.climate

object ClimateHandler {
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

    val region = chunk.getRegion(blockX, blockZ)
    if (region == null) {
      return
    }

    val constraints = region.climate
    @Suppress("CAST_NEVER_SUCCEEDS") val extender = climate as ClimateTargetPointExtender
    if (constraints != null) {
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
}
