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
    var minDist = Double.MIN_VALUE

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
    var bestValidX = 0.0
    var bestValidY = 0.0
    var maxClearance = -1.0
    var foundValid = false

    var bestBadX = 0.0
    var bestBadY = 0.0
    var minPenalty = Double.POSITIVE_INFINITY
    var foundBad = false

    for (i in 0 until attempts) {
      val x = bounds.minX + rng.nextDouble() * (bounds.maxX - bounds.minX)
      val z = bounds.minZ + rng.nextDouble() * (bounds.maxZ - bounds.minZ)

      if (sdf(x, z) > 0.0) continue

      val dist = compositeSdf(x, z)

      if (dist > r) {
        val clearance = dist - r
        if (clearance > maxClearance) {
          maxClearance = clearance
          bestValidX = x
          bestValidY = z
          foundValid = true
        }
      } else {
        // Invalid: overlaps with existing sites
        val penalty = r - dist
        if (penalty < minPenalty) {
          minPenalty = penalty
          bestBadX = x
          bestBadY = z
          foundBad = true
        }
      }
    }

    // Place best candidate
    val site =
        when {
          foundValid -> Site(bestValidX, bestValidY, r)
          foundBad -> Site(bestBadX, bestBadY, r)
          else -> null
        }

    site?.let { sites.add(it) }
  }

  return sites
}
