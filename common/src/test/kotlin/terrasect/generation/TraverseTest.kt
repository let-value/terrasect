package terrasect.generation

import org.junit.jupiter.api.Test
import terrasect.definition.RegionRegistry
import terrasect.sdf.Sdf2
import terrasect.sdf.drawSdf
import terrasect.sdf.translate
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

    registry.region("a").parent("cell")
    registry.region("b").parent("cell")
    registry.region("c").parent("cell")

    val root = registry.buildTree("hex")

    val traverse = Traverse(SEED, root)

    val step = traverse.iterate(0, 0)

    renderSnapshot("step0.png", step.sdf)
    step.next()
    renderSnapshot("step1.png", step.sdf)
    step.next()
    renderSnapshot("step2.png", step.sdf)
  }

  private fun renderSnapshot(name: String, sdf: Sdf2) {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    drawSdf(image, translate(sdf, CX, CZ))
    writeSnapshotPng(TraverseTest::class.java, name, image)
  }
}
