package terrasect.strategies

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

typealias Sdf2 = (Double, Double) -> Double
typealias SdfGradient2 = (Double, Double) -> Pair<Double, Double>

data class SdfBounds(
    val minX: Double,
    val maxX: Double,
    val minZ: Double,
    val maxZ: Double,
) {
  val spanX: Double
    get() = maxX - minX

  val spanZ: Double
    get() = maxZ - minZ

  fun expanded(margin: Double): SdfBounds {
    return SdfBounds(
        minX - margin,
        maxX + margin,
        minZ - margin,
        maxZ + margin,
    )
  }
}

object SdfSites {
  data class Site(
      var x: Double = 0.0,
      var z: Double = 0.0,
      var budget: Double = 0.0,
  )

  private const val DEFAULT_CELL_SIZE = 16.0
  private const val DEFAULT_MAX_RADIUS = 2048.0
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

  private fun cellSeed(seed: Long, saltA: Long, saltB: Long, siteCount: Int): Long {
    val mixed =
        seed xor mixSeed(saltA) xor (mixSeed(saltB) shl 1) xor (siteCount.toLong() * SM64_GAMMA)
    return mixSeed(mixed)
  }

  private fun numericGradient(sdf: Sdf2, x: Double, z: Double, eps: Double): Pair<Double, Double> {
    val dx = sdf(x + eps, z) - sdf(x - eps, z)
    val dz = sdf(x, z + eps) - sdf(x, z - eps)
    return Pair(dx / (2.0 * eps), dz / (2.0 * eps))
  }

  fun estimateBounds(
      sdf: Sdf2,
      originX: Double = 0.0,
      originZ: Double = 0.0,
      cellSize: Double = DEFAULT_CELL_SIZE,
      maxRadius: Double = DEFAULT_MAX_RADIUS,
      iso: Double = 0.0,
  ): SdfBounds? {
    require(cellSize > 0.0) { "cellSize must be > 0" }
    require(maxRadius > 0.0) { "maxRadius must be > 0" }

    val maxCells = max(1, ceil(maxRadius / cellSize).toInt())
    val originCellX = floor(originX / cellSize).toInt()
    val originCellZ = floor(originZ / cellSize).toInt()
    val gradientEps = max(1e-3, cellSize * 0.25)

    val originDistance = sdf(originX, originZ) - iso
    var bestX = originX
    var bestZ = originZ
    var bestAbs = abs(originDistance)

    if (originDistance <= 0.0) {
      return floodBounds(
          sdf,
          originX,
          originZ,
          originCellX,
          originCellZ,
          cellSize,
          maxCells,
          iso,
      )
    }

    var seedX = Double.NaN
    var seedZ = Double.NaN

    run search@ {
      for (dz in -maxCells..maxCells) {
        val z = (originCellZ + dz) * cellSize
        for (dx in -maxCells..maxCells) {
          val x = (originCellX + dx) * cellSize
          val distance = sdf(x, z) - iso
          if (distance <= 0.0) {
            seedX = x
            seedZ = z
            return@search
          }
          val distanceAbs = abs(distance)
          if (distanceAbs < bestAbs) {
            bestAbs = distanceAbs
            bestX = x
            bestZ = z
          }
        }
      }
    }

    if (seedX.isNaN()) {
      val boundary = projectToBoundary(sdf, bestX, bestZ, iso, gradientEps) ?: return null
      val (gx, gz) = numericGradient(sdf, boundary.first, boundary.second, gradientEps)
      val length = sqrt(gx * gx + gz * gz)
      if (length <= 1e-8) return null
      seedX = boundary.first - gx / length * (cellSize * 0.5)
      seedZ = boundary.second - gz / length * (cellSize * 0.5)
      if (sdf(seedX, seedZ) - iso > 0.0) return null
    }

    return floodBounds(
        sdf,
        seedX,
        seedZ,
        originCellX,
        originCellZ,
        cellSize,
        maxCells,
        iso,
    )
  }

  private fun projectToBoundary(
      sdf: Sdf2,
      startX: Double,
      startZ: Double,
      iso: Double,
      eps: Double,
  ): Pair<Double, Double>? {
    var x = startX
    var z = startZ
    var distance = sdf(x, z) - iso
    if (distance <= 0.0) return Pair(x, z)

    repeat(24) {
      val (gx, gz) = numericGradient(sdf, x, z, eps)
      val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-8)
      val step = distance.coerceAtMost(eps * 8.0)
      x -= gx / length * step
      z -= gz / length * step
      distance = sdf(x, z) - iso
      if (abs(distance) <= eps) return Pair(x, z)
    }

    return null
  }

  private fun floodBounds(
      sdf: Sdf2,
      seedX: Double,
      seedZ: Double,
      originCellX: Int,
      originCellZ: Int,
      cellSize: Double,
      maxCells: Int,
      iso: Double,
  ): SdfBounds? {
    val startCellX = floor(seedX / cellSize).toInt()
    val startCellZ = floor(seedZ / cellSize).toInt()
    val queue = ArrayDeque<Long>()
    val visited = HashSet<Long>()

    fun key(cx: Int, cz: Int): Long {
      return (cx.toLong() shl 32) xor (cz.toLong() and 0xFFFF_FFFFL)
    }

    fun withinBounds(cx: Int, cz: Int): Boolean {
      return abs(cx - originCellX) <= maxCells && abs(cz - originCellZ) <= maxCells
    }

    queue.add(key(startCellX, startCellZ))

    var minX = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var minZ = Double.POSITIVE_INFINITY
    var maxZ = Double.NEGATIVE_INFINITY
    var any = false

    while (queue.isNotEmpty()) {
      val packed = queue.removeFirst()
      if (!visited.add(packed)) continue
      val cx = (packed shr 32).toInt()
      val cz = packed.toInt()
      if (!withinBounds(cx, cz)) continue
      val x = cx * cellSize
      val z = cz * cellSize
      if (sdf(x, z) - iso > 0.0) continue

      any = true
      minX = min(minX, x)
      maxX = max(maxX, x)
      minZ = min(minZ, z)
      maxZ = max(maxZ, z)

      queue.add(key(cx + 1, cz))
      queue.add(key(cx - 1, cz))
      queue.add(key(cx, cz + 1))
      queue.add(key(cx, cz - 1))
    }

    if (!any) return null
    val half = cellSize * 0.5
    return SdfBounds(minX - half, maxX + half, minZ - half, maxZ + half)
  }

  fun getSites(
      seed: Long,
      budgets: IntArray,
      sdf: Sdf2,
      gradient: SdfGradient2? = null,
      originX: Double = 0.0,
      originZ: Double = 0.0,
      cellSize: Double = DEFAULT_CELL_SIZE,
      maxRadius: Double = DEFAULT_MAX_RADIUS,
      iso: Double = 0.0,
      seedSaltA: Long = 0L,
      seedSaltB: Long = 0L,
      relaxIterations: Int = 0,
      attemptsPerSite: Int = 32,
  ): Array<Site> {
    val bounds = estimateBounds(sdf, originX, originZ, cellSize, maxRadius, iso) ?: return emptyArray()
    val doubleBudgets = DoubleArray(budgets.size) { budgets[it].toDouble() }
    return getSites(
        seed,
        bounds,
        doubleBudgets,
        sdf,
        gradient,
        seedSaltA,
        seedSaltB,
        relaxIterations,
        attemptsPerSite,
    )
  }

  fun getSites(
      seed: Long,
      budgets: DoubleArray,
      sdf: Sdf2,
      gradient: SdfGradient2? = null,
      originX: Double = 0.0,
      originZ: Double = 0.0,
      cellSize: Double = DEFAULT_CELL_SIZE,
      maxRadius: Double = DEFAULT_MAX_RADIUS,
      iso: Double = 0.0,
      seedSaltA: Long = 0L,
      seedSaltB: Long = 0L,
      relaxIterations: Int = 0,
      attemptsPerSite: Int = 32,
  ): Array<Site> {
    val bounds = estimateBounds(sdf, originX, originZ, cellSize, maxRadius, iso) ?: return emptyArray()
    return getSites(
        seed,
        bounds,
        budgets,
        sdf,
        gradient,
        seedSaltA,
        seedSaltB,
        relaxIterations,
        attemptsPerSite,
    )
  }

  fun getSites(
      seed: Long,
      bounds: SdfBounds,
      budgets: IntArray,
      sdf: Sdf2,
      gradient: SdfGradient2? = null,
      seedSaltA: Long = 0L,
      seedSaltB: Long = 0L,
      relaxIterations: Int = 0,
      attemptsPerSite: Int = 32,
  ): Array<Site> {
    val doubleBudgets = DoubleArray(budgets.size) { budgets[it].toDouble() }
    return getSites(
        seed,
        bounds,
        doubleBudgets,
        sdf,
        gradient,
        seedSaltA,
        seedSaltB,
        relaxIterations,
        attemptsPerSite,
    )
  }

  fun getSites(
      seed: Long,
      bounds: SdfBounds,
      budgets: DoubleArray,
      sdf: Sdf2,
      gradient: SdfGradient2? = null,
      seedSaltA: Long = 0L,
      seedSaltB: Long = 0L,
      relaxIterations: Int = 0,
      attemptsPerSite: Int = 32,
  ): Array<Site> {
    if (budgets.isEmpty()) return emptyArray()

    val normalizedBudgets =
        DoubleArray(budgets.size) { index -> budgets[index].coerceAtLeast(0.0) }
    val order = normalizedBudgets.indices.sortedByDescending { normalizedBudgets[it] }

    val rng = SplitMix64(cellSeed(seed, seedSaltA, seedSaltB, budgets.size))
    val localX = DoubleArray(budgets.size)
    val localZ = DoubleArray(budgets.size)
    val maxBudget = normalizedBudgets.maxOrNull() ?: 0.0
    val cellSize = max(1.0, maxBudget)
    val grid = HashMap<Long, MutableList<Int>>(budgets.size * 2)
    // relaxIterations is kept for compatibility; it just adds more candidate attempts.
    val attempts = (attemptsPerSite + relaxIterations.coerceAtLeast(0)).coerceAtLeast(8)
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

      val boundaryDistance = sdf(chosenX, chosenZ) + budget
      if (boundaryDistance > 0.0) {
        val (gx, gz) = gradient?.invoke(chosenX, chosenZ) ?: numericGradient(
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
}

object Sdf {
  fun union(first: Sdf2, second: Sdf2): Sdf2 = { x, z ->
    min(first(x, z), second(x, z))
  }

  fun intersect(first: Sdf2, second: Sdf2): Sdf2 = { x, z ->
    max(first(x, z), second(x, z))
  }

  fun subtract(base: Sdf2, cut: Sdf2): Sdf2 = { x, z ->
    max(base(x, z), -cut(x, z))
  }

  fun translate(sdf: Sdf2, offsetX: Double, offsetZ: Double): Sdf2 = { x, z ->
    sdf(x - offsetX, z - offsetZ)
  }
}
