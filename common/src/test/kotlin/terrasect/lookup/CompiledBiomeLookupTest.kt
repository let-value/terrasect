package terrasect.lookup

import com.mojang.datafixers.util.Pair as MojangPair
import java.util.IdentityHashMap
import net.minecraft.core.Holder
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
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

  private fun point(value: Float): Climate.ParameterPoint =
    Climate.parameters(value, 0f, 0f, 0f, 0f, 0f, 0f)

  private fun parameters(vararg entries: Pair<Float, Biome>): Climate.ParameterList<Holder<Biome>> =
    Climate.ParameterList(
      entries.map { entry -> MojangPair(point(entry.first), Holder.direct(entry.second)) }
    )

  @Test
  fun `build returns null when no region has biome constraints`() {
    val root = region("root", null)

    assertNull(CompiledBiomeLookup.build(root, null, "test:dimension"))
  }

  @Test
  fun `empty biome table does not activate a lookup`() {
    val root = region("root", SelectionConstraints.builder().build())

    assertNull(CompiledBiomeLookup.build(root, null, "test:dimension"))
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
    val biome = BiomeFactory.instance("test:dummy")
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
      { assertTrue(decisions[root]!![biome]!!) },
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

  @Test
  fun `filtered parameter list uses exact ids and nearest allowed climate point`() {
    val plains = BiomeFactory.instance("minecraft:plains")
    val forest = BiomeFactory.instance("minecraft:forest")
    val desert = BiomeFactory.instance("minecraft:desert")
    val root =
      region(
        "root",
        SelectionConstraints.builder().allowNames("minecraft:plains", "minecraft:forest").build(),
      )
    val parameterList = parameters(0f to plains, 0.25f to forest, 1f to desert)
    val lookup =
      CompiledBiomeLookup.compile(
        listOf(root),
        parameterList,
        index(plains to emptySet(), forest to emptySet(), desert to emptySet()),
      )
    val target = Climate.target(1f, 0f, 0f, 0f, 0f, 0f)

    assertSame(forest, lookup.select(root, target)!!.value())
    assertTrue(lookup.isAdmitted(root, plains))
    assertFalse(lookup.isAdmitted(root, desert))
  }

  @Test
  fun `namespace allow and block rules are compiled`() {
    val biome = BiomeFactory.instance("minecraft:plains")
    val allowRoot = region("allow", SelectionConstraints.builder().allowMods("minecraft").build())
    val blockRoot = region("block", SelectionConstraints.builder().blockMods("minecraft").build())
    val index = index(biome to emptySet())

    val allowLookup = CompiledBiomeLookup.compile(listOf(allowRoot), parameters(0f to biome), index)
    val blockLookup = CompiledBiomeLookup.compile(listOf(blockRoot), parameters(0f to biome), index)

    assertTrue(allowLookup.isAdmitted(allowRoot, biome))
    assertFalse(blockLookup.isAdmitted(blockRoot, biome))
  }

  @Test
  fun `child region gets its own compiled parameter list`() {
    val plains = BiomeFactory.instance("minecraft:plains")
    val forest = BiomeFactory.instance("minecraft:forest")
    val desert = BiomeFactory.instance("minecraft:desert")
    val child =
      region("child", SelectionConstraints.builder().allowNames("minecraft:desert").build())
    val root =
      region(
        "root",
        SelectionConstraints.builder().allowNames("minecraft:plains", "minecraft:forest").build(),
        child,
      )
    val index = index(plains to emptySet(), forest to emptySet(), desert to emptySet())
    val lookup =
      CompiledBiomeLookup.compile(
        listOf(root, child),
        parameters(0f to plains, 0.25f to forest, 1f to desert),
        index,
      )
    val target = Climate.target(1f, 0f, 0f, 0f, 0f, 0f)

    assertSame(forest, lookup.select(root, target)!!.value())
    assertSame(desert, lookup.select(child, target)!!.value())
    assertSame(lookup.select(child, target), lookup.select(child, target))
  }

  @Test
  fun `zero-match allow list has no replacement and remains deterministic`() {
    val plains = BiomeFactory.instance("minecraft:plains")
    val root = region("root", SelectionConstraints.builder().allowNames("example:missing").build())
    val index = index(plains to emptySet())
    val lookup = CompiledBiomeLookup.compile(listOf(root), parameters(0f to plains), index)
    val target = Climate.target(0f, 0f, 0f, 0f, 0f, 0f)

    assertTrue(lookup.isConstrained(root))
    assertNull(lookup.select(root, target))
    assertFalse(lookup.isAdmitted(root, plains))
  }
}
