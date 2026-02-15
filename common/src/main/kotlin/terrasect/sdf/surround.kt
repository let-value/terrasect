package terrasect.sdf

import kotlin.math.max
import kotlin.math.sqrt

fun surroundDistance(
    x: Int,
    z: Int,
    parentSdf: Sdf2,
    centerX: Int,
    centerZ: Int,
    scale: Float,
    smoothing: Float = 0f,
): Float {
  val scaledX = centerX + (x - centerX) / scale
  val scaledZ = centerZ + (z - centerZ) / scale

  val dist = parentSdf(scaledX.toInt(), scaledZ.toInt()) * scale

  return dist - smoothing
}

class CenterCellSdf : Sdf2 {
  var parent: Sdf2 = EmptySdf
  var centerX: Int = 0
  var centerZ: Int = 0
  var scale: Float = 0f
  var smoothing: Float = 0f

  override fun invoke(x: Int, z: Int): Float {
    return surroundDistance(x, z, parent, centerX, centerZ, scale, smoothing)
  }
}

class SurroundCellSdf : Sdf2 {
  var parent: Sdf2 = EmptySdf
  var centerX: Int = 0
  var centerZ: Int = 0
  var scale: Float = 0f
  var smoothing: Float = 0f

  override fun invoke(x: Int, z: Int): Float {
    val outer = surroundDistance(x, z, parent, centerX, centerZ, 1f, smoothing)
    val inner = surroundDistance(x, z, parent, centerX, centerZ, scale, smoothing)
    return max(outer, -inner)
  }
}

fun surroundScale(centerBudget: Long, totalBudget: Long): Float {
  if (totalBudget <= 0) {
    return 1f
  }
  val centerFraction = (centerBudget / totalBudget).coerceIn(0, 1)
  return sqrt(centerFraction.toDouble()).coerceAtLeast(1e-6).toFloat()
}
