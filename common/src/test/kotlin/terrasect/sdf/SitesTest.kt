package terrasect.sdf

import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.drawCircle
import terrasect.testing.drawRing
import terrasect.testing.drawSdf
import terrasect.testing.writeSnapshotPng

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2.0
private const val CZ = HEIGHT / 2.0
private const val SEED = 1234L

class SitesTest {

  @Test
  fun `should render sites in circle sdf`() {
    val radius = 100.0
    val sdf: Sdf2 = translate({ x, z -> sqrt(x * x + z * z) - radius }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val area = estimateArea(sdf, bounds)
    assertTrue(area > 0.0, "Expected circle SDF to have area")

    val budgets = intArrayOf(5, 10, 20, 50)
    renderSitesSnapshot("circle.png", sdf, bounds, budgets)
  }

  @Test
  fun `should render sites in hex sdf`() {
    val apothem = 100.0
    val sdf: Sdf2 = translate({ x, z -> hexDistance(x, z, apothem) }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val area = estimateArea(sdf, bounds)
    assertTrue(area > 0.0, "Expected hex SDF to have area")

    val budgets = intArrayOf(5, 10, 20, 50)
    renderSitesSnapshot("hex.png", sdf, bounds, budgets)
  }

  @Test
  fun `should render sites in banana sdf`() {
    val sdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val budgets = intArrayOf(5, 10, 15, 8)
    val area = estimateArea(sdf, bounds)
    assertTrue(area > 0.0, "Expected banana SDF to have area")

    renderSitesSnapshot("banana.png", sdf, bounds, budgets)
  }

  private fun renderSitesSnapshot(
      snapshotName: String,
      sdf: Sdf2,
      bounds: SdfBounds,
      budgets: IntArray,
  ) {

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)

    drawSdf(image, sdf)

    val sites = getSites(SEED, bounds, budgets, sdf)

    for (site in sites) {
      val distance = sdf(site.x, site.z) + site.budget
      assertTrue(distance <= 0.5, "Expected site to stay inside SDF boundary")

      val x = site.x.roundToInt()
      val z = site.z.roundToInt()

      drawCircle(image, x, z, 1)
      drawRing(image, x, z, site.budget)
    }

    writeSnapshotPng(SitesTest::class.java, snapshotName, image)
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
