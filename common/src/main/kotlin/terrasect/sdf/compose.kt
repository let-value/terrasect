package terrasect.sdf

import kotlin.math.max

private object EmptySdf : Sdf2 {
  override fun invoke(x: Double, z: Double): Double = Double.NEGATIVE_INFINITY
}

class SdfCompose : Sdf2 {
  val layers = Array<Sdf2>(10) { EmptySdf }
  var count = 0

  fun append(sdf: Sdf2) {
    layers[count++] = sdf
  }

  fun reset() {
    for (i in 0 until count) {
      layers[i] = EmptySdf
    }
  }

  override fun invoke(x: Double, z: Double): Double {
    var result = Double.NEGATIVE_INFINITY
    for (i in 0 until count) {
      val value = layers[i](x, z)
      result = max(result, value)
    }
    return result
  }
}
