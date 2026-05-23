package terrasect.generation

import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import terrasect.cache.PalettedGrid
import terrasect.cache.RegionsCache
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region
import terrasect.extender.ChunkAccessExtender
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent

class ChunkContext {
  var height: Int = 0
  var width: Int = 0
  var originZ: Int = 0
  var originX: Int = 0
  var dimensionContext: DimensionContext? = null
  var cache: RegionsCache? = null
  var regions: PalettedGrid<Region>? = null
  var distances: FloatArray? = null

  constructor()

  fun idx(x: Int, z: Int): Int {
    val localX = x - originX
    val localZ = z - originZ
    return localX * height + localZ
  }

  constructor(chunk: ChunkAccessExtender, position: ChunkPos) {
    val level: Level =
      chunk.`terrasect$getLevel`()
        ?: run {
          var instr = TerrasectInstr.chunk
          instr.count(TerrasectMetricEvent.CHUNK_ERROR, "dimension") { "unknown" }
          throw IllegalStateException("Chunk is not attached to a level")
        }
    val dimension = ResourceKeyCompat.getKeyId(level.dimension())
    this.dimensionContext =
      DimensionContext.get(dimension)
        ?: run {
          var instr = TerrasectInstr.chunk
          instr.count(TerrasectMetricEvent.CHUNK_ERROR, "dimension") { dimension }
          return
        }

    val baseWidth = position.maxBlockX - position.minBlockX + 1
    val baseHeight = position.maxBlockZ - position.minBlockZ + 1
    val padding = baseWidth * 1
    this.originX = position.minBlockX - padding
    this.originZ = position.minBlockZ - padding
    this.width = baseWidth + padding * 2
    this.height = baseHeight + padding * 2

    this.cache = RegionsCache(6, dimensionContext!!.cache)
    this.regions = PalettedGrid(width, height, originX, originZ)
    this.distances = FloatArray(width * height)

    var instr = TerrasectInstr.chunk
    instr.count(TerrasectMetricEvent.CHUNK_TRAVERSE, "dimension") { dimension }
    for (x in 0 until width) {
      for (z in 0 until height) {
        val blockX = originX + x
        val blockZ = originZ + z
        val step = dimensionContext!!.traverser.traverse(blockX, blockZ, cache)
        this.regions!!.add(blockX, blockZ, step.region)
        this.distances!![idx(blockX, blockZ)] = step.distance
      }
    }
    instr.count(TerrasectMetricEvent.CHUNK_CREATED, "dimension") { dimension }
  }

  fun getDistance(blockX: Int, blockZ: Int): Float {
    if (distances != null && inBounds(blockX, blockZ)) {
      return distances!![idx(blockX, blockZ)]
    }
    val ctx = dimensionContext ?: return Float.NEGATIVE_INFINITY
    var instr = TerrasectInstr.chunk
    instr.count(TerrasectMetricEvent.CHUNK_TRAVERSE_CACHE_MISS, "dimension") {
      ctx.dimensionId
    }
    return ctx.traverser.traverse(blockX, blockZ, cache).distance
  }

  private fun inBounds(blockX: Int, blockZ: Int): Boolean {
    val localX = blockX - originX
    val localZ = blockZ - originZ
    return localX >= 0 && localX < width && localZ >= 0 && localZ < height
  }

  fun getRegion(blockX: Int, blockZ: Int): Region? {
    val ctx = dimensionContext ?: return null
    if (regions != null && inBounds(blockX, blockZ)) {
      return regions!!.get(blockX, blockZ)
    }
    var instr = TerrasectInstr.chunk
    instr.count(TerrasectMetricEvent.CHUNK_TRAVERSE_CACHE_MISS, "dimension") {
      ctx.dimensionId
    }
    return ctx.traverser.traverse(blockX, blockZ, cache).region
  }
}
