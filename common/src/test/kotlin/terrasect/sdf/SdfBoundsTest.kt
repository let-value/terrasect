package terrasect.sdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SdfBoundsTest {
  @Test
  fun `should expand bounds`() {
    val bounds = SdfBounds(-10.0, 10.0, -5.0, 5.0)
    val expanded = bounds.expand(2.0)
    assertEquals(-12.0, expanded.minX)
    assertEquals(12.0, expanded.maxX)
    assertEquals(-7.0, expanded.minZ)
    assertEquals(7.0, expanded.maxZ)
  }
}
