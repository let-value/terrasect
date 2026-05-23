package terrasect.sdf

import java.awt.image.BufferedImage
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.writeSnapshotPng

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2
private const val CZ = HEIGHT / 2

class BoundsTest {
  @Test
  fun `should expand bounds`() {
    val bounds = SdfBounds(-10, 10, -5, 5)
    val expanded = bounds.expand(2)
    assertEquals(-12, expanded.minX)
    assertEquals(12, expanded.maxX)
    assertEquals(-7, expanded.minZ)
    assertEquals(7, expanded.maxZ)
  }

  @Test
  fun `should find circle bounds`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)

    val radius = 100f
    val sdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)

    val bounds = estimateBounds(sdf)

    drawSdf(image, sdf)
    drawRectangle(image, bounds.minX, bounds.minZ, bounds.maxX, bounds.maxZ)

    writeSnapshotPng(BoundsTest::class.java, "circle.png", image)

    assertEquals(16, bounds.minX)
    assertEquals(224, bounds.maxX)
    assertEquals(16, bounds.minZ)
    assertEquals(224, bounds.maxZ)
  }

  @Test
  fun `should find hex bounds`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)

    val radius = 100f
    val sdf: Sdf2 = translate({ x, z -> hexDistance(x, z, radius) }, CX, CZ)
    val bounds = estimateBounds(sdf)

    drawSdf(image, sdf)
    drawRectangle(image, bounds.minX, bounds.minZ, bounds.maxX, bounds.maxZ)

    writeSnapshotPng(BoundsTest::class.java, "hex.png", image)

    assertEquals(16, bounds.minX)
    assertEquals(224, bounds.maxX)
    assertEquals(0, bounds.minZ)
    assertEquals(240, bounds.maxZ)
  }

  @Test
  fun `should find banana bounds`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val sdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)

    val bounds = estimateBounds(sdf)

    drawSdf(image, sdf)
    drawRectangle(image, bounds.minX, bounds.minZ, bounds.maxX, bounds.maxZ)
    writeSnapshotPng(BoundsTest::class.java, "banana.png", image)

    assertEquals(48, bounds.minX)
    assertEquals(160, bounds.maxX)
    assertEquals(48, bounds.minZ)
    assertEquals(192, bounds.maxZ)
  }

  @Test
  fun `should return finite bounds when sdf has no interior`() {
    val sdf: Sdf2 = { _, _ -> 1f }

    val bounds = estimateBounds(sdf)

    assertEquals(-8, bounds.minX)
    assertEquals(8, bounds.maxX)
    assertEquals(-8, bounds.minZ)
    assertEquals(8, bounds.maxZ)
    assertTrue(bounds.width > 0)
    assertTrue(bounds.height > 0)
  }
}
