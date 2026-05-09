package terrasect.sdf

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class SdfBounds(val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
  val width: Int
    get() = maxX - minX

  val height: Int
    get() = maxZ - minZ

  fun expand(margin: Int): SdfBounds {
    return SdfBounds(minX - margin, maxX + margin, minZ - margin, maxZ + margin)
  }
}

fun estimateBounds(sdf: Sdf2, originX: Int = 0, originZ: Int = 0): SdfBounds {
  val maxCells = max(1, MAX_RADIUS / CELL_SIZE)
  val originCellX = toCell(originX)
  val originCellZ = toCell(originZ)

  val originDistance = sdf(originX, originZ)
  var bestX = originX
  var bestZ = originZ
  var bestAbs = abs(originDistance)

  if (originDistance <= 0.0) {
    return floodBounds(sdf, originX, originZ, originX, originZ)
  }

  var seedX: Int? = null
  var seedZ: Int? = null

  for (dz in -maxCells..maxCells) {
    val z = cellCenter(originCellZ + dz)
    for (dx in -maxCells..maxCells) {
      val x = cellCenter(originCellX + dx)
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
    if (seedX != null) break
  }

  if (seedX == null) {
    val gradientEps = max(1, CELL_SIZE / 4)
    var x = bestX
    var z = bestZ
    var distance = sdf(x, z)
    for (i in 0 until 24) {
      val (gx, gz) = numericGradient(sdf, x, z, gradientEps)
      val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-8f)
      val step = distance.coerceAtMost(gradientEps * 8f)
      x -= (gx / length * step).toInt()
      z -= (gz / length * step).toInt()
      distance = sdf(x, z)
      if (abs(distance) <= gradientEps) break
    }

    val (gx, gz) = numericGradient(sdf, x, z, gradientEps)
    val length = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-8f)
    val halfCell = CELL_SIZE / 2

    seedX = (x - gx / length * halfCell).toInt()
    seedZ = (z - gz / length * halfCell).toInt()
  }

  return floodBounds(sdf, seedX, seedZ!!, originX, originZ)
}

fun numericGradient(sdf: Sdf2, x: Int, z: Int, eps: Int): Pair<Float, Float> {
  val dx = sdf(x + eps, z) - sdf(x - eps, z)
  val dz = sdf(x, z + eps) - sdf(x, z - eps)
  return Pair(dx / (2 * eps), dz / (2 * eps))
}

private fun floodBounds(sdf: Sdf2, seedX: Int, seedZ: Int, originX: Int, originZ: Int): SdfBounds {

  val maxCells = max(1, MAX_RADIUS / CELL_SIZE)
  val originCellX = toCell(originX)
  val originCellZ = toCell(originZ)
  val startCellX = toCell(seedX)
  val startCellZ = toCell(seedZ)
  val queue = ArrayDeque<Pair<Int, Int>>()
  val visited = HashSet<Pair<Int, Int>>()

  queue.add(Pair(startCellX, startCellZ))

  var minX = Int.MAX_VALUE
  var maxX = Int.MIN_VALUE
  var minZ = Int.MAX_VALUE
  var maxZ = Int.MIN_VALUE

  while (queue.isNotEmpty()) {
    val packed = queue.removeFirst()
    if (!visited.add(packed)) continue
    val (cx, cz) = packed
    if (abs(cx - originCellX) > maxCells || abs(cz - originCellZ) > maxCells) continue
    val x = cellCenter(cx)
    val z = cellCenter(cz)
    val startCellInside = cx == startCellX && cz == startCellZ && sdf(seedX, seedZ) <= 0.0f
    if (!startCellInside && sdf(x, z) > 0.0f) continue

    minX = min(minX, x)
    maxX = max(maxX, x)
    minZ = min(minZ, z)
    maxZ = max(maxZ, z)

    queue.add(Pair(cx + 1, cz))
    queue.add(Pair(cx - 1, cz))
    queue.add(Pair(cx, cz + 1))
    queue.add(Pair(cx, cz - 1))
  }

  if (
    minX == Int.MAX_VALUE || maxX == Int.MIN_VALUE || minZ == Int.MAX_VALUE || maxZ == Int.MIN_VALUE
  ) {
    return fallbackBounds(sdf, seedX, seedZ, originX, originZ)
  }

  val half = CELL_SIZE / 2
  return SdfBounds(
    saturatingAdd(minX, -half),
    saturatingAdd(maxX, half),
    saturatingAdd(minZ, -half),
    saturatingAdd(maxZ, half),
  )
}

private fun fallbackBounds(
  sdf: Sdf2,
  seedX: Int,
  seedZ: Int,
  originX: Int,
  originZ: Int,
): SdfBounds {
  val seedDistance = abs(sdf(seedX, seedZ))
  val originDistance = abs(sdf(originX, originZ))
  return if (seedDistance <= originDistance) {
    boundsAroundPoint(seedX, seedZ)
  } else {
    boundsAroundPoint(originX, originZ)
  }
}

private fun boundsAroundPoint(x: Int, z: Int): SdfBounds {
  val half = CELL_SIZE / 2
  return SdfBounds(
    saturatingAdd(x, -half),
    saturatingAdd(x, half),
    saturatingAdd(z, -half),
    saturatingAdd(z, half),
  )
}

private fun saturatingAdd(value: Int, delta: Int): Int {
  val sum = value.toLong() + delta.toLong()
  return sum.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

private fun toCell(value: Int): Int = Math.floorDiv(value, CELL_SIZE)

private fun cellCenter(cell: Int): Int = cell * CELL_SIZE + CELL_SIZE / 2
