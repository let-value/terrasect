package terrasect.sdf

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// An island is a circle whose rim is perturbed by low-frequency angular harmonics. The rim stays
// within radius * (1 + sum of amplitudes), so placement can bound an island's extent.
class IslandSdf : Sdf2 {
  var centerX: Int = 0
  var centerZ: Int = 0
  var radius: Float = 0f
  var amplitudes: FloatArray = floatArrayOf()
  var phases: FloatArray = floatArrayOf()

  fun rim(angle: Float): Float {
    var factor = 1f
    for (k in amplitudes.indices) {
      factor += amplitudes[k] * sin((k + 1) * angle + phases[k])
    }
    return radius * factor
  }

  override fun invoke(x: Int, z: Int): Float {
    val dx = (x - centerX).toFloat()
    val dz = (z - centerZ).toFloat()
    val distance = hypot(dx, dz)
    return distance - rim(atan2(dz, dx))
  }
}

// The sea is everything that is not an island; composed over the parent chain it becomes the
// parent region minus its archipelago — one seamless connected instance.
class ArchipelagoSeaSdf : Sdf2 {
  var islands: List<IslandSdf> = emptyList()

  override fun invoke(x: Int, z: Int): Float {
    var closest = Float.POSITIVE_INFINITY
    for (island in islands) {
      closest = min(closest, island(x, z))
    }
    return -closest
  }
}
