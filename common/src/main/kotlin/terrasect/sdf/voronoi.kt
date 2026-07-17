package terrasect.sdf

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

// The metric shapes the character of voronoi cells: euclidean gives organic polygons, manhattan
// diamond-faceted crystals, chebyshev square-faceted blocks.
enum class SiteMetric {
  EUCLIDEAN,
  MANHATTAN,
  CHEBYSHEV;

  fun distance(dx: Float, dz: Float): Float =
    when (this) {
      EUCLIDEAN -> hypot(dx, dz)
      MANHATTAN -> abs(dx) + abs(dz)
      CHEBYSHEV -> max(abs(dx), abs(dz))
    }
}

class VoronoiCellSdf : Sdf2 {
  var sites: List<Site> = emptyList()
  var index: Int = 0
  var metric: SiteMetric = SiteMetric.EUCLIDEAN

  override fun invoke(x: Int, z: Int): Float {
    if (sites.isEmpty()) {
      return Float.NEGATIVE_INFINITY
    }

    val cell = sites[index]
    val cellDist = metric.distance((x - cell.x).toFloat(), (z - cell.z).toFloat())
    val cellPower = cellDist - cell.radius

    var maxConstraint = Float.NEGATIVE_INFINITY
    for (j in sites.indices) {
      if (j == index) {
        continue
      }
      val other = sites[j]
      val otherDist = metric.distance((x - other.x).toFloat(), (z - other.z).toFloat())
      val otherPower = otherDist - other.radius
      val constraint = cellPower - otherPower
      if (constraint > maxConstraint) {
        maxConstraint = constraint
      }
    }

    return maxConstraint
  }
}
