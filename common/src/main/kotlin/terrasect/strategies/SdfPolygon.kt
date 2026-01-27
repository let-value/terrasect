package terrasect.strategies

import kotlin.math.*

data class Vec2(
    val x: Double,
    val z: Double,
)

object SdfPolygon {
  private data class Segment(val a: Vec2, val b: Vec2)
  private const val DEFAULT_CELL_SIZE = 16.0
  private const val DEFAULT_MAX_RADIUS = 2048.0
  private const val DEFAULT_SIMPLIFY_MULTIPLIER = 0.5

  fun polygonize(
      sdf: Sdf2,
      originX: Double = 0.0,
      originZ: Double = 0.0,
  ): List<Vec2> {
    val cellSize = DEFAULT_CELL_SIZE
    val simplifyTolerance = cellSize * DEFAULT_SIMPLIFY_MULTIPLIER
    val iso = 0.0
    val bounds =
        SdfSites.estimateBounds(sdf, originX, originZ, cellSize, DEFAULT_MAX_RADIUS, iso)
            ?: return emptyList()
    return polygonize(sdf, bounds.expanded(cellSize), cellSize, simplifyTolerance, iso)
  }

  private fun polygonize(
      sdf: Sdf2,
      bounds: SdfBounds,
      cellSize: Double,
      simplifyTolerance: Double = cellSize * 0.5,
      iso: Double = 0.0,
  ): List<Vec2> {
    val polygons = polygonizeRaw(sdf, bounds, cellSize, iso)
    val polygon = largestPolygon(polygons)
    return simplifyPolygon(polygon, simplifyTolerance)
  }

  private fun polygonizeRaw(
      sdf: Sdf2,
      bounds: SdfBounds,
      cellSize: Double,
      iso: Double,
  ): List<List<Vec2>> {
    require(cellSize > 0.0) { "cellSize must be > 0" }

    val segments = mutableListOf<Segment>()
    val cols = max(1, ceil(bounds.spanX / cellSize).toInt())
    val rows = max(1, ceil(bounds.spanZ / cellSize).toInt())

    for (row in 0 until rows) {
      val z0 = bounds.minZ + row * cellSize
      val z1 = min(bounds.maxZ, z0 + cellSize)
      for (col in 0 until cols) {
        val x0 = bounds.minX + col * cellSize
        val x1 = min(bounds.maxX, x0 + cellSize)

        val v0 = sdf(x0, z0) - iso
        val v1 = sdf(x1, z0) - iso
        val v2 = sdf(x1, z1) - iso
        val v3 = sdf(x0, z1) - iso

        val edgePoints = arrayOfNulls<Vec2>(4)
        if (crosses(v0, v1)) edgePoints[0] = interp(x0, z0, x1, z0, v0, v1)
        if (crosses(v1, v2)) edgePoints[1] = interp(x1, z0, x1, z1, v1, v2)
        if (crosses(v2, v3)) edgePoints[2] = interp(x1, z1, x0, z1, v2, v3)
        if (crosses(v3, v0)) edgePoints[3] = interp(x0, z1, x0, z0, v3, v0)

        val indices = edgePoints.indices.filter { edgePoints[it] != null }
        if (indices.size == 2) {
          val a = edgePoints[indices[0]]!!
          val b = edgePoints[indices[1]]!!
          segments.add(Segment(a, b))
          continue
        }
        if (indices.size == 4) {
          val centerValue = sdf((x0 + x1) * 0.5, (z0 + z1) * 0.5) - iso
          if (centerValue <= 0.0) {
            segments.add(Segment(edgePoints[0]!!, edgePoints[1]!!))
            segments.add(Segment(edgePoints[2]!!, edgePoints[3]!!))
          } else {
            segments.add(Segment(edgePoints[0]!!, edgePoints[3]!!))
            segments.add(Segment(edgePoints[1]!!, edgePoints[2]!!))
          }
        }
      }
    }

    return stitchSegments(segments, cellSize)
  }

  private fun crosses(a: Double, b: Double): Boolean {
    return (a <= 0.0 && b > 0.0) || (a > 0.0 && b <= 0.0)
  }

  private fun interp(
      x0: Double,
      z0: Double,
      x1: Double,
      z1: Double,
      v0: Double,
      v1: Double,
  ): Vec2 {
    val denom = v1 - v0
    val t = if (abs(denom) < 1e-12) 0.5 else (-v0 / denom).coerceIn(0.0, 1.0)
    return Vec2(x0 + (x1 - x0) * t, z0 + (z1 - z0) * t)
  }

  private fun stitchSegments(segments: List<Segment>, cellSize: Double): List<List<Vec2>> {
    if (segments.isEmpty()) return emptyList()

    val snap = max(1e-6, cellSize * 1e-3)
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

      val isClosed = path.size > 2 && key(path.first()) == key(path.last())
      if (isClosed) {
        path.removeAt(path.size - 1)
        polygons.add(path)
      }
    }

    return polygons
  }

  private fun largestPolygon(polygons: List<List<Vec2>>): List<Vec2> {
    var best: List<Vec2> = emptyList()
    var bestArea = 0.0
    for (polygon in polygons) {
      if (polygon.size < 3) continue
      val area = abs(polygonArea(polygon))
      if (area > bestArea) {
        bestArea = area
        best = polygon
      }
    }
    return best
  }

  private fun simplifyPolygon(polygon: List<Vec2>, tolerance: Double): List<Vec2> {
    if (polygon.size < 4) return polygon
    if (tolerance <= 0.0) return polygon

    val centroid = polygonCentroid(polygon)
    var startIndex = 0
    var maxDistance = Double.NEGATIVE_INFINITY
    for (i in polygon.indices) {
      val dx = polygon[i].x - centroid.x
      val dz = polygon[i].z - centroid.z
      val distance = dx * dx + dz * dz
      if (distance > maxDistance) {
        maxDistance = distance
        startIndex = i
      }
    }

    val reordered = ArrayList<Vec2>(polygon.size + 1)
    for (i in 0 until polygon.size) {
      val index = (startIndex + i) % polygon.size
      reordered.add(polygon[index])
    }
    reordered.add(reordered[0])

    val simplified = rdpSimplify(reordered, tolerance)
    if (simplified.size <= 3) return polygon
    simplified.removeAt(simplified.size - 1)
    return simplified
  }

  private fun polygonCentroid(polygon: List<Vec2>): Vec2 {
    var areaSum = 0.0
    var cx = 0.0
    var cz = 0.0
    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      val cross = a.x * b.z - b.x * a.z
      areaSum += cross
      cx += (a.x + b.x) * cross
      cz += (a.z + b.z) * cross
    }
    if (abs(areaSum) < 1e-8) {
      return polygon[0]
    }
    val factor = 1.0 / (3.0 * areaSum)
    return Vec2(cx * factor, cz * factor)
  }

  private fun rdpSimplify(points: List<Vec2>, tolerance: Double): MutableList<Vec2> {
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
    return if (maxDistance > tolerance) {
      val left = rdpSimplify(points.subList(0, index + 1), tolerance)
      val right = rdpSimplify(points.subList(index, points.size), tolerance)
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

  private fun polygonArea(polygon: List<Vec2>): Double {
    var sum = 0.0
    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      sum += a.x * b.z - b.x * a.z
    }
    return sum * 0.5
  }
}
