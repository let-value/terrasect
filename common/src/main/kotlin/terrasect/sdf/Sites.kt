package terrasect.sdf

import kotlin.math.*

data class Site(
    var x: Double = 0.0,
    var z: Double = 0.0,
    var budget: Double = 0.0,
)

private data class CandidateScore(
    val x: Double,
    val z: Double,
    val sdfValue: Double,
    val score: Double,
    val minOverlapClearance: Double,
    val minOverlapDx: Double,
    val minOverlapDz: Double,
    val neighborCount: Int,
)

private const val ATTEMPTS_PER_SITE = 32
private const val OVERLAP_PENALTY = 0.8
private const val OUTSIDE_PENALTY = 1.0
private const val BOUNDARY_PENALTY = 1.0
private const val SPREAD_BIAS = 0.35
private const val EDGE_BIAS = 0.25
private const val HASH_MIX_1 = -0x7a143595a75d9f3fL
private const val HASH_MIX_2 = -0x64b9d715d07a173fL
private const val SM64_GAMMA = -7046029254386353131L
private const val SM64_MIX_1 = -4658895280553007687L
private const val SM64_MIX_2 = -7723592293110705685L

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

private fun cellKey(cx: Int, cz: Int): Long {
  return (cx.toLong() shl 32) xor (cz.toLong() and 0xFFFF_FFFFL)
}

fun getSites(
    seed: Long,
    bounds: SdfBounds,
    budgets: DoubleArray,
    sdf: Sdf2,
): Array<Site> {
  if (budgets.isEmpty()) return emptyArray()

  val normalizedBudgets =
      DoubleArray(budgets.size) { index -> sqrt(budgets[index].coerceAtLeast(0.0) / PI) }
  val order = normalizedBudgets.indices.sortedByDescending { normalizedBudgets[it] }

  val rng = SplitMix64(cellSeed(seed, budgets.size))
  val localX = DoubleArray(budgets.size)
  val localZ = DoubleArray(budgets.size)
  val maxBudget = normalizedBudgets.maxOrNull() ?: 0.0
  val cellSize = max(1.0, maxBudget)
  val invCellSize = 1.0 / cellSize
  val grid = HashMap<Long, MutableList<Int>>(budgets.size * 2)
  val placed = IntArray(budgets.size)
  var placedCount = 0
  val attempts = ATTEMPTS_PER_SITE.coerceAtLeast(8)
  val insideAttempts = max(4, attempts / 4)
  val boundaryAttempts = max(2, attempts / 4)
  val neighborAttempts = max(2, attempts / 3)
  val insideTries = 6
  val gradientEps = max(1e-3, max(bounds.spanX, bounds.spanZ) * 1e-4)
  val spanX = bounds.spanX
  val spanZ = bounds.spanZ
  val centerX = (bounds.minX + bounds.maxX) * 0.5
  val centerZ = (bounds.minZ + bounds.maxZ) * 0.5
  val maxCenterDistance = max(1e-6, hypot(spanX, spanZ) * 0.5)

  for (index in order) {
    val budget = normalizedBudgets[index]
    val neighborRange = max(1, ceil((budget + maxBudget) / cellSize).toInt())
    val hasNeighbors = grid.isNotEmpty()
    var bestScore = Double.NEGATIVE_INFINITY
    var bestX = 0.0
    var bestZ = 0.0
    var bestContainedScore = Double.NEGATIVE_INFINITY
    var bestContainedX = 0.0
    var bestContainedZ = 0.0
    var bestNonOverlapScore = Double.NEGATIVE_INFINITY
    var bestNonOverlapX = 0.0
    var bestNonOverlapZ = 0.0
    var bestContainedNonOverlapScore = Double.NEGATIVE_INFINITY
    var bestContainedNonOverlapX = 0.0
    var bestContainedNonOverlapZ = 0.0

    fun scoreCandidate(candidateX: Double, candidateZ: Double, sdfValue: Double): CandidateScore {
      val boundaryClearance = -(sdfValue + budget)
      val boundaryTerm = min(boundaryClearance, 0.0) * BOUNDARY_PENALTY
      val edgeBias = if (maxBudget > 0.0) (maxBudget - budget) / maxBudget else 0.0
      val edgeTerm =
          if (boundaryClearance > 0.0) -boundaryClearance * EDGE_BIAS * edgeBias else 0.0
      val centerDistance = hypot(candidateX - centerX, candidateZ - centerZ)
      val spreadTerm =
          if (boundaryClearance > 0.0 && edgeBias > 0.0) {
            (centerDistance / maxCenterDistance) * SPREAD_BIAS * edgeBias
          } else {
            0.0
          }
      val baseScore = boundaryTerm + edgeTerm + spreadTerm

      if (!hasNeighbors) {
        return CandidateScore(
            candidateX,
            candidateZ,
            sdfValue,
            baseScore,
            Double.POSITIVE_INFINITY,
            0.0,
            0.0,
            0,
        )
      }

      var minOverlapClearance = Double.POSITIVE_INFINITY
      var minOverlapDx = 0.0
      var minOverlapDz = 0.0
      var overlapSum = 0.0
      var neighborCount = 0
      val cx = floor(candidateX * invCellSize).toInt()
      val cz = floor(candidateZ * invCellSize).toInt()

      for (gx in cx - neighborRange..cx + neighborRange) {
        for (gz in cz - neighborRange..cz + neighborRange) {
          val bucket = grid[cellKey(gx, gz)] ?: continue
          for (other in bucket) {
            val dx = candidateX - localX[other]
            val dz = candidateZ - localZ[other]
            val distance = sqrt(dx * dx + dz * dz)
            val overlapClearance = distance - (budget + normalizedBudgets[other])
            neighborCount++
            if (overlapClearance < 0.0) {
              overlapSum += overlapClearance
            }
            if (overlapClearance < minOverlapClearance) {
              minOverlapClearance = overlapClearance
              minOverlapDx = dx
              minOverlapDz = dz
            }
          }
        }
      }

      if (neighborCount == 0) {
        return CandidateScore(
            candidateX,
            candidateZ,
            sdfValue,
            baseScore,
            Double.POSITIVE_INFINITY,
            0.0,
            0.0,
            0,
        )
      }

      val overlapPenalty = overlapSum / neighborCount
      val outsidePenalty = -max(sdfValue, 0.0) * OUTSIDE_PENALTY
      val score =
          minOverlapClearance +
              overlapPenalty * OVERLAP_PENALTY +
              outsidePenalty +
              boundaryTerm +
              edgeTerm

      return CandidateScore(
          candidateX,
          candidateZ,
          sdfValue,
          score,
          minOverlapClearance,
          minOverlapDx,
          minOverlapDz,
          neighborCount,
      )
    }

    fun recordCandidate(candidate: CandidateScore) {
      val boundaryClearance = -(candidate.sdfValue + budget)
      val contained = boundaryClearance >= 0.0
      val nonOverlap = candidate.minOverlapClearance >= 0.0

      if (candidate.score > bestScore) {
        bestScore = candidate.score
        bestX = candidate.x
        bestZ = candidate.z
      }
      if (contained && candidate.score > bestContainedScore) {
        bestContainedScore = candidate.score
        bestContainedX = candidate.x
        bestContainedZ = candidate.z
      }
      if (nonOverlap && candidate.score > bestNonOverlapScore) {
        bestNonOverlapScore = candidate.score
        bestNonOverlapX = candidate.x
        bestNonOverlapZ = candidate.z
      }
      if (contained && nonOverlap && candidate.score > bestContainedNonOverlapScore) {
        bestContainedNonOverlapScore = candidate.score
        bestContainedNonOverlapX = candidate.x
        bestContainedNonOverlapZ = candidate.z
      }
    }

    fun evaluateCandidate(candidateX: Double, candidateZ: Double, candidateSdf: Double): CandidateScore {
      var x = candidateX
      var z = candidateZ
      var sdfValue = if (candidateSdf.isNaN()) sdf(x, z) else candidateSdf
      if (sdfValue > 0.0) {
        val (gx, gz) = numericGradient(sdf, x, z, gradientEps)
        val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-6)
        x -= gx / length * sdfValue
        z -= gz / length * sdfValue
        sdfValue = sdf(x, z)
      }

      val scored = scoreCandidate(x, z, sdfValue)
      recordCandidate(scored)

      val boundaryClearance = -(sdfValue + budget)
      if (boundaryClearance < 0.0) {
        val (gx, gz) = numericGradient(sdf, x, z, gradientEps)
        val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-6)
        val push = -boundaryClearance
        val containedX = x - gx / length * push
        val containedZ = z - gz / length * push
        val containedSdf = sdf(containedX, containedZ)
        val containedScore = scoreCandidate(containedX, containedZ, containedSdf)
        recordCandidate(containedScore)
      }

      return scored
    }

    for (attempt in 0 until attempts) {
      var candidateX = 0.0
      var candidateZ = 0.0
      var sdfValue = Double.POSITIVE_INFINITY
      if (attempt < insideAttempts) {
        val targetSdf = -budget
        var foundContained = false
        var bestContainedSdf = Double.NEGATIVE_INFINITY
        var bestContainedSampleX = 0.0
        var bestContainedSampleZ = 0.0
        var foundInside = false
        var bestInsideSdf = Double.NEGATIVE_INFINITY
        var insideSampleX = 0.0
        var insideSampleZ = 0.0
        var tries = 0
        while (tries < insideTries) {
          val sampleX = bounds.minX + rng.nextDouble() * spanX
          val sampleZ = bounds.minZ + rng.nextDouble() * spanZ
          val sampleSdf = sdf(sampleX, sampleZ)
          if (sampleSdf <= targetSdf) {
            if (!foundContained || sampleSdf > bestContainedSdf) {
              foundContained = true
              bestContainedSdf = sampleSdf
              bestContainedSampleX = sampleX
              bestContainedSampleZ = sampleZ
            }
          } else if (sampleSdf <= 0.0) {
            if (!foundInside || sampleSdf > bestInsideSdf) {
              foundInside = true
              bestInsideSdf = sampleSdf
              insideSampleX = sampleX
              insideSampleZ = sampleZ
            }
          }
          tries++
        }
        if (!foundContained) {
          if (foundInside) {
            candidateX = insideSampleX
            candidateZ = insideSampleZ
            sdfValue = bestInsideSdf
          } else {
            candidateX = bounds.minX + rng.nextDouble() * spanX
            candidateZ = bounds.minZ + rng.nextDouble() * spanZ
            sdfValue = sdf(candidateX, candidateZ)
          }
        } else {
          candidateX = bestContainedSampleX
          candidateZ = bestContainedSampleZ
          sdfValue = bestContainedSdf
        }
      } else if (attempt < insideAttempts + boundaryAttempts && budget < maxBudget * 0.85) {
        var sampleX = bounds.minX + rng.nextDouble() * spanX
        var sampleZ = bounds.minZ + rng.nextDouble() * spanZ
        var sampleSdf = sdf(sampleX, sampleZ)
        if (sampleSdf != 0.0) {
          val (gx, gz) = numericGradient(sdf, sampleX, sampleZ, gradientEps)
          val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-6)
          sampleX -= gx / length * sampleSdf
          sampleZ -= gz / length * sampleSdf
          sampleSdf = 0.0
        }
        val (gx, gz) = numericGradient(sdf, sampleX, sampleZ, gradientEps)
        val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-6)
        candidateX = sampleX - gx / length * budget
        candidateZ = sampleZ - gz / length * budget
        sdfValue = sdf(candidateX, candidateZ)
      } else if (hasNeighbors && attempt < insideAttempts + boundaryAttempts + neighborAttempts) {
        val slot = (rng.nextDouble() * placedCount).toInt().coerceIn(0, placedCount - 1)
        val other = placed[slot]
        val angle = rng.nextDouble() * PI * 2.0
        val jitter = (rng.nextDouble() - 0.5) * budget * 0.5
        val radius = max(budget * 0.25, budget + normalizedBudgets[other] + jitter)
        candidateX = localX[other] + cos(angle) * radius
        candidateZ = localZ[other] + sin(angle) * radius
        sdfValue = sdf(candidateX, candidateZ)
      } else {
        candidateX = bounds.minX + rng.nextDouble() * spanX
        candidateZ = bounds.minZ + rng.nextDouble() * spanZ
        sdfValue = sdf(candidateX, candidateZ)
      }
      val scored = evaluateCandidate(candidateX, candidateZ, sdfValue)
      if (scored.neighborCount > 0 && scored.minOverlapClearance < 0.0) {
        val dx = scored.minOverlapDx
        val dz = scored.minOverlapDz
        val length = sqrt(dx * dx + dz * dz)
        val push = -scored.minOverlapClearance
        val (dirX, dirZ) =
            if (length > 1e-6) {
              Pair(dx / length, dz / length)
            } else {
              val angle = rng.nextDouble() * PI * 2.0
              Pair(cos(angle), sin(angle))
            }
        val nudgedX = scored.x + dirX * push
        val nudgedZ = scored.z + dirZ * push
        evaluateCandidate(nudgedX, nudgedZ, Double.NaN)
      }
    }

    val useContained = bestContainedScore > Double.NEGATIVE_INFINITY
    val useNonOverlap = bestNonOverlapScore > Double.NEGATIVE_INFINITY
    val useContainedNonOverlap = bestContainedNonOverlapScore > Double.NEGATIVE_INFINITY
    var chosenX =
        when {
          useContainedNonOverlap -> bestContainedNonOverlapX
          useContained -> bestContainedX
          useNonOverlap -> bestNonOverlapX
          else -> bestX
        }
    var chosenZ =
        when {
          useContainedNonOverlap -> bestContainedNonOverlapZ
          useContained -> bestContainedZ
          useNonOverlap -> bestNonOverlapZ
          else -> bestZ
        }
    var chosenSdf = sdf(chosenX, chosenZ)
    if (chosenSdf > 0.0) {
      val (gx, gz) = numericGradient(sdf, chosenX, chosenZ, gradientEps)
      val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-6)
      chosenX -= gx / length * chosenSdf
      chosenZ -= gz / length * chosenSdf
      chosenSdf = sdf(chosenX, chosenZ)
    }
    val boundaryClearance = -(chosenSdf + budget)
    if (boundaryClearance < 0.0) {
      val (gx, gz) = numericGradient(sdf, chosenX, chosenZ, gradientEps)
      val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-6)
      val push = -boundaryClearance
      chosenX -= gx / length * push
      chosenZ -= gz / length * push
    }

    localX[index] = chosenX
    localZ[index] = chosenZ
    val cellX = floor(chosenX * invCellSize).toInt()
    val cellZ = floor(chosenZ * invCellSize).toInt()
    val key = cellKey(cellX, cellZ)
    grid.getOrPut(key) { mutableListOf() }.add(index)
    placed[placedCount] = index
    placedCount++
  }

  return Array(budgets.size) { i -> Site(localX[i], localZ[i], normalizedBudgets[i]) }
}
