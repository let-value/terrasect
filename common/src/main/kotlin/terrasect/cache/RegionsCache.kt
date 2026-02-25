package terrasect.cache

import com.github.benmanes.caffeine.cache.Caffeine
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.openhft.hashing.LongHashFunction
import terrasect.sdf.Site
import terrasect.strategies.HexCellResult
import terrasect.strategies.SubdivisionSplit
import terrasect.strategies.SurroundOriginResult
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.github.benmanes.caffeine.cache.Cache as CaffeineCache

val hasher: LongHashFunction = LongHashFunction.xx()

data class CacheKey(val hash: Long)

class RegionsCache(val maximumSize: Long = 10, shared: RegionsCache? = null) {
  private val owner = shared ?: this
  private val stripes = Array(KEY_STRIPES) { Long2ObjectOpenHashMap<CacheKey>() }
  private val locks = Array(KEY_STRIPES) { ReentrantLock() }
  private val keys: ThreadLocal<Long2ObjectOpenHashMap<CacheKey>> =
      ThreadLocal.withInitial { Long2ObjectOpenHashMap(LOCAL_KEY_CACHE_SIZE) }

  private val hexLocal =
      Caffeine.newBuilder().maximumSize(maximumSize).build<CacheKey, HexCellResult>()
  private val voronoiLocal =
      Caffeine.newBuilder().maximumSize(maximumSize).build<CacheKey, List<Site>>()
  private val subdivisionLocal =
      Caffeine.newBuilder().maximumSize(maximumSize).build<CacheKey, SubdivisionSplit>()
  private val surroundLocal =
      Caffeine.newBuilder().maximumSize(maximumSize).build<CacheKey, SurroundOriginResult>()

  val hex = LayeredCache(hexLocal, shared?.hexLocal)
  val voronoi = LayeredCache(voronoiLocal, shared?.voronoiLocal)
  val subdivision = LayeredCache(subdivisionLocal, shared?.subdivisionLocal)
  val surround = LayeredCache(surroundLocal, shared?.surroundLocal)

  fun getKey(id: ByteBuffer): CacheKey {
    val len = id.position()
    val hash = hasher.hashBytes(id, 0, len)
    return owner.internKey(hash)
  }

  private fun internKey(hash: Long): CacheKey {
    val local = keys.get()
    val cached = local.get(hash)
    if (cached != null) {
      return cached
    }

    val stripe = stripeFor(hash)
    val interned =
        locks[stripe].withLock {
          val keys = stripes[stripe]
          keys.get(hash) ?: CacheKey(hash).also { keys.put(hash, it) }
        }

    if (local.size >= LOCAL_KEY_CACHE_SIZE) {
      local.clear()
    }
    local.put(hash, interned)
    return interned
  }

  private fun stripeFor(hash: Long): Int {
    val mixed = hash xor (hash ushr 32)
    return (mixed.toInt() and Int.MAX_VALUE) % KEY_STRIPES
  }

  companion object {
    private const val KEY_STRIPES = 32
    private const val LOCAL_KEY_CACHE_SIZE = 1024
  }
}

class LayeredCache<T : Any>(
    private val local: CaffeineCache<CacheKey, T>,
    private val shared: CaffeineCache<CacheKey, T>?,
) {

  internal inline fun getOrCompute(key: CacheKey, crossinline compute: () -> T): T {
    val value = local.getIfPresent(key)
    if (value != null) {
      return value
    }

    if (shared != null) {
      val value = shared.get(key) { compute() }
      local.put(key, value)
      return value
    }

    return local.get(key) { compute() }
  }
}
