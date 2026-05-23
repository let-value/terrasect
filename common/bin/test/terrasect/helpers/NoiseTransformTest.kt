package terrasect.helpers

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NoiseTransformTest {

  @Test
  fun `clamp restricts value to range`() {
    val transform = NoiseTransform.builder().clamp(-0.5, 0.5).build()
    assertEquals(0.5, transform.apply(1.0), 1e-10)
    assertEquals(-0.5, transform.apply(-1.0), 1e-10)
    assertEquals(0.3, transform.apply(0.3), 1e-10)
  }

  @Test
  fun `add offsets value`() {
    val transform = NoiseTransform.builder().add(0.25).build()
    assertEquals(1.25, transform.apply(1.0), 1e-10)
    assertEquals(-0.75, transform.apply(-1.0), 1e-10)
  }

  @Test
  fun `multiply scales value`() {
    val transform = NoiseTransform.builder().multiply(2.0).build()
    assertEquals(2.0, transform.apply(1.0), 1e-10)
    assertEquals(-1.0, transform.apply(-0.5), 1e-10)
  }

  @Test
  fun `remap linearly maps between ranges`() {
    val transform =
      NoiseTransform.builder()
        .remap(inputMin = -1.0, inputMax = 1.0, outputMin = 0.0, outputMax = 100.0)
        .build()
    assertEquals(50.0, transform.apply(0.0), 1e-10)
    assertEquals(0.0, transform.apply(-1.0), 1e-10)
    assertEquals(100.0, transform.apply(1.0), 1e-10)
    assertEquals(75.0, transform.apply(0.5), 1e-10)
  }

  @Test
  fun `remap clamps input to source range`() {
    val transform =
      NoiseTransform.builder()
        .remap(inputMin = 0.0, inputMax = 1.0, outputMin = 60.0, outputMax = 70.0)
        .build()
    assertEquals(60.0, transform.apply(-5.0), 1e-10)
    assertEquals(70.0, transform.apply(5.0), 1e-10)
  }

  @Test
  fun `remap handles zero-width input range`() {
    val transform =
      NoiseTransform.builder()
        .remap(inputMin = 0.5, inputMax = 0.5, outputMin = 10.0, outputMax = 20.0)
        .build()
    assertEquals(15.0, transform.apply(0.5), 1e-10)
    assertEquals(15.0, transform.apply(999.0), 1e-10)
  }

  @Test
  fun `chained operations apply in order`() {
    val transform = NoiseTransform.builder().clamp(-0.5, 0.5).multiply(2.0).add(1.0).build()
    assertEquals(2.0, transform.apply(1.0), 1e-10)
    assertEquals(0.0, transform.apply(-0.5), 1e-10)
    assertEquals(1.5, transform.apply(0.25), 1e-10)
  }

  @Test
  fun `map abs returns absolute value`() {
    val transform = NoiseTransform.builder().abs().build()
    assertEquals(1.0, transform.apply(-1.0), 1e-10)
    assertEquals(0.5, transform.apply(0.5), 1e-10)
  }

  @Test
  fun `map square returns value squared`() {
    val transform = NoiseTransform.builder().square().build()
    assertEquals(4.0, transform.apply(2.0), 1e-10)
    assertEquals(0.25, transform.apply(-0.5), 1e-10)
  }

  @Test
  fun `map cube returns value cubed`() {
    val transform = NoiseTransform.builder().cube().build()
    assertEquals(8.0, transform.apply(2.0), 1e-10)
    assertEquals(-0.125, transform.apply(-0.5), 1e-10)
  }

  @Test
  fun `map half_negative halves only negative values`() {
    val transform = NoiseTransform.builder().halfNegative().build()
    assertEquals(1.0, transform.apply(1.0), 1e-10)
    assertEquals(-0.5, transform.apply(-1.0), 1e-10)
  }

  @Test
  fun `map quarter_negative quarters only negative values`() {
    val transform = NoiseTransform.builder().quarterNegative().build()
    assertEquals(1.0, transform.apply(1.0), 1e-10)
    assertEquals(-0.25, transform.apply(-1.0), 1e-10)
  }

  @Test
  fun `map squeeze produces expected shape`() {
    val transform = NoiseTransform.builder().squeeze().build()
    assertEquals(0.0, transform.apply(0.0), 1e-10)
    assertEquals(11.0 / 24.0, transform.apply(1.0), 1e-10)
    assertEquals(11.0 / 24.0, transform.apply(5.0), 1e-10)
  }

  @Test
  fun `empty transform returns original value`() {
    val transform = NoiseTransform.builder().build()
    assertTrue(transform.isEmpty())
    assertEquals(42.0, transform.apply(42.0), 1e-10)
  }

  @Test
  fun `builder consumer style works`() {
    val transform =
      NoiseTransform.builder()
        .also { builder ->
          builder.clamp(-1.0, 1.0)
          builder.multiply(0.5)
        }
        .build()
    assertEquals(0.5, transform.apply(2.0), 1e-10)
  }
}
