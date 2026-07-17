package terrasect.strategies

import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import terrasect.cache.RegionsCache
import terrasect.definition.Region
import terrasect.definition.RegionBuilder
import terrasect.definition.Strategy
import terrasect.definition.StrategySettings
import terrasect.generation.LocateStep
import terrasect.generation.TraversalStep
import terrasect.sdf.*
import terrasect.utils.first
import terrasect.utils.second

class VoronoiStrategy(
  override val id: Byte,
  val children: Array<Region>,
  val budgets: LongArray,
  val tiling: Boolean = false,
  val metric: SiteMetric = SiteMetric.EUCLIDEAN,
) : Strategy {
  override val targets = children.toList()
  override val tiled: Boolean
    get() = tiling

  val radii = FloatArray(budgets.size) { sqrt(budgets[it].coerceAtLeast(0) / PI).toFloat() }
  val spacing = hexSpacing(areaToApothem(budgets.average().toLong().coerceAtLeast(1)))

  val cellSdfRef: ThreadLocal<VoronoiCellSdf> = ThreadLocal.withInitial { VoronoiCellSdf() }

  class TiledCell(val q: Int, val r: Int, val childIndex: Int, val site: Site)

  class Neighborhood(val cells: List<TiledCell>, val centerIndex: Int) {
    val sites: List<Site> = cells.map { it.site }
  }

  fun scatter(seed: Long): HexScatter = HexScatter(seed * 31 + id, spacing, JITTER)

  fun tiledCell(scatter: HexScatter, q: Int, r: Int): TiledCell {
    val rng = scatter.rng(q, r)
    val childIndex = if (children.size > 1) rng.nextInt(children.size) else 0
    return TiledCell(q, r, childIndex, scatter.site(q, r, rng, radii[childIndex]))
  }

  fun neighborhood(scatter: HexScatter, q: Int, r: Int): Neighborhood {
    val cells = ArrayList<TiledCell>(NEIGHBORHOOD_SIZE)
    var centerIndex = 0
    scatter.forNeighborhood(q, r, RINGS) { cq, cr ->
      if (cq == q && cr == r) {
        centerIndex = cells.size
      }
      cells.add(tiledCell(scatter, cq, cr))
    }
    return Neighborhood(cells, centerIndex)
  }

  fun ownerCell(scatter: HexScatter, x: Int, z: Int): TiledCell {
    val coord = scatter.coordOf(x, z)
    val neighborhood = neighborhood(scatter, coord.first(), coord.second())
    val index = getCellIndex(x, z, neighborhood.sites, metric)
    return neighborhood.cells[index]
  }

  private fun getCachedSites(
    seed: Int,
    id: ByteBuffer,
    parentSdf: Sdf2,
    cache: RegionsCache?,
    originX: Int = 0,
    originZ: Int = 0,
  ): List<Site> {
    if (cache == null) {
      return getSites(seed, parentSdf, budgets, originX, originZ)
    }

    val key = cache.getKey(id)
    return cache.voronoi.getOrCompute(key) { getSites(seed, parentSdf, budgets, originX, originZ) }
  }

  override fun traverse(step: TraversalStep): TraversalStep {
    if (tiling) {
      return traverseTiled(step)
    }

    val seed = getCellSeed(step.traverser.seed, step.id)
    val sites = getCachedSites(seed, step.id, step.sdf, step.cache, step.centerX, step.centerZ)
    val index = getCellIndex(step.qx, step.qz, sites, metric)

    writeId(step.id, seed, index)

    val sdf = cellSdfRef.get()
    sdf.sites = sites
    sdf.index = index
    sdf.metric = metric
    step.append(sdf)

    val dist = step.sdf(step.x, step.z)

    step.distance = max(step.distance, dist)
    step.centerX = sites[index].x
    step.centerZ = sites[index].z
    step.region = children[index]

    return step
  }

  private fun traverseTiled(step: TraversalStep): TraversalStep {
    val scatter = scatter(step.traverser.seed)
    val owner = ownerCell(scatter, step.qx, step.qz)
    val neighborhood = neighborhood(scatter, owner.q, owner.r)

    writeTileId(step.id, owner.q, owner.r)

    val sdf = cellSdfRef.get()
    sdf.sites = neighborhood.sites
    sdf.index = neighborhood.centerIndex
    sdf.metric = metric
    step.append(sdf)

    val dist = step.sdf(step.x, step.z)
    step.distance = max(step.distance, dist)
    step.centerX = owner.site.x
    step.centerZ = owner.site.z
    step.region = children[owner.childIndex]

    return step
  }

  override fun locate(step: LocateStep): LocateStep? {
    if (tiling) {
      return locateTiled(step)
    }

    val originalPosition = step.id.position()
    val (seed, index) = readId(step.id) ?: return null
    if (index == Strategy.SELF_INDEX) {
      return step
    }
    if (index !in children.indices) {
      return null
    }

    val newPosition = step.id.position()
    step.id.position(originalPosition)
    val sites = getCachedSites(seed, step.id, step.sdf, step.cache, step.centerX, step.centerZ)
    step.id.position(newPosition)

    if (index !in sites.indices) {
      return null
    }

    val sdf = VoronoiCellSdf()
    sdf.sites = sites
    sdf.index = index
    sdf.metric = metric
    step.append(sdf)

    val site = sites[index]
    step.centerX = site.x
    step.centerZ = site.z
    step.region = children[index]

    return step
  }

  private fun locateTiled(step: LocateStep): LocateStep? {
    val tile = readTileId(step.id) ?: return null
    val scatter = scatter(step.locator.seed)
    val owner = tiledCell(scatter, tile.first, tile.second)
    val neighborhood = neighborhood(scatter, owner.q, owner.r)

    val sdf = VoronoiCellSdf()
    sdf.sites = neighborhood.sites
    sdf.index = neighborhood.centerIndex
    sdf.metric = metric
    step.append(sdf)

    step.centerX = owner.site.x
    step.centerZ = owner.site.z
    step.region = children[owner.childIndex]

    return step
  }

  override fun resolve(step: LocateStep, child: Region): LocateStep? {
    val index = children.indexOfFirst { it === child }
    if (index < 0) {
      return null
    }

    if (tiling) {
      val scatter = scatter(step.locator.seed)
      val origin = ownerCell(scatter, step.centerX, step.centerZ)
      val cell = findTiledChild(scatter, origin, index) ?: return null

      val position = step.id.position()
      writeTileId(step.id, cell.q, cell.r)
      step.id.position(position)
      step.ambiguous = true

      return locate(step)
    }

    val seed = getCellSeed(step.locator.seed, step.id)
    val position = step.id.position()
    writeId(step.id, seed, index)
    step.id.position(position)

    return locate(step)
  }

  private fun findTiledChild(scatter: HexScatter, origin: TiledCell, index: Int): TiledCell? {
    if (origin.childIndex == index) {
      return origin
    }
    for (ring in 1..RESOLVE_SEARCH_RINGS) {
      var found: TiledCell? = null
      scatter.forNeighborhood(origin.q, origin.r, ring) { q, r ->
        if (found == null && hexRingDistance(origin.q, origin.r, q, r) == ring) {
          val cell = tiledCell(scatter, q, r)
          if (cell.childIndex == index) {
            found = cell
          }
        }
      }
      if (found != null) {
        return found
      }
    }
    return null
  }

  override fun writeSelf(step: LocateStep, buffer: ByteBuffer) {
    writeId(buffer, getCellSeed(step.locator.seed, buffer), Strategy.SELF_INDEX)
  }

  fun writeId(buffer: ByteBuffer, seed: Int, index: Int) {
    buffer.put(id)
    buffer.putInt(seed)
    buffer.putInt(index)
  }

  fun writeTileId(buffer: ByteBuffer, q: Int, r: Int) {
    buffer.put(id)
    buffer.putInt(q)
    buffer.putInt(r)
  }

  fun readId(buffer: ByteBuffer): Pair<Int, Int>? {
    try {
      val strategyId = buffer.get()
      if (strategyId != id) {
        return null
      }

      val seed = buffer.getInt()
      val index = buffer.getInt()

      return seed to index
    } catch (_: Exception) {
      return null
    }
  }

  fun readTileId(buffer: ByteBuffer): Pair<Int, Int>? {
    try {
      val strategyId = buffer.get()
      if (strategyId != id) {
        return null
      }

      val q = buffer.getInt()
      val r = buffer.getInt()

      return q to r
    } catch (_: Exception) {
      return null
    }
  }

  companion object {
    const val RINGS = 2
    const val NEIGHBORHOOD_SIZE = 19
    const val JITTER = 0.5f
    const val RESOLVE_SEARCH_RINGS = 8

    fun hexRingDistance(q0: Int, r0: Int, q1: Int, r1: Int): Int {
      val dq = q1 - q0
      val dr = r1 - r0
      return (abs(dq) + abs(dr) + abs(dq + dr)) / 2
    }

    fun getCellSeed(seed: Long, id: ByteBuffer): Int {
      val idPos = id.position()
      var cellSeed = seed
      for (i in 0 until idPos) {
        cellSeed = cellSeed * 31 + id.get(i).toLong()
      }
      return cellSeed.toInt()
    }

    fun getSites(
      cellSeed: Int,
      parentSdf: Sdf2,
      budgets: LongArray,
      originX: Int = 0,
      originZ: Int = 0,
    ): List<Site> {
      val bounds = estimateBounds(parentSdf, originX, originZ)
      return getSites(cellSeed.toLong(), parentSdf, bounds, budgets)
    }

    fun getCellIndex(
      x: Int,
      z: Int,
      sites: List<Site>,
      metric: SiteMetric = SiteMetric.EUCLIDEAN,
    ): Int {
      var closestIndex = 0
      var closestPower = Float.POSITIVE_INFINITY

      for (i in sites.indices) {
        val site = sites[i]
        val dist = metric.distance((x - site.x).toFloat(), (z - site.z).toFloat())
        val power = dist - site.radius
        if (power < closestPower) {
          closestPower = power
          closestIndex = i
        }
      }

      return closestIndex
    }

    fun builder() = Builder()
  }

  class Builder : StrategySettings {
    var tiling = false
    var metric = SiteMetric.EUCLIDEAN

    fun tiling(value: Boolean = true) = apply { this.tiling = value }

    fun metric(value: SiteMetric) = apply { this.metric = value }

    override fun build(builder: RegionBuilder, children: Set<Region>): VoronoiStrategy {
      val sortedChildren = children.sortedByDescending { it.budget }
      val budgets = sortedChildren.map { it.budget }.sortedByDescending { it }.toLongArray()
      return VoronoiStrategy(builder.id, sortedChildren.toTypedArray(), budgets, tiling, metric)
    }
  }
}
