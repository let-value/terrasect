package terrasect.sdf

const val CELL_SIZE = 16.0
const val MAX_RADIUS = 2048.0

typealias Sdf2 = (Double, Double) -> Double

fun translate(sdf: Sdf2, tx: Double, tz: Double): Sdf2 = { x, z -> sdf(x - tx, z - tz) }
