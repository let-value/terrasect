package terrasect.sdf

import kotlin.math.max

object EmptySdf : Sdf2 {
  override fun invoke(x: Double, z: Double): Double = Double.NEGATIVE_INFINITY
}

class SdfCompose : Sdf2 {
  val layers = Array<Sdf2>(10) { EmptySdf }
  var count = 0

  fun append(sdf: Sdf2) {
    layers[count++] = sdf
  }

  fun reset() {
    for (i in 0..count) {
      layers[i] = EmptySdf
    }
  }

  override fun invoke(x: Double, z: Double): Double {
    var result = Double.NEGATIVE_INFINITY
    for (i in 0..count) {
      val value = layers[i](x, z)
      result = max(result, value)
    }
    return result
  }

  fun bake(): Sdf2 {
    val layers = this.layers.copyOfRange(0, count + 1)
    return object : Sdf2 {
      override fun invoke(x: Double, z: Double): Double {
        var result = Double.NEGATIVE_INFINITY
        for (sdf in layers) {
          val value = sdf(x, z)
          result = max(result, value)
        }
        return result
      }
    }
  }
}
