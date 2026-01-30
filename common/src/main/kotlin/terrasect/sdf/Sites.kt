package terrasect.sdf

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

data class Site(val x: Double, val z: Double, val radius: Double)

fun distance(x0: Double, z0: Double, x1: Double, z1: Double): Double = hypot(x0 - x1, z0 - z1)

fun getSites(
    seed: Long,
    sdf: Sdf2,
    bounds: SdfBounds,
    budgets: DoubleArray,
): List<Site> {
  val rng = Random(seed)

  val radii = budgets.map { sqrt(it.coerceAtLeast(0.0) / PI) }.sortedDescending()
  val sites = ArrayList<Site>(radii.size)

  fun compositeSdf(x: Double, z: Double): Double {
    var minDist = Double.POSITIVE_INFINITY

    for (site in sites) {
      val dx = x - site.x
      val dz = z - site.z
      val distToCenter = sqrt(dx * dx + dz * dz)
      val circleDist = distToCenter - site.radius

      if (circleDist < minDist) {
        minDist = circleDist
      }
    }

    return minDist
  }

  val attempts = 30

  for (r in radii) {
    var bestX = 0.0
    var bestY = 0.0
    var bestPenalty = Double.POSITIVE_INFINITY
    var bestClearance = Double.NEGATIVE_INFINITY
    var foundAny = false

    for (i in 0 until attempts) {
      val x = bounds.minX + rng.nextDouble() * (bounds.maxX - bounds.minX)
      val z = bounds.minZ + rng.nextDouble() * (bounds.maxZ - bounds.minZ)

      val sdfValue = sdf(x, z)
      if (sdfValue > 0.0) continue
      val boundaryClearance = -sdfValue

      val neighborClearance = compositeSdf(x, z)
      val overlapNeighbor = (r - neighborClearance).coerceAtLeast(0.0)
      val overlapBoundary = (r - boundaryClearance).coerceAtLeast(0.0)
      val penalty = overlapNeighbor + overlapBoundary
      val clearance = minOf(neighborClearance, boundaryClearance) - r

      if (penalty < bestPenalty || (penalty == bestPenalty && clearance > bestClearance)) {
        bestPenalty = penalty
        bestClearance = clearance
        bestX = x
        bestY = z
        foundAny = true
      }
    }

    // Place best candidate
    if (foundAny) {
      sites.add(Site(bestX, bestY, r))
    }
  }

  return sites
}
