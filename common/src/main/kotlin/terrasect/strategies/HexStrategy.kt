package terrasect.strategies

import terrasect.definition.HexSettings
import terrasect.definition.Strategy
import terrasect.definition.StrategyId
import terrasect.generation.Context
import terrasect.generation.TraversalStep
import kotlin.math.*

object HexStrategy {
  val discriminator = StrategyId.HEX.value

  data class GetCellResult(
      var q: Long = 0,
      var r: Long = 0,
      var distance: Double = 0.0,
      var isGap: Boolean = false,
  )

  data class Site(
      var x: Double = 0.0,
      var z: Double = 0.0,
      var budget: Double = 0.0,
  )

  val cellRef: ThreadLocal<GetCellResult> = ThreadLocal.withInitial { GetCellResult() }

  private val SQRT3 = sqrt(3.0)
  private val SIN60 = sin(Math.toRadians(60.0))
  private val COS60 = cos(Math.toRadians(60.0))
  private val TAN30 = tan(Math.toRadians(30.0))
  private const val ONE_THIRD = 1.0 / 3.0
  private const val TWO_THIRDS = 2.0 / 3.0

  fun getCell(x: Long, z: Long, radius: Int, gap: Int = 0): GetCellResult {
    val spacing = (radius + gap.coerceAtLeast(0))

    val qFrac = (TAN30 * x - ONE_THIRD * z) / spacing
    val rFrac = (TWO_THIRDS * z) / spacing
    val sFrac = -qFrac - rFrac

    var q = qFrac.roundToLong()
    var r = rFrac.roundToLong()
    val s = sFrac.roundToLong()

    val qDiff = abs(q - qFrac)
    val rDiff = abs(r - rFrac)
    val sDiff = abs(s - sFrac)

    if (qDiff > rDiff && qDiff > sDiff) {
      q = -r - s
    } else if (rDiff > sDiff) {
      r = -q - s
    }

    val centerX = (SQRT3 * q + SIN60 * r) * spacing
    val centerZ = (1.5 * r) * spacing

    val localX = x - centerX
    val localZ = z - centerZ

    var distance = hexDistance(localX, localZ, radius)
    val isGap = distance > 0f && gap > 0
    if (isGap) {
      distance = -distance
    }

    val cell = cellRef.get()
    cell.q = q
    cell.r = r
    cell.distance = distance
    cell.isGap = isGap

    return cell
  }

  fun hexDistance(px: Double, pz: Double, radius: Int): Double {
    val x = abs(px)
    val z = abs(pz)

    val d = x * 0.5 + z * SIN60

    return max(d, x) - radius
  }

  private fun hexGradient(px: Double, pz: Double): Pair<Double, Double> {
    val sx = if (px < 0.0) -1.0 else 1.0
    val sz = if (pz < 0.0) -1.0 else 1.0
    val x = abs(px)
    val z = abs(pz)
    val d = x * 0.5 + z * SIN60

    return if (x >= d) {
      Pair(sx, 0.0)
    } else {
      Pair(sx * 0.5, sz * SIN60)
    }
  }

  fun getSites(
      seed: Long,
      q: Long,
      r: Long,
      radius: Int,
      budgets: IntArray,
      gap: Int = 0,
      relaxIterations: Int = 24,
      attemptsPerSite: Int = 28,
  ): Array<Site> {
    if (budgets.isEmpty()) return emptyArray()
    val doubleBudgets = DoubleArray(budgets.size) { budgets[it].toDouble() }
    return getSites(seed, q, r, radius, doubleBudgets, gap, relaxIterations, attemptsPerSite)
  }

  fun getSites(
      seed: Long,
      q: Long,
      r: Long,
      radius: Int,
      budgets: DoubleArray,
      gap: Int = 0,
      relaxIterations: Int = 24,
      attemptsPerSite: Int = 28,
  ): Array<Site> {
    val spacing = (radius + gap.coerceAtLeast(0))
    val centerX = (SQRT3 * q + SIN60 * r) * spacing
    val centerZ = (1.5 * r) * spacing
    val maxZ = radius / SIN60
    val bounds = SdfBounds(-radius.toDouble(), radius.toDouble(), -maxZ, maxZ)
    val sdf: Sdf2 = { x, z -> hexDistance(x, z, radius) }
    val baseSites =
        SdfSites.getSites(
            seed = seed,
            bounds = bounds,
            budgets = budgets,
            sdf = sdf,
            gradient = ::hexGradient,
            seedSaltA = q,
            seedSaltB = r,
            relaxIterations = relaxIterations,
            attemptsPerSite = attemptsPerSite,
        )

    val sites = Array(baseSites.size) { Site() }
    for (i in baseSites.indices) {
      val site = baseSites[i]
      sites[i].x = centerX + site.x
      sites[i].z = centerZ + site.z
      sites[i].budget = site.budget
    }

    return sites
  }

  fun traverse(
      context: Context,
      step: TraversalStep,
      settings: HexSettings,
  ): TraversalStep {
    val cell = getCell(step.x, step.z, context.region.budget, settings.ringRegion?.budget ?: 0)

    step.id.put(discriminator)
    step.id.putLong(cell.q)
    step.id.putLong(cell.r)
    step.id.putChar(Strategy.SEPARATOR)

    step.region =
        if (cell.isGap && settings.ringRegion != null) settings.ringRegion else settings.children

    return step
  }
}
