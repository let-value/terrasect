package terrasect.cache

import com.github.benmanes.caffeine.cache.Caffeine
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.openhft.hashing.LongHashFunction
import terrasect.sdf.Site
import terrasect.strategies.HexCellResult
import terrasect.strategies.SubdivisionSplit
import terrasect.strategies.SurroundOriginResult
import java.nio.ByteBuffer

val hasher: LongHashFunction = LongHashFunction.xx()

class CacheKey(val hash: Long)

class Cache {
  private val map = Long2ObjectOpenHashMap<CacheKey>()
  val keys: Long2ObjectMap<CacheKey> = Long2ObjectMaps.synchronize(map)

  val hex = Caffeine.newBuilder().maximumSize(200_000).build<CacheKey, HexCellResult>()
  val voronoi = Caffeine.newBuilder().maximumSize(200_000).build<CacheKey, List<Site>>()
  val subdivision = Caffeine.newBuilder().maximumSize(200_000).build<CacheKey, SubdivisionSplit>()
  val surround = Caffeine.newBuilder().maximumSize(200_000).build<CacheKey, SurroundOriginResult>()

  fun getKey(id: ByteBuffer): CacheKey {
    val len = id.position()
    val hash = hasher.hashBytes(id, 0, len)
    keys.get(hash)?.let {
      return it
    }

    val key = CacheKey(hash)
    keys.put(hash, key)
    return key
  }
}
