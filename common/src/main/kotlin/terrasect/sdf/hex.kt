package terrasect.sdf

import kotlin.math.*
import terrasect.utils.first
import terrasect.utils.packPair
import terrasect.utils.second

val SIN60 = sin(Math.toRadians(60.0).toFloat())
val SQRT3 = sqrt(3f)
val TAN30 = tan(Math.toRadians(30.0).toFloat())
const val ONE_THIRD = 1f / 3f
const val TWO_THIRDS = 2f / 3f

fun areaToApothem(area: Long): Float = sqrt(area / (2f * SQRT3))

fun hexSpacing(apothem: Float, gap: Float = 0f): Float = (apothem + gap.coerceAtLeast(0f)) / SIN60

fun hexCenterX(q: Int, r: Int, spacing: Float): Int = ((SQRT3 * q + SIN60 * r) * spacing).toInt()

fun hexCenterZ(r: Int, spacing: Float): Int = (1.5f * r * spacing).toInt()

fun hexAxial(x: Int, z: Int, spacing: Float): Long {
  val qFrac = (TAN30 * x - ONE_THIRD * z) / spacing
  val rFrac = (TWO_THIRDS * z) / spacing
  val sFrac = -qFrac - rFrac

  var q = qFrac.roundToInt()
  var r = rFrac.roundToInt()
  val s = sFrac.roundToInt()

  val qDiff = abs(q - qFrac)
  val rDiff = abs(r - rFrac)
  val sDiff = abs(s - sFrac)

  if (qDiff > rDiff && qDiff > sDiff) {
    q = -r - s
  } else if (rDiff > sDiff) {
    r = -q - s
  }

  return packPair(q, r)
}

fun hexQ(axial: Long): Int = axial.first()

fun hexR(axial: Long): Int = axial.second()

fun hexDistance(px: Int, pz: Int, apothem: Float): Float {
  val x = abs(px).toFloat()
  val z = abs(pz)
  val d = (x * 0.5f + z * SIN60)
  return max(d, x) - apothem
}

class HexCellSdf(var centerX: Int = 0, var centerZ: Int = 0, var apothem: Float = 0f) : Sdf2 {

  override fun invoke(x: Int, z: Int): Float {
    return hexDistance(x - centerX, z - centerZ, apothem)
  }
}

class HexGapSdf(
  var centerX: Int = 0,
  var centerZ: Int = 0,
  var apothem: Float = 0f,
  var gap: Float = 0f,
) : Sdf2 {

  override fun invoke(x: Int, z: Int): Float {
    val dx = x - centerX
    val dz = z - centerZ
    val outer = hexDistance(dx, dz, apothem + gap)
    val inner = hexDistance(dx, dz, apothem)
    return max(outer, -inner)
  }
}
