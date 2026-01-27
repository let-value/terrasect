package terrasect.strategies

import terrasect.definition.HexSettings
import terrasect.definition.Strategy
import terrasect.definition.StrategyId
import terrasect.generation.Context
import terrasect.generation.TraversalStep
import kotlin.math.*

object HexStrategy {
  val discriminator = StrategyId.HEX.value

  data class GetCellResult(
      var q: Long = 0,
      var r: Long = 0,
      var distance: Double = 0.0,
      var isGap: Boolean = false,
  )

  data class Site(
      var x: Double = 0.0,
      var z: Double = 0.0,
      var budget: Double = 0.0,
  )

  val cellRef: ThreadLocal<GetCellResult> = ThreadLocal.withInitial { GetCellResult() }

  private val SQRT3 = sqrt(3.0)
  private val SIN60 = sin(Math.toRadians(60.0))
  private val COS60 = cos(Math.toRadians(60.0))
  private val TAN30 = tan(Math.toRadians(30.0))
  private const val ONE_THIRD = 1.0 / 3.0
  private const val TWO_THIRDS = 2.0 / 3.0
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

  fun getCell(x: Long, z: Long, radius: Int, gap: Int = 0): GetCellResult {
    val spacing = (radius + gap.coerceAtLeast(0))

    val qFrac = (TAN30 * x - ONE_THIRD * z) / spacing
    val rFrac = (TWO_THIRDS * z) / spacing
    val sFrac = -qFrac - rFrac

    var q = qFrac.roundToLong()
    var r = rFrac.roundToLong()
    val s = sFrac.roundToLong()

    val qDiff = abs(q - qFrac)
    val rDiff = abs(r - rFrac)
    val sDiff = abs(s - sFrac)

    if (qDiff > rDiff && qDiff > sDiff) {
      q = -r - s
    } else if (rDiff > sDiff) {
      r = -q - s
    }

    val centerX = (SQRT3 * q + SIN60 * r) * spacing
    val centerZ = (1.5 * r) * spacing

    val localX = x - centerX
    val localZ = z - centerZ

    var distance = hexDistance(localX, localZ, radius)
    val isGap = distance > 0f && gap > 0
    if (isGap) {
      distance = -distance
    }

    val cell = cellRef.get()
    cell.q = q
    cell.r = r
    cell.distance = distance
    cell.isGap = isGap

    return cell
  }

  fun hexDistance(px: Double, pz: Double, radius: Int): Double {
    val x = abs(px)
    val z = abs(pz)

    val d = x * 0.5 + z * SIN60

    return max(d, x) - radius
  }

  private fun hexGradient(px: Double, pz: Double): Pair<Double, Double> {
    val sx = if (px < 0.0) -1.0 else 1.0
    val sz = if (pz < 0.0) -1.0 else 1.0
    val x = abs(px)
    val z = abs(pz)
    val d = x * 0.5 + z * SIN60

    return if (x >= d) {
      Pair(sx, 0.0)
    } else {
      Pair(sx * 0.5, sz * SIN60)
    }
  }

  private fun mixSeed(value: Long): Long {
    var x = value
    x = (x xor (x ushr 33)) * HASH_MIX_1
    x = (x xor (x ushr 29)) * HASH_MIX_2
    return x xor (x ushr 32)
  }

  private fun cellSeed(seed: Long, q: Long, r: Long, siteCount: Int): Long {
    val mixed =
        seed xor mixSeed(q) xor (mixSeed(r) shl 1) xor (siteCount.toLong() * SM64_GAMMA)
    return mixSeed(mixed)
  }

  fun getSites(
      seed: Long,
      q: Long,
      r: Long,
      radius: Int,
      budgets: IntArray,
      gap: Int = 0,
      relaxIterations: Int = 24,
      attemptsPerSite: Int = 28,
  ): Array<Site> {
    val doubleBudgets = DoubleArray(budgets.size) { budgets[it].toDouble() }
    return getSites(seed, q, r, radius, doubleBudgets, gap, relaxIterations, attemptsPerSite)
  }

  fun getSites(
      seed: Long,
      q: Long,
      r: Long,
      radius: Int,
      budgets: DoubleArray,
      gap: Int = 0,
      relaxIterations: Int = 24,
      attemptsPerSite: Int = 28,
  ): Array<Site> {
    if (budgets.isEmpty()) return emptyArray()

    val spacing = (radius + gap.coerceAtLeast(0))
    val centerX = (SQRT3 * q + SIN60 * r) * spacing
    val centerZ = (1.5 * r) * spacing
    val maxZ = radius / SIN60

    val normalizedBudgets =
        DoubleArray(budgets.size) { index -> budgets[index].coerceAtLeast(0.0) }
    val order = normalizedBudgets.indices.sortedByDescending { normalizedBudgets[it] }

    val rng = SplitMix64(cellSeed(seed, q, r, budgets.size))
    val localX = DoubleArray(budgets.size)
    val localZ = DoubleArray(budgets.size)
    val maxBudget = normalizedBudgets.maxOrNull() ?: 0.0
    val cellSize = max(1.0, maxBudget)
    val grid = HashMap<Long, MutableList<Int>>(budgets.size * 2)
    // relaxIterations is kept for compatibility; it just adds more candidate attempts.
    val attempts = (attemptsPerSite + relaxIterations.coerceAtLeast(0)).coerceAtLeast(8)

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
        val candidateX = (rng.nextDouble() * 2.0 - 1.0) * radius
        val candidateZ = (rng.nextDouble() * 2.0 - 1.0) * maxZ
        val boundaryDistance = hexDistance(candidateX, candidateZ, radius) + budget
        val boundaryClearance = -boundaryDistance

        var neighborClearance = Double.POSITIVE_INFINITY
        val cx = floor(candidateX / cellSize).toInt()
        val cz = floor(candidateZ / cellSize).toInt()
        val neighborRange =
            if (maxBudget <= 0.0) 1 else ceil((budget + maxBudget) / cellSize).toInt()

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

      val boundaryDistance = hexDistance(chosenX, chosenZ, radius) + budget
      if (boundaryDistance > 0.0) {
        val (gx, gz) = hexGradient(chosenX, chosenZ)
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
      sites[i].x = centerX + localX[i]
      sites[i].z = centerZ + localZ[i]
      sites[i].budget = normalizedBudgets[i]
    }

    return sites
  }

  fun traverse(
      context: Context,
      step: TraversalStep,
      settings: HexSettings,
  ): TraversalStep {
    val cell = getCell(step.x, step.z, context.region.budget, settings.ringRegion?.budget ?: 0)

    step.id.put(discriminator)
    step.id.putLong(cell.q)
    step.id.putLong(cell.r)
    step.id.putChar(Strategy.SEPARATOR)

    step.region =
        if (cell.isGap && settings.ringRegion != null) settings.ringRegion else settings.children

    return step
  }
}
