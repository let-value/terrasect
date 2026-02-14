package terrasect.strategies

import java.awt.image.BufferedImage
import kotlin.math.sqrt
import org.junit.jupiter.api.Test
import terrasect.sdf.*
import terrasect.testing.writeSnapshotPng

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2.0
private const val CZ = HEIGHT / 2.0

private const val CENTER_COLOR = 0xFF3A6B8C.toInt()
private const val SURROUND_COLOR = 0xFF8C5A3A.toInt()

class SurroundStrategyTest {

  private val centerBudget = 3000.0
  private val surroundBudget = 1000.0
  private val scale = surroundScale(centerBudget, centerBudget + surroundBudget)

  @Test
  fun `should render surround cells in circle`() {
    val radius = 100.0
    val parentSdf: Sdf2 = translate({ x, z -> sqrt(x * x + z * z) - radius }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, parentSdf)
    drawSdf(image, parentSdf, insideColor = null)
    writeSnapshotPng(SurroundStrategyTest::class.java, "circle-cells.png", image)
  }

  @Test
  fun `should render surround distance in circle`() {
    val radius = 100.0
    val parentSdf: Sdf2 = translate({ x, z -> sqrt(x * x + z * z) - radius }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCellDistance(image, parentSdf, radius)
    writeSnapshotPng(SurroundStrategyTest::class.java, "circle-distance.png", image)
  }

  @Test
  fun `should render surround cells in hex`() {
    val apothem = 100.0
    val parentSdf: Sdf2 = translate({ x, z -> hexDistance(x, z, apothem) }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, parentSdf)
    drawSdf(image, parentSdf, insideColor = null)
    writeSnapshotPng(SurroundStrategyTest::class.java, "hex-cells.png", image)
  }

  @Test
  fun `should render surround cells in banana`() {
    val parentSdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, parentSdf)
    drawSdf(image, parentSdf, insideColor = null)
    writeSnapshotPng(SurroundStrategyTest::class.java, "banana-cells.png", image)
  }

  private fun drawCells(image: BufferedImage, parentSdf: Sdf2) {
    val origin = SurroundStrategy.getOrigin(parentSdf, scale)
    for (z in 0 until image.height) {
      for (x in 0 until image.width) {
        val px = x.toDouble()
        val pz = z.toDouble()

        val inner = SurroundStrategy.getDistance(px, pz, origin, parentSdf)
        val isCenter = inner <= 0.0
        image.setRGB(x, z, if (isCenter) CENTER_COLOR else SURROUND_COLOR)
      }
    }
  }

  private fun drawCellDistance(image: BufferedImage, parentSdf: Sdf2, maxDistance: Double) {
    val origin = SurroundStrategy.getOrigin(parentSdf, scale)
    val cellSdf = SurroundCellSdf()

    cellSdf.centerX = origin.centerX
    cellSdf.centerZ = origin.centerZ
    cellSdf.scale = origin.scale
    cellSdf.parent = origin.parent

    for (z in 0 until image.height) {
      for (x in 0 until image.width) {
        val px = x.toDouble()
        val pz = z.toDouble()
        if (parentSdf(px, pz) > 0.0) continue

        val dist = cellSdf(px, pz)
        image.setRGB(x, z, colorForSignedDistance(dist, maxDistance))
      }
    }
  }
}
