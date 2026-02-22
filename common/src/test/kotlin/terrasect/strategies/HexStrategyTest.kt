package terrasect.strategies

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.sdf.*
import terrasect.testing.writeSnapshotPng
import java.awt.image.BufferedImage
import kotlin.math.abs

class HexStrategyTest {
  val apothem = 40f
  val gap = 20f
  val width = 200
  val height = 200

  @Test
  fun `should render cells`() {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    for (z in 0 until height) {
      for (x in 0 until width) {
        val cell = HexStrategy.getCell(x, z, apothem, gap)
        val color = colorForCell(cell.q, cell.r, cell.isGap)

        image.setRGB(x, z, color)
      }
    }

    writeSnapshotPng(HexStrategyTest::class.java, "cells.png", image)
  }

  @Test
  fun `should scale hex cell and ring areas from budgets`() {
    val centerBudget = 60_000L
    val ringBudget = 15_000L

    val budgetApothem = areaToApothem(centerBudget)
    val budgetGap = areaToApothem(ringBudget)
    val bounds = estimateBounds({ x, z -> hexDistance(x, z, budgetApothem + budgetGap) })

    val centerArea = estimateArea(HexCellSdf(0, 0, budgetApothem), bounds).toDouble()
    val ringArea = estimateArea(HexGapSdf(0, 0, budgetApothem, budgetGap), bounds).toDouble()

    val expectedCenterArea = centerBudget.toDouble()
    val expectedRingArea =
        (2f * SQRT3 * ((budgetApothem + budgetGap) * (budgetApothem + budgetGap) - budgetApothem * budgetApothem)).toDouble()

    val centerRelativeError = abs(centerArea - expectedCenterArea) / expectedCenterArea
    val ringRelativeError = abs(ringArea - expectedRingArea) / expectedRingArea

    assertTrue(
        centerRelativeError <= 0.08,
        "center relative error=$centerRelativeError expected=$expectedCenterArea actual=$centerArea",
    )
    assertTrue(
        ringRelativeError <= 0.08,
        "ring relative error=$ringRelativeError expected=$expectedRingArea actual=$ringArea",
    )

    val largerGap = areaToApothem(ringBudget * 2)
    val largerBounds = estimateBounds({ x, z -> hexDistance(x, z, budgetApothem + largerGap) })
    val largerRingArea = estimateArea(HexGapSdf(0, 0, budgetApothem, largerGap), largerBounds).toDouble()

    assertTrue(
        largerRingArea > ringArea,
        "expected ring area to grow with budget, baseline=$ringArea larger=$largerRingArea",
    )
  }

  private fun colorForCell(q: Int, r: Int, isGap: Boolean): Int {
    val seed = q * 0x1f1f1f1f + r * 0x9e3779b9.toInt()
    val red = 0x40 + (seed and 0xBF)
    val green = 0x40 + ((seed shr 8) and 0xBF)
    val blue = 0x40 + ((seed shr 16) and 0xBF)
    val alpha = if (isGap) 0x60 else 0xFF
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
  }
}
