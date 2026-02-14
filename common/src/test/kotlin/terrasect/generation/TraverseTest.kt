package terrasect.generation

import org.junit.jupiter.api.Test
import terrasect.definition.RegionRegistry
import terrasect.sdf.*
import terrasect.strategies.HexStrategy
import terrasect.strategies.VoronoiStrategy
import terrasect.testing.writeSnapshotPng
import java.awt.image.BufferedImage

private const val SEED = 1234L
private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2.0
private const val CZ = HEIGHT / 2.0

class TraverseTest {

  @Test
  fun `traverse should iterate`() {
    val registry = RegionRegistry()
    registry.region("hex").area(10.0).strategy(HexStrategy.builder())
    registry.region("cell").parent("hex").strategy(VoronoiStrategy.builder())

    registry.region("a").area(2.0).parent("cell")
    registry.region("b").area(5.0).parent("cell")
    registry.region("c").area(3.0).parent("cell")

    val root = registry.buildTree("hex")

    val traverse = Traverse(SEED, root)

    val step = traverse.iterate(0, 0)
    renderSnapshot("step0.png", step.sdf)

    step.next()
    renderSnapshot("step1.png", step.sdf)

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawSdf(image, translate(step.sdf, CX, CZ))

    val sites = VoronoiStrategy.getSites(step, step.region.strategy as VoronoiStrategy)
    val voronoi = VoronoiCellSdf()
    val voronoiInParent = SdfCompose()
    voronoiInParent.append(step.sdf)
    voronoiInParent.append(voronoi)
    voronoi.sites = sites
    for (index in sites.indices) {
      voronoi.cellIndex = index
      drawSdf(image, translate(voronoiInParent, CX, CZ))
    }
    drawSites(image, sites.map { site -> Site(site.x + CX, site.z + CZ, site.radius) })
    writeSnapshotPng(TraverseTest::class.java, "step1.5.png", image)

    step.next()
    renderSnapshot("step2.png", step.sdf)
  }

  private fun renderSnapshot(name: String, sdf: Sdf2) {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawSdf(image, translate(sdf, CX, CZ))
    writeSnapshotPng(TraverseTest::class.java, name, image)
  }
}
