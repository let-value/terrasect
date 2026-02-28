package terrasect.generation

import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import terrasect.ChunkAccessExtender
import terrasect.Terrasect
import terrasect.cache.PalettedGrid
import terrasect.cache.RegionsCache
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.Region

class ChunkContext {
  var height: Int = 0
  var width: Int = 0
  var originZ: Int = 0
  var originX: Int = 0
  var context: Context? = null
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
            ?: throw IllegalStateException("Chunk is not attached to a level")
    val dimension = ResourceKeyCompat.getKeyId(level.dimension())
    this.context = Context.get(dimension) ?: return

    val baseWidth = position.maxBlockX - position.minBlockX + 1  
    val baseHeight = position.maxBlockZ - position.minBlockZ + 1
    val padding = baseWidth * 1 
    this.originX = position.minBlockX - padding
    this.originZ = position.minBlockZ - padding
    this.width   = baseWidth  + padding * 2
    this.height  = baseHeight + padding * 2

    this.cache = RegionsCache(64, Terrasect.cache)
    this.regions = PalettedGrid(width, height, originX, originZ)
    this.distances = FloatArray(width * height)

    for (x in 0 until width) {
      for (z in 0 until height) {
        val blockX = originX + x
        val blockZ = originZ + z
        val step = context!!.traverser.traverse(blockX, blockZ, cache)
        this.regions!!.add(blockX, blockZ, step.region)
        this.distances!![idx(blockX, blockZ)] = step.distance
      }
    }
  }

  fun getDistance(blockX: Int, blockZ: Int): Float {
    return distances?.get(idx(blockX, blockZ)) ?: return Float.NEGATIVE_INFINITY
  }
}
