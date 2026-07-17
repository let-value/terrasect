package terrasect.sdf

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Decorations spice up a region's partition of its children. Domain decorations warp the space
// the strategy decides in — both sides of every edge see the same warped query point, so the
// partition stays a partition and locate stays consistent with traverse. Layer decorations
// post-process each child layer symmetrically. Instances are created per region instance with a
// seed derived from the id prefix and the instance's canonical center.
sealed class Decoration {
  abstract fun instantiate(seed: Long, centerX: Int, centerZ: Int): DecorationInstance

  data class Warp(val amplitude: Float, val scale: Float, val octaves: Int = 2) : Decoration() {
    override fun instantiate(seed: Long, centerX: Int, centerZ: Int) =
      WarpInstance(seed, amplitude, scale.coerceAtLeast(1f), octaves)
  }

  data class Dither(val width: Float, val scale: Float = 8f) : Decoration() {
    override fun instantiate(seed: Long, centerX: Int, centerZ: Int) =
      WarpInstance(seed, width, scale.coerceAtLeast(1f), 1)
  }

  data class Swirl(val strength: Float, val radius: Float) : Decoration() {
    override fun instantiate(seed: Long, centerX: Int, centerZ: Int) =
      SwirlInstance(centerX, centerZ, strength, radius.coerceAtLeast(1f))
  }

  data class Ripple(val amplitude: Float, val wavelength: Float) : Decoration() {
    override fun instantiate(seed: Long, centerX: Int, centerZ: Int): DecorationInstance {
      val tau = (2.0 * PI).toFloat()
      val rng = java.util.Random(seed)
      return RippleInstance(
        amplitude,
        wavelength.coerceAtLeast(1f),
        rng.nextFloat() * tau,
        rng.nextFloat() * tau,
      )
    }
  }

  data class Shear(val x: Float = 0f, val z: Float = 0f) : Decoration() {
    override fun instantiate(seed: Long, centerX: Int, centerZ: Int) =
      ShearInstance(centerX, centerZ, x, z)
  }

  data class Terrace(val step: Float) : Decoration() {
    override fun instantiate(seed: Long, centerX: Int, centerZ: Int) =
      TerraceInstance(centerX, centerZ, step.coerceAtLeast(1f))
  }

  data class Gap(val width: Float) : Decoration() {
    override fun instantiate(seed: Long, centerX: Int, centerZ: Int) = GapInstance(width)
  }

  data class Onion(val thickness: Float) : Decoration() {
    override fun instantiate(seed: Long, centerX: Int, centerZ: Int) = OnionInstance(thickness)
  }

  data class Stripes(val width: Float, val gap: Float, val angle: Float = 0f) : Decoration() {
    override fun instantiate(seed: Long, centerX: Int, centerZ: Int): DecorationInstance {
      val radians = Math.toRadians(angle.toDouble()).toFloat()
      val phase = java.util.Random(seed).nextFloat() * (width + gap)
      return StripesInstance(width, gap, cos(radians), sin(radians), phase)
    }
  }

  data class Rings(val width: Float, val gap: Float) : Decoration() {
    override fun instantiate(seed: Long, centerX: Int, centerZ: Int) =
      RingsInstance(centerX, centerZ, width, gap)
  }

  companion object {
    fun warp(amplitude: Float, scale: Float, octaves: Int = 2) = Warp(amplitude, scale, octaves)

    fun dither(width: Float, scale: Float = 8f) = Dither(width, scale)

    fun swirl(strength: Float, radius: Float) = Swirl(strength, radius)

    fun ripple(amplitude: Float, wavelength: Float) = Ripple(amplitude, wavelength)

    fun shear(x: Float = 0f, z: Float = 0f) = Shear(x, z)

    fun terrace(step: Float) = Terrace(step)

    fun gap(width: Float) = Gap(width)

    fun onion(thickness: Float) = Onion(thickness)

    fun stripes(width: Float, gap: Float, angle: Float = 0f) = Stripes(width, gap, angle)

    fun rings(width: Float, gap: Float) = Rings(width, gap)
  }
}

sealed interface DecorationInstance

// Maps a query point before the strategy decides ownership. warpX and warpZ are evaluated from
// the same input point (parallel, not sequential).
interface DomainDecoration : DecorationInstance {
  fun warpX(x: Float, z: Float): Float

  fun warpZ(x: Float, z: Float): Float
}

// Post-processes a child layer's distance; receives the domain-warped point for spatial masks.
interface LayerDecoration : DecorationInstance {
  fun apply(distance: Float, x: Int, z: Int): Float
}

class WarpInstance(val seed: Long, val amplitude: Float, val scale: Float, val octaves: Int) :
  DomainDecoration {
  override fun warpX(x: Float, z: Float): Float =
    x + (fbm(seed, x / scale, z / scale, octaves) - 0.5f) * 2f * amplitude

  override fun warpZ(x: Float, z: Float): Float =
    z + (fbm(seed + 0x5DEECE66D, x / scale, z / scale, octaves) - 0.5f) * 2f * amplitude
}

class SwirlInstance(val centerX: Int, val centerZ: Int, val strength: Float, val radius: Float) :
  DomainDecoration {
  private fun angle(dx: Float, dz: Float): Float {
    val distance = hypot(dx, dz)
    return strength * max(0f, 1f - distance / radius)
  }

  override fun warpX(x: Float, z: Float): Float {
    val dx = x - centerX
    val dz = z - centerZ
    val rotation = angle(dx, dz)
    return centerX + dx * cos(rotation) - dz * sin(rotation)
  }

  override fun warpZ(x: Float, z: Float): Float {
    val dx = x - centerX
    val dz = z - centerZ
    val rotation = angle(dx, dz)
    return centerZ + dx * sin(rotation) + dz * cos(rotation)
  }
}

class RippleInstance(
  val amplitude: Float,
  val wavelength: Float,
  val phaseX: Float,
  val phaseZ: Float,
) : DomainDecoration {
  private val tau = (2.0 * PI).toFloat()

  override fun warpX(x: Float, z: Float): Float = x + sin(z / wavelength * tau + phaseX) * amplitude

  override fun warpZ(x: Float, z: Float): Float = z + sin(x / wavelength * tau + phaseZ) * amplitude
}

class ShearInstance(val centerX: Int, val centerZ: Int, val shearX: Float, val shearZ: Float) :
  DomainDecoration {
  override fun warpX(x: Float, z: Float): Float = x + (z - centerZ) * shearX

  override fun warpZ(x: Float, z: Float): Float = z + (x - centerX) * shearZ
}

class TerraceInstance(val centerX: Int, val centerZ: Int, val step: Float) : DomainDecoration {
  override fun warpX(x: Float, z: Float): Float =
    centerX + floor((x - centerX) / step) * step + step / 2f

  override fun warpZ(x: Float, z: Float): Float =
    centerZ + floor((z - centerZ) / step) * step + step / 2f
}

class GapInstance(val width: Float) : LayerDecoration {
  override fun apply(distance: Float, x: Int, z: Int): Float = distance + width
}

class OnionInstance(val thickness: Float) : LayerDecoration {
  override fun apply(distance: Float, x: Int, z: Int): Float = abs(distance) - thickness
}

private fun bandDistance(v: Float, width: Float, period: Float): Float {
  val wrapped = ((v % period) + period) % period
  return if (wrapped < width) {
    -min(wrapped, width - wrapped)
  } else {
    min(wrapped - width, period - wrapped)
  }
}

class StripesInstance(
  val width: Float,
  val gap: Float,
  val dirX: Float,
  val dirZ: Float,
  val phase: Float,
) : LayerDecoration {
  override fun apply(distance: Float, x: Int, z: Int): Float {
    val v = x * dirX + z * dirZ + phase
    return max(distance, bandDistance(v, width, width + gap))
  }
}

class RingsInstance(val centerX: Int, val centerZ: Int, val width: Float, val gap: Float) :
  LayerDecoration {
  override fun apply(distance: Float, x: Int, z: Int): Float {
    val radius = hypot((x - centerX).toFloat(), (z - centerZ).toFloat())
    return max(distance, bandDistance(radius, width, width + gap))
  }
}

// Wraps a child layer with the domain chain active at append time plus the owning region's layer
// decorations, so external queries at raw world coordinates warp exactly like the strategy's
// ownership decision did.
class DecoratedSdf(
  val layer: Sdf2,
  val chain: List<DomainDecoration>,
  val ops: List<LayerDecoration>,
) : Sdf2 {
  override fun invoke(x: Int, z: Int): Float {
    var fx = x.toFloat()
    var fz = z.toFloat()
    for (domain in chain) {
      val nx = domain.warpX(fx, fz)
      val nz = domain.warpZ(fx, fz)
      fx = nx
      fz = nz
    }
    val wx = fx.roundToInt()
    val wz = fz.roundToInt()
    var distance = layer(wx, wz)
    for (op in ops) {
      distance = op.apply(distance, wx, wz)
    }
    return distance
  }
}

fun warpPoint(chain: List<DomainDecoration>, x: Int, z: Int): Long {
  var fx = x.toFloat()
  var fz = z.toFloat()
  for (domain in chain) {
    val nx = domain.warpX(fx, fz)
    val nz = domain.warpZ(fx, fz)
    fx = nx
    fz = nz
  }
  return terrasect.utils.packPair(fx.roundToInt(), fz.roundToInt())
}
