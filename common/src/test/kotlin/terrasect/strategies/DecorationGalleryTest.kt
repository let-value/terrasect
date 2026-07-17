package terrasect.strategies

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy
import terrasect.generation.Locator
import terrasect.generation.Traverser
import terrasect.sdf.Decoration
import terrasect.sdf.SiteMetric
import terrasect.sdf.distanceColor
import terrasect.testing.writeSnapshotPng

private const val SEED = 1234L
private const val WIDTH = 240
private const val HEIGHT = 240
private const val DX = WIDTH / 2
private const val DZ = HEIGHT / 2

class DecorationGalleryTest {

  private fun voronoiRegistry(vararg decorations: Decoration): RegionRegistry {
    val registry = RegionRegistry()
    val land = registry.region("land").strategy(Strategy.voronoi().tiling())
    decorations.forEach(land::decoration)
    registry.region("meadow").parent("land").radius(30)
    registry.region("forest").parent("land").radius(20)
    registry.region("rocks").parent("land").radius(12)
    return registry
  }

  private fun hexRegistry(vararg decorations: Decoration): RegionRegistry {
    val registry = RegionRegistry()
    val comb = registry.region("comb").strategy(Strategy.hex())
    decorations.forEach(comb::decoration)
    registry.region("cell").parent("comb").radius(26)
    return registry
  }

  private fun render(registry: RegionRegistry, rootName: String, file: String) {
    val root = registry.buildTree(rootName)
    val traverser = Traverser(SEED, root)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    for (x in 0 until WIDTH) {
      for (z in 0 until HEIGHT) {
        val step = traverser.traverse(x - DX, z - DZ)
        image.setRGB(
          x,
          z,
          distanceColor(step.distance, insideColor = null) ?: pathColor(idHash(step.id)),
        )
      }
    }
    writeSnapshotPng(DecorationGalleryTest::class.java, file, image)
  }

  @Test
  fun `gallery of decorations on tiled voronoi`() {
    render(voronoiRegistry(), "land", "base-voronoi.png")
    render(voronoiRegistry(Decoration.warp(12f, 48f)), "land", "warp.png")
    render(voronoiRegistry(Decoration.dither(4f, 6f)), "land", "dither.png")
    render(voronoiRegistry(Decoration.swirl(1.4f, 130f)), "land", "swirl.png")
    render(voronoiRegistry(Decoration.ripple(7f, 52f)), "land", "ripple.png")
    render(voronoiRegistry(Decoration.shear(x = 0.6f)), "land", "shear.png")
    render(voronoiRegistry(Decoration.terrace(9f)), "land", "terrace.png")
    render(voronoiRegistry(Decoration.gap(4f)), "land", "gap.png")
    render(voronoiRegistry(Decoration.onion(5f)), "land", "onion.png")
    render(voronoiRegistry(Decoration.stripes(16f, 6f, 30f)), "land", "stripes.png")
    render(voronoiRegistry(Decoration.rings(16f, 8f)), "land", "rings.png")
  }

  @Test
  fun `gallery of decorations on hex`() {
    render(hexRegistry(), "comb", "hex-base.png")
    render(hexRegistry(Decoration.warp(8f, 40f)), "comb", "hex-warp.png")
    render(hexRegistry(Decoration.dither(3f, 5f)), "comb", "hex-dither.png")
  }

  @Test
  fun `gallery of strategy specific looks`() {
    val rounded = RegionRegistry()
    rounded.region("comb").strategy(Strategy.hex(null).rounding(10f))
    rounded.region("cell").parent("comb").radius(26)
    render(rounded, "comb", "hex-rounded.png")

    for (metric in listOf(SiteMetric.MANHATTAN, SiteMetric.CHEBYSHEV)) {
      val registry = RegionRegistry()
      registry.region("land").strategy(Strategy.voronoi().tiling().metric(metric))
      registry.region("meadow").parent("land").radius(30)
      registry.region("forest").parent("land").radius(20)
      registry.region("rocks").parent("land").radius(12)
      render(registry, "land", "voronoi-${metric.name.lowercase()}.png")
    }
  }

  @Test
  fun `gallery of stacked decorations in nested regions`() {
    val registry = RegionRegistry()
    registry
      .region("world")
      .radius(80)
      .strategy(Strategy.hex())
      .decoration(Decoration.warp(10f, 60f))
    registry
      .region("cell")
      .parent("world")
      .radius(50)
      .strategy(Strategy.voronoi().tiling())
      .decoration(Decoration.dither(3f, 5f))
    registry.region("meadow").parent("cell").radius(14)
    registry.region("forest").parent("cell").radius(10)
    render(registry, "world", "stacked-warp-dither.png")
  }

  @Test
  fun `decorated traverse and locate stay consistent`() {
    val registries =
      listOf(
        voronoiRegistry(Decoration.warp(12f, 48f)),
        voronoiRegistry(Decoration.swirl(1.4f, 130f), Decoration.gap(4f)),
        voronoiRegistry(Decoration.ripple(7f, 52f), Decoration.onion(5f)),
        hexRegistry(Decoration.warp(8f, 40f)),
      )
    for (registry in registries) {
      val rootName = if ("land" in registry.drafts) "land" else "comb"
      val root = registry.buildTree(rootName)
      val traverser = Traverser(SEED, root)
      val locator = Locator(SEED, root)

      for (x in -DX until DX step 17) {
        for (z in -DZ until DZ step 17) {
          val step = traverser.traverse(x, z)
          val distance = step.distance
          val result = locator.locate(step.id)
          assertNotNull(result, "no locate result at $x,$z")
          assertEquals(step.region.name, result!!.region.name, "region mismatch at $x,$z")
          assertEquals(step.centerX, result.centerX, "centerX mismatch at $x,$z")
          assertEquals(step.centerZ, result.centerZ, "centerZ mismatch at $x,$z")
          assertEquals(distance, result.sdf(x, z), 0.001f, "distance mismatch at $x,$z")
        }
      }
    }
  }

  @Test
  fun `decorated selector resolution finds children`() {
    val root = voronoiRegistry(Decoration.warp(12f, 48f)).buildTree("land")
    val traverser = Traverser(SEED, root)
    val locator = Locator(SEED, root)

    val context = traverser.traverse(0, 0).id
    for (child in listOf("meadow", "forest", "rocks")) {
      val result = locator.query(".$child", context)
      assertNotNull(result, "selector .$child did not resolve")
      assertEquals(child, result!!.region.name)
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
