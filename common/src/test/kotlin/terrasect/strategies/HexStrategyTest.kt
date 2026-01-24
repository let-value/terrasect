package terrasect.strategies

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.testing.SnapshotOutputPaths
import terrasect.utils.first
import terrasect.utils.second
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class HexStrategyTest {

  @Test
  fun `should render cells`() {
    val size = 20
    val width = 200
    val height = 200
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val originX = width / 2
    val originZ = height / 2

    for (z in 0 until height) {
      for (x in 0 until width) {
        val cell = HexStrategy.getCell(x - originX, z - originZ, size)
        image.setRGB(x, z, colorForCell(cell.first(), cell.second()))
      }
    }

    val file = SnapshotOutputPaths.forTestClass(HexStrategyTest::class.java, "cells.png")
    file.parentFile.mkdirs()
    val written = ImageIO.write(image, "png", file)
    assertTrue(written, "Expected to write PNG snapshot to ${file.absolutePath}")
  }

  private fun colorForCell(q: Int, r: Int): Int {
    val seed = q * 0x1f1f1f1f + r * 0x9e3779b9.toInt()
    val red = 0x40 + (seed and 0xBF)
    val green = 0x40 + ((seed shr 8) and 0xBF)
    val blue = 0x40 + ((seed shr 16) and 0xBF)
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
  }
}
