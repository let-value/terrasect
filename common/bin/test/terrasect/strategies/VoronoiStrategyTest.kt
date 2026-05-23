package terrasect.strategies

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.sdf.*
import terrasect.testing.writeSnapshotPng

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2
private const val CZ = HEIGHT / 2
private const val SEED = 1234

class VoronoiStrategyTest {

  @Test
  fun `should render voronoi cells in circle`() {
    val radius = 100f
    val sdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)
    val budgets = longArrayOf(500, 100, 200, 300, 1000, 5000, 3000)
    val sites = VoronoiStrategy.getSites(SEED, sdf, budgets)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, sites)
    drawSdf(image, sdf, insideColor = null)
    drawSites(image, sites)
    writeSnapshotPng(VoronoiStrategyTest::class.java, "circle-cells.png", image)
  }

  @Test
  fun `should render voronoi distance in circle`() {
    val radius = 100f
    val sdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)
    val budgets = longArrayOf(500, 100, 200, 300, 1000, 5000, 3000)
    val sites = VoronoiStrategy.getSites(SEED, sdf, budgets)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val voronoiSdf = VoronoiCellSdf()

    for (z in 0 until HEIGHT) {
      for (x in 0 until WIDTH) {

        if (sdf(x, z) > 0f) continue

        val cellIndex = VoronoiStrategy.getCellIndex(x, z, sites)
        voronoiSdf.sites = sites
        voronoiSdf.index = cellIndex
        val dist = voronoiSdf(x, z)
        image.setRGB(x, z, colorForSignedDistance(dist, radius))
      }
    }

    writeSnapshotPng(VoronoiStrategyTest::class.java, "circle-distance.png", image)
  }

  @Test
  fun `should render voronoi cells in hex`() {
    val apothem = 100f
    val sdf: Sdf2 = translate({ x, z -> hexDistance(x, z, apothem) }, CX, CZ)
    val budgets = longArrayOf(500, 100, 200, 300, 1000, 5000, 3000)
    val sites = VoronoiStrategy.getSites(SEED, sdf, budgets)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, sites)
    drawSdf(image, sdf, insideColor = null)
    drawSites(image, sites)
    writeSnapshotPng(VoronoiStrategyTest::class.java, "hex-cells.png", image)
  }

  @Test
  fun `should render voronoi cells in banana`() {
    val sdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)

    val budgets = longArrayOf(500, 100, 200, 300, 1000)
    val sites = VoronoiStrategy.getSites(SEED, sdf, budgets)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, sites)
    drawSdf(image, sdf, insideColor = null)
    drawSites(image, sites)
    writeSnapshotPng(VoronoiStrategyTest::class.java, "banana-cells.png", image)
  }

  @Test
  fun `should allocate voronoi area roughly by budget`() {
    val radius = 100f
    val parentSdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)
    val bounds = estimateBounds(parentSdf)
    val parentArea = estimateArea(parentSdf, bounds)
    val safeParentArea = parentArea.coerceAtLeast(1L).toDouble()

    val budgets =
      longArrayOf(
        (parentArea * 0.35).roundToLong(),
        (parentArea * 0.25).roundToLong(),
        (parentArea * 0.20).roundToLong(),
        (parentArea * 0.12).roundToLong(),
        (parentArea * 0.08).roundToLong(),
      )

    val sites = VoronoiStrategy.getSites(SEED, parentSdf, budgets)
    val totalBudget = budgets.sum().toDouble().coerceAtLeast(1.0)
    val sortedBudgets = budgets.sortedDescending()
    val cellSdf = VoronoiCellSdf().apply { this.sites = sites }

    var realizedTotal = 0.0
    for (index in sites.indices) {
      cellSdf.index = index
      val childSdf: Sdf2 = { x, z -> max(parentSdf(x, z), cellSdf(x, z)) }
      val cellArea = estimateArea(childSdf, bounds)

      val realizedFraction = cellArea / safeParentArea
      val expectedFraction = sortedBudgets[index] / totalBudget
      realizedTotal += realizedFraction

      assertTrue(
        abs(realizedFraction - expectedFraction) <= 0.20,
        "voronoi cell $index expected=$expectedFraction realized=$realizedFraction area=$cellArea budget=${sortedBudgets[index]}",
      )
    }

    assertTrue(abs(realizedTotal - 1.0) <= 0.05, "realized total area fraction=$realizedTotal")
  }

  @Test
  fun `should test dense voronoi when child budgets exactly match parent budget`() {
    val radius = 100f
    val parentSdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)
    val bounds = estimateBounds(parentSdf)
    val parentArea = estimateArea(parentSdf, bounds)
    val childCount = 24

    val baseBudget = parentArea / childCount
    val remainder = (parentArea % childCount).toInt()
    val budgets = LongArray(childCount) { baseBudget }
    for (i in 0 until remainder) {
      budgets[i] += 1
    }

    assertEquals(parentArea, budgets.sum(), "dense setup requires exact budget sum")

    val sites = VoronoiStrategy.getSites(SEED, parentSdf, budgets)
    val sortedBudgets = budgets.sortedDescending()
    val sdf = VoronoiCellSdf().apply { this.sites = sites }
    val safeParentArea = parentArea.coerceAtLeast(1L).toDouble()

    var realizedTotal = 0.0
    var worstRelativeError = 0.0
    var averageRelativeError = 0.0

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawCells(image, sites)
    drawSdf(image, parentSdf, insideColor = null)
    drawSites(image, sites)
    writeSnapshotPng(VoronoiStrategyTest::class.java, "dense-cells.png", image)

    for (index in sites.indices) {
      sdf.index = index
      val childSdf: Sdf2 = { x, z -> max(parentSdf(x, z), sdf(x, z)) }
      val cellArea = estimateArea(childSdf, bounds).toDouble()
      val expectedArea = sortedBudgets[index].toDouble().coerceAtLeast(1.0)
      val relativeError = abs(cellArea - expectedArea) / expectedArea

      realizedTotal += cellArea / safeParentArea
      worstRelativeError = max(worstRelativeError, relativeError)
      averageRelativeError += relativeError
    }

    averageRelativeError /= sites.size.coerceAtLeast(1)

    println(
      "dense voronoi stats: parentArea=$parentArea childCount=$childCount worstRelError=$worstRelativeError avgRelError=$averageRelativeError realizedTotal=$realizedTotal"
    )

    assertTrue(
      abs(realizedTotal - 1.0) <= 0.05,
      "dense realized total area fraction=$realizedTotal",
    )
    assertTrue(
      worstRelativeError <= 0.35,
      "dense voronoi worst relative error too high=$worstRelativeError avg=$averageRelativeError",
    )
  }

  private fun drawCells(image: BufferedImage, sites: List<Site>) {
    for (z in 0 until image.height) {
      for (x in 0 until image.width) {

        val cellIndex = VoronoiStrategy.getCellIndex(x, z, sites)
        val color = colorForCell(cellIndex)
        image.setRGB(x, z, color)
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
