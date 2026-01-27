package terrasect.sdf

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.SnapshotOutputPaths
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

fun smoothMin(a: Double, b: Double, k: Double): Double {
  val h = (0.5 + 0.5 * (b - a) / k).coerceIn(0.0, 1.0)
  return a * h + b * (1.0 - h) - k * h * (1.0 - h)
}

fun smoothMax(a: Double, b: Double, k: Double): Double {
  return -smoothMin(-a, -b, k)
}

fun bananaLocalSdf(x: Double, z: Double): Double {
  val outer = sqrt(x * x + z * z) - 34.0
  val innerX = x - 18.0
  val innerZ = z + 6.0
  val inner = sqrt(innerX * innerX + innerZ * innerZ) - 30.0
  return smoothMax(outer, -inner, 6.0)
}

const val LINE_COLOR = 0xFF4DD5FF.toInt()

class PolygonTest {
  private val width = 200
  private val height = 200

  @Test
  fun `should render banana polygon`() {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val centerX = width / 2.0
    val centerZ = height / 2.0
    val scale = 2.0

    for (z in 0 until height) {
      for (x in 0 until width) {
        val localX = (x - centerX) / scale
        val localZ = (z - centerZ) / scale
        val distance = bananaLocalSdf(localX, localZ)
        val color =
            when {
              abs(distance) <= 0.7 -> 0xFFFFFFFF.toInt()
              distance < 0.0 -> 0xFF202020.toInt()
              else -> 0xFF0B0B0B.toInt()
            }
        image.setRGB(x, z, color)
      }
    }

    val sdf: Sdf2 = { x, z -> bananaLocalSdf(x / scale, z / scale) }

    val bounds = estimateBounds(sdf)
    val polygon = polygonize(sdf, bounds)
    if (polygon.isNotEmpty()) {

      for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        drawLine(
            image,
            (centerX + a.x).roundToInt(),
            (centerZ + a.z).roundToInt(),
            (centerX + b.x).roundToInt(),
            (centerZ + b.z).roundToInt(),
        )
      }
    }

    val polygonFile =
        SnapshotOutputPaths.forTestClass(PolygonTest::class.java, "banana-polygon.png")
    polygonFile.parentFile.mkdirs()
    val polygonWritten = ImageIO.write(image, "png", polygonFile)
    assertTrue(polygonWritten, "Expected to write PNG snapshot to ${polygonFile.absolutePath}")
  }

  private fun drawLine(
      image: BufferedImage,
      x0: Int,
      z0: Int,
      x1: Int,
      z1: Int,
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
        image.setRGB(sx, sz, LINE_COLOR)
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
}
