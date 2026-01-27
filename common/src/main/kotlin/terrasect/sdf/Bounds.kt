package terrasect.sdf

import terrasect.utils.first
import terrasect.utils.packPair
import terrasect.utils.second
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

  for (dz in -maxCells..maxCells) {
    val z = (originCellZ + dz) * CELL_SIZE
    for (dx in -maxCells..maxCells) {
      val x = (originCellX + dx) * CELL_SIZE
      val distance = sdf(x, z)
      if (distance <= 0.0) {
        seedX = x
        seedZ = z
        break
      }
      val distanceAbs = abs(distance)
      if (distanceAbs < bestAbs) {
        bestAbs = distanceAbs
        bestX = x
        bestZ = z
      }
    }
    if (!seedX.isNaN()) break
  }

  if (seedX.isNaN()) {
    var x = bestX
    var z = bestZ
    var distance = sdf(x, z)
    for (i in 0 until 24) {
      val (gx, gz) = numericGradient(sdf, x, z, gradientEps)
      val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-8)
      val step = distance.coerceAtMost(gradientEps * 8.0)
      x -= gx / length * step
      z -= gz / length * step
      distance = sdf(x, z)
      if (abs(distance) <= gradientEps) break
    }

    val (gx, gz) = numericGradient(sdf, x, z, gradientEps)
    val length = sqrt(gx * gx + gz * gz)

    seedX = x - gx / length * (CELL_SIZE * 0.5)
    seedZ = z - gz / length * (CELL_SIZE * 0.5)
  }

  return floodBounds(sdf, seedX, seedZ, originX, originZ)
}

fun numericGradient(sdf: Sdf2, x: Double, z: Double, eps: Double): Pair<Double, Double> {
  val dx = sdf(x + eps, z) - sdf(x - eps, z)
  val dz = sdf(x, z + eps) - sdf(x, z - eps)
  return Pair(dx / (2.0 * eps), dz / (2.0 * eps))
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

  queue.add(packPair(startCellX, startCellZ))

  var minX = Double.POSITIVE_INFINITY
  var maxX = Double.NEGATIVE_INFINITY
  var minZ = Double.POSITIVE_INFINITY
  var maxZ = Double.NEGATIVE_INFINITY

  while (queue.isNotEmpty()) {
    val packed = queue.removeFirst()
    if (!visited.add(packed)) continue
    val cx = packed.first()
    val cz = packed.second()
    if (abs(cx - originCellX) > maxCells || abs(cz - originCellZ) > maxCells) continue
    val x = cx * CELL_SIZE
    val z = cz * CELL_SIZE
    if (sdf(x, z) > 0.0) continue

    minX = min(minX, x)
    maxX = max(maxX, x)
    minZ = min(minZ, z)
    maxZ = max(maxZ, z)

    queue.add(packPair(cx + 1, cz))
    queue.add(packPair(cx - 1, cz))
    queue.add(packPair(cx, cz + 1))
    queue.add(packPair(cx, cz - 1))
  }

  val half = CELL_SIZE * 0.5
  return SdfBounds(minX - half, maxX + half, minZ - half, maxZ + half)
}
