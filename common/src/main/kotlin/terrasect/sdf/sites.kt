package terrasect.sdf

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random

data class Site(val x: Double, val z: Double, val radius: Double)

const val attempts = 30
const val outsidePenaltyWeight = 25.0
const val goodPenaltyEps = 1e-9
const val greatClearanceFactor = 0.25
const val earlyStopStreak = 5

fun getSites(seed: Long, sdf: Sdf2, bounds: SdfBounds, budgets: DoubleArray): List<Site> {
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

  for (radius in radii) {
    var bestX = bounds.minX + rng.nextDouble() * (bounds.maxX - bounds.minX)
    var bestY = bounds.minZ + rng.nextDouble() * (bounds.maxZ - bounds.minZ)
    var bestPenalty = Double.POSITIVE_INFINITY
    var bestClearance = Double.NEGATIVE_INFINITY
    var streak = 0
    val greatClearance = radius * greatClearanceFactor

    for (i in 0 until attempts) {
      val x = bounds.minX + rng.nextDouble() * (bounds.maxX - bounds.minX)
      val z = bounds.minZ + rng.nextDouble() * (bounds.maxZ - bounds.minZ)

      val dist = sdf(x, z)
      val boundaryClearance = -dist
      val outsidePenalty = if (dist > 0.0) dist else 0.0

      val neighborClearance = compositeSdf(x, z)
      val overlapNeighbor = (radius - neighborClearance).coerceAtLeast(0.0)
      val overlapBoundary = (radius - boundaryClearance).coerceAtLeast(0.0)
      val penalty = overlapNeighbor + overlapBoundary + outsidePenalty * outsidePenaltyWeight
      val clearance = minOf(neighborClearance, boundaryClearance) - radius

      if (penalty < bestPenalty || (penalty == bestPenalty && clearance > bestClearance)) {
        bestPenalty = penalty
        bestClearance = clearance
        bestX = x
        bestY = z
        if (penalty <= goodPenaltyEps && clearance >= greatClearance) {
          streak += 1
          if (streak >= earlyStopStreak) {
            break
          }
        } else {
          streak = 0
        }
      }
    }

    sites.add(Site(bestX, bestY, radius))
  }

  return sites
}
