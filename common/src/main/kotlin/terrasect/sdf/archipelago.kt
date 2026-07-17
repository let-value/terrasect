package terrasect.sdf

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// An island is a circle whose rim is perturbed by low-frequency angular harmonics, clipped to the
// tile's voronoi cell so neighboring islands can never contest a point. The perturbation keeps the
// rim within radius * (1 + sum of amplitudes), so callers can bound the island's extent.
class IslandSdf : Sdf2 {
  var centerX: Int = 0
  var centerZ: Int = 0
  var radius: Float = 0f
  var amplitudes: FloatArray = floatArrayOf()
  var phases: FloatArray = floatArrayOf()
  var cell: Sdf2 = EmptySdf

  fun rim(angle: Float): Float {
    var factor = 1f
    for (k in amplitudes.indices) {
      factor += amplitudes[k] * sin((k + 1) * angle + phases[k])
    }
    return radius * factor
  }

  fun blob(x: Int, z: Int): Float {
    val dx = (x - centerX).toFloat()
    val dz = (z - centerZ).toFloat()
    val distance = hypot(dx, dz)
    return distance - rim(atan2(dz, dx))
  }

  override fun invoke(x: Int, z: Int): Float = max(blob(x, z), cell(x, z))
}

// The sea of a tile is its voronoi cell minus its island — the organic complement that stitches
// seamlessly with neighboring tiles' seas across bisector edges.
class ArchipelagoSeaSdf : Sdf2 {
  var cell: Sdf2 = EmptySdf
  var island: IslandSdf = IslandSdf()

  override fun invoke(x: Int, z: Int): Float = max(cell(x, z), -island.blob(x, z))
}
