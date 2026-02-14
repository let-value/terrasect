package terrasect.sdf

import kotlin.math.sqrt

fun surroundInnerDistance(
    x: Double,
    z: Double,
    parentSdf: Sdf2,
    centerX: Double,
    centerZ: Double,
    scale: Double,
): Double {
  val safeScale = scale.coerceAtLeast(1e-6)
  val scaledX = centerX + (x - centerX) / safeScale
  val scaledZ = centerZ + (z - centerZ) / safeScale
  return parentSdf(scaledX, scaledZ) * safeScale
}

class SurroundCellSdf : Sdf2 {
  var innerDistance: Double = Double.POSITIVE_INFINITY
  var isCenter: Boolean = true

  override fun invoke(x: Double, z: Double): Double {
    return if (isCenter) innerDistance else -innerDistance
  }
}

fun surroundScale(centerBudget: Double, totalBudget: Double): Double {
  if (totalBudget <= 0.0) {
    return 1.0
  }
  val centerFraction = (centerBudget / totalBudget).coerceIn(0.0, 1.0)
  return sqrt(centerFraction)
}
