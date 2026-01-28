package terrasect.sdf

import org.junit.jupiter.api.Assertions.assertTrue
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

class SitesTest {
  private val imageWidth = 240
  private val imageHeight = 240
  private val seed = 1234L

  @Test
  fun `should render sites in circle sdf`() {
    val radius = 100.0
    val sdf: Sdf2 = { x, z -> sqrt(x * x + z * z) - radius }
    val bounds = estimateBounds(sdf)
    val budgets = intArrayOf(5, 10, 20, 50)
    renderSitesSnapshot("circle.png", sdf, bounds, budgets)
  }

  @Test
  fun `should render sites in hex sdf`() {
    val radius = 100
    val sdf: Sdf2 = { x, z -> hexDistance(x, z, radius) }
    val bounds = estimateBounds(sdf)
    val budgets = intArrayOf(5, 10, 20, 50)
    renderSitesSnapshot("hex.png", sdf, bounds, budgets)
  }

  @Test
  fun `should render sites in banana sdf`() {
    val sdf: Sdf2 = { x, z -> bananaSdf(x, z) }
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

    val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
    val worldCenterX = (bounds.minX + bounds.maxX) * 0.5
    val worldCenterZ = (bounds.minZ + bounds.maxZ) * 0.5

    val centeredSdf: Sdf2 = { x, z -> sdf(x + worldCenterX, z + worldCenterZ) }
    val centerPixelX = imageWidth / 2.0
    val centerPixelZ = imageHeight / 2.0
    drawSdf(image, centerPixelX, centerPixelZ, centeredSdf)

    val sites = getSites(seed, bounds, budgets, sdf)

    for (site in sites) {
      // val distance = sdf(site.x, site.z) + site.budget
      // assertTrue(distance <= 0.5, "Expected site to stay inside SDF boundary")

      val sx = (centerPixelX + (site.x - worldCenterX)).roundToInt()
      val sz = (centerPixelZ + (site.z - worldCenterZ)).roundToInt()

      drawCircle(image, sx, sz, 1)
      drawRing(image, sx, sz, site.budget)
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
