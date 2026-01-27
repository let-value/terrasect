package terrasect.sdf

const val CELL_SIZE = 16.0
const val MAX_RADIUS = 2048.0

typealias Sdf2 = (Double, Double) -> Double

typealias SdfGradient2 = (Double, Double) -> Pair<Double, Double>
