package terrasect.helpers

import java.awt.image.BufferedImage
import net.minecraft.world.level.levelgen.DensityFunction
import terrasect.testing.MutablePointContext

fun sampleXZ(function: DensityFunction, imgSize: Int, worldSize: Int, y: Int): DoubleArray {
  val step = worldSize / imgSize
  val start = -worldSize / 2
  val point = MutablePointContext()
  val values = DoubleArray(imgSize * imgSize)

  for (iz in 0 until imgSize) {
    val z = start + iz * step
    for (ix in 0 until imgSize) {
      val x = start + ix * step
      point.set(x, y, z)
      var v = function.compute(point)
      if (!v.isFinite()) v = 0.0
      values[ix + iz * imgSize] = v
    }
  }
  return values
}

fun renderNormalizedGrayscale(values: DoubleArray, size: Int): BufferedImage {
  var min = Double.POSITIVE_INFINITY
  var max = Double.NEGATIVE_INFINITY
  for (v in values) {
    if (v < min) min = v
    if (v > max) max = v
  }
  if (!min.isFinite() || !max.isFinite() || max <= min) {
    min = 0.0
    max = 1.0
  }
  val range = max - min
  val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
  for (y in 0 until size) {
    for (x in 0 until size) {
      val t = ((values[x + y * size] - min) / range).coerceIn(0.0, 1.0)
      val gray = (t * 255).toInt()
      img.setRGB(x, y, (gray shl 16) or (gray shl 8) or gray)
    }
  }
  return img
}

fun renderSideBySideGrayscale(
  original: DoubleArray,
  transformed: DoubleArray,
  size: Int,
): BufferedImage {
  var min = Double.POSITIVE_INFINITY
  var max = Double.NEGATIVE_INFINITY
  for (v in original) {
    if (v < min) min = v
    if (v > max) max = v
  }
  for (v in transformed) {
    if (v < min) min = v
    if (v > max) max = v
  }
  if (!min.isFinite() || !max.isFinite() || max <= min) {
    min = 0.0
    max = 1.0
  }
  val range = max - min

  val totalWidth = size * 2 + 2
  val img = BufferedImage(totalWidth, size, BufferedImage.TYPE_INT_RGB)

  for (y in 0 until size) {
    for (x in 0 until size) {
      val tOrig = ((original[x + y * size] - min) / range).coerceIn(0.0, 1.0)
      val gOrig = (tOrig * 255).toInt()
      img.setRGB(x, y, (gOrig shl 16) or (gOrig shl 8) or gOrig)

      val tTrans = ((transformed[x + y * size] - min) / range).coerceIn(0.0, 1.0)
      val gTrans = (tTrans * 255).toInt()
      img.setRGB(x + size + 2, y, (gTrans shl 16) or (gTrans shl 8) or gTrans)
    }
    img.setRGB(size, y, 0x444444)
    img.setRGB(size + 1, y, 0x444444)
  }
  return img
}

fun renderTripleGrayscale(
  original: DoubleArray,
  transformed: DoubleArray,
  blended: DoubleArray,
  size: Int,
): BufferedImage {
  var min = Double.POSITIVE_INFINITY
  var max = Double.NEGATIVE_INFINITY
  for (arr in arrayOf(original, transformed, blended)) {
    for (v in arr) {
      if (v < min) min = v
      if (v > max) max = v
    }
  }
  if (!min.isFinite() || !max.isFinite() || max <= min) {
    min = 0.0
    max = 1.0
  }
  val range = max - min

  val totalWidth = size * 3 + 4
  val img = BufferedImage(totalWidth, size, BufferedImage.TYPE_INT_RGB)

  for (y in 0 until size) {
    for (x in 0 until size) {
      fun gray(v: Double): Int {
        val t = ((v - min) / range).coerceIn(0.0, 1.0)
        val g = (t * 255).toInt()
        return (g shl 16) or (g shl 8) or g
      }
      img.setRGB(x, y, gray(original[x + y * size]))
      img.setRGB(x + size + 2, y, gray(transformed[x + y * size]))
      img.setRGB(x + size * 2 + 4, y, gray(blended[x + y * size]))
    }
    img.setRGB(size, y, 0x444444)
    img.setRGB(size + 1, y, 0x444444)
    img.setRGB(size * 2 + 2, y, 0x444444)
    img.setRGB(size * 2 + 3, y, 0x444444)
  }
  return img
}

fun applyTransform(values: DoubleArray, transform: NoiseTransform): DoubleArray {
  return DoubleArray(values.size) { transform.apply(values[it]) }
}

fun applyBlend(
  original: DoubleArray,
  transformed: DoubleArray,
  size: Int,
  blendWidth: Float,
  sdfAt: (x: Int, z: Int) -> Float,
  worldSize: Int,
): DoubleArray {
  val step = worldSize / size
  val start = -worldSize / 2
  val blended = DoubleArray(original.size)
  for (iz in 0 until size) {
    val z = start + iz * step
    for (ix in 0 until size) {
      val x = start + ix * step
      val sdf = sdfAt(x, z)
      val strength = (-sdf / blendWidth).coerceIn(0f, 1f)
      val idx = ix + iz * size
      blended[idx] = original[idx] + (transformed[idx] - original[idx]) * strength
    }
  }
  return blended
}
