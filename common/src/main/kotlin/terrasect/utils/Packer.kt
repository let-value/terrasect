package terrasect.utils

object Packer {
  fun packPair(first: Float, second: Float): Long {
    return (((second.toRawBits()).toLong() shl 32) or
        ((first.toRawBits()).toLong() and 0xFFFFFFFFL))
  }

  fun unpackPairFirst(packed: Long): Float {
    return (packed.toInt().toFloat())
  }

  fun unpackPairSecond(packed: Long): Float {
    return ((packed ushr 32).toInt().toFloat())
  }

  fun packIntFloat(intVal: Int, floatVal: Float): Long {
    return (intVal.toLong() shl 32) or ((floatVal.toRawBits()).toLong() and 0xFFFFFFFFL)
  }

  fun unpackInt(packed: Long): Int {
    return (packed ushr 32).toInt()
  }

  fun unpackFloat(packed: Long): Float {
    return (packed.toInt().toFloat())
  }
}
