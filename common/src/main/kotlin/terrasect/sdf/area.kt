package terrasect.sdf

import kotlin.math.roundToInt

fun estimateArea(sdf: Sdf2, bounds: SdfBounds, step: Int = 1): Long {
  val safeStep = step.coerceAtLeast(1)
  val minX = bounds.minX
  val maxX = bounds.maxX
  val minZ = bounds.minZ
  val maxZ = bounds.maxZ
  val cellArea = safeStep * safeStep
  var area = 0L
  var z = minZ
  while (z < maxZ) {
    var x = minX
    while (x < maxX) {
      val sampleX = (x + safeStep * 0.5f).roundToInt()
      val sampleZ = (z + safeStep * 0.5f).roundToInt()
      if (sdf(sampleX, sampleZ) <= 0.0) {
        area += cellArea
      }
      x += safeStep
    }
    z += safeStep
  }
  return area
}
