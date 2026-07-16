package terrasect.lookup

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.IdentityHashMap
import kotlin.math.PI
import kotlin.math.ceil
import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.levelgen.structure.Structure
import terrasect.cache.RegionsCache
import terrasect.cache.hasher
import terrasect.compat.ResourceKeyCompat
import terrasect.compat.StructureMetadataCompat
import terrasect.definition.Region
import terrasect.generation.ForcedPlan
import terrasect.generation.ForcedSite
import terrasect.generation.TraversalStep
import terrasect.generation.Traverser
import terrasect.generation.buildForcedPlan
import terrasect.handler.NoiseLogger

private val forcedLog = NoiseLogger.registry

const val FORCED_BUDGET_MARGIN = 16
const val DEFAULT_FORCED_RADIUS = 80
const val FORCED_PLAN_CACHE_SIZE = 512L

class ForcedStructureEntry(val id: String, val holder: Holder<Structure>, val budget: Long)

class ForcedRegionSpec(val entries: List<ForcedStructureEntry>) {
  val budgets = LongArray(entries.size) { entries[it].budget }
}

class ForcedStructureStart(val entry: ForcedStructureEntry, val site: ForcedSite)

class ForcedChunkDecision(
  val leaf: Region,
  val banned: Boolean,
  val starts: List<ForcedStructureStart>,
)

class CompiledForcedStructures
private constructor(
  private val seed: Long,
  private val specs: IdentityHashMap<Region, ForcedRegionSpec>,
) {
  private val plans =
    Caffeine.newBuilder().maximumSize(FORCED_PLAN_CACHE_SIZE).build<Long, ForcedPlan>()

  fun query(
    traverser: Traverser,
    cache: RegionsCache?,
    chunkX: Int,
    chunkZ: Int,
  ): ForcedChunkDecision {
    val blockX = (chunkX shl 4) + 8
    val blockZ = (chunkZ shl 4) + 8
    var step = traverser.iterate(blockX, blockZ, cache)
    var banned = false
    var starts: MutableList<ForcedStructureStart>? = null
    while (true) {
      val spec = specs[step.region]
      if (spec != null) {
        val plan = planFor(spec, step)
        if (plan.isBanned(chunkX, chunkZ)) banned = true
        for (site in plan.startsAt(chunkX, chunkZ)) {
          val list = starts ?: mutableListOf<ForcedStructureStart>().also { starts = it }
          list.add(ForcedStructureStart(spec.entries[site.index], site))
        }
      }
      if (!step.region.hasChildren) break
      step = step.next() ?: break
    }
    return ForcedChunkDecision(step.region, banned, starts ?: emptyList())
  }

  // Lists every forced site of every region instance containing the given block position, for
  // debugging and tests; the chunk-generation hot path uses query instead.
  fun sitesAt(
    traverser: Traverser,
    cache: RegionsCache?,
    blockX: Int,
    blockZ: Int,
  ): List<ForcedStructureStart> {
    var step = traverser.iterate(blockX, blockZ, cache)
    val result = mutableListOf<ForcedStructureStart>()
    while (true) {
      val spec = specs[step.region]
      if (spec != null) {
        val plan = planFor(spec, step)
        for (site in plan.sites) {
          result.add(ForcedStructureStart(spec.entries[site.index], site))
        }
      }
      if (!step.region.hasChildren) break
      step = step.next() ?: break
    }
    return result
  }

  // Plans are deterministic in (world seed, region-instance address), so they are recomputed on
  // cache eviction instead of persisted. The address prefix in step.id uniquely identifies the
  // region instance whose composed SDF is currently on the step.
  private fun planFor(spec: ForcedRegionSpec, step: TraversalStep): ForcedPlan {
    val instanceHash = hasher.hashBytes(step.id, 0, step.id.position())
    return plans.get(instanceHash) {
      buildForcedPlan(
        hasher.hashLong(instanceHash xor seed),
        step.sdf,
        spec.budgets,
        step.centerX,
        step.centerZ,
      )
    }
  }

  companion object {
    fun build(
      seed: Long,
      root: Region,
      registry: RegistryAccess.Frozen,
    ): CompiledForcedStructures? {
      val regions = mutableListOf<Region>()
      val queue = ArrayDeque<Region>()
      queue.add(root)
      while (queue.isNotEmpty()) {
        val region = queue.removeFirst()
        if (!region.structures?.forced.isNullOrEmpty()) regions.add(region)
        queue.addAll(region.children)
      }
      if (regions.isEmpty()) return null

      val byId = HashMap<String, Holder<Structure>>()
      registry.lookupOrThrow(Registries.STRUCTURE).listElements().forEach { holder ->
        byId[ResourceKeyCompat.getKeyId(holder.key())] = holder
      }

      val specs = IdentityHashMap<Region, ForcedRegionSpec>()
      for (region in regions) {
        val entries = ArrayList<ForcedStructureEntry>()
        for (request in region.structures!!.forced) {
          val holder = byId[request.name]
          if (holder == null) {
            forcedLog.warn {
              "forced structure ${request.name} missing from registry, skipped in region=${region.name}"
            }
            continue
          }
          val budget = request.budget ?: derivedBudget(holder.value())
          entries.add(ForcedStructureEntry(request.name, holder, budget))
        }
        if (entries.isEmpty()) continue
        entries.sortByDescending { it.budget }
        specs[region] = ForcedRegionSpec(entries)
      }
      if (specs.isEmpty()) return null
      forcedLog.debug {
        "build: ${specs.size} regions with forced structures under root=${root.name}"
      }
      return CompiledForcedStructures(seed, specs)
    }

    private fun derivedBudget(structure: Structure): Long {
      val structureRadius = StructureMetadataCompat.jigsawRadius(structure) ?: DEFAULT_FORCED_RADIUS
      val radius = structureRadius + FORCED_BUDGET_MARGIN
      return ceil(PI * radius * radius).toLong()
    }
  }
}
