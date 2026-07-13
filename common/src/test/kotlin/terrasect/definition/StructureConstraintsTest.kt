package terrasect.definition

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StructureConstraintsTest {

  @Test
  fun `no configuration yields all-null constraints`() {
    val constraints = StructureConstraints.builder().build()
    assertNull(constraints.selection)
    assertNull(constraints.spacing)
    assertNull(constraints.separation)
    assertNull(constraints.frequency)
  }

  @Test
  fun `any selection call builds a non-null selection`() {
    val constraints = StructureConstraints.builder().blockNames("minecraft:village").build()
    assertNotNull(constraints.selection)
    assertEquals(setOf("minecraft:village"), constraints.selection!!.blockedNames)
  }

  @Test
  fun `placement overrides are independent of selection`() {
    val constraints =
      StructureConstraints.builder().spacing(20).separation(5).frequency(0.5f).build()
    assertNull(constraints.selection)
    assertEquals(20, constraints.spacing)
    assertEquals(5, constraints.separation)
    assertEquals(0.5f, constraints.frequency)
  }

  @Test
  fun `child overrides parent placement values`() {
    val parent = StructureConstraints.builder().spacing(20).separation(5).frequency(0.5f)
    val child = StructureConstraints.builder().spacing(40).inheritParent(parent).build()

    assertEquals(40, child.spacing)
    assertEquals(5, child.separation)
    assertEquals(0.5f, child.frequency)
  }

  @Test
  fun `child inherits parent placement values entirely when unset`() {
    val parent = StructureConstraints.builder().spacing(20).separation(5).frequency(0.5f)
    val child = StructureConstraints.builder().inheritParent(parent).build()

    assertEquals(20, child.spacing)
    assertEquals(5, child.separation)
    assertEquals(0.5f, child.frequency)
  }

  @Test
  fun `child selection inherits and unions with parent selection`() {
    val parent = StructureConstraints.builder().allowNames("minecraft:village")
    val child =
      StructureConstraints.builder().blockNames("minecraft:swamp_hut").inheritParent(parent).build()

    assertNotNull(child.selection)
    assertEquals(setOf("minecraft:village"), child.selection!!.allowedNames)
    assertEquals(setOf("minecraft:swamp_hut"), child.selection!!.blockedNames)
  }

  @Test
  fun `child without any selection call stays selection-free even if parent has one`() {
    // inheritParent only pulls in the parent's selection when the parent actually configured one;
    // it should not force every child into having a (trivially empty) SelectionConstraints.
    val parentNoSelection = StructureConstraints.builder().spacing(20)
    val child = StructureConstraints.builder().inheritParent(parentNoSelection).build()
    assertNull(child.selection)
  }
}
