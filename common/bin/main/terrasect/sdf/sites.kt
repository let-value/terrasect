package terrasect.sdf

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

data class Site(val x: Int, val z: Int, val radius: Float)

const val attempts = 30
const val outsidePenaltyWeight = 200f
const val goodPenaltyEps = 1e-9f
const val greatClearanceFactor = 0.25f
const val earlyStopStreak = 5
const val relaxationPasses = 2
const val relaxationStep = 4
const val relaxationMove = 0.6f

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

  if (sites.size > 1) {
    relaxSites(sites, sdf, bounds)
  }

  return sites
}

private fun relaxSites(sites: MutableList<Site>, sdf: Sdf2, bounds: SdfBounds) {
  val count = sites.size
  if (count <= 1) {
    return
  }

  repeat(relaxationPasses) {
    val sumX = FloatArray(count)
    val sumZ = FloatArray(count)
    val samples = IntArray(count)

    var z = bounds.minZ
    while (z < bounds.maxZ) {
      var x = bounds.minX
      while (x < bounds.maxX) {
        if (sdf(x, z) <= 0f) {
          val cellIndex = nearestPowerSiteIndex(x, z, sites)
          sumX[cellIndex] += x
          sumZ[cellIndex] += z
          samples[cellIndex] += 1
        }
        x += relaxationStep
      }
      z += relaxationStep
    }

    var moved = false
    for (i in 0 until count) {
      val sampleCount = samples[i]
      if (sampleCount == 0) {
        continue
      }

      val site = sites[i]
      val targetX = (sumX[i] / sampleCount).roundToInt()
      val targetZ = (sumZ[i] / sampleCount).roundToInt()
      val nextX = (site.x + (targetX - site.x) * relaxationMove).roundToInt()
      val nextZ = (site.z + (targetZ - site.z) * relaxationMove).roundToInt()
      val clampedX = nextX.coerceIn(bounds.minX, bounds.maxX - 1)
      val clampedZ = nextZ.coerceIn(bounds.minZ, bounds.maxZ - 1)
      if (sdf(clampedX, clampedZ) <= 0f && (clampedX != site.x || clampedZ != site.z)) {
        sites[i] = Site(clampedX, clampedZ, site.radius)
        moved = true
      }
    }

    if (!moved) {
      return
    }
  }
}

private fun nearestPowerSiteIndex(x: Int, z: Int, sites: List<Site>): Int {
  var bestIndex = 0
  var bestPower = Float.POSITIVE_INFINITY

  for (i in sites.indices) {
    val site = sites[i]
    val dx = x - site.x
    val dz = z - site.z
    val dist = hypot(dx.toDouble(), dz.toDouble()).toFloat()
    val power = dist - site.radius
    if (power < bestPower) {
      bestPower = power
      bestIndex = i
    }
  }

  return bestIndex
}
