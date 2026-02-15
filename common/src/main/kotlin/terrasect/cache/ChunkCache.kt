package terrasect.cache

import net.minecraft.world.level.Level
import terrasect.ChunkAccessExtender
import terrasect.Terrasect
import terrasect.compat.ResourceKeyCompat.getKeyId
import terrasect.definition.Region
import terrasect.generation.Context

class ChunkCache {
  var cache: Cache? = null
  var grid: GridCache<Region>? = null

  constructor(chunk: ChunkAccessExtender) {
    val position = chunk.`terrasect$getChunk`().pos
    val level: Level =
        chunk.`terrasect$getLevel`()
            ?: throw IllegalStateException("Chunk is not attached to a level")
    val dimension = getKeyId(level.dimension())
    val context = Context.get(dimension) ?: return

    // TODO: not used
    this.cache = Cache()
    this.grid =
        GridCache(
            position.maxBlockX - position.minBlockX,
            position.maxBlockZ - position.minBlockZ,
            position.minBlockX,
            position.minBlockZ,
        )

    for (x in position.minBlockX until position.maxBlockX) {
      for (z in position.minBlockZ until position.maxBlockZ) {
        val step = context.traverse(x, z, Terrasect.cache)
        this.grid!!.add(x, z, step.region)
      }
    }
  }
}
