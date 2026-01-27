package terrasect.sdf

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.SnapshotOutputPaths

class HexSdfTest {
  private val width = 240
  private val height = 240
  private val scale = 1.0
  private val cellRadius = 40
  private val budgets = intArrayOf(14, 12, 10, 8, 6, 6)

  @Test
  fun `should render origin hex cell sdf`() {
    renderHexSdfSnapshot("hex-origin.png", 0.0, 0.0)
  }

  @Test
  fun `should render offset hex cell sdf`() {
    val centerX = 64.0
    val centerZ = 48.0
    renderHexSdfSnapshot("hex-offset.png", centerX, centerZ)
  }

  private fun renderHexSdfSnapshot(
      snapshotName: String,
      centerX: Double,
      centerZ: Double,
  ) {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val sdf = HexCellSdf().apply {
      this.centerX = centerX
      this.centerZ = centerZ
      this.radius = cellRadius
    }

    val centerPixelX = width / 2.0
    val centerPixelZ = height / 2.0

    for (z in 0 until height) {
      for (x in 0 until width) {
        val worldX = (x - centerPixelX) / scale
        val worldZ = (z - centerPixelZ) / scale
        val distance = sdf(worldX, worldZ)
        val color =
            when {
              abs(distance) <= 0.6 -> 0xFFFFFFFF.toInt()
              distance < 0.0 -> 0xFF202020.toInt()
              else -> 0xFF0B0B0B.toInt()
            }
        image.setRGB(x, z, color)
      }
    }

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
          (centerPixelX + a.x * scale).roundToInt(),
          (centerPixelZ + a.z * scale).roundToInt(),
          (centerPixelX + b.x * scale).roundToInt(),
          (centerPixelZ + b.z * scale).roundToInt(),
      )
    }

    val sites = getSites(1234L, bounds, budgets, sdf)
    assertTrue(sites.isNotEmpty(), "Expected sites for $snapshotName")

    for (site in sites) {
      val distance = sdf(site.x, site.z) + site.budget
      assertTrue(distance <= 0.5, "Expected site to stay inside SDF boundary")

      val sx = (centerPixelX + site.x * scale).roundToInt()
      val sz = (centerPixelZ + site.z * scale).roundToInt()
      drawCircle(image, sx, sz, (site.budget * scale).roundToInt())

      val ringSteps = 64
      val ringColor = 0xFF4DD5FF.toInt()
      for (step in 0 until ringSteps) {
        val angle = step / ringSteps.toDouble() * Math.PI * 2.0
        val rx = (sx + kotlin.math.cos(angle) * site.budget * scale).roundToInt()
        val rz = (sz + kotlin.math.sin(angle) * site.budget * scale).roundToInt()
        if (rx in 0 until width && rz in 0 until height) {
          image.setRGB(rx, rz, ringColor)
        }
      }
    }

    val outputFile = SnapshotOutputPaths.forTestClass(HexSdfTest::class.java, snapshotName)
    outputFile.parentFile.mkdirs()
    val written = ImageIO.write(image, "png", outputFile)
    assertTrue(written, "Expected to write PNG snapshot to ${outputFile.absolutePath}")
  }

  private fun drawLine(
      image: BufferedImage,
      x0: Int,
      z0: Int,
      x1: Int,
      z1: Int,
  ) {
    var sx = x0
    var sz = z0
    val dx = abs(x1 - x0)
    val dz = abs(z1 - z0)
    val stepX = if (x0 < x1) 1 else -1
    val stepZ = if (z0 < z1) 1 else -1
    var err = dx - dz

    while (true) {
      if (sx in 0 until image.width && sz in 0 until image.height) {
        image.setRGB(sx, sz, LINE_COLOR)
      }
      if (sx == x1 && sz == z1) break
      val e2 = err * 2
      if (e2 > -dz) {
        err -= dz
        sx += stepX
      }
      if (e2 < dx) {
        err += dx
        sz += stepZ
      }
    }
  }

  private fun drawCircle(
      image: BufferedImage,
      centerX: Int,
      centerZ: Int,
      radius: Int,
  ) {
    if (radius <= 0) return

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
