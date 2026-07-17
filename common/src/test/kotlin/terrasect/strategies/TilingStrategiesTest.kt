package terrasect.strategies

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
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
import terrasect.testing.writeSnapshotPng

private const val SEED = 1234L
private const val WIDTH = 240
private const val HEIGHT = 240
private const val DX = WIDTH / 2
private const val DZ = HEIGHT / 2

class TilingStrategiesTest {

  private fun tiledVoronoiRegistry(): RegionRegistry {
    val registry = RegionRegistry()
    registry.region("land").strategy(Strategy.voronoi().tiling())
    registry.region("meadow").parent("land").radius(30)
    registry.region("forest").parent("land").radius(20)
    registry.region("rocks").parent("land").radius(12)
    return registry
  }

  private fun nestedRegistry(): RegionRegistry {
    val registry = RegionRegistry()
    registry.region("world").radius(80).strategy(Strategy.hex())
    registry.region("cell").parent("world").radius(50).strategy(Strategy.voronoi().tiling())
    registry.region("meadow").parent("cell").radius(14)
    registry.region("forest").parent("cell").radius(10)
    return registry
  }

  private fun stripesRegistry(): RegionRegistry {
    val registry = RegionRegistry()
    registry.region("world").radius(80).strategy(Strategy.hex())
    registry.region("bands").parent("world").radius(50).strategy(Strategy.subdivision().tiling())
    registry.region("dune").parent("bands").radius(20)
    registry.region("oasis").parent("bands").radius(12)
    registry.region("ridge").parent("bands").radius(9)
    return registry
  }

  @Test
  fun `should tile voronoi as higher level voronoi grid`() {
    val root = tiledVoronoiRegistry().buildTree("land")
    val traverser = Traverser(SEED, root)

    val regions = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val distances = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val seen = mutableSetOf<String>()

    for (x in 0 until WIDTH) {
      for (z in 0 until HEIGHT) {
        val step = traverser.traverse(x - DX, z - DZ)
        seen += step.region.name
        regions.setRGB(
          x,
          z,
          distanceColor(step.distance, insideColor = null) ?: pathColor(idHash(step.id)),
        )
        distances.setRGB(x, z, colorForSignedDistance(step.distance, 20f))
      }
    }

    writeSnapshotPng(TilingStrategiesTest::class.java, "voronoi-tiled-regions.png", regions)
    writeSnapshotPng(TilingStrategiesTest::class.java, "voronoi-tiled-distances.png", distances)
    assertEquals(setOf("meadow", "forest", "rocks"), seen)
  }

  @Test
  fun `should clip tiled voronoi inside hex parent`() {
    val root = nestedRegistry().buildTree("world")
    val traverser = Traverser(SEED, root)

    val regions = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    for (x in 0 until WIDTH) {
      for (z in 0 until HEIGHT) {
        val step = traverser.traverse(x - DX, z - DZ)
        regions.setRGB(
          x,
          z,
          distanceColor(step.distance, insideColor = null) ?: pathColor(idHash(step.id)),
        )
      }
    }

    writeSnapshotPng(TilingStrategiesTest::class.java, "voronoi-tiled-in-hex.png", regions)
  }

  @Test
  fun `should keep tiled child at own budget instead of stretching`() {
    val root = nestedRegistry().buildTree("world")
    val traverser = Traverser(SEED, root)

    val instances = mutableSetOf<String>()
    for (x in 0 until WIDTH step 4) {
      for (z in 0 until HEIGHT step 4) {
        val step = traverser.traverse(x - DX, z - DZ)
        instances += terrasect.generation.Address.serialize(step.id)
      }
    }

    // A stretched child would produce one instance per hex cell; tiling must produce many.
    assertTrue(instances.size > 20, "expected many tiled instances, got ${instances.size}")
  }

  @Test
  fun `should tile subdivision as repeating stripes`() {
    val root = stripesRegistry().buildTree("world")
    val traverser = Traverser(SEED, root)

    val regions = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val seen = mutableSetOf<String>()
    for (x in 0 until WIDTH) {
      for (z in 0 until HEIGHT) {
        val step = traverser.traverse(x - DX, z - DZ)
        seen += step.region.name
        regions.setRGB(
          x,
          z,
          distanceColor(step.distance, insideColor = null) ?: pathColor(idHash(step.id)),
        )
      }
    }

    writeSnapshotPng(TilingStrategiesTest::class.java, "subdivision-tiled-stripes.png", regions)
    assertEquals(setOf("dune", "oasis", "ridge"), seen)
  }

  @Test
  fun `should locate tiled instances from their id`() {
    for (registry in listOf(tiledVoronoiRegistry(), nestedRegistry(), stripesRegistry())) {
      val name = registry.dimensionRoots.values.firstOrNull() ?: registry.drafts.keys.first()
      val root = registry.buildTree(if ("world" in registry.drafts) "world" else name)
      val traverser = Traverser(SEED, root)
      val locator = Locator(SEED, root)

      for (x in -DX until DX step 20) {
        for (z in -DZ until DZ step 20) {
          val step = traverser.traverse(x, z)
          val result = locator.locate(step.id)
          assertNotNull(result, "no locate result at $x,$z")
          assertEquals(step.region.name, result!!.region.name, "region mismatch at $x,$z")
          assertEquals(step.centerX, result.centerX, "centerX mismatch at $x,$z")
          assertEquals(step.centerZ, result.centerZ, "centerZ mismatch at $x,$z")
        }
      }
    }
  }

  @Test
  fun `should resolve tiled children by selector`() {
    val root = tiledVoronoiRegistry().buildTree("land")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val context = traverser.traverse(0, 0).id
    for (child in listOf("meadow", "forest", "rocks")) {
      val result = locator.query(".$child", context)
      assertNotNull(result, "selector .$child did not resolve")
      assertEquals(child, result!!.region.name)
      assertTrue(result.sdf(result.centerX, result.centerZ) <= 0f, ".$child center escaped tile")
    }
  }

  private fun idHash(id: ByteBuffer): Int {
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
