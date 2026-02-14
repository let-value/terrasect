package terrasect.sdf

import kotlin.math.hypot

class VoronoiCellSdf : Sdf2 {
  var sites: List<Site> = emptyList()
  var index: Int = 0

  override fun invoke(x: Double, z: Double): Double {
    if (sites.isEmpty()) {
      return Double.NEGATIVE_INFINITY
    }

    val cell = sites[index]
    val dx = x - cell.x
    val dz = z - cell.z
    val cellDist = hypot(dx, dz)
    val cellPower = cellDist - cell.radius

    var minEdgeDist = Double.POSITIVE_INFINITY
    for (j in sites.indices) {
      if (j == index) {
        continue
      }
      val other = sites[j]
      val odx = x - other.x
      val odz = z - other.z
      val otherDist = hypot(odx, odz)
      val otherPower = otherDist - other.radius
      val edgeDist = (otherPower - cellPower) / 2.0
      if (edgeDist < minEdgeDist) {
        minEdgeDist = edgeDist
      }
    }

    return -minEdgeDist
  }
}
