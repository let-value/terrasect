package terrasect.strategies

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.SnapshotOutputPaths
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class HexStrategyTest {

  @Test
  fun `should render cells`() {
    val size = 40
    val gap = 4
    val width = 200
    val height = 200
    val cellImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val distanceImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val originX = width / 2
    val originZ = height / 2

    for (z in 0 until height) {
      for (x in 0 until width) {
        val cell = HexStrategy.getCell(x - originX, z - originZ, size, gap)

        val color =
            if (cell.isGap) {
              0xFFF2F2F2.toInt()
            } else {
              colorForCell(cell.q, cell.r)
            }
        cellImage.setRGB(x, z, color)
        distanceImage.setRGB(x, z, colorForDistance(cell.distance, size.toFloat()))
      }
    }

    val cellFile = SnapshotOutputPaths.forTestClass(HexStrategyTest::class.java, "cells.png")
    cellFile.parentFile.mkdirs()
    val cellsWritten = ImageIO.write(cellImage, "png", cellFile)
    assertTrue(cellsWritten, "Expected to write PNG snapshot to ${cellFile.absolutePath}")

    val distanceFile =
        SnapshotOutputPaths.forTestClass(HexStrategyTest::class.java, "cells-distance.png")
    distanceFile.parentFile.mkdirs()
    val distanceWritten = ImageIO.write(distanceImage, "png", distanceFile)
    assertTrue(distanceWritten, "Expected to write PNG snapshot to ${distanceFile.absolutePath}")
  }

  private fun colorForCell(q: Int, r: Int): Int {
    val seed = q * 0x1f1f1f1f + r * 0x9e3779b9.toInt()
    val red = 0x40 + (seed and 0xBF)
    val green = 0x40 + ((seed shr 8) and 0xBF)
    val blue = 0x40 + ((seed shr 16) and 0xBF)
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
  }

  private fun colorForDistance(distance: Float, size: Float): Int {
    val magnitude = (kotlin.math.abs(distance) / size).coerceIn(0f, 1f)
    val edge = 1f - magnitude
    val edgeByte = (edge * 255).toInt().coerceIn(0, 255)
    return if (distance <= 0f) {
      (0xFF shl 24) or (edgeByte shl 16) or (edgeByte shl 8) or 0xFF
    } else {
      (0xFF shl 24) or (0xFF shl 16) or (edgeByte shl 8) or edgeByte
    }
  }
}
