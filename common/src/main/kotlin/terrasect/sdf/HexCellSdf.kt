package terrasect.sdf

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

private val SIN60 = sin(Math.toRadians(60.0))

fun hexDistance(px: Double, pz: Double, radius: Int): Double {
  val x = abs(px)
  val z = abs(pz)
  val d = x * 0.5 + z * SIN60
  return max(d, x) - radius
}

class HexCellSdf : Sdf2 {
  var centerX = 0.0
  var centerZ = 0.0
  var radius = 0

  override fun invoke(x: Double, z: Double): Double {
    return hexDistance(x - centerX, z - centerZ, radius)
  }
}

class HexGapSdf : Sdf2 {
  var centerX = 0.0
  var centerZ = 0.0
  var innerRadius = 0
  var outerRadius = 0

  override fun invoke(x: Double, z: Double): Double {
    val dx = x - centerX
    val dz = z - centerZ
    val outer = hexDistance(dx, dz, outerRadius)
    val inner = hexDistance(dx, dz, innerRadius)
    return max(outer, -inner)
  }
}
