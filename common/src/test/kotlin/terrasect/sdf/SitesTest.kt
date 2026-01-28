package terrasect.sdf

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.drawCircle
import terrasect.testing.drawRing
import terrasect.testing.drawSdf
import terrasect.testing.writeSnapshotPng
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlin.math.sqrt

class SitesTest {
  private val width = 200
  private val height = 200

  @Test
  fun `should render sites in circle sdf`() {
    val radius = 70.0
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val centerX = width / 2.0
    val centerZ = height / 2.0

    val sdf: Sdf2 = { x, z -> sqrt(x * x + z * z) - radius }

    drawSdf(image, centerX, centerZ, 1.0, sdf)

    val bounds = estimateBounds(sdf)
    val budgets = intArrayOf(14, 12, 10, 8, 6, 6)
    val sites = getSites(1234L, bounds, budgets, sdf)

    for (site in sites) {
      val distance = sdf(site.x, site.z) + site.budget
      assertTrue(distance <= 0.5, "Expected site to stay inside SDF boundary")

      val sx = (centerX + site.x).roundToInt()
      val sz = (centerZ + site.z).roundToInt()
      drawCircle(image, sx, sz, site.budget.roundToInt())
      drawRing(image, sx, sz, site.budget)
    }

    writeSnapshotPng(SitesTest::class.java, "sdf-sites.png", image)
  }
}
