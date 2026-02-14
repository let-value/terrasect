package terrasect.strategies

import org.junit.jupiter.api.Test
import terrasect.sdf.*
import terrasect.testing.writeSnapshotPng
import java.awt.image.BufferedImage
import kotlin.math.sqrt

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2.0
private const val CZ = HEIGHT / 2.0

class SubdivisionStrategyTest {

  private val budgets = doubleArrayOf(1000.0, 500.0, 300.0, 200.0, 100.0)

  @Test
  fun `should render subdivision cells in circle`() {
    val radius = 100.0
    val parentSdf: Sdf2 = translate({ x, z -> sqrt(x * x + z * z) - radius }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, parentSdf)
    drawSdf(image, parentSdf, insideColor = null)
    writeSnapshotPng(SubdivisionStrategyTest::class.java, "circle-cells.png", image)
  }

  @Test
  fun `should render subdivision distance in circle`() {
    val radius = 100.0
    val parentSdf: Sdf2 = translate({ x, z -> sqrt(x * x + z * z) - radius }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCellDistance(image, parentSdf, radius)
    writeSnapshotPng(SubdivisionStrategyTest::class.java, "circle-distance.png", image)
  }

  @Test
  fun `should render subdivision cells in hex`() {
    val apothem = 100.0
    val parentSdf: Sdf2 = translate({ x, z -> hexDistance(x, z, apothem) }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, parentSdf)
    drawSdf(image, parentSdf, insideColor = null)
    writeSnapshotPng(SubdivisionStrategyTest::class.java, "hex-cells.png", image)
  }

  @Test
  fun `should render subdivision cells in banana`() {
    val parentSdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, parentSdf)
    drawSdf(image, parentSdf, insideColor = null)
    writeSnapshotPng(SubdivisionStrategyTest::class.java, "banana-cells.png", image)
  }

  private fun drawCells(image: BufferedImage, parentSdf: Sdf2) {
    val split = SubdivisionStrategy.getSplit(parentSdf, budgets)
    for (z in 0 until image.height) {
      for (x in 0 until image.width) {
        val px = x.toDouble()
        val pz = z.toDouble()
        if (parentSdf(px, pz) > 0.0) continue

        val v = if (split.axis == 0) px else pz
        val index = SubdivisionStrategy.getChildIndex(v, split)
        image.setRGB(x, z, colorForCell(index))
      }
    }
  }

  private fun drawCellDistance(image: BufferedImage, parentSdf: Sdf2, maxDistance: Double) {
    val split = SubdivisionStrategy.getSplit(parentSdf, budgets)
    val cellSdf = SubdivisionCellSdf()

    for (z in 0 until image.height) {
      for (x in 0 until image.width) {
        val px = x.toDouble()
        val pz = z.toDouble()
        if (parentSdf(px, pz) > 0.0) continue

        val v = if (split.axis == 0) px else pz
        val index = SubdivisionStrategy.getChildIndex(v, split)

        cellSdf.axis = split.axis
        cellSdf.lo = split.edges[index]
        cellSdf.hi = split.edges[index + 1]
        val dist = cellSdf(px, pz)
        image.setRGB(x, z, colorForSignedDistance(dist, maxDistance))
      }
    }
  }

  private fun colorForCell(index: Int): Int {
    val seed = index * 0x9e3779b9.toInt()
    val red = 0x30 + (seed and 0x7F)
    val green = 0x30 + ((seed shr 8) and 0x7F)
    val blue = 0x30 + ((seed shr 16) and 0x7F)
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
  }
}
