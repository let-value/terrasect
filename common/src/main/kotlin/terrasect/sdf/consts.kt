package terrasect.sdf

const val CELL_SIZE = 16.0
const val MAX_RADIUS = 2048.0

typealias Sdf2 = (Double, Double) -> Double

fun smoothMin(a: Double, b: Double, k: Double): Double {
  val h = (0.5 + 0.5 * (b - a) / k).coerceIn(0.0, 1.0)
  return a * h + b * (1.0 - h) - k * h * (1.0 - h)
}

fun smoothMax(a: Double, b: Double, k: Double): Double {
  return -smoothMin(-a, -b, k)
}

fun translate(sdf: Sdf2, dx: Double, dz: Double): Sdf2 = { x, z -> sdf(x - dx, z - dz) }
