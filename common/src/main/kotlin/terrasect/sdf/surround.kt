package terrasect.sdf

import kotlin.math.max
import kotlin.math.sqrt

fun surroundDistance(
    x: Double,
    z: Double,
    parentSdf: Sdf2,
    centerX: Double,
    centerZ: Double,
    scale: Double,
): Double {
  val scaledX = centerX + (x - centerX) / scale
  val scaledZ = centerZ + (z - centerZ) / scale
  return parentSdf(scaledX, scaledZ) * scale
}

class CenterCellSdf : Sdf2 {
  var parent: Sdf2 = EmptySdf
  var centerX: Double = 0.0
  var centerZ: Double = 0.0
  var scale: Double = 0.0

  override fun invoke(x: Double, z: Double): Double {
    return surroundDistance(x, z, parent, centerX, centerZ, scale)
  }
}

class SurroundCellSdf : Sdf2 {
  var parent: Sdf2 = EmptySdf
  var centerX: Double = 0.0
  var centerZ: Double = 0.0
  var scale: Double = 0.0

  override fun invoke(x: Double, z: Double): Double {
    val outer = surroundDistance(x, z, parent, centerX, centerZ, 1.0)
    val inner = surroundDistance(x, z, parent, centerX, centerZ, scale)
    return max(outer, -inner)
  }
}

fun surroundScale(centerBudget: Double, totalBudget: Double): Double {
  if (totalBudget <= 0.0) {
    return 1.0
  }
  val centerFraction = (centerBudget / totalBudget).coerceIn(0.0, 1.0)
  return sqrt(centerFraction).coerceAtLeast(1e-6)
}
