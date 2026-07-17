package terrasect.strategies

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy
import terrasect.generation.Locator
import terrasect.generation.Traverser
import terrasect.sdf.colorForSignedDistance
import terrasect.sdf.distanceColor
import terrasect.sdf.estimateArea
import terrasect.sdf.estimateBounds
import terrasect.testing.writeSnapshotPng

private const val SEED = 1234L
private const val WIDTH = 240
private const val HEIGHT = 240
private const val DX = WIDTH / 2
private const val DZ = HEIGHT / 2

private const val SEA_COLOR = 0xFF16324C.toInt()

class ArchipelagoStrategyTest {

  private fun archipelagoRegistry(scale: Long = 1): RegionRegistry {
    val registry = RegionRegistry()
    registry.region("ocean").strategy(Strategy.archipelago("sea"))
    registry.region("volcano").parent("ocean").radius(22 * scale)
    registry.region("atoll").parent("ocean").radius(16 * scale)
    registry.region("skerry").parent("ocean").radius(10 * scale)
    registry.region("sea").budget(500 * scale * scale)
    return registry
  }

  @Test
  fun `should render archipelago islands and sea`() {
    val root = archipelagoRegistry().buildTree("ocean")
    val traverser = Traverser(SEED, root)

    val regions = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val distances = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val seen = mutableSetOf<String>()

    for (x in 0 until WIDTH) {
      for (z in 0 until HEIGHT) {
        val step = traverser.traverse(x - DX, z - DZ)
        seen += step.region.name
        val color =
          distanceColor(step.distance, insideColor = null)
            ?: if (step.region.name == "sea") SEA_COLOR else pathColor(idHash(step.id))
        regions.setRGB(x, z, color)
        distances.setRGB(x, z, colorForSignedDistance(step.distance, 20f))
      }
    }

    writeSnapshotPng(ArchipelagoStrategyTest::class.java, "islands-and-sea.png", regions)
    writeSnapshotPng(ArchipelagoStrategyTest::class.java, "distances.png", distances)
    assertEquals(setOf("volcano", "atoll", "skerry", "sea"), seen)
  }

  @Test
  fun `should clip archipelago inside hex parent`() {
    val registry = archipelagoRegistry()
    registry.region("world").radius(80).strategy(Strategy.hex())
    registry.region("ocean").parent("world")
    val root = registry.buildTree("world")
    val traverser = Traverser(SEED, root)

    val regions = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    for (x in 0 until WIDTH) {
      for (z in 0 until HEIGHT) {
        val step = traverser.traverse(x - DX, z - DZ)
        val color =
          distanceColor(step.distance, insideColor = null)
            ?: if (step.region.name == "sea") SEA_COLOR else pathColor(idHash(step.id))
        regions.setRGB(x, z, color)
      }
    }

    writeSnapshotPng(ArchipelagoStrategyTest::class.java, "islands-in-hex.png", regions)
  }

  @Test
  fun `should keep island area near its budget`() {
    // Islands must dwarf the estimateBounds sampling cell for the area estimate to be meaningful.
    val root = archipelagoRegistry(scale = 4).buildTree("ocean")
    val traverser = Traverser(SEED, root)

    var checked = 0
    val visited = mutableSetOf<String>()
    for (x in -DX * 4 until DX * 4 step 48) {
      for (z in -DZ * 4 until DZ * 4 step 48) {
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
    val root = archipelagoRegistry().buildTree("ocean")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    for (x in -DX until DX step 16) {
      for (z in -DZ until DZ step 16) {
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
    val root = archipelagoRegistry().buildTree("ocean")
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
