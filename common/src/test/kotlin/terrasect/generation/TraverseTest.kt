package terrasect.generation

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import terrasect.cache.Cache
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy
import terrasect.sdf.*
import terrasect.strategies.VoronoiStrategy
import terrasect.testing.writeSnapshotPng
import java.awt.image.BufferedImage

private const val SEED = 1234L
private const val WIDTH = 240
private const val HEIGHT = 240
private const val DX = WIDTH / 2
private const val DZ = HEIGHT / 2

class TraverseTest {
  companion object {
    val registry = RegionRegistry()

    @BeforeAll
    @JvmStatic
    fun setup() {
      registry.region("hex").area(150).strategy(Strategy.hex().tiling())
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

    val traverse = Traverse(SEED, root)
    val step = traverse.iterate(0, 0)
    renderSnapshot("step0.png", step.sdf)

    step.next()
    renderSnapshot("step1.png", step.sdf)

    SdfCompose().let { sdf ->
      val voronoi = VoronoiCellSdf()
      sdf.append(step.sdf)
      sdf.append(voronoi)

      val cellSeed = VoronoiStrategy.getCellSeed(step.traverse.seed, step.id)
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
      writeSnapshotPng(TraverseTest::class.java, "step1_5.png", image)
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
    val traverse = Traverse(SEED, root)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)

    for (x in 0 until image.width) {
      for (z in 0 until image.height) {
        val step = traverse.traverse(x - DX, z - DZ, cache)
        val color = distanceColor(step.distance)
        if (color != null) {
          image.setRGB(x, z, color)
        }
      }
    }

    writeSnapshotPng(TraverseTest::class.java, "cached.png", image)
  }

  private fun renderSnapshot(name: String, sdf: Sdf2) {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawSdf(image, translate(sdf, DX, DZ))
    writeSnapshotPng(TraverseTest::class.java, name, image)
  }
}
