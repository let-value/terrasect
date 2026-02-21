package terrasect.generation

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.cache.Cache
import terrasect.definition.Region
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy
import terrasect.sdf.*
import terrasect.strategies.VoronoiStrategy
import terrasect.testing.writeSnapshotPng
import java.awt.Color
import java.awt.image.BufferedImage

private const val SEED = 1234L
private const val WIDTH = 240
private const val HEIGHT = 240
private const val DX = WIDTH / 2
private const val DZ = HEIGHT / 2

class TraverserTest {
  companion object {
    val registry = RegionRegistry()

    init {
      registry.region("hex").area(150).strategy(Strategy.hex())
      registry.region("cell").parent("hex").strategy(Strategy.voronoi())

      registry.region("voronoi1").area(30).parent("cell")
      registry.region("voronoi2").area(45).parent("cell")

      registry.region("voronoi3").area(75).parent("cell").strategy(Strategy.surround("surround"))

      registry.region("surround").area(50)
      registry.region("center").parent("voronoi3").area(100)
    }
  }

  @Test
  fun `should iterate`() {
    val root = registry.buildTree("hex")

    val traverse = Traverser(SEED, root)
    val step = traverse.iterate(0, 0)
    renderSnapshot("step0.png", step.sdf)

    step.next()
    renderSnapshot("step1.png", step.sdf)

    SdfCompose().let { sdf ->
      val voronoi = VoronoiCellSdf()
      sdf.append(step.sdf)
      sdf.append(voronoi)

      val cellSeed = VoronoiStrategy.getCellSeed(step.traverser.seed, step.id)
      val sites =
          VoronoiStrategy.getSites(
              cellSeed,
              step.sdf,
              (step.region.strategy as VoronoiStrategy).budgets,
          )
      voronoi.sites = sites

      val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
      drawSdf(image, translate(step.sdf, DX, DZ))
      for (index in sites.indices) {
        voronoi.index = index
        drawSdf(image, translate(sdf, DX, DZ))
      }
      drawSites(image, sites.map { site -> Site(site.x + DX, site.z + DZ, site.radius) })
      writeSnapshotPng(TraverserTest::class.java, "step1_5.png", image)
    }

    step.next()
    renderSnapshot("step2.png", step.sdf)

    step.next()
    renderSnapshot("step3.png", step.sdf)
  }

  @Test
  fun `should iterate with cache`() {
    val root = registry.buildTree("hex")
    val cache = Cache()
    val traverse = Traverser(SEED, root)

    val borders = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val distances = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val regions = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)

    for (x in 0 until borders.width) {
      for (z in 0 until borders.height) {
        val step = traverse.traverse(x - DX, z - DZ, cache)
        val borderColor = distanceColor(step.distance)
        assertNotNull(borderColor, "$x, $z: ${step.distance}")
        val pathColor = traversalPathColor(traversalIdHash(step.id))

        borders.setRGB(x, z, borderColor!!)
        distances.setRGB(x, z, colorForSignedDistance(step.distance, 20f))
        regions.setRGB(x, z, pathColor)
      }
    }

    writeSnapshotPng(TraverserTest::class.java, "borders.png", borders)
    writeSnapshotPng(TraverserTest::class.java, "distances.png", distances)
    writeSnapshotPng(TraverserTest::class.java, "regions.png", regions)
  }

  @Test
  fun `should keep nested traversal inside parent bounds with cache`() {
    val root = registry.buildTree("hex")
    val cache = Cache()
    val traverser = Traverser(SEED, root)
    val maxDepth = maxStrategyDepth(root)

    var parentDistances: Array<FloatArray>? = null
    val epsilon = 1e-4f

    for (depth in 1..maxDepth) {
      val currentDistances = Array(WIDTH) { FloatArray(HEIGHT) }

      for (x in 0 until WIDTH) {
        for (z in 0 until HEIGHT) {
          val step = traverser.iterate(x - DX, z - DZ, cache)
          traverseToDepth(step, depth)
          currentDistances[x][z] = step.distance
          val parentDistance = parentDistances?.get(x)?.get(z) ?: continue
          assertTrue(
              step.distance + epsilon >= parentDistance,
              "distance violated parent bounds at $x,$z depth=$depth parent=$parentDistance child=${step.distance}",
          )
        }
      }

      parentDistances = currentDistances
    }
  }

  @Test
  fun `should traverse`() {
    val root = registry.buildTree("hex")
    val traverse = Traverser(SEED, root)

    val step = traverse.traverse(0, 0)
    renderSnapshot("traverse.png", step.sdf)
  }

  private fun renderSnapshot(name: String, sdf: Sdf2) {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawSdf(image, translate(sdf, DX, DZ))
    writeSnapshotPng(TraverserTest::class.java, name, image)
  }

  private fun traverseToDepth(step: TraversalStep, depth: Int): TraversalStep {
    var current = step
    repeat(depth) {
      val next = current.next() ?: return current
      current = next
    }
    return current
  }

  private fun maxStrategyDepth(region: Region): Int {
    if (!region.hasChildren) {
      return 0
    }
    return 1 + region.children.maxOf { maxStrategyDepth(it) }
  }

  private fun traversalIdHash(id: java.nio.ByteBuffer): Int {
    val len = id.position()
    var hash = 1
    for (i in 0 until len) {
      hash = 31 * hash + id[i]
    }
    return hash
  }

  private fun traversalPathColor(idHash: Int): Int {
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
