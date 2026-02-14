package terrasect.generation

import org.junit.jupiter.api.Test
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy
import terrasect.sdf.*
import terrasect.strategies.VoronoiStrategy
import terrasect.testing.writeSnapshotPng
import java.awt.image.BufferedImage

private const val SEED = 1234L
private const val WIDTH = 240
private const val HEIGHT = 240
private const val DX = WIDTH / 2.0
private const val DZ = HEIGHT / 2.0

class TraverseTest {

  @Test
  fun `traverse should iterate`() {
    val registry = RegionRegistry()
    registry.region("hex").area(150.0).strategy(Strategy.hex())
    registry.region("cell").parent("hex").strategy(Strategy.voronoi())

    registry.region("voronoi1").area(0.2 * 150.0).parent("cell")
    registry.region("voronoi2").area(0.3 * 150.0).parent("cell")

    registry
        .region("voronoi3")
        .area(0.5 * 150.0)
        .parent("cell")
        .strategy(Strategy.surround("surround"))

    registry.region("surround").area(50.0)
    registry.region("center").parent("voronoi3").area(100.0)

    val root = registry.buildTree("hex")

    val traverse = Traverse(SEED, root)
    val step = traverse.iterate(0.0, 0.0)
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

  private fun renderSnapshot(name: String, sdf: Sdf2) {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawSdf(image, translate(sdf, DX, DZ))
    writeSnapshotPng(TraverseTest::class.java, name, image)
  }
}
