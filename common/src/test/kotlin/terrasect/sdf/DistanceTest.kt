package terrasect.sdf

import java.awt.image.BufferedImage
import kotlin.math.hypot
import org.junit.jupiter.api.Test
import terrasect.testing.writeSnapshotPng

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2
private const val CZ = HEIGHT / 2
private const val APOTHEM = 80f
private const val CIRCLE_RADIUS = 90f

class DistanceTest {

  @Test
  fun `should draw hex distance`() {
    val hex = HexCellSdf(CX, CZ, APOTHEM)
    renderSnapshot("hex.png", hex)
  }

  @Test
  fun `should draw hex gap distance`() {
    val hex = HexGapSdf(CX, CZ, APOTHEM, 10f)
    renderSnapshot("hex-gap.png", hex)
  }

  @Test
  fun `should draw circle distance`() {
    val circle = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - CIRCLE_RADIUS }, CX, CZ)
    renderSnapshot("circle.png", circle)
  }

  @Test
  fun `should draw banana distance`() {
    val banana = translate({ x, z -> bananaSdf(x, z, 3f) }, CX, CZ)
    renderSnapshot("banana.png", banana)
  }

  private fun renderSnapshot(name: String, sdf: Sdf2) {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawDistance(image, sdf, 100f)
    writeSnapshotPng(DistanceTest::class.java, name, image)
  }
}
