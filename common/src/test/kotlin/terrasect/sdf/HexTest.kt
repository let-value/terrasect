package terrasect.sdf

import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.*

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2.0
private const val CZ = HEIGHT / 2.0

private const val APOTHEM = 40.0
private val budgets = doubleArrayOf(14.0, 12.0, 10.0, 8.0, 6.0, 6.0)

class HexTest {

  @Test
  fun `should render origin hex cell sdf`() {
    renderHexSdfSnapshot(1234L, "origin.png", CX, CZ)
  }

  @Test
  fun `should render offset hex cell sdf`() {
    val centerX = 64.0
    val centerZ = 48.0
    renderHexSdfSnapshot(1337L, "offset.png", CX + centerX, CZ + centerZ)
  }

  private fun renderHexSdfSnapshot(
    seed: Long,
    snapshotName: String,
    centerX: Double,
    centerZ: Double,
  ) {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val sdf = HexCellSdf()

    sdf.centerX = centerX
    sdf.centerZ = centerZ
    sdf.apothem = APOTHEM

    drawSdf(image, sdf)

    val bounds = estimateBounds(sdf)
    val polygon = polygonize(sdf, bounds)

    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      drawLine(
        image,
        (a.x).roundToInt(),
        (a.z).roundToInt(),
        (b.x).roundToInt(),
        (b.z).roundToInt(),
      )
    }

    val sites = getSites(seed, sdf, bounds, budgets)
    assertTrue(sites.isNotEmpty(), "Expected sites for $snapshotName")

    for (site in sites) {
      val distance = sdf(site.x, site.z) + site.radius
      assertTrue(distance <= 0.5, "Expected site to stay inside SDF boundary")

      val sx = (site.x).roundToInt()
      val sz = (site.z).roundToInt()
      drawCircle(image, sx, sz, 2)
      drawRing(image, sx, sz, site.radius)
    }

    writeSnapshotPng(HexTest::class.java, snapshotName, image)
  }
}
