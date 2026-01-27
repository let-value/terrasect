package terrasect.sdf

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.SnapshotOutputPaths
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val SITE_AREA_COLOR = 0xFFFF4D4D.toInt()

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

    for (z in 0 until height) {
      for (x in 0 until width) {
        val localX = x - centerX
        val localZ = z - centerZ
        val distance = sdf(localX, localZ)
        val color =
            when {
              abs(distance) <= 0.6 -> 0xFFFFFFFF.toInt()
              distance < 0.0 -> 0xFF202020.toInt()
              else -> 0xFF0B0B0B.toInt()
            }
        image.setRGB(x, z, color)
      }
    }

    val bounds = estimateBounds(sdf)
    val budgets = intArrayOf(14, 12, 10, 8, 6, 6)
    val sites = getSites(1234L, bounds, budgets, sdf)

    for (site in sites) {
      val distance = sdf(site.x, site.z) + site.budget
      assertTrue(distance <= 0.5, "Expected site to stay inside SDF boundary")

      val sx = (centerX + site.x).roundToInt()
      val sz = (centerZ + site.z).roundToInt()
      drawCircle(image, sx, sz, site.budget.roundToInt())

      val ringSteps = 64
      val ringColor = 0xFF4DD5FF.toInt()
      for (step in 0 until ringSteps) {
        val angle = step / ringSteps.toDouble() * Math.PI * 2.0
        val rx = (sx + kotlin.math.cos(angle) * site.budget).roundToInt()
        val rz = (sz + kotlin.math.sin(angle) * site.budget).roundToInt()
        if (rx in 0 until width && rz in 0 until height) {
          image.setRGB(rx, rz, ringColor)
        }
      }
    }

    val sitesFile = SnapshotOutputPaths.forTestClass(SitesTest::class.java, "sdf-sites.png")
    sitesFile.parentFile.mkdirs()
    val sitesWritten = ImageIO.write(image, "png", sitesFile)
    assertTrue(sitesWritten, "Expected to write PNG snapshot to ${sitesFile.absolutePath}")
  }

  private fun drawCircle(
      image: BufferedImage,
      centerX: Int,
      centerZ: Int,
      radius: Int,
  ) {
    val minX = (centerX - radius).coerceAtLeast(0)
    val maxX = (centerX + radius).coerceAtMost(image.width - 1)
    val minZ = (centerZ - radius).coerceAtLeast(0)
    val maxZ = (centerZ + radius).coerceAtMost(image.height - 1)

    val rSquared = radius * radius
    for (z in minZ..maxZ) {
      val dz = z - centerZ
      for (x in minX..maxX) {
        val dx = x - centerX
        if (dx * dx + dz * dz <= rSquared) {
          image.setRGB(x, z, SITE_AREA_COLOR)
        }
      }
    }
  }
}
