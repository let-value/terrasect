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

  val noiseRegistry: CompiledNoiseRegistry? = CompiledNoiseRegistry.build(root)
  val structureLookup: CompiledStructureLookup? =
    CompiledStructureLookup.build(allSets, root, registry)
  val forcedStructures: CompiledForcedStructures? =
    CompiledForcedStructures.build(seed, root, registry)
  val lootLookup: CompiledLootLookup? = CompiledLootLookup.build(root, registry)
  val mobLookup: CompiledMobLookup? = CompiledMobLookup.build(root, registry)

  init {
    log.debug {
      "built preset=$presetId dim=$dimensionId noiseRegistry=${if (noiseRegistry != null) "ACTIVE" else "NULL"} structureLookup=${if (structureLookup != null) "ACTIVE" else "NULL"} lootLookup=${if (lootLookup != null) "ACTIVE" else "NULL"} mobLookup=${if (mobLookup != null) "ACTIVE" else "NULL"}"
    }
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
    ) {
      val dimensionId = ResourceKeyCompat.getKeyId(dimension)
      log.debug {
        "register called: preset=$presetId force=${PresetRegistry.forcePresetId} dim=$dimensionId"
      }
      val resolvedRegistry = PresetRegistry.resolve(presetId)
      if (resolvedRegistry == null) {
        log.debug { "no preset resolved — noise constraints disabled for $dimensionId" }
        map.remove(dimensionId)
        return
      }
      val name = resolvedRegistry.getRoot(dimensionId)
      if (name == null) {
        log.debug { "no root region for dim=$dimensionId in preset=$presetId" }
        return
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
    }

    fun get(dimensionId: String): DimensionContext? = map[dimensionId]
  }
}
