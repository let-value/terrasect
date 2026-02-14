package terrasect.sdf

import kotlin.math.*

data class Vec2(val x: Double, val z: Double)

private data class Segment(val a: Vec2, val b: Vec2)

private const val SIMPLIFY_TOLERANCE = CELL_SIZE * 0.5

fun polygonize(sdf: Sdf2, bounds: SdfBounds): List<Vec2> {
  val expanded = bounds.expand(CELL_SIZE)
  val segments = mutableListOf<Segment>()
  val cols = max(1, ceil(expanded.width / CELL_SIZE).toInt())
  val rows = max(1, ceil(expanded.height / CELL_SIZE).toInt())

  var z0 = expanded.minZ
  for (row in 0 until rows) {
    val z1 = if (row == rows - 1) expanded.maxZ else z0 + CELL_SIZE
    var x0 = expanded.minX
    for (col in 0 until cols) {
      val x1 = if (col == cols - 1) expanded.maxX else x0 + CELL_SIZE

      val v0 = sdf(x0, z0)
      val v1 = sdf(x1, z0)
      val v2 = sdf(x1, z1)
      val v3 = sdf(x0, z1)

      val e0 = v0 <= 0.0
      val e1 = v1 <= 0.0
      val e2 = v2 <= 0.0
      val e3 = v3 <= 0.0

      var p0: Vec2? = null
      var p1: Vec2? = null
      var p2: Vec2? = null
      var p3: Vec2? = null
      var count = 0
      var first: Vec2? = null
      var second: Vec2? = null

      if (e0 != e1) {
        val p = interp(x0, z0, x1, z0, v0, v1)
        p0 = p
        if (count == 0) first = p else if (count == 1) second = p
        count++
      }
      if (e1 != e2) {
        val p = interp(x1, z0, x1, z1, v1, v2)
        p1 = p
        if (count == 0) first = p else if (count == 1) second = p
        count++
      }
      if (e2 != e3) {
        val p = interp(x1, z1, x0, z1, v2, v3)
        p2 = p
        if (count == 0) first = p else if (count == 1) second = p
        count++
      }
      if (e3 != e0) {
        val p = interp(x0, z1, x0, z0, v3, v0)
        p3 = p
        if (count == 0) first = p else if (count == 1) second = p
        count++
      }

      if (count == 2) {
        segments.add(Segment(first!!, second!!))
      } else if (count == 4) {
        val centerValue = sdf((x0 + x1) * 0.5, (z0 + z1) * 0.5)
        if (centerValue <= 0.0) {
          segments.add(Segment(p0!!, p1!!))
          segments.add(Segment(p2!!, p3!!))
        } else {
          segments.add(Segment(p0!!, p3!!))
          segments.add(Segment(p1!!, p2!!))
        }
      }

      x0 = x1
    }
    z0 = z1
  }

  if (segments.isEmpty()) return emptyList()

  val polygons = stitchSegments(segments)
  var best: List<Vec2> = emptyList()
  var bestArea = 0.0
  for (polygon in polygons) {
    if (polygon.size < 3) continue
    var area = 0.0
    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      area += a.x * b.z - b.x * a.z
    }
    val absArea = abs(area)
    if (absArea > bestArea) {
      bestArea = absArea
      best = polygon
    }
  }
  if (best.isEmpty()) return emptyList()
  if (best.size < 4) return best

  val closed = ArrayList<Vec2>(best.size + 1)
  closed.addAll(best)
  closed.add(best[0])

  val simplified = rdpSimplify(closed)
  if (simplified.size <= 3) return best
  simplified.removeAt(simplified.size - 1)
  return simplified
}

private fun stitchSegments(segments: List<Segment>): List<List<Vec2>> {
  if (segments.size < 2) return emptyList()

  val snap = max(1e-6, CELL_SIZE * 1e-3)
  fun key(p: Vec2): Long {
    val kx = floor(p.x / snap).toLong()
    val kz = floor(p.z / snap).toLong()
    return (kx shl 32) xor (kz and 0xFFFF_FFFFL)
  }

  val adjacency = HashMap<Long, MutableList<Int>>(segments.size * 2)
  for (i in segments.indices) {
    val aKey = key(segments[i].a)
    val bKey = key(segments[i].b)
    adjacency.getOrPut(aKey) { mutableListOf() }.add(i)
    adjacency.getOrPut(bKey) { mutableListOf() }.add(i)
  }

  val used = BooleanArray(segments.size)
  val polygons = mutableListOf<List<Vec2>>()

  for (i in segments.indices) {
    if (used[i]) continue
    used[i] = true
    val segment = segments[i]
    val path = ArrayList<Vec2>()
    path.add(segment.a)
    path.add(segment.b)

    var current = segment.b
    var currentKey = key(current)
    val startKey = key(segment.a)

    while (true) {
      val candidates = adjacency[currentKey] ?: break
      val nextIndex = candidates.firstOrNull { !used[it] } ?: break
      used[nextIndex] = true
      val next = segments[nextIndex]
      if (key(next.a) == currentKey) {
        current = next.b
      } else {
        current = next.a
      }
      path.add(current)
      currentKey = key(current)
      if (currentKey == startKey) break
    }

    val isClosed = path.size > 2 && currentKey == startKey
    if (isClosed) {
      path.removeAt(path.size - 1)
      polygons.add(path)
    }
  }

  return polygons
}

private fun interp(x0: Double, z0: Double, x1: Double, z1: Double, v0: Double, v1: Double): Vec2 {
  val denom = v1 - v0
  val t = if (abs(denom) < 1e-12) 0.5 else (-v0 / denom).coerceIn(0.0, 1.0)
  return Vec2(x0 + (x1 - x0) * t, z0 + (z1 - z0) * t)
}

private fun rdpSimplify(points: List<Vec2>): MutableList<Vec2> {
  if (points.size < 3) return points.toMutableList()
  var maxDistance = 0.0
  var index = 0
  val start = points.first()
  val end = points.last()
  for (i in 1 until points.size - 1) {
    val distance = distanceToSegment(points[i], start, end)
    if (distance > maxDistance) {
      maxDistance = distance
      index = i
    }
  }
  return if (maxDistance > SIMPLIFY_TOLERANCE) {
    val left = rdpSimplify(points.subList(0, index + 1))
    val right = rdpSimplify(points.subList(index, points.size))
    left.removeAt(left.size - 1)
    left.addAll(right)
    left
  } else {
    mutableListOf(start, end)
  }
}

private fun distanceToSegment(point: Vec2, a: Vec2, b: Vec2): Double {
  val abX = b.x - a.x
  val abZ = b.z - a.z
  val apX = point.x - a.x
  val apZ = point.z - a.z
  val abLengthSquared = abX * abX + abZ * abZ
  if (abLengthSquared <= 1e-12) {
    return sqrt(apX * apX + apZ * apZ)
  }
  val t = ((apX * abX + apZ * abZ) / abLengthSquared).coerceIn(0.0, 1.0)
  val closestX = a.x + abX * t
  val closestZ = a.z + abZ * t
  val dx = point.x - closestX
  val dz = point.z - closestZ
  return sqrt(dx * dx + dz * dz)
}
