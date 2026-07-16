package terrasect.generation

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlin.math.ceil
import terrasect.sdf.Sdf2
import terrasect.sdf.Site
import terrasect.sdf.estimateBounds
import terrasect.sdf.getSites
import terrasect.utils.packPair

class ForcedSite(val index: Int, val blockX: Int, val blockZ: Int, val radius: Float) {
  val chunkX = blockX shr 4
  val chunkZ = blockZ shr 4
}

class ForcedPlan(val sites: List<ForcedSite>) {
  private val banned = LongOpenHashSet()
  private val starts = Long2ObjectOpenHashMap<MutableList<ForcedSite>>()

  init {
    for (site in sites) {
      starts.computeIfAbsent(packPair(site.chunkX, site.chunkZ)) { mutableListOf() }.add(site)
      banChunksTouching(site)
    }
  }

  fun isBanned(chunkX: Int, chunkZ: Int): Boolean = banned.contains(packPair(chunkX, chunkZ))

  fun startsAt(chunkX: Int, chunkZ: Int): List<ForcedSite> =
    starts.get(packPair(chunkX, chunkZ)) ?: emptyList()

  private fun banChunksTouching(site: ForcedSite) {
    val reach = ceil(site.radius).toInt()
    val minChunkX = (site.blockX - reach) shr 4
    val maxChunkX = (site.blockX + reach) shr 4
    val minChunkZ = (site.blockZ - reach) shr 4
    val maxChunkZ = (site.blockZ + reach) shr 4
    val radiusSq = site.radius.toDouble() * site.radius.toDouble()
    for (chunkZ in minChunkZ..maxChunkZ) {
      for (chunkX in minChunkX..maxChunkX) {
        val nearestX = site.blockX.coerceIn(chunkX shl 4, (chunkX shl 4) + 15).toDouble()
        val nearestZ = site.blockZ.coerceIn(chunkZ shl 4, (chunkZ shl 4) + 15).toDouble()
        val dx = nearestX - site.blockX
        val dz = nearestZ - site.blockZ
        if (dx * dx + dz * dz <= radiusSq) {
          banned.add(packPair(chunkX, chunkZ))
        }
      }
    }
  }
}

// Budgets must be sorted descending (getSites enforces it): sites are returned in budget order,
// so index alignment between the requested budgets and the returned sites depends on it. The
// origin must be a deterministic point of the region instance the sdf describes — estimateBounds
// searches (and clamps its flood fill) around it, so a far-off origin yields garbage bounds.
fun buildForcedPlan(
  seed: Long,
  sdf: Sdf2,
  budgets: LongArray,
  originX: Int,
  originZ: Int,
): ForcedPlan {
  val bounds = estimateBounds(sdf, originX, originZ)
  val sites: List<Site> = getSites(seed, sdf, bounds, budgets)
  return ForcedPlan(
    sites.mapIndexed { index, site -> ForcedSite(index, site.x, site.z, site.radius) }
  )
}
