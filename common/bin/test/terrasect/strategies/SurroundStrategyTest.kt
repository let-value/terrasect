package terrasect.strategies

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.sdf.*
import terrasect.testing.writeSnapshotPng

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2
private const val CZ = HEIGHT / 2

private const val CENTER_COLOR = 0xFF3A6B8C.toInt()
private const val SURROUND_COLOR = 0xFF8C5A3A.toInt()

class SurroundStrategyTest {

  private val centerBudget = 3000L
  private val surroundBudget = 1000L
  private val scale = surroundScale(centerBudget, centerBudget + surroundBudget)

  @Test
  fun `should render surround cells in circle`() {
    val radius = 100f
    val parentSdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, parentSdf)
    drawSdf(image, parentSdf, insideColor = null)
    writeSnapshotPng(SurroundStrategyTest::class.java, "circle-cells.png", image)
  }

  @Test
  fun `should render surround distance in circle`() {
    val radius = 100f
    val parentSdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCellDistance(image, parentSdf, radius)
    writeSnapshotPng(SurroundStrategyTest::class.java, "circle-distance.png", image)
  }

  @Test
  fun `should render surround cells in hex`() {
    val apothem = 100f
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

  @Test
  fun `should allocate surround area by budgets`() {
    val radius = 100f
    val parentSdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)
    val bounds = estimateBounds(parentSdf)
    val origin = SurroundStrategy.getOrigin(parentSdf)
    val safeParentArea = estimateArea(parentSdf, bounds).coerceAtLeast(1L).toDouble()
    val totalBudget = (centerBudget + surroundBudget).toDouble().coerceAtLeast(1.0)

    val centerSdf =
      CenterCellSdf().apply {
        parent = parentSdf
        centerX = origin.centerX
        centerZ = origin.centerZ
        this.scale = this@SurroundStrategyTest.scale
      }

    val surroundSdf =
      SurroundCellSdf().apply {
        parent = parentSdf
        centerX = origin.centerX
        centerZ = origin.centerZ
        this.scale = this@SurroundStrategyTest.scale
      }

    val centerArea = estimateArea({ x, z -> max(parentSdf(x, z), centerSdf(x, z)) }, bounds)
    val surroundArea = estimateArea({ x, z -> max(parentSdf(x, z), surroundSdf(x, z)) }, bounds)

    val centerFraction = centerArea / safeParentArea
    val surroundFraction = surroundArea / safeParentArea
    val expectedCenterFraction = centerBudget / totalBudget
    val expectedSurroundFraction = surroundBudget / totalBudget

    assertTrue(
      abs(centerFraction - expectedCenterFraction) <= 0.07,
      "center expected=$expectedCenterFraction realized=$centerFraction area=$centerArea",
    )
    assertTrue(
      abs(surroundFraction - expectedSurroundFraction) <= 0.07,
      "surround expected=$expectedSurroundFraction realized=$surroundFraction area=$surroundArea",
    )
    assertTrue(
      abs((centerFraction + surroundFraction) - 1.0) <= 0.03,
      "realized total area fraction=${centerFraction + surroundFraction}",
    )
  }

  private fun drawCells(image: BufferedImage, parentSdf: Sdf2) {
    val origin = SurroundStrategy.getOrigin(parentSdf)
    for (z in 0 until image.height) {
      for (x in 0 until image.width) {

        val inner = surroundDistance(x, z, parentSdf, origin.centerX, origin.centerZ, scale)
        val isCenter = inner <= 0.0
        image.setRGB(x, z, if (isCenter) CENTER_COLOR else SURROUND_COLOR)
      }
    }
  }

  private fun drawCellDistance(image: BufferedImage, parentSdf: Sdf2, maxDistance: Float) {
    val origin = SurroundStrategy.getOrigin(parentSdf)
    val cellSdf = SurroundCellSdf()

    cellSdf.centerX = origin.centerX
    cellSdf.centerZ = origin.centerZ
    cellSdf.scale = scale
    cellSdf.parent = parentSdf

    for (z in 0 until image.height) {
      for (x in 0 until image.width) {
        if (parentSdf(x, z) > 0f) continue

        val dist = cellSdf(x, z)
        image.setRGB(x, z, colorForSignedDistance(dist, maxDistance))
      }
    }
  }
}
