package terrasect.sdf

import kotlin.math.*

val SIN60 = sin(Math.toRadians(60.0))
val SQRT3 = sqrt(3.0)
val TAN30 = tan(Math.toRadians(30.0))
const val ONE_THIRD = 1.0 / 3.0
const val TWO_THIRDS = 2.0 / 3.0

fun areaToApothem(area: Double): Double = sqrt(area / (2.0 * SQRT3))

fun hexDistance(px: Double, pz: Double, apothem: Double): Double {
  val x = abs(px)
  val z = abs(pz)
  val d = x * 0.5 + z * SIN60
  return max(d, x) - apothem
}

class HexCellSdf(
    var centerX: Double = 0.0,
    var centerZ: Double = 0.0,
    var apothem: Double = 0.0,
) : Sdf2 {

  override fun invoke(x: Double, z: Double): Double {
    return hexDistance(x - centerX, z - centerZ, apothem)
  }
}

class HexGapSdf(
    var centerX: Double = 0.0,
    var centerZ: Double = 0.0,
    var apothem: Double = 0.0,
    var gap: Double = 0.0,
) : Sdf2 {

  override fun invoke(x: Double, z: Double): Double {
    val dx = x - centerX
    val dz = z - centerZ
    val outer = hexDistance(dx, dz, apothem + gap)
    val inner = hexDistance(dx, dz, apothem)
    return max(outer, -inner)
  }
}
