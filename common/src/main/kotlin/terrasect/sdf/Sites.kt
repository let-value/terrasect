package terrasect.sdf

import kotlin.math.*

data class Site(
    var x: Double = 0.0,
    var z: Double = 0.0,
    var budget: Double = 0.0,
)

const val ATTEMPTS_PER_SITE = 32
const val HASH_MIX_1 = -0x7a143595a75d9f3fL
const val HASH_MIX_2 = -0x64b9d715d07a173fL
const val SM64_GAMMA = -7046029254386353131L
const val SM64_MIX_1 = -4658895280553007687L
const val SM64_MIX_2 = -7723592293110705685L

private class SplitMix64(seed: Long) {
  private var state = seed

  fun nextLong(): Long {
    state += SM64_GAMMA
    var z = state
    z = (z xor (z ushr 30)) * SM64_MIX_1
    z = (z xor (z ushr 27)) * SM64_MIX_2
    return z xor (z ushr 31)
  }

  fun nextDouble(): Double {
    return (nextLong() ushr 11) * (1.0 / (1L shl 53))
  }
}

private fun mixSeed(value: Long): Long {
  var x = value
  x = (x xor (x ushr 33)) * HASH_MIX_1
  x = (x xor (x ushr 29)) * HASH_MIX_2
  return x xor (x ushr 32)
}

private fun cellSeed(seed: Long, siteCount: Int): Long {
  val mixed = seed xor (siteCount.toLong() * SM64_GAMMA)
  return mixSeed(mixed)
}

fun getSites(
    seed: Long,
    bounds: SdfBounds,
    budgets: IntArray,
    sdf: Sdf2,
): Array<Site> {
  if (budgets.isEmpty()) return emptyArray()

  val normalizedBudgets =
      DoubleArray(budgets.size) { index -> budgets[index].coerceAtLeast(0).toDouble() }
  val order = normalizedBudgets.indices.sortedByDescending { normalizedBudgets[it] }

  val rng = SplitMix64(cellSeed(seed, budgets.size))
  val localX = DoubleArray(budgets.size)
  val localZ = DoubleArray(budgets.size)
  val maxBudget = normalizedBudgets.maxOrNull() ?: 0.0
  val cellSize = max(1.0, maxBudget)
  val grid = HashMap<Long, MutableList<Int>>(budgets.size * 2)
  // relaxIterations is kept for compatibility; it just adds more candidate attempts.
  val attempts = (ATTEMPTS_PER_SITE).coerceAtLeast(8)
  val gradientEps = max(1e-3, max(bounds.spanX, bounds.spanZ) * 1e-4)

  fun cellKey(cx: Int, cz: Int): Long {
    return (cx.toLong() shl 32) xor (cz.toLong() and 0xFFFF_FFFFL)
  }

  fun insert(index: Int) {
    val cx = floor(localX[index] / cellSize).toInt()
    val cz = floor(localZ[index] / cellSize).toInt()
    val key = cellKey(cx, cz)
    val bucket = grid.getOrPut(key) { mutableListOf() }
    bucket.add(index)
  }

  for (index in order) {
    val budget = normalizedBudgets[index]
    var bestScore = Double.NEGATIVE_INFINITY
    var bestX = 0.0
    var bestZ = 0.0
    var bestInsideScore = Double.NEGATIVE_INFINITY
    var bestInsideX = 0.0
    var bestInsideZ = 0.0

    for (attempt in 0 until attempts) {
      val candidateX = bounds.minX + rng.nextDouble() * (bounds.maxX - bounds.minX)
      val candidateZ = bounds.minZ + rng.nextDouble() * (bounds.maxZ - bounds.minZ)
      val boundaryDistance = sdf(candidateX, candidateZ) + budget
      val boundaryClearance = -boundaryDistance

      var neighborClearance = Double.POSITIVE_INFINITY
      val cx = floor(candidateX / cellSize).toInt()
      val cz = floor(candidateZ / cellSize).toInt()
      val neighborRange = if (maxBudget <= 0.0) 1 else ceil((budget + maxBudget) / cellSize).toInt()

      for (gx in cx - neighborRange..cx + neighborRange) {
        for (gz in cz - neighborRange..cz + neighborRange) {
          val bucket = grid[cellKey(gx, gz)] ?: continue
          for (other in bucket) {
            val dx = candidateX - localX[other]
            val dz = candidateZ - localZ[other]
            val distance = sqrt(dx * dx + dz * dz)
            val minDistance = budget + normalizedBudgets[other]
            val clearance = distance - minDistance
            if (clearance < neighborClearance) {
              neighborClearance = clearance
            }
          }
        }
      }

      val score = min(boundaryClearance, neighborClearance)
      if (score > bestScore) {
        bestScore = score
        bestX = candidateX
        bestZ = candidateZ
      }
      if (boundaryClearance >= 0.0 && score > bestInsideScore) {
        bestInsideScore = score
        bestInsideX = candidateX
        bestInsideZ = candidateZ
      }
    }

    var chosenX = if (bestInsideScore > Double.NEGATIVE_INFINITY) bestInsideX else bestX
    var chosenZ = if (bestInsideScore > Double.NEGATIVE_INFINITY) bestInsideZ else bestZ

    val boundaryDistance = sdf(chosenX, chosenZ) + budget
    if (boundaryDistance > 0.0) {
      val (gx, gz) =
          numericGradient(
              sdf,
              chosenX,
              chosenZ,
              gradientEps,
          )
      val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-6)
      chosenX -= gx / length * boundaryDistance
      chosenZ -= gz / length * boundaryDistance
    }

    localX[index] = chosenX
    localZ[index] = chosenZ
    insert(index)
  }

  val sites = Array(budgets.size) { Site() }
  for (i in normalizedBudgets.indices) {
    sites[i].x = localX[i]
    sites[i].z = localZ[i]
    sites[i].budget = normalizedBudgets[i]
  }

  return sites
}
