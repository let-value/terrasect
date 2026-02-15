package terrasect.sdf

import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import org.junit.jupiter.api.Test
import terrasect.testing.writeSnapshotPng

private const val WIDTH = 200
private const val HEIGHT = 200
private const val CX = WIDTH / 2
private const val CZ = HEIGHT / 2

class PolygonTest {

  @Test
  fun `should render banana polygon`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)

    val sdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)

    drawSdf(image, sdf)

    val bounds = estimateBounds(sdf)
    val polygon = polygonize(sdf, bounds)

    if (polygon.isNotEmpty()) {

      for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        drawLine(image, a.x.roundToInt(), a.z.roundToInt(), b.x.roundToInt(), b.z.roundToInt())
      }
    }

    writeSnapshotPng(PolygonTest::class.java, "banana.png", image)
  }
}
