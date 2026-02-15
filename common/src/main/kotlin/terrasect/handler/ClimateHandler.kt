package terrasect.handler

import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.biome.Climate
import terrasect.ChunkAccessExtender
import terrasect.ClimateTargetPointExtender
import terrasect.definition.ClimateRange
import terrasect.generation.Context
import terrasect.utils.packPair
import kotlin.math.max
import kotlin.math.min

object ClimateHandler {
  fun getInfluence(context: Context, x: Int, z: Int): Long {
    if (context.biomesClimate == null) return 0

    val target = context.sampler.sample(x shr 2, 16, z shr 2)

    val biome = context.biomesClimate.findValue(target)

    val river = if (biome.`is`(BiomeTags.IS_RIVER)) 1.0f else 0.0f

    val weirdness = target.weirdness()
    val normalized = (weirdness + 10000.0f) / 20000.0f
    val ridge = max(0.0f, min(1.0f, normalized))

    return packPair(river.toRawBits(), ridge.toRawBits())
  }

  fun modifyClimate(
      chunk: ChunkAccessExtender,
      quadX: Int,
      quadY: Int,
      quadZ: Int,
      original: Climate.TargetPoint,
  ) {
    val grid = chunk.`terrasect$getCache`()?.grid ?: return
    val blockX = quadX shl 2
    val blockZ = quadZ shl 2
    val region = grid.get(blockX, blockZ) ?: return
    val climate = region.climate ?: return

    @Suppress("CAST_NEVER_SUCCEEDS") val extender = original as ClimateTargetPointExtender

    climate.temperature?.let {
      extender.`terrasect$setTemperature`(mapToRange(it, original.temperature))
    }
    climate.humidity?.let { extender.`terrasect$setHumidity`(mapToRange(it, original.humidity)) }
    climate.continentalness?.let {
      extender.`terrasect$setContinentalness`(mapToRange(it, original.continentalness))
    }
    climate.erosion?.let { extender.`terrasect$setErosion`(mapToRange(it, original.erosion)) }
    climate.depth?.let { extender.`terrasect$setDepth`(mapToRange(it, original.depth)) }
    climate.weirdness?.let { extender.`terrasect$setWeirdness`(mapToRange(it, original.weirdness)) }
  }

  private fun mapToRange(range: ClimateRange, original: Long): Long {
    return original.coerceIn(range.min, range.max)
  }
}
