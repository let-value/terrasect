package terrasect.sdf

import kotlin.math.hypot

class VoronoiCellSdf : Sdf2 {
  var sites: List<Site> = emptyList()
  var index: Int = 0

  override fun invoke(x: Int, z: Int): Float {
    if (sites.isEmpty()) {
      return Float.NEGATIVE_INFINITY
    }

    val cell = sites[index]
    val dx = x - cell.x
    val dz = z - cell.z
    val cellDist = hypot(dx.toDouble(), dz.toDouble()).toFloat()
    val cellSafe = cellDist.coerceAtLeast(1e-6f)
    val cellPower = cellDist - cell.radius
    val cellGradX = dx / cellSafe
    val cellGradZ = dz / cellSafe

    var minEdgeDist = Float.POSITIVE_INFINITY
    for (j in sites.indices) {
      if (j == index) {
        continue
      }
      val other = sites[j]
      val odx = x - other.x
      val odz = z - other.z
      val otherDist = hypot(odx.toDouble(), odz.toDouble()).toFloat()
      val otherSafe = otherDist.coerceAtLeast(1e-6f)
      val otherPower = otherDist - other.radius
      val edgeValue = (otherPower - cellPower) / 2f
      val otherGradX = odx / otherSafe
      val otherGradZ = odz / otherSafe
      val gradX = (otherGradX - cellGradX) / 2f
      val gradZ = (otherGradZ - cellGradZ) / 2f
      val gradNorm = hypot(gradX.toDouble(), gradZ.toDouble()).toFloat().coerceAtLeast(1e-6f)
      val edgeDist = edgeValue / gradNorm
      if (edgeDist < minEdgeDist) {
        minEdgeDist = edgeDist
      }
    }

    return -minEdgeDist
  }
}
