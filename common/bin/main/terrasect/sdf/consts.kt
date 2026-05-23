package terrasect.sdf

const val CELL_SIZE = 16
const val MAX_RADIUS = 2048

typealias Sdf2 = (Int, Int) -> Float

fun smoothMin(a: Float, b: Float, k: Float): Float {
  val h = (0.5f + 0.5f * (b - a) / k).coerceIn(0.0f, 1.0f)
  return a * h + b * (1.0f - h) - k * h * (1.0f - h)
}

fun smoothMax(a: Float, b: Float, k: Float): Float {
  return -smoothMin(-a, -b, k)
}

fun translate(sdf: Sdf2, dx: Int, dz: Int): Sdf2 = { x, z -> sdf(x - dx, z - dz) }
