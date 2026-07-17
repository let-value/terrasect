package terrasect.strategies

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy
import terrasect.generation.Locator
import terrasect.generation.Traverser
import terrasect.sdf.*
import terrasect.testing.writeSnapshotPng

private const val SEED = 1234L
private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2
private const val CZ = HEIGHT / 2

private const val SEA_COLOR = 0xFF16324C.toInt()
private val ISLAND_COLORS =
  intArrayOf(0xFF3FA65C.toInt(), 0xFFC7A44A.toInt(), 0xFFB4573A.toInt(), 0xFF7A5FA8.toInt())

class ArchipelagoStrategyTest {

  private val budgets = longArrayOf(900, 500, 300)

  @Test
  fun `should render archipelago in circle`() {
    val radius = 100f
    val sdf: Sdf2 = translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - radius }, CX, CZ)
    renderArchipelago(sdf, "circle-islands.png")
  }

  @Test
  fun `should render archipelago in hex`() {
    val apothem = 100f
    val sdf: Sdf2 = translate({ x, z -> hexDistance(x, z, apothem) }, CX, CZ)
    renderArchipelago(sdf, "hex-islands.png")
  }

  @Test
  fun `should render archipelago in banana`() {
    val sdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)
    renderArchipelago(sdf, "banana-islands.png")
  }

  private fun renderArchipelago(parentSdf: Sdf2, snapshotName: String) {
    val placed = ArchipelagoStrategy.getIslands(SEED.toInt(), parentSdf, budgets)

    assertTrue(placed.isNotEmpty(), "no islands placed for $snapshotName")
    for (entry in placed) {
      val island = entry.island
      assertTrue(
        parentSdf(island.centerX, island.centerZ) < 0f,
        "$snapshotName island center (${island.centerX}, ${island.centerZ}) escaped parent",
      )
    }

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    for (z in 0 until HEIGHT) {
      for (x in 0 until WIDTH) {
        if (parentSdf(x, z) > 0f) {
          continue
        }
        val slot = ArchipelagoStrategy.islandSlotAt(x, z, placed)
        val color =
          if (slot >= 0) ISLAND_COLORS[placed[slot].childIndex % ISLAND_COLORS.size] else SEA_COLOR
        image.setRGB(x, z, color)
      }
    }
    for (entry in placed) {
      val clipped: Sdf2 = { x, z -> maxOf(parentSdf(x, z), entry.island(x, z)) }
      drawSdf(image, clipped, insideColor = null)
    }
    drawSdf(image, parentSdf, insideColor = null)
    writeSnapshotPng(ArchipelagoStrategyTest::class.java, snapshotName, image)
  }

  private fun traversalRegistry(scale: Long = 1): RegionRegistry {
    val registry = RegionRegistry()
    registry.region("world").radius(110 * scale).strategy(Strategy.hex())
    registry
      .region("ocean")
      .parent("world")
      .radius(100 * scale)
      .strategy(Strategy.archipelago("sea"))
    registry.region("volcano").parent("ocean").radius(24 * scale)
    registry.region("atoll").parent("ocean").radius(17 * scale)
    registry.region("skerry").parent("ocean").radius(12 * scale)
    registry.region("sea").budget(500 * scale * scale)
    return registry
  }

  @Test
  fun `should traverse archipelago inside hex world`() {
    val root = traversalRegistry().buildTree("world")
    val traverser = Traverser(SEED, root)

    val regions = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val seen = mutableSetOf<String>()
    for (x in 0 until WIDTH) {
      for (z in 0 until HEIGHT) {
        val step = traverser.traverse(x - CX, z - CZ)
        seen += step.region.name
        val color =
          distanceColor(step.distance, insideColor = null)
            ?: if (step.region.name == "sea") SEA_COLOR else pathColor(idHash(step.id))
        regions.setRGB(x, z, color)
      }
    }

    writeSnapshotPng(ArchipelagoStrategyTest::class.java, "traversal-in-hex.png", regions)
    assertTrue("sea" in seen, "traversal never reached the sea")
    assertTrue(seen.any { it != "sea" && it != "world" }, "traversal never reached an island")
  }

  @Test
  fun `should keep island area near its budget`() {
    // Islands must dwarf the estimateBounds sampling cell for the area estimate to be meaningful.
    val root = traversalRegistry(scale = 4).buildTree("world")
    val traverser = Traverser(SEED, root)

    var checked = 0
    val visited = mutableSetOf<String>()
    for (x in -CX * 4 until CX * 4 step 48) {
      for (z in -CZ * 4 until CZ * 4 step 48) {
        val step = traverser.traverse(x, z)
        if (step.region.name == "sea") {
          continue
        }
        val key = terrasect.generation.Address.serialize(step.id)
        if (!visited.add(key)) {
          continue
        }

        val sdf = step.sdf.bake()
        val bounds = estimateBounds(sdf, step.centerX, step.centerZ)
        val area = estimateArea(sdf, bounds).toDouble()
        val budget = step.region.budget.toDouble()
        assertTrue(
          area > budget * 0.3 && area < budget * 2.5,
          "island ${step.region.name} area=$area budget=$budget",
        )
        checked++
      }
    }

    assertTrue(checked >= 3, "expected to sample several islands, got $checked")
  }

  @Test
  fun `should locate island and sea instances from their id`() {
    val root = traversalRegistry().buildTree("world")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    for (x in -CX until CX step 16) {
      for (z in -CZ until CZ step 16) {
        val step = traverser.traverse(x, z)
        val result = locator.locate(step.id)
        assertNotNull(result, "no locate result at $x,$z")
        assertEquals(step.region.name, result!!.region.name, "region mismatch at $x,$z")
        assertEquals(step.centerX, result.centerX, "centerX mismatch at $x,$z")
        assertTrue(abs(result.sdf(x, z) - step.sdf(x, z)) < 1e-3, "sdf mismatch at $x,$z")
      }
    }
  }

  @Test
  fun `should resolve islands and sea by selector`() {
    val root = traversalRegistry().buildTree("world")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val context = traverser.traverse(0, 0).id
    for (child in listOf("volcano", "atoll", "skerry", "sea")) {
      val result = locator.query(".$child", context)
      assertNotNull(result, "selector .$child did not resolve")
      assertEquals(child, result!!.region.name)
    }
  }

  private fun idHash(id: java.nio.ByteBuffer): Int {
    var hash = 1
    for (i in 0 until id.position()) {
      hash = 31 * hash + id[i]
    }
    return hash
  }

  private fun pathColor(idHash: Int): Int {
    var h = idHash
    h = h xor (h ushr 16)
    h *= 0x7feb352d
    h = h xor (h ushr 15)
    h *= 0x846ca68b.toInt()
    h = h xor (h ushr 16)

    val hue = ((h ushr 1) and 0x3FF) / 1024f
    val saturation = 0.62f + (((h ushr 11) and 0x1F) / 255f)
    val brightness = 0.58f + (((h ushr 16) and 0x3F) / 255f)

    return Color.HSBtoRGB(hue, saturation.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f))
  }
}
