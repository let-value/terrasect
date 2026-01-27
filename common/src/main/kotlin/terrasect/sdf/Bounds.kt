package terrasect.sdf

import kotlin.math.*

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

  fun expand(margin: Double): SdfBounds {
    return SdfBounds(
        minX - margin,
        maxX + margin,
        minZ - margin,
        maxZ + margin,
    )
  }
}

fun estimateBounds(
    sdf: Sdf2,
    originX: Double = 0.0,
    originZ: Double = 0.0,
): SdfBounds {

  val maxCells = max(1, ceil(MAX_RADIUS / CELL_SIZE).toInt())
  val originCellX = floor(originX / CELL_SIZE).toInt()
  val originCellZ = floor(originZ / CELL_SIZE).toInt()
  val gradientEps = max(1e-3, CELL_SIZE * 0.25)

  val originDistance = sdf(originX, originZ)
  var bestX = originX
  var bestZ = originZ
  var bestAbs = abs(originDistance)

  if (originDistance <= 0.0) {
    return floodBounds(sdf, originX, originZ, originX, originZ)
  }

  var seedX = Double.NaN
  var seedZ = Double.NaN

  run search@{
    for (dz in -maxCells..maxCells) {
      val z = (originCellZ + dz) * CELL_SIZE
      for (dx in -maxCells..maxCells) {
        val x = (originCellX + dx) * CELL_SIZE
        val distance = sdf(x, z)
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
    val boundary = projectToBoundary(sdf, bestX, bestZ, gradientEps)
    val (gx, gz) = numericGradient(sdf, boundary.first, boundary.second, gradientEps)
    val length = sqrt(gx * gx + gz * gz)

    seedX = boundary.first - gx / length * (CELL_SIZE * 0.5)
    seedZ = boundary.second - gz / length * (CELL_SIZE * 0.5)
  }

  return floodBounds(sdf, seedX, seedZ, originX, originZ)
}

fun numericGradient(sdf: Sdf2, x: Double, z: Double, eps: Double): Pair<Double, Double> {
  val dx = sdf(x + eps, z) - sdf(x - eps, z)
  val dz = sdf(x, z + eps) - sdf(x, z - eps)
  return Pair(dx / (2.0 * eps), dz / (2.0 * eps))
}

fun projectToBoundary(
    sdf: Sdf2,
    startX: Double,
    startZ: Double,
    eps: Double,
): Pair<Double, Double> {
  var x = startX
  var z = startZ
  var distance = sdf(x, z)
  if (distance <= 0.0) return Pair(x, z)

  repeat(24) {
    val (gx, gz) = numericGradient(sdf, x, z, eps)
    val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-8)
    val step = distance.coerceAtMost(eps * 8.0)
    x -= gx / length * step
    z -= gz / length * step
    distance = sdf(x, z)
    if (abs(distance) <= eps) return Pair(x, z)
  }

  return Pair(x, z)
}

private fun floodBounds(
    sdf: Sdf2,
    seedX: Double,
    seedZ: Double,
    originX: Double,
    originZ: Double,
): SdfBounds {

  val maxCells = max(1, ceil(MAX_RADIUS / CELL_SIZE).toInt())
  val originCellX = floor(originX / CELL_SIZE).toInt()
  val originCellZ = floor(originZ / CELL_SIZE).toInt()
  val startCellX = floor(seedX / CELL_SIZE).toInt()
  val startCellZ = floor(seedZ / CELL_SIZE).toInt()
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
    val x = cx * CELL_SIZE
    val z = cz * CELL_SIZE
    if (sdf(x, z) > 0.0) continue

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

  val half = CELL_SIZE * 0.5
  return SdfBounds(minX - half, maxX + half, minZ - half, maxZ + half)
}
