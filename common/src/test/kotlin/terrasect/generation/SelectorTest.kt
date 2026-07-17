package terrasect.generation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SelectorTest {
  @Test
  fun `should parse name qualifier`() {
    val parts = Selector.parse(".hex")
    assertEquals(listOf(SelectorPart(null, "hex", false)), parts)
  }

  @Test
  fun `should parse id with name qualifier`() {
    val parts = Selector.parse("#3vQB7B.hex")
    assertEquals(listOf(SelectorPart("3vQB7B", "hex", false)), parts)
  }

  @Test
  fun `should parse descendant and child combinators`() {
    val parts = Selector.parse(".hex .cell > .segment")
    assertEquals(
      listOf(
        SelectorPart(null, "hex", false),
        SelectorPart(null, "cell", false),
        SelectorPart(null, "segment", true),
      ),
      parts,
    )
  }

  @Test
  fun `should parse child combinator without spaces`() {
    val parts = Selector.parse(".hex>.cell")
    assertEquals(
      listOf(SelectorPart(null, "hex", false), SelectorPart(null, "cell", true)),
      parts,
    )
  }

  @Test
  fun `should parse id only qualifier`() {
    val parts = Selector.parse("#3vQB7B")
    assertEquals(listOf(SelectorPart("3vQB7B", null, false)), parts)
  }

  @Test
  fun `should reject malformed queries`() {
    assertTrue(Selector.parse("").isEmpty())
    assertTrue(Selector.parse(">").isEmpty())
    assertTrue(Selector.parse(".hex >").isEmpty())
    assertTrue(Selector.parse("> .hex").isEmpty())
    assertTrue(Selector.parse(".hex > > .cell").isEmpty())
    assertTrue(Selector.parse("hex").isEmpty())
  }
}
