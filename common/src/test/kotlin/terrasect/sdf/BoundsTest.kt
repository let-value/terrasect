package terrasect.sdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import terrasect.testing.writeSnapshotPng
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2.0
private const val CZ = HEIGHT / 2.0

class BoundsTest {
  @Test
  fun `should expand bounds`() {
    val bounds = SdfBounds(-10.0, 10.0, -5.0, 5.0)
    val expanded = bounds.expand(2.0)
    assertEquals(-12.0, expanded.minX)
    assertEquals(12.0, expanded.maxX)
    assertEquals(-7.0, expanded.minZ)
    assertEquals(7.0, expanded.maxZ)
  }

  @Test
  fun `should find circle bounds`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)

    val radius = 100.0
    val sdf: Sdf2 = translate({ x, z -> sqrt(x * x + z * z) - radius }, CX, CZ)

    val bounds = estimateBounds(sdf)

    drawSdf(image, sdf)
    drawRectangle(
        image,
        bounds.minX.roundToInt(),
        bounds.minZ.roundToInt(),
        bounds.maxX.roundToInt(),
        bounds.maxZ.roundToInt(),
    )

    writeSnapshotPng(BoundsTest::class.java, "circle.png", image)

    assertEquals(20.0, bounds.minX, 5.0)
    assertEquals(220.0, bounds.maxX, 5.0)
    assertEquals(20.0, bounds.minZ, 5.0)
    assertEquals(220.0, bounds.maxZ, 5.0)
  }

  @Test
  fun `should find hex bounds`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)

    val radius = 100.0
    val sdf: Sdf2 = translate({ x, z -> hexDistance(x, z, radius) }, CX, CZ)
    val bounds = estimateBounds(sdf)

    drawSdf(image, sdf)
    drawRectangle(
        image,
        bounds.minX.roundToInt(),
        bounds.minZ.roundToInt(),
        bounds.maxX.roundToInt(),
        bounds.maxZ.roundToInt(),
    )

    writeSnapshotPng(BoundsTest::class.java, "hex.png", image)

    assertEquals(20.0, bounds.minX, 5.0)
    assertEquals(220.0, bounds.maxX, 5.0)
    assertEquals(10.0, bounds.minZ, 5.0)
    assertEquals(230.0, bounds.maxZ, 5.0)
  }

  @Test
  fun `should find banana bounds`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val sdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)

    val bounds = estimateBounds(sdf)

    drawSdf(image, sdf)
    drawRectangle(
        image,
        bounds.minX.roundToInt(),
        bounds.minZ.roundToInt(),
        bounds.maxX.roundToInt(),
        bounds.maxZ.roundToInt(),
    )
    writeSnapshotPng(BoundsTest::class.java, "banana.png", image)

    assertEquals(60.0, bounds.minX, 5.0)
    assertEquals(150.0, bounds.maxX, 5.0)
    assertEquals(60.0, bounds.minZ, 5.0)
    assertEquals(180.0, bounds.maxZ, 5.0)
  }
}
