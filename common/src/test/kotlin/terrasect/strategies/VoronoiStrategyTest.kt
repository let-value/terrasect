package terrasect.strategies

import java.awt.image.BufferedImage
import kotlin.math.hypot
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
