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

class SdfComposeTest {

  @Test
  fun `should restrict bounds while composing sdf`() {
    val sdf = SdfCompose()
    sdf.append(HexCellSdf(CX, CZ, APOTHEM))
    renderSnapshot("step1.png", sdf)

    sdf.append(translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - CIRCLE_RADIUS }, CX, CZ - 50))
    renderSnapshot("step2.png", sdf)

    sdf.append(translate({ x, z -> bananaSdf(x, z, 3f) }, CX, CZ - 20))
    renderSnapshot("step3.png", sdf)
  }

  private fun renderSnapshot(name: String, sdf: Sdf2) {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawDistance(image, sdf, 100f)
    writeSnapshotPng(SdfComposeTest::class.java, name, image)
  }
}
