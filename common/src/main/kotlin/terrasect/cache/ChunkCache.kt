package terrasect.cache

import net.minecraft.world.level.Level
import terrasect.ChunkAccessExtender
import terrasect.Terrasect
import terrasect.compat.ResourceKeyCompat.getKeyId
import terrasect.definition.Region
import terrasect.generation.Context

class ChunkCache {
  var grid: GridCache<Region>? = null

  constructor(chunk: ChunkAccessExtender) {
    val position = chunk.`terrasect$getChunk`().pos
    val level: Level =
        chunk.`terrasect$getLevel`()
            ?: throw IllegalStateException("Chunk is not attached to a level")
    val dimension = getKeyId(level.dimension())
    val context = Context.get(dimension) ?: return

    val baseWidth = position.maxBlockX - position.minBlockX
    val baseHeight = position.maxBlockZ - position.minBlockZ
    val originX = position.minBlockX - baseWidth
    val originZ = position.minBlockZ - baseHeight
    val width = baseWidth * 3
    val height = baseHeight * 3

    this.grid = GridCache(width, height, originX, originZ)

    for (x in 0 until width) {
      for (z in 0 until height) {
        val blockX = originX + x
        val blockZ = originZ + z
        val step = context.traverse(blockX, blockZ, Terrasect.cache)
        this.grid!!.add(blockX, blockZ, step.region)
      }
    }
  }
}
