package terrasect.strategies

import java.awt.image.BufferedImage
import org.junit.jupiter.api.Test
import terrasect.sdf.Sdf2
import terrasect.sdf.drawDistance
import terrasect.testing.writeSnapshotPng

class HexStrategyTest {
  val apothem = 40.0
  val gap = 20.0
  val width = 200
  val height = 200

  @Test
  fun `should render cells`() {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    for (z in 0 until height) {
      for (x in 0 until width) {
        val cell = HexStrategy.getCell(x.toLong(), z.toLong(), apothem, gap)
        val color = colorForCell(cell.q.toInt(), cell.r.toInt(), cell.isGap)

        image.setRGB(x, z, color)
      }
    }

    writeSnapshotPng(HexStrategyTest::class.java, "cells.png", image)
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
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    val sdf: Sdf2 = { x, z -> HexStrategy.getCell(x.toLong(), z.toLong(), apothem, gap).distance }
    val maxDistance = (apothem + gap)
    drawDistance(image, sdf, maxDistance)
    writeSnapshotPng(HexStrategyTest::class.java, "cells-distance.png", image)
  }
}
