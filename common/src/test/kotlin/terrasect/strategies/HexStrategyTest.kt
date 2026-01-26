package terrasect.strategies

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.SnapshotOutputPaths
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs

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
}
