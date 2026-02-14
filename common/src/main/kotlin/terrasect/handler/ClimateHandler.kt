package terrasect.handler

import net.minecraft.world.level.biome.Climate
import terrasect.generation.Context

object ClimateHandler {
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
