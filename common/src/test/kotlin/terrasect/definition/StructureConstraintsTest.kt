package terrasect.definition

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StructureConstraintsTest {

  @Test
  fun `selection is null until a selection rule is added`() {
    assertNull(StructureConstraints.builder().build().selection)
    assertNull(StructureConstraints.builder().spacing(32).build().selection)
  }

  @Test
  fun `selection rules populate the underlying SelectionConstraints`() {
    val c =
      StructureConstraints.builder()
        .allowNames("minecraft:village_plains")
        .blockTags("c:ruins")
        .build()

    assertNotNull(c.selection)
    assertEquals(setOf("minecraft:village_plains"), c.selection!!.allowedNames)
    assertEquals(setOf("c:ruins"), c.selection!!.blockedTags)
  }

  @Test
  fun `placement overrides are carried through the builder`() {
    val c = StructureConstraints.builder().spacing(40).separation(12).frequency(0.5f).build()
    assertEquals(40, c.spacing)
    assertEquals(12, c.separation)
    assertEquals(0.5f, c.frequency)
  }

  @Test
  fun `inheritParent fills unset placement fields but keeps child overrides`() {
    val parent = StructureConstraints.builder().spacing(40).separation(12).frequency(0.5f)
    val child = StructureConstraints.builder().spacing(20).inheritParent(parent).build()

    assertEquals(20, child.spacing)
    assertEquals(12, child.separation)
    assertEquals(0.5f, child.frequency)
  }

  @Test
  fun `inheritParent merges parent selection when the child has none of its own`() {
    val parent = StructureConstraints.builder().allowNames("minecraft:village_plains")
    val child = StructureConstraints.builder().inheritParent(parent).build()

    assertNotNull(child.selection)
    assertEquals(setOf("minecraft:village_plains"), child.selection!!.allowedNames)
  }

  @Test
  fun `inheritParent from a selectionless parent leaves selection null`() {
    val parent = StructureConstraints.builder().spacing(40)
    val child = StructureConstraints.builder().inheritParent(parent).build()
    assertNull(child.selection)
  }

  @Test
  fun `child and parent selection rules combine`() {
    val parent = StructureConstraints.builder().blockTags("c:ruins")
    val child =
      StructureConstraints.builder().allowNames("minecraft:village_plains").inheritParent(parent)
    val selection = child.build().selection!!

    assertFalse(selection.evaluate("modid:ruin", setOf("c:ruins")))
    assertTrue(selection.evaluate("minecraft:village_plains", emptySet()))
  }
}
