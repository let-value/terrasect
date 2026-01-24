package terrasect.utils

typealias Packed = Long

fun Packed.first(): Int = this.toInt()

fun Packed.second(): Int = (this ushr 32).toInt()

fun packPair(first: Int, second: Int): Packed {
  return ((second.toLong() shl 32) or (first.toLong() and 0xFFFFFFFFL))
}
