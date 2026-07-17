package terrasect.strategies

import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import terrasect.definition.Region
import terrasect.definition.RegionBuilder
import terrasect.definition.Strategy
import terrasect.definition.StrategySettings
import terrasect.generation.LocateStep
import terrasect.generation.TraversalStep
import terrasect.sdf.*
import terrasect.utils.first
import terrasect.utils.second

class ArchipelagoStrategy(
  override val id: Byte,
  val islands: Array<Region>,
  val sea: Region,
) : Strategy {
  override val targets = islands.toList() + sea
  override val tiled = true

  val apothem = areaToApothem(islands.map { it.budget }.average().toLong() + sea.budget)
  val spacing = hexSpacing(apothem)
  val radii =
    FloatArray(islands.size) {
      min(sqrt(islands[it].budget.coerceAtLeast(0) / PI).toFloat(), apothem * MAX_RADIUS_FACTOR)
    }

  val islandSdfRef: ThreadLocal<IslandSdf> = ThreadLocal.withInitial { IslandSdf() }
  val seaSdfRef: ThreadLocal<ArchipelagoSeaSdf> = ThreadLocal.withInitial { ArchipelagoSeaSdf() }

  class Isle(
    val q: Int,
    val r: Int,
    val childIndex: Int,
    val site: Site,
    val amplitudes: FloatArray,
    val phases: FloatArray,
  )

  class Neighborhood(val isles: List<Isle>, val centerIndex: Int) {
    val sites: List<Site> = isles.map { it.site }
  }

  fun scatter(seed: Long): HexScatter = HexScatter(seed * 31 + id, spacing, JITTER)

  fun isle(scatter: HexScatter, q: Int, r: Int): Isle {
    val rng = scatter.rng(q, r)
    val childIndex = if (islands.size > 1) rng.nextInt(islands.size) else 0
    // Tiles are plain voronoi cells (radius 0 keeps the power metric unweighted); island size
    // comes from the blob radius so a small island never shrinks its own tile.
    val site = scatter.site(q, r, rng, 0f)
    val amplitudes = FloatArray(HARMONICS)
    val phases = FloatArray(HARMONICS)
    for (k in 0 until HARMONICS) {
      amplitudes[k] = rng.nextFloat() * AMPLITUDES[k]
      phases[k] = rng.nextFloat() * (2.0 * PI).toFloat()
    }
    return Isle(q, r, childIndex, site, amplitudes, phases)
  }

  fun neighborhood(scatter: HexScatter, q: Int, r: Int): Neighborhood {
    val isles = ArrayList<Isle>(VoronoiStrategy.NEIGHBORHOOD_SIZE)
    var centerIndex = 0
    scatter.forNeighborhood(q, r, RINGS) { cq, cr ->
      if (cq == q && cr == r) {
        centerIndex = isles.size
      }
      isles.add(isle(scatter, cq, cr))
    }
    return Neighborhood(isles, centerIndex)
  }

  fun ownerIsle(scatter: HexScatter, x: Int, z: Int): Isle {
    val coord = scatter.coordOf(x, z)
    val neighborhood = neighborhood(scatter, coord.first(), coord.second())
    val index = VoronoiStrategy.getCellIndex(x, z, neighborhood.sites)
    return neighborhood.isles[index]
  }

  private fun cellSdf(scatter: HexScatter, owner: Isle): VoronoiCellSdf {
    val neighborhood = neighborhood(scatter, owner.q, owner.r)
    val sdf = VoronoiCellSdf()
    sdf.sites = neighborhood.sites
    sdf.index = neighborhood.centerIndex
    return sdf
  }

  private fun applyIsland(sdf: IslandSdf, owner: Isle, cell: Sdf2) {
    sdf.centerX = owner.site.x
    sdf.centerZ = owner.site.z
    sdf.radius = radii[owner.childIndex]
    sdf.amplitudes = owner.amplitudes
    sdf.phases = owner.phases
    sdf.cell = cell
  }

  override fun traverse(step: TraversalStep): TraversalStep {
    val scatter = scatter(step.traverser.seed)
    val owner = ownerIsle(scatter, step.x, step.z)
    val cell = cellSdf(scatter, owner)

    val island = islandSdfRef.get()
    applyIsland(island, owner, cell)
    val isIsland = island.blob(step.x, step.z) <= 0f

    writeId(step.id, owner.q, owner.r, isIsland)

    if (isIsland) {
      step.sdf.append(island)
    } else {
      val seaSdf = seaSdfRef.get()
      seaSdf.cell = cell
      seaSdf.island = island
      step.sdf.append(seaSdf)
    }

    val distance = step.sdf(step.x, step.z)
    step.distance = max(step.distance, distance)
    step.centerX = owner.site.x
    step.centerZ = owner.site.z
    step.region = if (isIsland) islands[owner.childIndex] else sea

    return step
  }

  override fun locate(step: LocateStep): LocateStep? {
    val tile = readId(step.id) ?: return null
    val scatter = scatter(step.locator.seed)
    val owner = isle(scatter, tile.q, tile.r)
    val cell = cellSdf(scatter, owner)

    val island = IslandSdf()
    applyIsland(island, owner, cell)

    if (tile.isIsland) {
      step.sdf.append(island)
    } else {
      val seaSdf = ArchipelagoSeaSdf()
      seaSdf.cell = cell
      seaSdf.island = island
      step.sdf.append(seaSdf)
    }

    step.centerX = owner.site.x
    step.centerZ = owner.site.z
    step.region = if (tile.isIsland) islands[owner.childIndex] else sea

    return step
  }

  override fun resolve(step: LocateStep, child: Region): LocateStep? {
    val isSea = child === sea
    val index = if (isSea) -1 else islands.indexOfFirst { it === child }
    if (!isSea && index < 0) {
      return null
    }

    val scatter = scatter(step.locator.seed)
    val origin = ownerIsle(scatter, step.centerX, step.centerZ)
    val owner = if (isSea) origin else findIslandChild(scatter, origin, index) ?: return null

    val position = step.id.position()
    writeId(step.id, owner.q, owner.r, !isSea)
    step.id.position(position)
    step.ambiguous = true

    return locate(step)
  }

  private fun findIslandChild(scatter: HexScatter, origin: Isle, index: Int): Isle? {
    if (origin.childIndex == index) {
      return origin
    }
    for (ring in 1..VoronoiStrategy.RESOLVE_SEARCH_RINGS) {
      var found: Isle? = null
      scatter.forNeighborhood(origin.q, origin.r, ring) { q, r ->
        if (found == null && VoronoiStrategy.hexRingDistance(origin.q, origin.r, q, r) == ring) {
          val isle = isle(scatter, q, r)
          if (isle.childIndex == index) {
            found = isle
          }
        }
      }
      if (found != null) {
        return found
      }
    }
    return null
  }

  fun writeId(buffer: ByteBuffer, q: Int, r: Int, isIsland: Boolean) {
    buffer.put(id)
    buffer.putInt(q)
    buffer.putInt(r)
    buffer.put(if (isIsland) 1.toByte() else 0.toByte())
  }

  class Tile(val q: Int, val r: Int, val isIsland: Boolean)

  fun readId(buffer: ByteBuffer): Tile? {
    try {
      val strategyId = buffer.get()
      if (strategyId != id) {
        return null
      }

      val q = buffer.getInt()
      val r = buffer.getInt()
      val isIsland = buffer.get() == 1.toByte()
      return Tile(q, r, isIsland)
    } catch (_: Exception) {
      return null
    }
  }

  companion object {
    const val RINGS = VoronoiStrategy.RINGS
    const val JITTER = 0.4f
    const val HARMONICS = 3
    const val MAX_RADIUS_FACTOR = 0.85f
    val AMPLITUDES = floatArrayOf(0.18f, 0.12f, 0.08f)

    fun builder(seaRegionName: String) = Builder(seaRegionName)
  }

  class Builder(val seaRegionName: String) : StrategySettings {

    override fun build(builder: RegionBuilder, children: Set<Region>): ArchipelagoStrategy {
      val islands =
        children
          .filter { it.name != seaRegionName }
          .sortedByDescending { it.budget }
          .ifEmpty { listOf(Region.empty("${builder.name}_island")) }
      val sea = builder.registry.buildTree(seaRegionName)
      return ArchipelagoStrategy(builder.id, islands.toTypedArray(), sea)
    }
  }
}
