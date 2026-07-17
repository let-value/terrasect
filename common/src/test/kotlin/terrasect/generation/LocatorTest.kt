package terrasect.generation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy

private const val SEED = 1234L

class LocatorTest {
  companion object {
    val registry = RegionRegistry()

    init {
      registry.region("hex").radius(150).strategy(Strategy.hex())
      registry.region("cell").parent("hex").strategy(Strategy.voronoi())

      registry.region("voronoi1").radius(30).parent("cell")
      registry.region("voronoi2").radius(45).parent("cell")
      registry.region("voronoi3").radius(75).parent("cell")
    }
  }

  @Test
  fun `should locate from serialized address`() {
    val root = registry.buildTree("hex")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val step = traverser.traverse(0, 0)
    val address = Address.serialize(step.id)
    val located = locator.locate(address)

    assertNotNull(located)
    located!!
    assertEquals(step.region.name, located.region.name)
    assertTrue(located.sdf(located.centerX, located.centerZ) <= 0f)
  }

  @Test
  fun `should locate from traversal bytebuffer with position as length`() {
    val root = registry.buildTree("hex")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val step = traverser.traverse(96, -32)
    val located = locator.locate(step.id)

    assertNotNull(located)
    located!!
    assertEquals(step.region.name, located.region.name)
    assertTrue(located.sdf(located.centerX, located.centerZ) <= 0f)
  }

  @Test
  fun `should query tiling region as current tile from player context`() {
    val root = registry.buildTree("hex")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val tile = traverser.iterate(340, -260)
    tile.next()
    val tileCenterX = tile.centerX
    val tileCenterZ = tile.centerZ
    val step = traverser.traverse(340, -260)

    val located = locator.query(".hex", step.id)

    assertNotNull(located)
    located!!
    assertEquals("hex", located.region.name)
    assertEquals(tileCenterX, located.centerX)
    assertEquals(tileCenterZ, located.centerZ)
    assertTrue(located.distance < 0f)
    assertFalse(located.ambiguous)
    assertEquals(listOf("hex"), located.chain.map { it.name })
  }

  @Test
  fun `should report full qualifier chain with resolvable addresses`() {
    val root = registry.buildTree("hex")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val step = traverser.traverse(96, -32)
    val located = locator.query(".${step.region.name}", step.id)

    assertNotNull(located)
    located!!
    assertEquals(step.region.name, located.region.name)
    assertEquals(step.centerX, located.centerX)
    assertEquals(step.centerZ, located.centerZ)
    assertFalse(located.ambiguous)
    assertEquals(listOf("hex", "cell", step.region.name), located.chain.map { it.name })

    val tile = locator.query("#${located.chain[0].address}.hex")
    assertNotNull(tile)
    assertEquals("hex", tile!!.region.name)
    assertFalse(tile.ambiguous)

    val cell = locator.query("#${located.chain[1].address}.cell")
    assertNotNull(cell)
    assertEquals("cell", cell!!.region.name)
    assertFalse(cell.ambiguous)
  }

  @Test
  fun `should resolve immediate children anchored at a resolved region`() {
    val root = registry.buildTree("hex")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val step = traverser.traverse(96, -32)
    val cell = locator.query(".cell", step.id)

    assertNotNull(cell)
    cell!!
    val anchor = cell.chain.last()
    val children = cell.region.strategy!!.targets
    assertEquals(setOf("voronoi1", "voronoi2", "voronoi3"), children.map { it.name }.toSet())

    val addresses = mutableSetOf(anchor.address)
    for (child in children) {
      val resolved = locator.query("#${anchor.address}.${anchor.name} > .${child.name}", step.id)
      assertNotNull(resolved, "child ${child.name} did not resolve")
      resolved!!
      assertEquals(child.name, resolved.region.name)
      assertEquals(listOf("hex", "cell", child.name), resolved.chain.map { it.name })
      assertTrue(
        addresses.add(resolved.chain.last().address),
        "child ${child.name} id collides with its parent or a sibling",
      )
    }
  }

  @Test
  fun `should assign distinct roundtrippable ids to every chain level`() {
    val root = registry.buildTree("hex")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val step = traverser.traverse(96, -32)
    val located = locator.query(".${step.region.name}", step.id)

    assertNotNull(located)
    located!!
    val addresses = located.chain.map { it.address }
    assertEquals(addresses.size, addresses.toSet().size, "chain ids are not unique: $addresses")

    for (qualifier in located.chain) {
      val resolved = locator.query("#${qualifier.address}.${qualifier.name}")
      assertNotNull(resolved, "chain qualifier #${qualifier.address}.${qualifier.name} lost")
      resolved!!
      assertEquals(qualifier.name, resolved.region.name)
      assertEquals(qualifier.address, resolved.chain.last().address)
    }
  }

  @Test
  fun `should query sibling region from player context`() {
    val root = registry.buildTree("hex")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val step = traverser.traverse(96, -32)
    val sibling = setOf("voronoi1", "voronoi2", "voronoi3").first { it != step.region.name }
    val located = locator.query(".hex .cell > .$sibling", step.id)

    assertNotNull(located)
    located!!
    assertEquals(sibling, located.region.name)
    assertTrue(located.sdf(located.centerX, located.centerZ) <= 0f)
    assertFalse(located.ambiguous)
  }

  @Test
  fun `should query without context as ambiguous`() {
    val root = registry.buildTree("hex")
    val locator = Locator(SEED, root)

    val located = locator.query(".hex .cell > .voronoi1")

    assertNotNull(located)
    located!!
    assertEquals("voronoi1", located.region.name)
    assertTrue(located.ambiguous)
    assertTrue(located.sdf(located.centerX, located.centerZ) <= 0f)
  }

  @Test
  fun `should query by explicit id`() {
    val root = registry.buildTree("hex")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val step = traverser.traverse(0, 0)
    val address = Address.serialize(step.id)
    val located = locator.query("#$address")

    assertNotNull(located)
    located!!
    assertEquals(step.region.name, located.region.name)
    assertEquals(step.centerX, located.centerX)
    assertEquals(step.centerZ, located.centerZ)
  }

  @Test
  fun `should reject explicit id with mismatched name`() {
    val root = registry.buildTree("hex")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val step = traverser.traverse(0, 0)
    val address = Address.serialize(step.id)
    val wrongName = setOf("voronoi1", "voronoi2", "voronoi3").first { it != step.region.name }

    assertNull(locator.query("#$address.$wrongName"))
  }

  @Test
  fun `should return null for structurally impossible query`() {
    val root = registry.buildTree("hex")
    val locator = Locator(SEED, root)

    assertNull(locator.query(".hex > .voronoi1"))
    assertNull(locator.query(".unknown"))
  }

  @Test
  fun `should keep surround chain addresses stable across queries`() {
    val registry = RegionRegistry()
    registry.region("hex").radius(150).strategy(Strategy.hex())
    registry.region("cell").parent("hex").strategy(Strategy.voronoi())
    registry.region("voronoi1").radius(30).parent("cell")
    registry.region("voronoi3").radius(75).parent("cell").strategy(Strategy.surround("surround"))
    registry.region("surround").radius(50)
    registry.region("center").parent("voronoi3").radius(100)

    val root = registry.buildTree("hex")
    val locator = Locator(SEED, root)

    val located = locator.query(".center")

    assertNotNull(located)
    located!!
    assertEquals(listOf("hex", "cell", "voronoi3", "center"), located.chain.map { it.name })

    val addresses = located.chain.map { it.address }
    assertEquals(addresses.size, addresses.toSet().size, "chain ids are not unique: $addresses")

    for (qualifier in located.chain) {
      val resolved = locator.query("#${qualifier.address}.${qualifier.name}")
      assertNotNull(resolved, "chain qualifier #${qualifier.address}.${qualifier.name} lost")
      resolved!!
      assertEquals(qualifier.name, resolved.region.name)
      assertEquals(qualifier.address, resolved.chain.last().address)
    }
  }

  @Test
  fun `should return null for unknown strategy id`() {
    val root = registry.buildTree("hex")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val step = traverser.traverse(0, 0)
    val id = Address.deserialize(Address.serialize(step.id))
    id.put(0, Byte.MAX_VALUE)

    val located = locator.locate(id)
    assertNull(located)
  }
}
