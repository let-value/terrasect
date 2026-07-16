package terrasect.sdf

import java.awt.image.BufferedImage
import kotlin.math.hypot
import kotlin.math.roundToLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.writeSnapshotPng

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
    val budgets = longArrayOf(5000, 3000, 1000, 500, 300, 200, 100)
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
    val budgets = longArrayOf(5000, 3000, 1000, 500, 300, 200, 100)
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
    val budgets = longArrayOf(1000, 500, 300, 200, 100)
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
        (area * 0.5).roundToLong(),
        (area * 0.3).roundToLong(),
        (area * 0.2).roundToLong(),
      )

    val sites = getSites(SEED, sdf, bounds, budgets)

    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "dense.png", image)
  }

  @Test
  fun `should place sites when sdf has no interior`() {
    val sdf: Sdf2 = { _, _ -> 1f }
    val bounds = estimateBounds(sdf)
    val budgets = longArrayOf(500, 200, 100)

    val sites = getSites(SEED, sdf, bounds, budgets)

    assertEquals(budgets.size, sites.size)
    assertTrue(sites.all { it.x in bounds.minX until bounds.maxX })
    assertTrue(sites.all { it.z in bounds.minZ until bounds.maxZ })
  }
}
