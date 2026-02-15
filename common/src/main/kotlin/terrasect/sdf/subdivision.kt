package terrasect.sdf

import kotlin.math.max

class SubdivisionCellSdf : Sdf2 {
  var axis: Int = 0 // 0 = X, 1 = Z
  var lo: Float = Float.NEGATIVE_INFINITY
  var hi: Float = Float.POSITIVE_INFINITY

  override fun invoke(x: Int, z: Int): Float {
    val v = if (axis == 0) x else z
    return max(lo - v, v - hi)
  }
}
