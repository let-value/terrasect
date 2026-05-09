package terrasect.sdf

import java.awt.image.BufferedImage
import kotlin.math.*

const val LINE_COLOR = 0xFF4DD5FF.toInt()
const val SITE_AREA_COLOR = 0xFFFF4D4D.toInt()
const val SDF_EDGE_COLOR = 0xFFFFFFFF.toInt()
const val SDF_INSIDE_COLOR = 0xFF202020.toInt()
const val SDF_OUTSIDE_COLOR = 0xFF0B0B0B.toInt()

fun bananaLocalSdf(x: Float, z: Float): Float {
  val outer = hypot(x, z) - 34f
  val innerX = x - 18f
  val innerZ = z + 6f
  val inner = sqrt(innerX * innerX + innerZ * innerZ) - 30f
  return smoothMax(outer, -inner, 6f)
}

fun bananaSdf(x: Int, z: Int, scale: Float = 2f): Float {
  return bananaLocalSdf(x / scale, z / scale) * scale
}

fun distanceColor(
  distance: Float,
  edgeThreshold: Float = 0.6f,
  edgeColor: Int = SDF_EDGE_COLOR,
  insideColor: Int? = SDF_INSIDE_COLOR,
  outsideColor: Int? = null,
): Int? {
  return when {
    abs(distance) <= edgeThreshold -> edgeColor
    distance < 0.0 -> insideColor
    else -> outsideColor
  }
}

fun drawSdf(
  image: BufferedImage,
  sdf: Sdf2,
  edgeThreshold: Float = 0.6f,
  edgeColor: Int = SDF_EDGE_COLOR,
  insideColor: Int? = SDF_INSIDE_COLOR,
  outsideColor: Int? = null,
) {
  for (z in 0 until image.height) {
    for (x in 0 until image.width) {
      val distance = sdf(x, z)
      val color =
        distanceColor(distance, edgeThreshold, edgeColor, insideColor, outsideColor) ?: continue
      image.setRGB(x, z, color)
    }
  }
}

fun drawDistance(image: BufferedImage, sdf: Sdf2, maxDistance: Float = Float.MAX_VALUE) {
  val safeMax = if (maxDistance <= 0f) 1f else maxDistance
  for (z in 0 until image.height) {
    for (x in 0 until image.width) {
      val distance = sdf(x, z)
      image.setRGB(x, z, colorForSignedDistance(distance, safeMax))
    }
  }
}

fun colorForSignedDistance(distance: Float, maxDistance: Float): Int {
  val normalizedDistance = (abs(distance) / maxDistance * 255f).coerceIn(0f, 255f).toInt()

  return if (distance <= 0.0) {
    (0xFF shl 24) or (normalizedDistance shl 16) or (normalizedDistance shl 8) or 0xFF
  } else {
    (0xFF shl 24) or (0xFF shl 16) or (normalizedDistance shl 8) or normalizedDistance
  }
}

fun drawSites(image: BufferedImage, sites: List<Site>) {
  for (site in sites) {

    drawCircle(image, site.x, site.z, 1)
    drawRing(image, site.x, site.z, site.radius)
  }
}

fun drawLine(image: BufferedImage, x0: Int, z0: Int, x1: Int, z1: Int, color: Int = LINE_COLOR) {
  var sx = x0
  var sz = z0
  val dx = abs(x1 - x0)
  val dz = abs(z1 - z0)
  val stepX = if (x0 < x1) 1 else -1
  val stepZ = if (z0 < z1) 1 else -1
  var err = dx - dz

  while (true) {
    if (sx in 0 until image.width && sz in 0 until image.height) {
      image.setRGB(sx, sz, color)
    }
    if (sx == x1 && sz == z1) break
    val e2 = err * 2
    if (e2 > -dz) {
      err -= dz
      sx += stepX
    }
    if (e2 < dx) {
      err += dx
      sz += stepZ
    }
  }
}

fun drawCircle(
  image: BufferedImage,
  centerX: Int,
  centerZ: Int,
  radius: Int,
  color: Int = SITE_AREA_COLOR,
) {
  if (radius <= 0) return

  val minX = (centerX - radius).coerceAtLeast(0)
  val maxX = (centerX + radius).coerceAtMost(image.width - 1)
  val minZ = (centerZ - radius).coerceAtLeast(0)
  val maxZ = (centerZ + radius).coerceAtMost(image.height - 1)

  val rSquared = radius * radius
  for (z in minZ..maxZ) {
    val dz = z - centerZ
    for (x in minX..maxX) {
      val dx = x - centerX
      if (dx * dx + dz * dz <= rSquared) {
        image.setRGB(x, z, color)
      }
    }
  }
}

fun drawRing(
  image: BufferedImage,
  centerX: Int,
  centerZ: Int,
  radius: Float,
  steps: Int = 64,
  color: Int = LINE_COLOR,
) {
  if (radius <= 0.0 || steps <= 0) return

  val stepAngle = Math.PI * 2.0 / steps
  for (step in 0 until steps) {
    val angle = step * stepAngle
    val rx = (centerX + cos(angle) * radius).roundToInt()
    val rz = (centerZ + sin(angle) * radius).roundToInt()
    if (rx in 0 until image.width && rz in 0 until image.height) {
      image.setRGB(rx, rz, color)
    }
  }
}

fun drawRectangle(
  image: BufferedImage,
  minX: Int,
  minZ: Int,
  maxX: Int,
  maxZ: Int,
  color: Int = LINE_COLOR,
) {
  drawLine(image, minX, minZ, maxX, minZ, color)
  drawLine(image, maxX, minZ, maxX, maxZ, color)
  drawLine(image, maxX, maxZ, minX, maxZ, color)
  drawLine(image, minX, maxZ, minX, minZ, color)
}
