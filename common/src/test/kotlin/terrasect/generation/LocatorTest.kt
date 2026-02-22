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
