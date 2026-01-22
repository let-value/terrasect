package terrasect.helpers

class NoiseTransform(val operations: List<Operation>) {
  companion object {
    fun builder() = Builder()
  }

  interface Operation

  class Clamp(val min: Double, val max: Double) : Operation

  class Add(val value: Double) : Operation

  class Multiply(val factor: Double) : Operation

  class Map(val type: MapType) : Operation

  enum class MapType {
    ABS,
    SQUARE,
    CUBE,
    HALF_NEGATIVE,
    QUARTER_NEGATIVE,
    INVERT,
    SQUEEZE,
  }

  class Builder {
    private val operations = mutableListOf<Operation>()

    fun clamp(min: Double, max: Double) = apply { operations.add(Clamp(min, max)) }

    fun add(value: Double) = apply { operations.add(Add(value)) }

    fun multiply(factor: Double) = apply { operations.add(Multiply(factor)) }

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
