package terrasect.definition

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SelectionConstraintsTest {

  @Test
  fun `empty constraints allow everything`() {
    val constraints = SelectionConstraints.builder().build()
    assertTrue(constraints.evaluate("minecraft:village", setOf("some_tag")))
    assertTrue(constraints.evaluate(null, null))
  }

  @Test
  fun `allow-list is exclusive - unlisted names are rejected`() {
    val constraints = SelectionConstraints.builder().allowNames("minecraft:village").build()
    assertTrue(constraints.evaluate("minecraft:village", emptySet()))
    assertFalse(constraints.evaluate("minecraft:pillager_outpost", emptySet()))
  }

  @Test
  fun `block-list rejects only the listed names`() {
    val constraints = SelectionConstraints.builder().blockNames("minecraft:village").build()
    assertFalse(constraints.evaluate("minecraft:village", emptySet()))
    assertTrue(constraints.evaluate("minecraft:pillager_outpost", emptySet()))
  }

  @Test
  fun `blocked name takes precedence over allowed name`() {
    val constraints =
      SelectionConstraints.builder()
        .allowNames("minecraft:village")
        .blockNames("minecraft:village")
        .build()
    assertFalse(constraints.evaluate("minecraft:village", emptySet()))
  }

  @Test
  fun `allowed name beats a blocked tag`() {
    val constraints =
      SelectionConstraints.builder().allowNames("minecraft:village").blockTags("village").build()
    assertTrue(constraints.evaluate("minecraft:village", setOf("village")))
  }

  @Test
  fun `blocked name beats an allowed tag`() {
    val constraints =
      SelectionConstraints.builder().blockNames("minecraft:village").allowTags("village").build()
    assertFalse(constraints.evaluate("minecraft:village", setOf("village")))
  }

  @Test
  fun `blocked tag takes precedence over allowed tag`() {
    val constraints = SelectionConstraints.builder().allowTags("village").blockTags("ruins").build()
    assertFalse(constraints.evaluate("minecraft:swamp_ruins", setOf("village", "ruins")))
  }

  @Test
  fun `tags are ignored when the caller passes null`() {
    val constraints = SelectionConstraints.builder().blockTags("village").build()
    assertTrue(constraints.evaluate("minecraft:village", null))
  }

  @Test
  fun `blocked tag beats an allowed mod`() {
    val constraints =
      SelectionConstraints.builder().blockTags("village").allowMods("minecraft").build()
    assertFalse(constraints.evaluate("minecraft:village", setOf("village")))
  }

  @Test
  fun `mod namespace is derived from the resource id`() {
    val constraints = SelectionConstraints.builder().blockMods("towntalk").build()
    assertFalse(constraints.evaluate("towntalk:market", emptySet()))
    assertTrue(constraints.evaluate("minecraft:village", emptySet()))
  }

  @Test
  fun `unnamespaced or null ids default to the minecraft namespace`() {
    assertEquals("minecraft", SelectionConstraints.extractNamespace(null))
    assertEquals("minecraft", SelectionConstraints.extractNamespace(""))
    assertEquals("minecraft", SelectionConstraints.extractNamespace("village"))
    assertEquals("towntalk", SelectionConstraints.extractNamespace("towntalk:market"))
  }

  @Test
  fun `allow-list mod is exclusive - other mods are rejected`() {
    val constraints = SelectionConstraints.builder().allowMods("towntalk").build()
    assertTrue(constraints.evaluate("towntalk:market", emptySet()))
    assertFalse(constraints.evaluate("minecraft:village", emptySet()))
  }

  @Test
  fun `allow rule at any level makes unmatched entries fail across levels`() {
    val constraints =
      SelectionConstraints.builder().allowNames("towntalk:market").allowMods("terrasect").build()
    assertTrue(constraints.evaluate("towntalk:market", emptySet()))
    assertTrue(constraints.evaluate("terrasect:shrine", emptySet()))
    assertFalse(constraints.evaluate("minecraft:village", emptySet()))
  }

  @Test
  fun `inheritParent unions allow and block sets rather than overriding`() {
    val parent = SelectionConstraints.builder().allowNames("minecraft:village").blockMods("bad_mod")
    val child =
      SelectionConstraints.builder().blockNames("minecraft:swamp_hut").inheritParent(parent).build()

    assertEquals(setOf("minecraft:village"), child.allowedNames)
    assertEquals(setOf("minecraft:swamp_hut"), child.blockedNames)
    assertEquals(setOf("bad_mod"), child.blockedMods)
  }
}
