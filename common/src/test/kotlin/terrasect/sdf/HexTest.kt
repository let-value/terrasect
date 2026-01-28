package terrasect.sdf

import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.*

class HexTest {
  private val width = 240
  private val height = 240

  private val cellRadius = 40
  private val budgets = intArrayOf(14, 12, 10, 8, 6, 6)

  @Test
  fun `should render origin hex cell sdf`() {
    renderHexSdfSnapshot("origin.png", 0.0, 0.0)
  }

  @Test
  fun `should render offset hex cell sdf`() {
    val centerX = 64.0
    val centerZ = 48.0
    renderHexSdfSnapshot("offset.png", centerX, centerZ)
  }

  private fun renderHexSdfSnapshot(
      snapshotName: String,
      centerX: Double,
      centerZ: Double,
  ) {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val sdf =
        HexCellSdf().apply {
          this.centerX = centerX
          this.centerZ = centerZ
          this.radius = cellRadius
        }

    val centerPixelX = width / 2.0
    val centerPixelZ = height / 2.0

    drawSdf(image, centerPixelX, centerPixelZ, sdf)

    val margin = cellRadius + CELL_SIZE * 2.0
    val bounds =
        SdfBounds(
            centerX - margin,
            centerX + margin,
            centerZ - margin,
            centerZ + margin,
        )
    val polygon = polygonize(sdf, bounds)
    val centerDistance = sdf(centerX, centerZ)
    val edgeXDistance = sdf(centerX + margin, centerZ)
    val edgeZDistance = sdf(centerX, centerZ + margin)
    assertTrue(
        polygon.isNotEmpty(),
        "Expected polygon for $snapshotName (center=$centerDistance edgeX=$edgeXDistance edgeZ=$edgeZDistance bounds=$bounds)",
    )

    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      drawLine(
          image,
          (centerPixelX + a.x).roundToInt(),
          (centerPixelZ + a.z).roundToInt(),
          (centerPixelX + b.x).roundToInt(),
          (centerPixelZ + b.z).roundToInt(),
      )
    }

    val sites = getSites(1234L, bounds, budgets, sdf)
    assertTrue(sites.isNotEmpty(), "Expected sites for $snapshotName")

    for (site in sites) {
      val distance = sdf(site.x, site.z) + site.budget
      assertTrue(distance <= 0.5, "Expected site to stay inside SDF boundary")

      val sx = (centerPixelX + site.x).roundToInt()
      val sz = (centerPixelZ + site.z).roundToInt()
      drawCircle(image, sx, sz, (site.budget).roundToInt())
      drawRing(image, sx, sz, site.budget)
    }

    writeSnapshotPng(HexTest::class.java, snapshotName, image)
  }
}
