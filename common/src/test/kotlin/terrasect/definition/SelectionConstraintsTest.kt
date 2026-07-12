package terrasect.definition

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SelectionConstraintsTest {

  private fun constraints(block: SelectionConstraints.Builder.() -> Unit) =
    SelectionConstraints.builder().apply(block).build()

  @Test
  fun `empty constraints allow everything`() {
    val c = constraints {}
    assertTrue(c.evaluate("minecraft:diamond", setOf("c:gems")))
    assertTrue(c.evaluate("modid:thing", emptySet()))
    assertTrue(c.evaluate(null, null))
  }

  @Test
  fun `allow-list is not exclusive - unlisted entries still pass by default`() {
    val c = constraints { allowNames("minecraft:diamond") }
    assertTrue(c.evaluate("minecraft:diamond", emptySet()))
    assertTrue(c.evaluate("minecraft:dirt", emptySet()))
  }

  @Test
  fun `blocked name is rejected, others pass`() {
    val c = constraints { blockNames("minecraft:tnt") }
    assertFalse(c.evaluate("minecraft:tnt", emptySet()))
    assertTrue(c.evaluate("minecraft:stone", emptySet()))
  }

  @Test
  fun `blocked tag rejects any entry carrying that tag`() {
    val c = constraints { blockTags("c:explosives") }
    assertFalse(c.evaluate("modid:dynamite", setOf("c:explosives", "c:tools")))
    assertTrue(c.evaluate("modid:hammer", setOf("c:tools")))
  }

  @Test
  fun `allowed tag alone does not exclude entries without it`() {
    val c = constraints { allowTags("c:gems") }
    assertTrue(c.evaluate("minecraft:diamond", setOf("c:gems")))
    assertTrue(c.evaluate("minecraft:dirt", setOf("c:blocks")))
  }

  @Test
  fun `blocked mod rejects the whole namespace`() {
    val c = constraints { blockMods("evilmod") }
    assertFalse(c.evaluate("evilmod:cursed", emptySet()))
    assertTrue(c.evaluate("minecraft:stone", emptySet()))
  }

  @Test
  fun `entries without a namespace are treated as minecraft`() {
    val blockVanilla = constraints { blockMods("minecraft") }
    assertFalse(blockVanilla.evaluate("stone", emptySet()))
    assertFalse(blockVanilla.evaluate(null, emptySet()))
    assertFalse(blockVanilla.evaluate("", emptySet()))
  }

  @Test
  fun `name allow beats a blocked tag`() {
    val c = constraints {
      allowNames("minecraft:diamond")
      blockTags("c:gems")
    }
    assertTrue(c.evaluate("minecraft:diamond", setOf("c:gems")))
    assertFalse(c.evaluate("minecraft:emerald", setOf("c:gems")))
  }

  @Test
  fun `blocked name beats an allowed tag`() {
    val c = constraints {
      blockNames("minecraft:diamond")
      allowTags("c:gems")
    }
    assertFalse(c.evaluate("minecraft:diamond", setOf("c:gems")))
    assertTrue(c.evaluate("minecraft:emerald", setOf("c:gems")))
  }

  @Test
  fun `blocked tag beats an allowed mod`() {
    val c = constraints {
      allowMods("minecraft")
      blockTags("c:gems")
    }
    assertFalse(c.evaluate("minecraft:diamond", setOf("c:gems")))
    assertTrue(c.evaluate("minecraft:stone", setOf("c:blocks")))
  }

  @Test
  fun `null tags skip tag checks and fall through to mod rules`() {
    val c = constraints {
      blockTags("c:gems")
      blockMods("evilmod")
    }
    assertTrue(c.evaluate("minecraft:diamond", null))
    assertFalse(c.evaluate("evilmod:cursed", null))
  }

  @Test
  fun `extractNamespace splits on first colon and defaults to minecraft`() {
    assertEquals("modid", SelectionConstraints.extractNamespace("modid:item"))
    assertEquals("minecraft", SelectionConstraints.extractNamespace("plainname"))
    assertEquals("minecraft", SelectionConstraints.extractNamespace(":leading"))
    assertEquals("minecraft", SelectionConstraints.extractNamespace(null))
    assertEquals("minecraft", SelectionConstraints.extractNamespace(""))
  }

  @Test
  fun `loot scenario - block a tag while permitting a specific override`() {
    val loot = constraints {
      blockTags("c:foods")
      allowNames("minecraft:golden_apple")
    }
    assertFalse(loot.evaluate("minecraft:bread", setOf("c:foods")))
    assertTrue(loot.evaluate("minecraft:golden_apple", setOf("c:foods")))
    assertTrue(loot.evaluate("minecraft:diamond", setOf("c:gems")))
  }

  @Test
  fun `mob scenario - block hostile namespace but allow a named exception`() {
    val mobs = constraints {
      blockTags("c:hostile")
      allowNames("minecraft:enderman")
    }
    assertFalse(mobs.evaluate("minecraft:zombie", setOf("c:hostile")))
    assertTrue(mobs.evaluate("minecraft:enderman", setOf("c:hostile")))
    assertTrue(mobs.evaluate("minecraft:cow", setOf("c:passive")))
  }

  @Test
  fun `inheritParent merges parent rules into child`() {
    val parent =
      SelectionConstraints.builder().blockNames("minecraft:tnt").blockTags("c:explosives")
    val child = SelectionConstraints.builder().allowNames("minecraft:diamond").inheritParent(parent)
    val c = child.build()

    assertFalse(c.evaluate("minecraft:tnt", emptySet()))
    assertFalse(c.evaluate("modid:dynamite", setOf("c:explosives")))
    assertTrue(c.evaluate("minecraft:diamond", emptySet()))
  }

  @Test
  fun `build produces an independent snapshot`() {
    val builder = SelectionConstraints.builder().blockNames("minecraft:tnt")
    val first = builder.build()
    builder.blockNames("minecraft:stone")
    assertFalse(first.blockedNames!!.contains("minecraft:stone"))
  }
}
