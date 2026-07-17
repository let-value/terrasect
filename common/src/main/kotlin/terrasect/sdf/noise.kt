package terrasect.sdf

import kotlin.math.floor

private fun mix(seed: Long, xi: Int, zi: Int): Long {
  var h = seed
  h = h * -0x61C8864680B583EBL + xi
  h = h * -0x3D4D51C2D82B14B1L + zi
  h = (h xor (h ushr 30)) * -0x40A7B892E31B1A47L
  h = (h xor (h ushr 27)) * -0x6B2FB644ECCEEE15L
  return h xor (h ushr 31)
}

private fun corner(seed: Long, xi: Int, zi: Int): Float {
  return ((mix(seed, xi, zi) ushr 40) and 0xFFFFFF).toFloat() / 0xFFFFFF
}

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

fun valueNoise(seed: Long, x: Float, z: Float): Float {
  val xf = floor(x)
  val zf = floor(z)
  val xi = xf.toInt()
  val zi = zf.toInt()
  val tx = smooth(x - xf)
  val tz = smooth(z - zf)

  val c00 = corner(seed, xi, zi)
  val c10 = corner(seed, xi + 1, zi)
  val c01 = corner(seed, xi, zi + 1)
  val c11 = corner(seed, xi + 1, zi + 1)

  val top = c00 + (c10 - c00) * tx
  val bottom = c01 + (c11 - c01) * tx
  return top + (bottom - top) * tz
}

fun fbm(seed: Long, x: Float, z: Float, octaves: Int): Float {
  var amplitude = 1f
  var frequency = 1f
  var total = 0f
  var range = 0f
  for (octave in 0 until octaves) {
    total += valueNoise(seed + octave, x * frequency, z * frequency) * amplitude
    range += amplitude
    amplitude *= 0.5f
    frequency *= 2f
  }
  return total / range
}
