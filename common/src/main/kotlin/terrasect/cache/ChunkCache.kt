package terrasect.cache

import net.minecraft.world.level.Level
import terrasect.ChunkAccessExtender
import terrasect.Terrasect
import terrasect.compat.ResourceKeyCompat.getKeyId
import terrasect.definition.Region
import terrasect.generation.Context

class ChunkCache {
  var height: Int = 0
  var width: Int = 0
  var originZ: Int = 0
  var originX: Int = 0
  var cache: Cache? = null
  var regions: GridCache<Region>? = null
  var distances: FloatArray? = null
  var noiseConstraintCache: NoiseConstraintCache? = null

  internal constructor()

  fun idx(x: Int, z: Int): Int {
    return x * height + z
  }

  constructor(chunk: ChunkAccessExtender) {
    val position = chunk.`terrasect$getChunk`().pos
    val level: Level =
        chunk.`terrasect$getLevel`()
            ?: throw IllegalStateException("Chunk is not attached to a level")
    val dimension = getKeyId(level.dimension())
    val context = Context.get(dimension) ?: return

    val baseWidth = position.maxBlockX - position.minBlockX
    val baseHeight = position.maxBlockZ - position.minBlockZ
    this.originX = position.minBlockX - baseWidth
    this.originZ = position.minBlockZ - baseHeight
    this.width = baseWidth * 3
    this.height = baseHeight * 3

    this.cache = Cache(64, Terrasect.cache)
    this.regions = GridCache(width, height, originX, originZ)
    this.distances = FloatArray(width * height)

    for (x in 0 until width) {
      for (z in 0 until height) {
        val blockX = originX + x
        val blockZ = originZ + z
        val step = context.traverser.traverse(blockX, blockZ, cache)
        this.regions!!.add(blockX, blockZ, step.region)
        this.distances!![idx(blockX, blockZ)] = step.distance
      }
    }

    if (context.noiseRegistry != null) {
      this.noiseConstraintCache = NoiseConstraintCache(this, context.noiseRegistry)
    }
  }

  fun getDistance(blockX: Int, blockZ: Int): Float {
    return distances?.get(idx(blockX, blockZ)) ?: return Float.NEGATIVE_INFINITY
  }
}
