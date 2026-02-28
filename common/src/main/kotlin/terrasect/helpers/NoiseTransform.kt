package terrasect.helpers

import kotlin.math.abs

class NoiseTransform(val operations: List<Operation>) {
  companion object {
    fun builder() = Builder()
  }

  fun interface Operation {
    fun apply(value: Double): Double
  }

  class Clamp(val min: Double, val max: Double) : Operation {
    override fun apply(value: Double): Double = value.coerceIn(min, max)
  }

  class Add(val value: Double) : Operation {
    override fun apply(value: Double): Double = value + this.value
  }

  class Multiply(val factor: Double) : Operation {
    override fun apply(value: Double): Double = value * factor
  }

  class Remap(
      val inputMin: Double,
      val inputMax: Double,
      val outputMin: Double,
      val outputMax: Double,
  ) : Operation {
    override fun apply(value: Double): Double {
      val range = inputMax - inputMin
      val t = if (range == 0.0) 0.5 else ((value - inputMin) / range).coerceIn(0.0, 1.0)
      return outputMin + t * (outputMax - outputMin)
    }
  }

  class Map(val type: MapType) : Operation {
    override fun apply(value: Double): Double =
        when (type) {
          MapType.ABS -> abs(value)
          MapType.SQUARE -> value * value
          MapType.CUBE -> value * value * value
          MapType.HALF_NEGATIVE -> if (value > 0.0) value else value * 0.5
          MapType.QUARTER_NEGATIVE -> if (value > 0.0) value else value * 0.25
          MapType.INVERT -> 1.0 / value
          MapType.SQUEEZE -> {
            val clamped = value.coerceIn(-1.0, 1.0)
            clamped / 2.0 - clamped * clamped * clamped / 24.0
          }
        }
  }

  enum class MapType {
    ABS,
    SQUARE,
    CUBE,
    HALF_NEGATIVE,
    QUARTER_NEGATIVE,
    INVERT,
    SQUEEZE,
  }

  fun isEmpty(): Boolean = operations.isEmpty()

  fun apply(value: Double): Double {
    var out = value
    for (op in operations) out = op.apply(out)
    return out
  }

  class Builder {
    private val operations = mutableListOf<Operation>()

    fun clamp(min: Double, max: Double) = apply { operations.add(Clamp(min, max)) }

    fun add(value: Double) = apply { operations.add(Add(value)) }

    fun multiply(factor: Double) = apply { operations.add(Multiply(factor)) }

    fun remap(inputMin: Double, inputMax: Double, outputMin: Double, outputMax: Double) = apply {
      operations.add(Remap(inputMin, inputMax, outputMin, outputMax))
    }

    fun map(type: MapType) = apply { operations.add(Map(type)) }

    fun abs() = apply { operations.add(Map(MapType.ABS)) }

    fun square() = apply { operations.add(Map(MapType.SQUARE)) }

    fun cube() = apply { operations.add(Map(MapType.CUBE)) }

    fun halfNegative() = apply { operations.add(Map(MapType.HALF_NEGATIVE)) }

    fun quarterNegative() = apply { operations.add(Map(MapType.QUARTER_NEGATIVE)) }

    fun invert() = apply { operations.add(Map(MapType.INVERT)) }

    fun squeeze() = apply { operations.add(Map(MapType.SQUEEZE)) }

    fun build(): NoiseTransform {
      return NoiseTransform(operations)
    }
  }
}
