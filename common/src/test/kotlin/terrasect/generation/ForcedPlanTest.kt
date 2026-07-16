package terrasect.generation

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import terrasect.sdf.Sdf2
import terrasect.sdf.translate

private const val SEED = 42L

class ForcedPlanTest {
  private val circle: Sdf2 =
    translate({ x, z -> hypot(x.toFloat(), z.toFloat()) - 800f }, 1000, 1000)

  @Test
  fun `sites align with descending budgets by index`() {
    val budgets = longArrayOf(50000, 20000, 5000)
    val plan = buildForcedPlan(SEED, circle, budgets)

    assertEquals(3, plan.sites.size)
    for ((index, site) in plan.sites.withIndex()) {
      assertEquals(index, site.index)
      assertEquals(sqrt(budgets[index] / PI).toFloat(), site.radius)
    }
    assertTrue(plan.sites[0].radius > plan.sites[1].radius)
    assertTrue(plan.sites[1].radius > plan.sites[2].radius)
  }

  @Test
  fun `sites land inside the region sdf`() {
    val plan = buildForcedPlan(SEED, circle, longArrayOf(30000, 30000, 10000))
    for (site in plan.sites) {
      assertTrue(circle(site.blockX, site.blockZ) <= 0f) {
        "site at ${site.blockX},${site.blockZ} is outside the region"
      }
    }
  }

  @Test
  fun `plan is deterministic for the same seed and budgets`() {
    val a = buildForcedPlan(SEED, circle, longArrayOf(30000, 10000))
    val b = buildForcedPlan(SEED, circle, longArrayOf(30000, 10000))
    assertEquals(
      a.sites.map { Triple(it.blockX, it.blockZ, it.radius) },
      b.sites.map { Triple(it.blockX, it.blockZ, it.radius) },
    )
  }

  @Test
  fun `start chunk maps back to the site`() {
    val plan = buildForcedPlan(SEED, circle, longArrayOf(30000))
    val site = plan.sites.single()
    assertEquals(listOf(site), plan.startsAt(site.chunkX, site.chunkZ))
    assertTrue(plan.startsAt(site.chunkX + 100, site.chunkZ).isEmpty())
  }

  @Test
  fun `chunks touching the site radius are banned and distant chunks are not`() {
    val plan = buildForcedPlan(SEED, circle, longArrayOf(80000))
    val site = plan.sites.single()

    assertTrue(plan.isBanned(site.chunkX, site.chunkZ))
    val edgeChunk = (site.blockX + site.radius.toInt() - 1) shr 4
    assertTrue(plan.isBanned(edgeChunk, site.chunkZ))

    val farChunk = (site.blockX + site.radius.toInt() + 32) shr 4
    assertFalse(plan.isBanned(farChunk, site.chunkZ))
  }

  @Test
  fun `every block within radius falls in a banned chunk`() {
    val plan = buildForcedPlan(SEED, circle, longArrayOf(20000))
    val site = plan.sites.single()
    val reach = site.radius.toInt()
    var z = site.blockZ - reach
    while (z <= site.blockZ + reach) {
      var x = site.blockX - reach
      while (x <= site.blockX + reach) {
        val dx = (x - site.blockX).toFloat()
        val dz = (z - site.blockZ).toFloat()
        if (hypot(dx, dz) <= site.radius) {
          assertTrue(plan.isBanned(x shr 4, z shr 4)) { "block $x,$z not covered by ban" }
        }
        x += 4
      }
      z += 4
    }
  }
}
