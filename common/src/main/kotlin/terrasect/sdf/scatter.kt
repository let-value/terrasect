package terrasect.sdf

import java.util.Random

// Infinite seeded point process shared by tiling strategies. Sites are jittered away from the
// centers of a hidden hex arrangement so the distribution is isotropic and deterministic per
// world position, independent of any parent instance; the arrangement itself never produces
// visible geometry — every boundary a strategy renders comes from voronoi bisectors between the
// scattered sites. Reads like a higher-level voronoi grid whose cells repeat statistically.
class HexScatter(val seed: Long, val spacing: Float, val jitter: Float) {

  fun coordOf(x: Int, z: Int): Long = hexAxial(x, z, spacing)

  fun siteSeed(q: Int, r: Int): Long {
    var h = seed
    h = h * -0x61C8864680B583EBL + q
    h = h * -0x3D4D51C2D82B14B1L + r
    h = (h xor (h ushr 30)) * -0x40A7B892E31B1A47L
    h = (h xor (h ushr 27)) * -0x6B2FB644ECCEEE15L
    return h xor (h ushr 31)
  }

  fun rng(q: Int, r: Int): Random = Random(siteSeed(q, r))

  fun site(q: Int, r: Int, rng: Random, radius: Float): Site {
    val centerX = hexCenterX(q, r, spacing)
    val centerZ = hexCenterZ(r, spacing)
    val angle = rng.nextFloat() * (2.0 * Math.PI)
    val distance = kotlin.math.sqrt(rng.nextFloat()) * spacing * jitter
    val x = centerX + (kotlin.math.cos(angle) * distance).toInt()
    val z = centerZ + (kotlin.math.sin(angle) * distance).toInt()
    return Site(x, z, radius)
  }

  inline fun forNeighborhood(q: Int, r: Int, rings: Int, consumer: (Int, Int) -> Unit) {
    for (dq in -rings..rings) {
      for (dr in maxOf(-rings, -dq - rings)..minOf(rings, -dq + rings)) {
        consumer(q + dq, r + dr)
      }
    }
  }
}
