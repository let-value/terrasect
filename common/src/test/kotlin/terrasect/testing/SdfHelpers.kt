package terrasect.testing

import terrasect.sdf.Sdf2
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

const val LINE_COLOR = 0xFF4DD5FF.toInt()
const val SITE_AREA_COLOR = 0xFFFF4D4D.toInt()
const val SDF_EDGE_COLOR = 0xFFFFFFFF.toInt()
const val SDF_INSIDE_COLOR = 0xFF202020.toInt()
const val SDF_OUTSIDE_COLOR = 0xFF0B0B0B.toInt()

fun distanceColor(
    distance: Double,
    edgeThreshold: Double = 0.6,
    edgeColor: Int = SDF_EDGE_COLOR,
    insideColor: Int = SDF_INSIDE_COLOR,
    outsideColor: Int = SDF_OUTSIDE_COLOR,
): Int {
  return when {
    abs(distance) <= edgeThreshold -> edgeColor
    distance < 0.0 -> insideColor
    else -> outsideColor
  }
}

fun drawSdf(
    image: BufferedImage,
    sdf: Sdf2,
    edgeThreshold: Double = 0.6,
    edgeColor: Int = SDF_EDGE_COLOR,
    insideColor: Int = SDF_INSIDE_COLOR,
    outsideColor: Int = SDF_OUTSIDE_COLOR,
) {
  for (z in 0 until image.height) {
    for (x in 0 until image.width) {
      val distance = sdf(x.toDouble(), z.toDouble())
      val color = distanceColor(distance, edgeThreshold, edgeColor, insideColor, outsideColor)
      image.setRGB(x, z, color)
    }
  }
}

fun drawDistance(
    image: BufferedImage,
    sdf: Sdf2,
    maxDistance: Double,
) {
  val safeMax = if (maxDistance <= 0.0) 1.0 else maxDistance
  for (z in 0 until image.height) {
    for (x in 0 until image.width) {
      val distance = sdf(x.toDouble(), z.toDouble())
      image.setRGB(x, z, colorForSignedDistance(distance, safeMax))
    }
  }
}

fun colorForSignedDistance(distance: Double, maxDistance: Double): Int {
  val normalizedDistance = (abs(distance) / maxDistance * 255.0).coerceIn(0.0, 255.0).toInt()

  return if (distance <= 0.0) {
    (0xFF shl 24) or (normalizedDistance shl 16) or (normalizedDistance shl 8) or 0xFF
  } else {
    (0xFF shl 24) or (0xFF shl 16) or (normalizedDistance shl 8) or normalizedDistance
  }
}

fun drawLine(
    image: BufferedImage,
    x0: Int,
    z0: Int,
    x1: Int,
    z1: Int,
    color: Int = LINE_COLOR,
) {
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
    radius: Double,
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
  drawLine(image, minX, minZ, maxX, minZ, color) // Top
  drawLine(image, maxX, minZ, maxX, maxZ, color) // Right
  drawLine(image, maxX, maxZ, minX, maxZ, color) // Bottom
  drawLine(image, minX, maxZ, minX, minZ, color) // Left
}
