package terrasect.sdf

import org.junit.jupiter.api.Test
import terrasect.testing.writeSnapshotPng
import java.awt.image.BufferedImage
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2
private const val CZ = HEIGHT / 2
private const val SEED = 1234L

class SitesTest {
  @Test
  fun `should render sites in circle sdf`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val radius = 100f
    val sdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val budgets = longArrayOf(500, 100, 200, 300, 1000, 5000, 3000)
    val sites = getSites(SEED, sdf, bounds, budgets)
    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "circle.png", image)
  }

  @Test
  fun `should render sites in hex sdf`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val apothem = 100f
    val sdf: Sdf2 = translate({ x, z -> hexDistance(x, z, apothem) }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val budgets = longArrayOf(500, 100, 200, 300, 1000, 5000, 3000)
    val sites = getSites(SEED, sdf, bounds, budgets)
    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "hex.png", image)
  }

  @Test
  fun `should render sites in banana sdf`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val sdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val budgets = longArrayOf(500, 100, 200, 300, 1000)
    val sites = getSites(SEED, sdf, bounds, budgets)
    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "banana.png", image)
  }

  @Test
  fun `should position dense sites`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val sdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val area = estimateArea(sdf, bounds)

    val budgets =
        longArrayOf(
            (area * 0.3).roundToLong(),
            (area * 0.5).roundToLong(),
            (area * 0.2).roundToLong(),
        )

    val sites = getSites(SEED, sdf, bounds, budgets)

    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "dense.png", image)
  }

  private fun estimateArea(sdf: Sdf2, bounds: SdfBounds, step: Int = 1): Long {
    val safeStep = step.coerceAtLeast(1)
    val minX = bounds.minX
    val maxX = bounds.maxX
    val minZ = bounds.minZ
    val maxZ = bounds.maxZ
    val cellArea = safeStep * safeStep
    var area = 0L
    var z = minZ
    while (z < maxZ) {
      var x = minX
      while (x < maxX) {
        val sampleX = (x + safeStep * 0.5f).roundToInt()
        val sampleZ = (z + safeStep * 0.5f).roundToInt()
        if (sdf(sampleX, sampleZ) <= 0.0) {
          area += cellArea
        }
        x += safeStep
      }
      z += safeStep
    }
    return area
  }
}
