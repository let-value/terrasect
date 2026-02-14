package terrasect.sdf

import kotlin.math.max

class SubdivisionCellSdf : Sdf2 {
  var axis: Int = 0 // 0 = X, 1 = Z
  var lo: Double = Double.NEGATIVE_INFINITY
  var hi: Double = Double.POSITIVE_INFINITY

  override fun invoke(x: Double, z: Double): Double {
    val v = if (axis == 0) x else z
    return max(lo - v, v - hi)
  }
}
