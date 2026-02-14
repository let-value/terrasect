package terrasect.sdf

import org.junit.jupiter.api.Test
import terrasect.testing.writeSnapshotPng
import java.awt.image.BufferedImage
import kotlin.math.sqrt

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2.0
private const val CZ = HEIGHT / 2.0
private const val APOTHEM = 80.0
private const val CIRCLE_RADIUS = 90.0

class SdfComposeTest {

  @Test
  fun `should restrict bounds while composing sdf`() {
    val sdf = SdfCompose()
    sdf.append(HexCellSdf(CX, CZ, APOTHEM))
    renderSnapshot("step1.png", sdf)

    sdf.append(translate({ x, z -> sqrt(x * x + z * z) - CIRCLE_RADIUS }, CX, CZ - 50.0))
    renderSnapshot("step2.png", sdf)

    sdf.append(translate({ x, z -> bananaSdf(x, z, 3.0) }, CX, CZ - 20.0))
    renderSnapshot("step3.png", sdf)
  }

  private fun renderSnapshot(name: String, sdf: Sdf2) {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawDistance(image, sdf, 100.0)
    writeSnapshotPng(SdfComposeTest::class.java, name, image)
  }
}
