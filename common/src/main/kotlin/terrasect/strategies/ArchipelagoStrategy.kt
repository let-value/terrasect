package terrasect.strategies

import java.nio.ByteBuffer
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import terrasect.cache.RegionsCache
import terrasect.definition.Region
import terrasect.definition.RegionBuilder
import terrasect.definition.Strategy
import terrasect.definition.StrategySettings
import terrasect.generation.LocateStep
import terrasect.generation.TraversalStep
import terrasect.sdf.*

class PlacedIsland(val childIndex: Int, val island: IslandSdf)

class ArchipelagoStrategy(
  override val id: Byte,
  val islands: Array<Region>,
  val sea: Region,
) : Strategy {
  override val targets = islands.toList() + sea

  val budgets = LongArray(islands.size) { islands[it].budget }

  val seaSdfRef: ThreadLocal<ArchipelagoSeaSdf> = ThreadLocal.withInitial { ArchipelagoSeaSdf() }

  private fun getCachedIslands(
    seed: Int,
    id: ByteBuffer,
    parentSdf: Sdf2,
    cache: RegionsCache?,
    originX: Int,
    originZ: Int,
  ): List<PlacedIsland> {
    if (cache == null) {
      return getIslands(seed, parentSdf, budgets, originX, originZ)
    }

    val key = cache.getKey(id)
    return cache.archipelago.getOrCompute(key) {
      getIslands(seed, parentSdf, budgets, originX, originZ)
    }
  }

  override fun traverse(step: TraversalStep): TraversalStep {
    val seed = VoronoiStrategy.getCellSeed(step.traverser.seed, step.id)
    val placed = getCachedIslands(seed, step.id, step.sdf, step.cache, step.centerX, step.centerZ)
    val slot = islandSlotAt(step.x, step.z, placed)

    writeId(step.id, seed, slot)

    if (slot >= 0) {
      val island = placed[slot].island
      step.sdf.append(island)
      step.centerX = island.centerX
      step.centerZ = island.centerZ
      step.region = islands[placed[slot].childIndex]
    } else {
      val seaSdf = seaSdfRef.get()
      seaSdf.islands = placed.map { it.island }
      step.sdf.append(seaSdf)
      step.region = sea
    }

    val distance = step.sdf(step.x, step.z)
    step.distance = max(step.distance, distance)

    return step
  }

  override fun locate(step: LocateStep): LocateStep? {
    val originalPosition = step.id.position()
    val (seed, slot) = readId(step.id) ?: return null
    if (slot == Strategy.SELF_INDEX) {
      return step
    }

    val newPosition = step.id.position()
    step.id.position(originalPosition)
    val placed = getCachedIslands(seed, step.id, step.sdf, step.cache, step.centerX, step.centerZ)
    step.id.position(newPosition)

    if (slot == SEA_INDEX) {
      val seaSdf = ArchipelagoSeaSdf()
      seaSdf.islands = placed.map { it.island }
      step.sdf.append(seaSdf)
      step.region = sea
      return step
    }

    if (slot !in placed.indices) {
      return null
    }

    val island = placed[slot].island
    step.sdf.append(island)
    step.centerX = island.centerX
    step.centerZ = island.centerZ
    step.region = islands[placed[slot].childIndex]

    return step
  }

  override fun resolve(step: LocateStep, child: Region): LocateStep? {
    val seed = VoronoiStrategy.getCellSeed(step.locator.seed, step.id)
    val slot =
      if (child === sea) {
        SEA_INDEX
      } else {
        val index = islands.indexOfFirst { it === child }
        if (index < 0) {
          return null
        }
        val placed =
          getCachedIslands(seed, step.id, step.sdf, step.cache, step.centerX, step.centerZ)
        placed.indexOfFirst { it.childIndex == index }.takeIf { it >= 0 } ?: return null
      }

    val position = step.id.position()
    writeId(step.id, seed, slot)
    step.id.position(position)
    if (slot != SEA_INDEX) {
      step.ambiguous = true
    }

    return locate(step)
  }

  override fun writeSelf(step: LocateStep, buffer: ByteBuffer) {
    writeId(buffer, VoronoiStrategy.getCellSeed(step.locator.seed, buffer), Strategy.SELF_INDEX)
  }

  fun writeId(buffer: ByteBuffer, seed: Int, slot: Int) {
    buffer.put(id)
    buffer.putInt(seed)
    buffer.putInt(slot)
  }

  fun readId(buffer: ByteBuffer): Pair<Int, Int>? {
    try {
      val strategyId = buffer.get()
      if (strategyId != id) {
        return null
      }

      val seed = buffer.getInt()
      val slot = buffer.getInt()
      return seed to slot
    } catch (_: Exception) {
      return null
    }
  }

  companion object {
    const val SEA_INDEX = -2
    const val HARMONICS = 3
    val AMPLITUDES = floatArrayOf(0.25f, 0.18f, 0.12f)
    const val PATH_SAMPLES = 64
    const val MAX_SLOTS = 256
    const val NUDGE_STEPS = 10
    const val DEPTH_FACTOR = 0.35f

    fun islandSlotAt(x: Int, z: Int, placed: List<PlacedIsland>): Int {
      var slot = SEA_INDEX
      var closest = 0f
      for (i in placed.indices) {
        val distance = placed[i].island(x, z)
        if (distance <= 0f && (slot == SEA_INDEX || distance < closest)) {
          slot = i
          closest = distance
        }
      }
      return slot
    }

    // Islands are laid out along a seeded arc across the parent instance — the way real
    // archipelagos trace volcanic chains — cycling through the children so every child recurs as
    // the chain progresses. Positions are pulled inside the parent along its gradient; slots that
    // cannot reach comfortable depth are skipped, so the chain follows the parent's shape instead
    // of escaping it.
    fun getIslands(
      cellSeed: Int,
      parentSdf: Sdf2,
      budgets: LongArray,
      originX: Int = 0,
      originZ: Int = 0,
    ): List<PlacedIsland> {
      if (budgets.isEmpty()) {
        return emptyList()
      }

      val bounds = estimateBounds(parentSdf, originX, originZ)
      val rng = Random(cellSeed.toLong())
      val radii = FloatArray(budgets.size) { sqrt(budgets[it].coerceAtLeast(0) / PI).toFloat() }

      val centerX = (bounds.minX + bounds.maxX) / 2f
      val centerZ = (bounds.minZ + bounds.maxZ) / 2f
      val span = max(bounds.width, bounds.height).toFloat()

      val angle = rng.nextFloat() * (2.0 * PI).toFloat()
      val exitAngle = angle + PI.toFloat() + (rng.nextFloat() - 0.5f) * 0.9f
      val x0 = centerX + cos(angle) * span * 0.48f
      val z0 = centerZ + sin(angle) * span * 0.48f
      val x2 = centerX + cos(exitAngle) * span * 0.48f
      val z2 = centerZ + sin(exitAngle) * span * 0.48f
      val bend = (rng.nextFloat() - 0.5f) * 0.9f
      val normalX = -(z2 - z0)
      val normalZ = x2 - x0
      val normalLength = hypot(normalX, normalZ).coerceAtLeast(1e-6f)
      val x1 = (x0 + x2) / 2f + normalX / normalLength * span * bend
      val z1 = (z0 + z2) / 2f + normalZ / normalLength * span * bend

      val pathX = FloatArray(PATH_SAMPLES + 1)
      val pathZ = FloatArray(PATH_SAMPLES + 1)
      val lengths = FloatArray(PATH_SAMPLES + 1)
      for (i in 0..PATH_SAMPLES) {
        val t = i.toFloat() / PATH_SAMPLES
        val u = 1f - t
        pathX[i] = u * u * x0 + 2f * u * t * x1 + t * t * x2
        pathZ[i] = u * u * z0 + 2f * u * t * z1 + t * t * z2
        if (i > 0) {
          lengths[i] = lengths[i - 1] + hypot(pathX[i] - pathX[i - 1], pathZ[i] - pathZ[i - 1])
        }
      }
      val pathLength = lengths[PATH_SAMPLES]

      val placed = ArrayList<PlacedIsland>()
      var along = radii[0]
      var slot = 0
      // The cursor advances only when a child actually lands, so a slot lost to nudging or
      // overlap retries the same child further along the chain instead of dropping it.
      var childCursor = 0
      while (along < pathLength && slot < MAX_SLOTS) {
        val childIndex = childCursor % budgets.size
        val radius = radii[childIndex]

        val lateral = (rng.nextFloat() - 0.5f) * 2f * radius * 0.8f
        val amplitudes = FloatArray(HARMONICS)
        val phases = FloatArray(HARMONICS)
        for (k in 0 until HARMONICS) {
          amplitudes[k] = rng.nextFloat() * AMPLITUDES[k]
          phases[k] = rng.nextFloat() * (2.0 * PI).toFloat()
        }
        val gap = radius * (0.6f + rng.nextFloat() * 1.2f)

        val (baseX, baseZ) = pointAt(along, pathX, pathZ, lengths)
        val (tangentX, tangentZ) = tangentAt(along, pathX, pathZ, lengths)
        var x = baseX - tangentZ * lateral
        var z = baseZ + tangentX * lateral

        // Aim for comfortable depth but step at most one radius at a time — in thin parents
        // (crescents) the descent settles at the deepest reachable point instead of overshooting
        // across the shape, and acceptance below only requires modest depth.
        val aim = radius * (1f + DEPTH_FACTOR)
        for (attempt in 0 until NUDGE_STEPS) {
          val distance = parentSdf(x.toInt(), z.toInt())
          if (distance <= -aim) {
            break
          }
          val eps = max(1, (radius / 4f).toInt())
          val (gx, gz) = numericGradient(parentSdf, x.toInt(), z.toInt(), eps)
          val gradientLength = hypot(gx, gz).coerceAtLeast(1e-6f)
          val step = (distance + aim).coerceAtMost(radius)
          x -= gx / gradientLength * step
          z -= gz / gradientLength * step
        }

        if (parentSdf(x.toInt(), z.toInt()) <= -radius * DEPTH_FACTOR) {
          val island = IslandSdf()
          island.centerX = x.toInt()
          island.centerZ = z.toInt()
          island.radius = radius
          island.amplitudes = amplitudes
          island.phases = phases

          if (placed.none { overlaps(it.island, island) }) {
            placed.add(PlacedIsland(childIndex, island))
            childCursor++
          }
        }

        along += radius + gap + radii[childCursor % budgets.size]
        slot++
      }

      return placed
    }

    private fun overlaps(a: IslandSdf, b: IslandSdf): Boolean {
      val distance = hypot((a.centerX - b.centerX).toFloat(), (a.centerZ - b.centerZ).toFloat())
      return distance < (a.radius + b.radius) * 1.2f
    }

    private fun pointAt(
      along: Float,
      pathX: FloatArray,
      pathZ: FloatArray,
      lengths: FloatArray,
    ): Pair<Float, Float> {
      val i = segmentAt(along, lengths)
      val segment = (lengths[i + 1] - lengths[i]).coerceAtLeast(1e-6f)
      val t = ((along - lengths[i]) / segment).coerceIn(0f, 1f)
      return pathX[i] + (pathX[i + 1] - pathX[i]) * t to pathZ[i] + (pathZ[i + 1] - pathZ[i]) * t
    }

    private fun tangentAt(
      along: Float,
      pathX: FloatArray,
      pathZ: FloatArray,
      lengths: FloatArray,
    ): Pair<Float, Float> {
      val i = segmentAt(along, lengths)
      val dx = pathX[i + 1] - pathX[i]
      val dz = pathZ[i + 1] - pathZ[i]
      val length = hypot(dx, dz).coerceAtLeast(1e-6f)
      return dx / length to dz / length
    }

    private fun segmentAt(along: Float, lengths: FloatArray): Int {
      for (i in 1 until lengths.size) {
        if (along <= lengths[i]) {
          return i - 1
        }
      }
      return lengths.size - 2
    }

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
