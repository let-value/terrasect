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

class SubdivisionStrategyTest {

  private val budgets = longArrayOf(1000, 500, 300, 200, 100)

  @Test
  fun `should render subdivision cells in circle`() {
    val radius = 100f
    val parentSdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, parentSdf)
    drawSdf(image, parentSdf, insideColor = null)
    writeSnapshotPng(SubdivisionStrategyTest::class.java, "circle-cells.png", image)
  }

  @Test
  fun `should render subdivision distance in circle`() {
    val radius = 100f
    val parentSdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCellDistance(image, parentSdf, radius)
    writeSnapshotPng(SubdivisionStrategyTest::class.java, "circle-distance.png", image)
  }

  @Test
  fun `should render subdivision cells in hex`() {
    val apothem = 100f
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

  @Test
  fun `should split far parent instance around its origin`() {
    val originX = 500_000
    val originZ = -300_000
    val radius = 100f
    val parentSdf: Sdf2 =
      translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, originX, originZ)

    val split = SubdivisionStrategy.getSplit(parentSdf, budgets, originX, originZ)
    val min = split.edges.first()
    val max = split.edges.last()

    val origin = if (split.axis == 0) originX.toFloat() else originZ.toFloat()
    assertTrue(origin in min..max) {
      "far subdivision split did not cover the parent origin: axis=${split.axis} min=$min max=$max"
    }
  }

  @Test
  fun `should allocate subdivision area by budget in rectangle sdf`() {
    val halfWidth = 120
    val halfHeight = 80
    val parentSdf: Sdf2 = { x, z ->
      val dx = kotlin.math.abs(x - CX) - halfWidth
      val dz = kotlin.math.abs(z - CZ) - halfHeight
      max(dx, dz).toFloat()
    }

    val bounds = estimateBounds(parentSdf)
    val split = SubdivisionStrategy.getSplit(parentSdf, budgets)
    val cellSdf = SubdivisionCellSdf()
    val safeParentArea = estimateArea(parentSdf, bounds).coerceAtLeast(1L).toDouble()
    val totalBudget = budgets.sum().toDouble().coerceAtLeast(1.0)

    var realizedTotal = 0.0
    for (i in budgets.indices) {
      cellSdf.axis = split.axis
      cellSdf.lo = split.edges[i]
      cellSdf.hi = split.edges[i + 1]

      val childSdf: Sdf2 = { x, z -> max(parentSdf(x, z), cellSdf(x, z)) }
      val childArea = estimateArea(childSdf, bounds)
      val realizedFraction = childArea / safeParentArea
      val expectedFraction = budgets[i] / totalBudget
      realizedTotal += realizedFraction

      assertTrue(
        abs(realizedFraction - expectedFraction) <= 0.08,
        "subdivision child $i expected=$expectedFraction realized=$realizedFraction area=$childArea budget=${budgets[i]}",
      )
    }

    assertTrue(abs(realizedTotal - 1.0) <= 0.03, "realized total area fraction=$realizedTotal")
  }

  private fun drawCells(image: BufferedImage, parentSdf: Sdf2) {
    val split = SubdivisionStrategy.getSplit(parentSdf, budgets)
    for (z in 0 until image.height) {
      for (x in 0 until image.width) {

        if (parentSdf(x, z) > 0.0) continue

        val v = if (split.axis == 0) x else z
        val index = SubdivisionStrategy.getChildIndex(v, split)
        image.setRGB(x, z, colorForCell(index))
      }
    }
  }

  private fun drawCellDistance(image: BufferedImage, parentSdf: Sdf2, maxDistance: Float) {
    val split = SubdivisionStrategy.getSplit(parentSdf, budgets)
    val cellSdf = SubdivisionCellSdf()

    for (z in 0 until image.height) {
      for (x in 0 until image.width) {

        if (parentSdf(x, z) > 0.0) continue

        val v = if (split.axis == 0) x else z
        val index = SubdivisionStrategy.getChildIndex(v, split)

        cellSdf.axis = split.axis
        cellSdf.lo = split.edges[index]
        cellSdf.hi = split.edges[index + 1]
        val dist = cellSdf(x, z)
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
