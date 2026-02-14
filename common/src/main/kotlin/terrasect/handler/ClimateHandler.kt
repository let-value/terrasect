package terrasect.handler

import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.biome.Climate
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

  fun modifyTargetPoint(
      context: Context,
      x: Int,
      z: Int,
      original: Climate.TargetPoint,
  ): Climate.TargetPoint {
    x shl 2
    z shl 2

    return original
  }
}
