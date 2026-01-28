package terrasect.sdf

import org.junit.jupiter.api.Test
import terrasect.testing.drawCircle
import terrasect.testing.drawRing
import terrasect.testing.drawSdf
import terrasect.testing.writeSnapshotPng
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2.0
private const val CZ = HEIGHT / 2.0
private const val SEED = 1234L

class SitesTest {
  @Test
  fun `should render sites in circle sdf`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val radius = 100.0
    val sdf: Sdf2 = translate({ x, z -> sqrt(x * x + z * z) - radius }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val budgets = intArrayOf(5, 10, 20, 50)
    val sites = getSites(SEED, bounds, budgets, sdf)
    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "circle.png", image)
  }

  @Test
  fun `should render sites in hex sdf`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val apothem = 100.0
    val sdf: Sdf2 = translate({ x, z -> hexDistance(x, z, apothem) }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val budgets = intArrayOf(5, 10, 20, 50)
    val sites = getSites(SEED, bounds, budgets, sdf)
    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "hex.png", image)
  }

  @Test
  fun `should render sites in banana sdf`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val sdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val budgets = intArrayOf(5, 10, 15, 8)
    val sites = getSites(SEED, bounds, budgets, sdf)
    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "banana.png", image)
  }

  private fun drawSites(image: BufferedImage, sites: Array<Site>) {
    for (site in sites) {
      val x = site.x.roundToInt()
      val z = site.z.roundToInt()
      drawCircle(image, x, z, 1)
      drawRing(image, x, z, site.budget)
    }
  }

  private fun estimateArea(
      sdf: Sdf2,
      bounds: SdfBounds,
      step: Double = 1.0,
  ): Double {
    val safeStep = step.coerceAtLeast(0.25)
    val minX = floor(bounds.minX)
    val maxX = ceil(bounds.maxX)
    val minZ = floor(bounds.minZ)
    val maxZ = ceil(bounds.maxZ)
    val cellArea = safeStep * safeStep
    var area = 0.0
    var z = minZ
    while (z < maxZ) {
      var x = minX
      while (x < maxX) {
        val sampleX = x + safeStep * 0.5
        val sampleZ = z + safeStep * 0.5
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
