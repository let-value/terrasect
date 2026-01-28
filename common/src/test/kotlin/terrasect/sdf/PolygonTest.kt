package terrasect.sdf

import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.junit.jupiter.api.Test
import terrasect.testing.drawLine
import terrasect.testing.drawSdf
import terrasect.testing.writeSnapshotPng

fun smoothMin(a: Double, b: Double, k: Double): Double {
  val h = (0.5 + 0.5 * (b - a) / k).coerceIn(0.0, 1.0)
  return a * h + b * (1.0 - h) - k * h * (1.0 - h)
}

fun smoothMax(a: Double, b: Double, k: Double): Double {
  return -smoothMin(-a, -b, k)
}

fun bananaSdf(x: Double, z: Double): Double {
  val outer = sqrt(x * x + z * z) - 34.0
  val innerX = x - 18.0
  val innerZ = z + 6.0
  val inner = sqrt(innerX * innerX + innerZ * innerZ) - 30.0
  return smoothMax(outer, -inner, 6.0)
}

class PolygonTest {
  private val width = 200
  private val height = 200

  @Test
  fun `should render banana polygon`() {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val centerX = width / 2.0
    val centerZ = height / 2.0
    val scale = 2.0
    val sdf: Sdf2 = { x, z -> bananaSdf(x / scale, z / scale) }

    drawSdf(image, centerX, centerZ, sdf, edgeThreshold = 0.7)

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

    writeSnapshotPng(PolygonTest::class.java, "banana.png", image)
  }
}
