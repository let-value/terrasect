package terrasect.utils

typealias Packed = Long

object Packer {
  fun pack(first: Int, second: Int): Packed {
    return ((second.toLong() shl 32) or (first.toLong() and 0xFFFFFFFFL))
  }

  fun unpackFirst(packed: Packed): Int {
    return (packed.toInt())
  }

  fun unpackSecond(packed: Packed): Int {
    return ((packed ushr 32).toInt())
  }
}
