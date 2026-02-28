package terrasect.helpers

import kotlin.math.hypot
import net.minecraft.SharedConstants
import net.minecraft.core.registries.Registries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.levelgen.DensityFunction
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import net.minecraft.world.level.levelgen.NoiseRouter
import net.minecraft.world.level.levelgen.RandomState
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import terrasect.testing.writeSnapshotPng

private const val IMG = 256
private const val WORLD = 4096
private const val SEED = 12345L
private const val Y = 63

class NoiseTransformSnapshotTest {

  companion object {
    private lateinit var router: NoiseRouter

    @BeforeAll
    @JvmStatic
    fun bootstrap() {
      SharedConstants.tryDetectVersion()
      Bootstrap.bootStrap()

      val lookup = VanillaRegistries.createLookup()
      val noiseParams = lookup.lookupOrThrow(Registries.NOISE)
      val settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
          .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value()
      val randomState = RandomState.create(settings, noiseParams, SEED)
      router = randomState.router()
    }
  }

  private fun sample(fn: DensityFunction) = sampleXZ(fn, IMG, WORLD, Y)

  @Test
  fun `clamp - continents`() {
    val original = sample(router.continents())
    val transform = NoiseTransform.builder().clamp(-0.3, 0.3).build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/clamp-continents.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `clamp - depth`() {
    val original = sample(router.depth())
    val transform = NoiseTransform.builder().clamp(-0.2, 0.2).build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/clamp-depth.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `multiply - reduce amplitude`() {
    val original = sample(router.depth())
    val transform = NoiseTransform.builder().multiply(0.5).build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/multiply-half.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `multiply - amplify`() {
    val original = sample(router.depth())
    val transform = NoiseTransform.builder().multiply(2.0).build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/multiply-double.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `add - raise baseline`() {
    val original = sample(router.depth())
    val transform = NoiseTransform.builder().add(0.3).build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/add-raise.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `add - lower baseline`() {
    val original = sample(router.depth())
    val transform = NoiseTransform.builder().add(-0.3).build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/add-lower.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `remap - compress range`() {
    val original = sample(router.depth())
    val transform = NoiseTransform.builder()
        .remap(inputMin = -1.0, inputMax = 1.0, outputMin = -0.3, outputMax = 0.3)
        .build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/remap-compress.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `remap - invert range`() {
    val original = sample(router.depth())
    val transform = NoiseTransform.builder()
        .remap(inputMin = -1.0, inputMax = 1.0, outputMin = 1.0, outputMax = -1.0)
        .build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/remap-invert.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `abs - erosion`() {
    val original = sample(router.erosion())
    val transform = NoiseTransform.builder().abs().build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/abs-erosion.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `square - ridges`() {
    val original = sample(router.ridges())
    val transform = NoiseTransform.builder().square().build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/square-ridges.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `halfNegative - depth`() {
    val original = sample(router.depth())
    val transform = NoiseTransform.builder().halfNegative().build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/half-negative-depth.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `squeeze - depth`() {
    val original = sample(router.depth())
    val transform = NoiseTransform.builder().squeeze().build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "operations/squeeze-depth.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `chain - mountain to hill`() {
    val original = sample(router.depth())
    val transform = NoiseTransform.builder()
        .clamp(-0.5, 1.0)
        .remap(inputMin = -0.5, inputMax = 1.0, outputMin = -0.3, outputMax = 0.4)
        .build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "chains/mountain-to-hill.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `chain - flatten terrain`() {
    val original = sample(router.depth())
    val transform = NoiseTransform.builder()
        .multiply(0.3)
        .add(0.1)
        .clamp(-0.2, 0.3)
        .build()
    val transformed = applyTransform(original, transform)
    writeSnapshotPng(javaClass, "chains/flatten.png", renderSideBySideGrayscale(original, transformed, IMG))
  }

  @Test
  fun `blend - circular region with clamp`() {
    val regionRadius = 800f
    val sdfAt = { x: Int, z: Int -> hypot(x.toFloat(), z.toFloat()) - regionRadius }

    val original = sample(router.depth())
    val transform = NoiseTransform.builder().clamp(-0.2, 0.2).build()
    val transformed = applyTransform(original, transform)
    val blended = applyBlend(original, transformed, IMG, 128f, sdfAt, WORLD)

    writeSnapshotPng(javaClass, "blend/circle-clamp.png", renderTripleGrayscale(original, transformed, blended, IMG))
  }

  @Test
  fun `blend - circular region with mountain to hill`() {
    val regionRadius = 800f
    val sdfAt = { x: Int, z: Int -> hypot(x.toFloat(), z.toFloat()) - regionRadius }

    val original = sample(router.depth())
    val transform = NoiseTransform.builder()
        .clamp(-0.5, 1.0)
        .remap(inputMin = -0.5, inputMax = 1.0, outputMin = -0.3, outputMax = 0.4)
        .build()
    val transformed = applyTransform(original, transform)
    val blended = applyBlend(original, transformed, IMG, 128f, sdfAt, WORLD)

    writeSnapshotPng(javaClass, "blend/circle-mountain-to-hill.png", renderTripleGrayscale(original, transformed, blended, IMG))
  }

  @Test
  fun `blend - wide vs narrow blend width`() {
    val regionRadius = 800f
    val sdfAt = { x: Int, z: Int -> hypot(x.toFloat(), z.toFloat()) - regionRadius }

    val original = sample(router.depth())
    val transform = NoiseTransform.builder().clamp(-0.2, 0.2).build()
    val transformed = applyTransform(original, transform)

    for ((name, width) in listOf("narrow-32" to 32f, "medium-128" to 128f, "wide-256" to 256f)) {
      val blended = applyBlend(original, transformed, IMG, width, sdfAt, WORLD)
      writeSnapshotPng(javaClass, "blend/width/$name.png", renderNormalizedGrayscale(blended, IMG))
    }
  }

  @Test
  fun `all router functions - original`() {
    writeSnapshotPng(javaClass, "vanilla/continents.png", renderNormalizedGrayscale(sample(router.continents()), IMG))
    writeSnapshotPng(javaClass, "vanilla/erosion.png", renderNormalizedGrayscale(sample(router.erosion()), IMG))
    writeSnapshotPng(javaClass, "vanilla/ridges.png", renderNormalizedGrayscale(sample(router.ridges()), IMG))
    writeSnapshotPng(javaClass, "vanilla/depth.png", renderNormalizedGrayscale(sample(router.depth()), IMG))
    writeSnapshotPng(javaClass, "vanilla/temperature.png", renderNormalizedGrayscale(sample(router.temperature()), IMG))
    writeSnapshotPng(javaClass, "vanilla/vegetation.png", renderNormalizedGrayscale(sample(router.vegetation()), IMG))
  }

  @Test
  fun `blend - multiple transform comparison`() {
    val regionRadius = 800f
    val sdfAt = { x: Int, z: Int -> hypot(x.toFloat(), z.toFloat()) - regionRadius }

    val original = sample(router.depth())
    val transforms = mapOf(
        "clamp" to NoiseTransform.builder().clamp(-0.2, 0.2).build(),
        "multiply-half" to NoiseTransform.builder().multiply(0.5).build(),
        "flatten" to NoiseTransform.builder().multiply(0.3).add(0.1).clamp(-0.2, 0.3).build(),
        "mountain-to-hill" to NoiseTransform.builder()
            .clamp(-0.5, 1.0)
            .remap(-0.5, 1.0, -0.3, 0.4)
            .build(),
    )

    for ((name, transform) in transforms) {
      val transformed = applyTransform(original, transform)
      val blended = applyBlend(original, transformed, IMG, 128f, sdfAt, WORLD)
      writeSnapshotPng(javaClass, "blend/compare/$name.png", renderTripleGrayscale(original, transformed, blended, IMG))
    }
  }
}
