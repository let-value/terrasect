package terrasect.lookup

import java.util.IdentityHashMap
import net.minecraft.core.RegistryAccess
import net.minecraft.world.level.biome.Biome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import terrasect.definition.Region
import terrasect.definition.RegionRegistry
import terrasect.definition.SelectionConstraints

class CompiledBiomeLookupTest {

  private fun region(name: String, biomes: SelectionConstraints?, vararg children: Region) =
    Region(name, 1000, children.toSet(), biomes = biomes)

  private fun index(vararg entries: Pair<Biome, Set<String>>): IdentityHashMap<Biome, BiomeEntry> =
    IdentityHashMap<Biome, BiomeEntry>().apply {
      entries.forEach { (biome, tags) -> this[biome] = BiomeEntry(BiomeFactory.idOf(biome), tags) }
    }

  @Test
  fun `build returns null when no region has biome constraints`() {
    val root = region("root", null)
    assertNull(CompiledBiomeLookup.build(root, RegistryAccess.EMPTY))
  }

  @Test
  fun `decision is precomputed per constrained region`() {
    val biome = BiomeFactory.instance()
    val region =
      region(
        "r",
        SelectionConstraints.builder().blockNames(BiomeFactory.idOf(biome)).build(),
      )
    val index = index(biome to emptySet())

    val decisions = CompiledBiomeLookup.collectDecisions(listOf(region), index)

    assertFalse(decisions[region]!![biome]!!)
  }

  @Test
  fun `unlisted biome in block-name rule is admitted`() {
    val biome = BiomeFactory.instance()
    val region = region("r", SelectionConstraints.builder().blockNames("test:other").build())
    val index = index(biome to emptySet())

    val decisions = CompiledBiomeLookup.collectDecisions(listOf(region), index)

    assertTrue(decisions[region]!![biome]!!)
  }

  @Test
  fun `tag matching uses the tag set of the biome`() {
    val biome = BiomeFactory.instance()
    val region = region("r", SelectionConstraints.builder().allowTags("test:is_desert").build())
    val index = index(biome to setOf("test:is_desert"))

    val decisions = CompiledBiomeLookup.collectDecisions(listOf(region), index)

    assertTrue(decisions[region]!![biome]!!)
  }

  @Test
  fun `mod matching uses the namespace of the biome id`() {
    val biome = BiomeFactory.instance()
    val region = region("r", SelectionConstraints.builder().blockMods("test").build())
    val index = index(biome to emptySet())

    val decisions = CompiledBiomeLookup.collectDecisions(listOf(region), index)

    assertFalse(decisions[region]!![biome]!!)
  }

  @Test
  fun `inherited union of rules is evaluated as a single constraint`() {
    val registry = RegionRegistry()
    registry.region("parent").biomes { allowTags("taiga") }
    registry.region("child").biomes { blockNames("test:dummy") }.parent("parent")

    val root = registry.buildTree("parent")
    val child = root.children.single { it.name == "child" }
    val biome = BiomeFactory.instance()
    val index = index(biome to setOf("taiga"))

    val decisions = CompiledBiomeLookup.collectDecisions(listOf(root, child), index)

    assertAll(
      // parent's allow-list admits the taiga-tagged biome
      { assertTrue(decisions[root]!![biome]!!) },
      // child's own blockName wins inside the inherited union
      { assertFalse(decisions[child]!![biome]!!) },
    )
  }

  @Test
  fun `all-empty constraint admits every biome`() {
    val biome = BiomeFactory.instance()
    val region = region("r", SelectionConstraints.builder().build())
    val index = index(biome to emptySet())

    val decisions = CompiledBiomeLookup.collectDecisions(listOf(region), index)

    assertTrue(decisions[region]!![biome]!!)
  }

  @Test
  fun `isAdmitted falls back to true for unconstrained or unknown regions`() {
    val biome = BiomeFactory.instance()
    val region = region("r", SelectionConstraints.builder().blockNames("test:other").build())
    val index = index(biome to emptySet())
    val lookup = CompiledBiomeLookup(CompiledBiomeLookup.collectDecisions(listOf(region), index))

    assertAll(
      { assertTrue(lookup.isAdmitted(region, biome)) },
      { assertTrue(lookup.isAdmitted(region("other", null), biome)) },
    )
  }

  @Test
  fun `decision is retrievable for lookup construction verification`() {
    val biome = BiomeFactory.instance()
    val region = region("r", SelectionConstraints.builder().blockNames("test:other").build())
    val index = index(biome to emptySet())
    val lookup = CompiledBiomeLookup(CompiledBiomeLookup.collectDecisions(listOf(region), index))

    assertTrue(lookup.decision(region, biome)!!)
  }
}
