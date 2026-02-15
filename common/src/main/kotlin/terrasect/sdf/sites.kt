package terrasect.sdf

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

data class Site(val x: Int, val z: Int, val radius: Float)

const val attempts = 30
const val outsidePenaltyWeight = 25f
const val goodPenaltyEps = 1e-9f
const val greatClearanceFactor = 0.25f
const val earlyStopStreak = 5

fun getSites(seed: Long, sdf: Sdf2, bounds: SdfBounds, budgets: LongArray): List<Site> {
  val rng = Random(seed)

  val radii = budgets.map { sqrt(it.coerceAtLeast(0) / PI).toFloat() }.sortedDescending()
  val sites = ArrayList<Site>(radii.size)

  fun compositeSdf(x: Int, z: Int): Float {
    var minDist = Float.POSITIVE_INFINITY

    for (site in sites) {
      val dx = x - site.x
      val dz = z - site.z
      val distToCenter = hypot(dx.toFloat(), dz.toFloat())
      val circleDist = distToCenter - site.radius

      if (circleDist < minDist) {
        minDist = circleDist
      }
    }

    return minDist
  }

  for (radius in radii) {
    var bestX = rng.nextInt(bounds.minX, bounds.maxX)
    var bestY = rng.nextInt(bounds.minZ, bounds.maxZ)
    var bestPenalty = Float.POSITIVE_INFINITY
    var bestClearance = Float.NEGATIVE_INFINITY
    var streak = 0
    val greatClearance = radius * greatClearanceFactor

    for (i in 0 until attempts) {
      val x = rng.nextInt(bounds.minX, bounds.maxX)
      val z = rng.nextInt(bounds.minZ, bounds.maxZ)

      val dist = sdf(x, z)
      val boundaryClearance = -dist
      val outsidePenalty = if (dist > 0f) dist else 0f

      val neighborClearance = compositeSdf(x, z)
      val overlapNeighbor = (radius - neighborClearance).coerceAtLeast(0f)
      val overlapBoundary = (radius - boundaryClearance).coerceAtLeast(0f)
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
