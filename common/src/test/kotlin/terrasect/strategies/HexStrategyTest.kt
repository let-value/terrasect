package terrasect.strategies

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.SnapshotOutputPaths
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.max

class HexStrategyTest {
  val size = 40
  val gap = 20
  val width = 200
  val height = 200

  @Test
  fun `should render cells`() {
    val cellImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    for (z in 0 until height) {
      for (x in 0 until width) {
        val cell = HexStrategy.getCell(x.toLong(), z.toLong(), size, gap)
        val color = colorForCell(cell.q.toInt(), cell.r.toInt(), cell.isGap)

        cellImage.setRGB(x, z, color)
      }
    }

    val cellFile = SnapshotOutputPaths.forTestClass(HexStrategyTest::class.java, "cells.png")
    cellFile.parentFile.mkdirs()
    val cellsWritten = ImageIO.write(cellImage, "png", cellFile)
    assertTrue(cellsWritten, "Expected to write PNG snapshot to ${cellFile.absolutePath}")
  }

  private fun colorForCell(q: Int, r: Int, isGap: Boolean): Int {
    val seed = q * 0x1f1f1f1f + r * 0x9e3779b9.toInt()
    val red = 0x40 + (seed and 0xBF)
    val green = 0x40 + ((seed shr 8) and 0xBF)
    val blue = 0x40 + ((seed shr 16) and 0xBF)
    val alpha = if (isGap) 0x60 else 0xFF
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
  }

  @Test
  fun `should render distance`() {
    val distanceImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    for (z in 0 until height) {
      for (x in 0 until width) {
        val cell = HexStrategy.getCell(x.toLong(), z.toLong(), size, gap)

        distanceImage.setRGB(x, z, colorForDistance(cell.distance, cell.isGap))
      }
    }

    val distanceFile =
        SnapshotOutputPaths.forTestClass(HexStrategyTest::class.java, "cells-distance.png")
    distanceFile.parentFile.mkdirs()
    val distanceWritten = ImageIO.write(distanceImage, "png", distanceFile)
    assertTrue(distanceWritten, "Expected to write PNG snapshot to ${distanceFile.absolutePath}")
  }

  private fun colorForDistance(distance: Double, isGap: Boolean): Int {
    val maxDistance = if (isGap) gap else size
    val normalizedDistance = (abs(distance) / maxDistance * 255f).coerceIn(0.0, 255.0).toInt()

    return if (distance <= 0f) {
      (0xFF shl 24) or (normalizedDistance shl 16) or (normalizedDistance shl 8) or 0xFF
    } else {
      (0xFF shl 24) or (0xFF shl 16) or (normalizedDistance shl 8) or normalizedDistance
    }
  }

  @Test
  fun `should render hex sites`() {
    val radius = size
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val centerX = width / 2.0
    val centerZ = height / 2.0

    for (z in 0 until height) {
      for (x in 0 until width) {
        val localX = x - centerX
        val localZ = z - centerZ
        val distance = HexStrategy.hexDistance(localX, localZ, radius)
        val color =
            when {
              abs(distance) <= 0.6 -> 0xFFFFFFFF.toInt()
              distance < 0.0 -> 0xFF202020.toInt()
              else -> 0xFF0B0B0B.toInt()
            }
        image.setRGB(x, z, color)
      }
    }

    val budgets = intArrayOf(20, 10, 7, 6, 5)
    val sites = HexStrategy.getSites(1234L, 0, 0, radius, budgets, 0, 0, 96)
    val epsilon = 0.35

    for (site in sites) {
      val distance = HexStrategy.hexDistance(site.x, site.z, radius) + site.budget
      assertTrue(distance <= 0.5, "Expected site to stay inside hex boundary")

      val sx = (centerX + site.x).roundToInt()
      val sz = (centerZ + site.z).roundToInt()
      drawCircle(image, sx, sz, 2, 0xFFFF4D4D.toInt())

      val ringSteps = 64
      val ringColor = 0xFF4DD5FF.toInt()
      for (step in 0 until ringSteps) {
        val angle = step / ringSteps.toDouble() * Math.PI * 2.0
        val rx = (sx + cos(angle) * site.budget).roundToInt()
        val rz = (sz + sin(angle) * site.budget).roundToInt()
        if (rx in 0 until width && rz in 0 until height) {
          image.setRGB(rx, rz, ringColor)
        }
      }
    }

    for (i in sites.indices) {
      for (j in i + 1 until sites.size) {
        val dx = sites[i].x - sites[j].x
        val dz = sites[i].z - sites[j].z
        val distance = kotlin.math.sqrt(dx * dx + dz * dz)
        val minDistance = sites[i].budget + sites[j].budget
        assertTrue(
            distance + epsilon >= minDistance,
            "Expected sites to respect budget spacing",
        )
      }
    }

    val sitesFile = SnapshotOutputPaths.forTestClass(HexStrategyTest::class.java, "cell-sites.png")
    sitesFile.parentFile.mkdirs()
    val sitesWritten = ImageIO.write(image, "png", sitesFile)
    assertTrue(sitesWritten, "Expected to write PNG snapshot to ${sitesFile.absolutePath}")
  }

  @Test
  fun `should render banana sdf polygon`() {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val centerX = width / 2.0
    val centerZ = height / 2.0
    val scale = 2.0

    fun smoothMin(a: Double, b: Double, k: Double): Double {
      val h = (0.5 + 0.5 * (b - a) / k).coerceIn(0.0, 1.0)
      return a * h + b * (1.0 - h) - k * h * (1.0 - h)
    }

    fun smoothMax(a: Double, b: Double, k: Double): Double {
      return -smoothMin(-a, -b, k)
    }

    fun bananaLocalSdf(x: Double, z: Double): Double {
      val outer = sqrt(x * x + z * z) - 34.0
      val innerX = x - 18.0
      val innerZ = z + 6.0
      val inner = sqrt(innerX * innerX + innerZ * innerZ) - 30.0
      return smoothMax(outer, -inner, 6.0)
    }

    var minDistance = Double.POSITIVE_INFINITY
    var maxDistance = Double.NEGATIVE_INFINITY
    for (z in 0 until height) {
      for (x in 0 until width) {
        val localX = (x - centerX) / scale
        val localZ = (z - centerZ) / scale
        val distance = bananaLocalSdf(localX, localZ)
        minDistance = min(minDistance, distance)
        maxDistance = max(maxDistance, distance)
        val color =
            when {
              abs(distance) <= 0.7 -> 0xFFFFFFFF.toInt()
              distance < 0.0 -> 0xFF202020.toInt()
              else -> 0xFF0B0B0B.toInt()
            }
        image.setRGB(x, z, color)
      }
    }

    val sdf: Sdf2 = { x, z ->
      bananaLocalSdf(x / scale, z / scale)
    }

    val polygon = SdfPolygon.polygonize(sdf)

    println("banana polygon points=${polygon.size} minDistance=$minDistance maxDistance=$maxDistance")
    if (polygon.isNotEmpty()) {
      println("banana polygon points=" + polygon.joinToString { "(${it.x}, ${it.z})" })
    }

    val palette =
        intArrayOf(
            0xFF4DD5FF.toInt(),
            0xFFFFB04D.toInt(),
            0xFF7CFF4D.toInt(),
            0xFFFF4D8D.toInt(),
        )
    if (polygon.isNotEmpty()) {
      val color = palette[0]
      for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        drawLine(
            image,
            (centerX + a.x).roundToInt(),
            (centerZ + a.z).roundToInt(),
            (centerX + b.x).roundToInt(),
            (centerZ + b.z).roundToInt(),
            color,
        )
      }
    }

    val polygonFile =
        SnapshotOutputPaths.forTestClass(HexStrategyTest::class.java, "banana-polygon.png")
    polygonFile.parentFile.mkdirs()
    val polygonWritten = ImageIO.write(image, "png", polygonFile)
    assertTrue(polygonWritten, "Expected to write PNG snapshot to ${polygonFile.absolutePath}")
  }

  private fun drawCircle(
      image: BufferedImage,
      centerX: Int,
      centerZ: Int,
      radius: Int,
      color: Int,
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
          image.setRGB(x, z, color)
        }
      }
    }
  }

  private fun drawLine(
      image: BufferedImage,
      x0: Int,
      z0: Int,
      x1: Int,
      z1: Int,
      color: Int,
  ) {
    var sx = x0
    var sz = z0
    val dx = kotlin.math.abs(x1 - x0)
    val dz = kotlin.math.abs(z1 - z0)
    val stepX = if (x0 < x1) 1 else -1
    val stepZ = if (z0 < z1) 1 else -1
    var err = dx - dz

    while (true) {
      if (sx in 0 until image.width && sz in 0 until image.height) {
        image.setRGB(sx, sz, color)
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
}
