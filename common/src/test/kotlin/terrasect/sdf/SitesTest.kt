package terrasect.sdf

import java.awt.image.BufferedImage
import kotlin.math.*
import org.junit.jupiter.api.Test
import terrasect.testing.drawCircle
import terrasect.testing.drawRing
import terrasect.testing.drawSdf
import terrasect.testing.writeSnapshotPng

private const val WIDTH = 240
private const val HEIGHT = 240
private const val CX = WIDTH / 2.0
private const val CZ = HEIGHT / 2.0
private const val SEED = 1234L

class SitesTest {
  @Test
  fun `should render sites in circle sdf`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val radius = 100.0
    val sdf: Sdf2 = translate({ x, z -> sqrt(x * x + z * z) - radius }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val budgets = doubleArrayOf(500.0, 100.0, 200.0, 300.0, 1000.0, 5000.0, 3000.0)
    val sites = getSites(SEED, sdf, bounds, budgets)
    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "circle.png", image)
  }

  @Test
  fun `should render sites in hex sdf`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val apothem = 100.0
    val sdf: Sdf2 = translate({ x, z -> hexDistance(x, z, apothem) }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val budgets = doubleArrayOf(500.0, 100.0, 200.0, 300.0, 1000.0, 5000.0, 3000.0)
    val sites = getSites(SEED, sdf, bounds, budgets)
    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "hex.png", image)
  }

  @Test
  fun `should render sites in banana sdf`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val sdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val budgets = doubleArrayOf(500.0, 100.0, 200.0, 300.0, 1000.0)
    val sites = getSites(SEED, sdf, bounds, budgets)
    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "banana.png", image)
  }

  @Test
  fun `should position dense sites`() {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val sdf: Sdf2 = translate({ x, z -> bananaSdf(x, z) }, CX, CZ)
    val bounds = estimateBounds(sdf)
    val area = estimateArea(sdf, bounds)

    val budgets = doubleArrayOf((area * 0.3), (area * 0.5), (area * 0.2))

    val sites = getSites(SEED, sdf, bounds, budgets)
    logSiteDistances("dense", sites, sdf)

    drawSdf(image, sdf)
    drawSites(image, sites)
    writeSnapshotPng(SitesTest::class.java, "dense.png", image)
  }

  private fun drawSites(image: BufferedImage, sites: List<Site>) {
    for (site in sites) {
      val x = site.x.roundToInt()
      val z = site.z.roundToInt()
      drawCircle(image, x, z, 1)
      drawRing(image, x, z, site.radius)
    }
  }

  private fun estimateArea(sdf: Sdf2, bounds: SdfBounds, step: Double = 1.0): Double {
    val safeStep = step.coerceAtLeast(0.25)
    val minX = floor(bounds.minX)
    val maxX = ceil(bounds.maxX)
    val minZ = floor(bounds.minZ)
    val maxZ = ceil(bounds.maxZ)
    val cellArea = safeStep * safeStep
    var area = 0.0
    var z = minZ
    while (z < maxZ) {
      var x = minX
      while (x < maxX) {
        val sampleX = x + safeStep * 0.5
        val sampleZ = z + safeStep * 0.5
        if (sdf(sampleX, sampleZ) <= 0.0) {
          area += cellArea
        }
        x += safeStep
      }
      z += safeStep
    }
    return area
  }

  private fun logSiteDistances(label: String, sites: List<Site>, sdf: Sdf2) {
    if (sites.isEmpty()) return

    var minClearance = Double.POSITIVE_INFINITY
    var minDistance = Double.POSITIVE_INFINITY
    var minPairA = -1
    var minPairB = -1
    val overlapDetails = mutableListOf<Pair<Double, String>>()

    for (i in 0 until sites.size) {
      for (j in i + 1 until sites.size) {
        val dx = sites[i].x - sites[j].x
        val dz = sites[i].z - sites[j].z
        val distance = sqrt(dx * dx + dz * dz)
        val clearance = distance - (sites[i].radius + sites[j].radius)
        if (clearance < minClearance) {
          minClearance = clearance
          minPairA = i
          minPairB = j
        }
        if (distance < minDistance) {
          minDistance = distance
        }
        if (clearance < 0.0) {
          overlapDetails.add(
            Pair(
              clearance,
              String.format("pair %d-%d distance=%.2f clearance=%.2f", i, j, distance, clearance),
            )
          )
        }
      }
    }

    var minBoundary = Double.POSITIVE_INFINITY
    var maxBoundary = Double.NEGATIVE_INFINITY
    var sumBoundary = 0.0
    val perSite = mutableListOf<String>()

    for (i in sites.indices) {
      val site = sites[i]
      val boundaryClearance = -(sdf(site.x, site.z) + site.radius)
      minBoundary = min(minBoundary, boundaryClearance)
      maxBoundary = max(maxBoundary, boundaryClearance)
      sumBoundary += boundaryClearance

      var nearestDistance = Double.POSITIVE_INFINITY
      var nearestClearance = Double.POSITIVE_INFINITY
      var nearestIndex = -1
      for (j in sites.indices) {
        if (i == j) continue
        val dx = site.x - sites[j].x
        val dz = site.z - sites[j].z
        val distance = sqrt(dx * dx + dz * dz)
        if (distance < nearestDistance) {
          nearestDistance = distance
          nearestIndex = j
          nearestClearance = distance - (site.radius + sites[j].radius)
        }
      }

      perSite.add(
        String.format(
          "site %d r=%.2f boundary=%.2f nearest=%d dist=%.2f clearance=%.2f",
          i,
          site.radius,
          boundaryClearance,
          nearestIndex,
          nearestDistance,
          nearestClearance,
        )
      )
    }

    println("Sites debug ($label): count=${sites.size}")
    println(
      String.format(
        "  min clearance=%.2f pair=%d-%d, min distance=%.2f",
        minClearance,
        minPairA,
        minPairB,
        minDistance,
      )
    )
    println(
      String.format(
        "  boundary clearance min=%.2f avg=%.2f max=%.2f",
        minBoundary,
        sumBoundary / sites.size,
        maxBoundary,
      )
    )
    for (line in perSite) {
      println("  $line")
    }
    if (overlapDetails.isNotEmpty()) {
      println("  overlaps:")
      for (detail in overlapDetails.sortedBy { it.first }) {
        println("    ${detail.second}")
      }
    }
  }
}
