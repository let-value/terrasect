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
    val cellPower = cellDist - cell.radius

    // Exact sign for the weighted Voronoi partition used by getCellIndex:
    // inside cell <=> cellPower <= otherPower for every other site.
    var maxConstraint = Float.NEGATIVE_INFINITY
    for (j in sites.indices) {
      if (j == index) {
        continue
      }
      val other = sites[j]
      val odx = x - other.x
      val odz = z - other.z
      val otherDist = hypot(odx.toDouble(), odz.toDouble()).toFloat()
      val otherPower = otherDist - other.radius
      val constraint = cellPower - otherPower
      if (constraint > maxConstraint) {
        maxConstraint = constraint
      }
    }

    return maxConstraint
  }
}
