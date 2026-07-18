package terrasect.generation

import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import terrasect.cache.RegionsCache
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import terrasect.extender.ChunkAccessExtender
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent
import terrasect.lookup.ForcedChunkDecision

private val instr = TerrasectInstr.chunk

class ChunkContext {
  @JvmField var height: Int = 0
  @JvmField var width: Int = 0
  @JvmField var originZ: Int = 0
  @JvmField var originX: Int = 0
  var chunkX: Int = 0
  var chunkZ: Int = 0
  var dimensionContext: DimensionContext? = null
  var cache: RegionsCache? = null
  // Flat region grid indexed by idx(x, z); the density-function hot path reads it and `distances`
  // together off a single index, so both are exposed as fields rather than behind an accessor.
  @JvmField var regions: Array<Region?>? = null
  @JvmField var distances: FloatArray? = null
  private var forcedDecision: ForcedChunkDecision? = null

  constructor()

  fun idx(x: Int, z: Int): Int {
    val localX = x - originX
    val localZ = z - originZ
    return localX * height + localZ
  }

  constructor(chunk: ChunkAccessExtender, position: ChunkPos) {
    val level: Level = chunk.`terrasect$getLevel`() ?: return
    val dimension = ResourceKeyCompat.getKeyId(level.dimension())
    this.dimensionContext =
      DimensionContext.get(dimension)
        ?: run {
          instr.count(TerrasectMetricEvent.CHUNK_ERROR, "dimension") { dimension }
          return
        }

    this.chunkX = position.x
    this.chunkZ = position.z
    val baseWidth = position.maxBlockX - position.minBlockX + 1
    val baseHeight = position.maxBlockZ - position.minBlockZ + 1
    // A thin border covers density/climate samples that spill a cell past the chunk edge; anything
    // further falls back to a live traverse in getRegion/getDistance. Padding by a full chunk (the
    // old baseWidth) traversed a 48x48 grid per 16x16 chunk — 9x the work for a border almost never
    // read.
    val padding = 2
    this.originX = position.minBlockX - padding
    this.originZ = position.minBlockZ - padding
    this.width = baseWidth + padding * 2
    this.height = baseHeight + padding * 2

    this.cache = RegionsCache(6, dimensionContext!!.cache)
    val regions = arrayOfNulls<Region>(width * height)
    val distances = FloatArray(width * height)
    this.regions = regions
    this.distances = distances

    instr.count(TerrasectMetricEvent.CHUNK_TRAVERSE, "dimension") { dimension }
    for (x in 0 until width) {
      for (z in 0 until height) {
        val blockX = originX + x
        val blockZ = originZ + z
        val step = dimensionContext!!.traverser.traverse(blockX, blockZ, cache)
        val cell = idx(blockX, blockZ)
        regions[cell] = step.region
        distances[cell] = step.distance
      }
    }
    instr.count(TerrasectMetricEvent.CHUNK_CREATED, "dimension") { dimension }
  }

  fun getDistance(blockX: Int, blockZ: Int): Float {
    val distances = this.distances
    if (distances != null && inBounds(blockX, blockZ)) {
      return distances[idx(blockX, blockZ)]
    }
    val ctx = dimensionContext ?: return Float.NEGATIVE_INFINITY
    instr.count(TerrasectMetricEvent.CHUNK_TRAVERSE_CACHE_MISS, "dimension") { ctx.dimensionId }
    return ctx.traverser.traverse(blockX, blockZ, cache).distance
  }

  private fun inBounds(blockX: Int, blockZ: Int): Boolean {
    val localX = blockX - originX
    val localZ = blockZ - originZ
    return localX >= 0 && localX < width && localZ >= 0 && localZ < height
  }

  fun getForcedDecision(): ForcedChunkDecision? {
    val ctx = dimensionContext ?: return null
    val forced = ctx.forcedStructures ?: return null
    forcedDecision?.let {
      return it
    }
    return forced.query(ctx.traverser, cache, chunkX, chunkZ).also { forcedDecision = it }
  }

  fun getRegion(blockX: Int, blockZ: Int): Region? {
    val ctx = dimensionContext ?: return null
    val regions = this.regions
    if (regions != null && inBounds(blockX, blockZ)) {
      return regions[idx(blockX, blockZ)]
    }
    instr.count(TerrasectMetricEvent.CHUNK_TRAVERSE_CACHE_MISS, "dimension") { ctx.dimensionId }
    return ctx.traverser.traverse(blockX, blockZ, cache).region
  }
}
