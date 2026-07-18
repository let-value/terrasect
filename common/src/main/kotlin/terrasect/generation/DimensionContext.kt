package terrasect.generation

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import net.minecraft.world.level.levelgen.structure.StructureSet
import terrasect.Terrasect
import terrasect.cache.RegionsCache
import terrasect.compat.ResourceKeyCompat
import terrasect.definition.PresetRegistry
import terrasect.definition.Region
import terrasect.handler.NoiseLogger
import terrasect.lookup.CompiledForcedStructures
import terrasect.lookup.CompiledLootLookup
import terrasect.lookup.CompiledMobLookup
import terrasect.lookup.CompiledNoiseRegistry
import terrasect.lookup.CompiledStructureLookup

private val log = NoiseLogger.context

class DimensionContext(
  val presetId: String?,
  val dimensionId: String,
  val seed: Long,
  val root: Region,
  val sampler: Climate.Sampler,
  val biomesClimate: Climate.ParameterList<Holder<Biome>>?,
  allSets: List<Holder<StructureSet>>,
  registry: RegistryAccess.Frozen,
) {
  val cache = RegionsCache(200, Terrasect.cache)
  val traverser = Traverser(seed, root)
  val locator = Locator(seed, root)

  fun query(selector: String, x: Int, z: Int): LocatorResult? {
    return locator.query(selector, traverser.traverse(x, z, cache).id, cache)
  }

  val noiseRegistry: CompiledNoiseRegistry? = CompiledNoiseRegistry.build(root)
  val structureLookup: CompiledStructureLookup? =
    CompiledStructureLookup.build(allSets, root, registry)
  val forcedStructures: CompiledForcedStructures? =
    CompiledForcedStructures.build(seed, root, registry)
  val lootLookup: CompiledLootLookup? = CompiledLootLookup.build(root, registry)
  val mobLookup: CompiledMobLookup? = CompiledMobLookup.build(root, registry)

  init {
    anchorOrigin()
    log.debug {
      "built preset=$presetId dim=$dimensionId noiseRegistry=${if (noiseRegistry != null) "ACTIVE" else "NULL"} structureLookup=${if (structureLookup != null) "ACTIVE" else "NULL"} lootLookup=${if (lootLookup != null) "ACTIVE" else "NULL"} mobLookup=${if (mobLookup != null) "ACTIVE" else "NULL"}"
    }
  }

  // Locate the region marked as origin anchor and shift the traverser so its center sits at world
  // (0, 0). Reuses the locator's canonical resolution — the same path `/ts locate` walks — so the
  // anchored instance is deterministic and matches what queries report. The query runs before the
  // offsets are set, so its center is still in generator space; the locator then reports every
  // later result in the shifted world space.
  private fun anchorOrigin() {
    val anchor = findAnchor(root) ?: return
    val located = locator.query(".${anchor.name}", null, cache) ?: return
    traverser.offsetX = located.centerX
    traverser.offsetZ = located.centerZ
    locator.offsetX = located.centerX
    locator.offsetZ = located.centerZ
    log.debug {
      "anchored dim=$dimensionId region=${anchor.name} offset=(${located.centerX}, ${located.centerZ})"
    }
  }

  private fun findAnchor(region: Region): Region? {
    if (region.originAnchor) {
      return region
    }
    return region.children.firstNotNullOfOrNull { findAnchor(it) }
  }

  companion object {
    val map = ConcurrentHashMap<String, DimensionContext>()

    @JvmStatic
    fun register(
      presetId: String?,
      dimension: ResourceKey<Level>,
      structureSets: MutableList<Holder<StructureSet>>,
      registry: RegistryAccess.Frozen,
      seed: Long,
      sampler: Climate.Sampler,
      biomesClimate: Climate.ParameterList<Holder<Biome>>?,
    ): DimensionContext? {
      val dimensionId = ResourceKeyCompat.getKeyId(dimension)
      log.debug {
        "register called: preset=$presetId force=${PresetRegistry.forcePresetId} dim=$dimensionId"
      }
      val resolvedRegistry = PresetRegistry.resolve(presetId)
      if (resolvedRegistry == null) {
        log.debug { "no preset resolved — noise constraints disabled for $dimensionId" }
        map.remove(dimensionId)
        return null
      }
      val name = resolvedRegistry.getRoot(dimensionId)
      if (name == null) {
        log.debug { "no root region for dim=$dimensionId in preset=$presetId" }
        map.remove(dimensionId)
        return null
      }
      val root = resolvedRegistry.buildTree(name)

      val dimensionContext =
        DimensionContext(
          presetId,
          dimensionId,
          seed,
          root,
          sampler,
          biomesClimate,
          structureSets,
          registry,
        )
      map[dimensionId] = dimensionContext
      log.debug { "registered dim=$dimensionId" }
      return dimensionContext
    }

    fun get(dimensionId: String): DimensionContext? = map[dimensionId]
  }
}
