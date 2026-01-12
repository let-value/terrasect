package com.terrasect.common.generation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.terrasect.common.Context;
import com.terrasect.common.TestRegions;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class RegionBudgetTest {

    @Test public void analyzeRegionBudgetsAndAreas() {
        Region root = TestRegions.buildTestWorld();
        World.register(root, World.OVERWORLD);

        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║          REGION BUDGET AND AREA ANALYSIS                      ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.println("║ KEY: radius() values are PROPORTIONAL weights, not absolute.  ║");
        System.out.println("║ Children fill parent space proportionally by budget ratio.    ║");
        System.out.println("║ ACTUAL size = parentRadius × sqrt(budget / totalBudgets)      ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        var rootBudget = root.areaBudget();
        var rootRadius = (float) Math.sqrt(rootBudget);
        var rootDiameter = rootRadius * 2;

        System.out.printf("ROOT: %s%n", root.name());
        System.out.printf("  Total Budget: %,d (sum of children)%n", rootBudget);
        System.out.printf("  Implied Radius: %.0f blocks (%.1f chunks)%n", rootRadius, rootRadius / 16);
        System.out.printf("  Implied Diameter: %.0f blocks (%.1f chunks)%n", rootDiameter, rootDiameter / 16);
        System.out.println();

        analyzeChildren(root, rootRadius, 1, "");
    }

    private void analyzeChildren(Region parent, float parentRadius, int depth, String indent) {
        if (!parent.hasChildren()) return;

        var totalChildBudget =
                parent.children().stream().mapToInt(Region::areaBudget).sum();

        System.out.printf("%sChildren of %s (depth %d):%n", indent, parent.name(), depth);
        System.out.printf("%s  Total child budget: %,d%n", indent, totalChildBudget);
        System.out.printf("%s  Parent radius: %.1f blocks%n", indent, parentRadius);
        System.out.println();

        for (Region child : parent.children()) {
            var childBudget = child.areaBudget();
            var budgetRatio = (float) childBudget / totalChildBudget;

            var proportionalRadius = parentRadius * (float) Math.sqrt(budgetRatio);
            var proportionalDiameter = proportionalRadius * 2;

            var specifiedRadius = (float) Math.sqrt(childBudget);

            System.out.printf("%s  %s:%n", indent, child.name());
            System.out.printf("%s    Specified radius(): %d blocks%n", indent, (int) specifiedRadius);
            System.out.printf("%s    Budget Proportion: %.1f%% of parent's space%n", indent, budgetRatio * 100);
            System.out.printf(
                    "%s    ACTUAL Radius: %.0f blocks (%.1f chunks)%n",
                    indent, proportionalRadius, proportionalRadius / 16);
            System.out.printf(
                    "%s    ACTUAL Diameter: %.0f blocks (%.1f chunks)%n",
                    indent, proportionalDiameter, proportionalDiameter / 16);

            if (Math.abs(specifiedRadius - proportionalRadius) > specifiedRadius * 0.1f) {
                var scaleFactor = proportionalRadius / specifiedRadius;
                System.out.printf(
                        "%s    ⚠️  SIZE MISMATCH: Actual is %.1fx the specified value!%n", indent, scaleFactor);

                System.out.printf(
                        "%s    💡 To get %.0f block radius, multiply all sibling radii by %.1fx%n",
                        indent, specifiedRadius, scaleFactor);
            }
            System.out.println();

            if (child.hasChildren()) {
                analyzeChildren(child, proportionalRadius, depth + 1, indent + "    ");
            }
        }
    }

    @Test public void sampleTestRegionsAtCoordinates() {
        Region root = TestRegions.buildTestWorld();
        World.register(root, World.OVERWORLD);

        var seed = 12345L;
        var context = new SnapshotTest.MockStrategy(seed);

        World.initialize(context);

        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║         SAMPLING TESTREGIONS AT SPECIFIC COORDINATES          ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        System.out.println("=== Searching for SPAWN and SEASONS_HUB locations ===");
        findRegionLocations(context, "SEASONS_HUB", 2);
        findRegionLocations(context, "SPAWN", 3);
        System.out.println();

        System.out.println("At ORIGIN (0, 0):");
        samplePoint(0, 0, context);

        System.out.println("\nWalking EAST from origin (step=16 blocks):");
        walkDirection(0, 0, 16, 0, 30, context);

        System.out.println("\nWalking NORTH from origin:");
        walkDirection(0, 0, 0, -16, 30, context);

        System.out.println("\nWalking SOUTH from origin:");
        walkDirection(0, 0, 0, 16, 30, context);

        System.out.println("\nWalking WEST from origin:");
        walkDirection(0, 0, -16, 0, 30, context);

        System.out.println("\n\nSampling at larger distances from origin:");
        int[] distances = {500, 1000, 2000, 5000, 10000};
        for (int dist : distances) {
            System.out.printf("\nAt (%d, 0):%n", dist);
            samplePoint(dist, 0, context);
        }
    }

    private void samplePoint(int x, int z, Context context) {
        var d1 = World.traverse(context, x, z, 1).region;
        var d2 = World.traverse(context, x, z, 2).region;
        var d3 = World.traverse(context, x, z, 3).region;
        var d4 = World.traverse(context, x, z, 4).region;

        System.out.printf("  Depth 1: %s%n", d1.name());
        System.out.printf("  Depth 2: %s%n", d2.name());
        System.out.printf("  Depth 3: %s%n", d3.name());
        System.out.printf("  Depth 4: %s%n", d4.name());
    }

    private void walkDirection(int startX, int startZ, int stepX, int stepZ, int steps, Context context) {
        var lastRegion = "";
        var lastTransitionAt = 0;

        for (var i = 0; i <= steps; i++) {
            var x = startX + i * stepX;
            var z = startZ + i * stepZ;

            var d3 = World.traverse(context, x, z, 3).region;
            var regionName = d3.name();

            if (!regionName.equals(lastRegion)) {
                if (i > 0) {
                    var blocksDist = (int) Math.sqrt((x - startX) * (x - startX) + (z - startZ) * (z - startZ));
                    var transitionDist = blocksDist - lastTransitionAt;
                    System.out.printf(
                            "  [%d blocks] %s → %s (span: %d blocks = %.1f chunks)%n",
                            blocksDist, lastRegion, regionName, transitionDist, transitionDist / 16.0f);
                    lastTransitionAt = blocksDist;
                } else {
                    System.out.printf("  [0 blocks] Starting in: %s%n", regionName);
                }
                lastRegion = regionName;
            }
        }

        var totalDist = (int) Math.sqrt((steps * stepX) * (steps * stepX) + (steps * stepZ) * (steps * stepZ));
        System.out.printf("  [%d blocks] Still in: %s%n", totalDist, lastRegion);
    }

    private void findRegionLocations(Context context, String targetRegion, int depth) {
        System.out.printf("Searching for %s at depth %d...%n", targetRegion, depth);

        var range = 2000;
        var step = 50;

        var foundCount = 0;
        int firstX = 0, firstZ = 0;

        for (var z = -range; z <= range && foundCount < 5; z += step) {
            for (var x = -range; x <= range && foundCount < 5; x += step) {
                TraversalResult traversal = World.traverse(context, x, z, depth);
                Region region = traversal != null ? traversal.region : null;
                if (region != null && region.name().equals(targetRegion)) {
                    if (foundCount == 0) {
                        firstX = x;
                        firstZ = z;
                    }
                    foundCount++;
                    if (foundCount <= 3) {
                        System.out.printf("  Found %s at (%d, %d)%n", targetRegion, x, z);
                    }
                }
            }
        }

        if (foundCount == 0) {
            System.out.printf("  ⚠️ %s NOT FOUND in ±%d block range!%n", targetRegion, range);
        } else if (foundCount > 3) {
            System.out.printf("  ... and %d more locations%n", foundCount - 3);
            System.out.printf(
                    "  First occurrence at (%d, %d), distance from origin: %.0f blocks%n",
                    firstX, firstZ, Math.sqrt(firstX * firstX + firstZ * firstZ));
        }
    }

    @Test public void testRegionBudgetDistribution() {

        var registry = new RegionRegistry();
        registry.region("ROOT")
                .child("CIVILIZATION", civ -> civ.child("CITY", city -> city.radius(1000)
                        .adjacentTo("FARMLAND")
                        .child("DOWNTOWN", d -> d.radius(700))
                        .child("SUBURBS", s -> s.radius(700)))
                        .child("FARMLAND", farm -> farm.radius(1732).adjacentTo("CITY", "FOREST"))
                        .child("FOREST", forest -> forest.radius(1000).adjacentTo("FARMLAND")))
                .child("WILDERNESS", wild -> wild.radius(1000));

        World.register(registry.build("ROOT"), World.OVERWORLD);

        long[] seeds = {12345L, 98765L, 112233L, 55555L, 999999L, 101010L, 424242L, 777777L, 314159L, 271828L};
        var failures = new java.util.ArrayList<String>();

        for (long seed : seeds) {
            try {
                System.out.println("\nTesting Seed: " + seed);
                checkSeed(seed);
            } catch (AssertionError | RuntimeException e) {
                failures.add("Seed " + seed + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            for (String fail : failures) {
                System.err.println(fail);
            }
            assertTrue(
                    failures.isEmpty(),
                    "Failed " + failures.size() + " out of " + seeds.length + " seeds.\n"
                            + String.join("\n", failures));
        }
    }

    private void checkSeed(long seed) {
        var context = new SnapshotTest.MockStrategy(seed);

        var hexSize = World.getRoot(World.OVERWORLD).radius();

        int[][] hexes = {{0, 0}, {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};

        for (int[] hex : hexes) {
            var q = hex[0];
            var r = hex[1];

            var hx = hexSize * ((float) Math.sqrt(3) * q + (float) Math.sqrt(3) / 2.0f * r);
            var hz = hexSize * (3.0f / 2.0f * r);

            System.out.printf("  Checking Hex (%d, %d) at (%.1f, %.1f)%n", q, r, hx, hz);
            checkRegion(context, (int) hx, (int) hz);
        }
    }

    private void checkRegion(Context context, int centerX, int centerZ) {

        TraversalResult target = World.traverse(context, centerX, centerZ, 1);
        var targetRootId = target.seed;
        var targetRegion = target.region;

        var rootRadius = World.getRoot(World.OVERWORLD).radius();
        var range = (int) (rootRadius * 2.0f);
        var step = 100;

        var counts = new HashMap<String, Integer>();
        var cityCounts = new HashMap<String, Integer>();
        var uniqueRegionInstances = new HashSet<Long>();
        var regionPixels = new HashMap<Long, Set<Point>>();
        var totalSamplesInTarget = 0L;
        var citySamples = 0L;

        for (var z = centerZ - range; z <= centerZ + range; z += step) {
            for (var x = centerX - range; x <= centerX + range; x += step) {
                var rootId = World.traverse(context, x, z, 1).seed;

                if (rootId == targetRootId) {
                    TraversalResult childTraversal = World.traverse(context, x, z, 2);
                    var child = childTraversal.region;
                    var childId = childTraversal.seed;

                    counts.put(child.name(), counts.getOrDefault(child.name(), 0) + 1);
                    uniqueRegionInstances.add(childId);
                    regionPixels.computeIfAbsent(childId, k -> new HashSet<>()).add(new Point(x, z));
                    totalSamplesInTarget++;

                    if (child.name().equals("CITY")) {
                        var grandChild = World.traverse(context, x, z, 3).region;
                        cityCounts.put(grandChild.name(), cityCounts.getOrDefault(grandChild.name(), 0) + 1);
                        citySamples++;
                    }
                }
            }
        }

        System.out.println("    Region: " + targetRegion.name());
        System.out.println("    Samples: " + totalSamplesInTarget);
        System.out.println("    Counts: " + counts);

        if (totalSamplesInTarget == 0) {
            System.out.println("    WARNING: No samples found for target region!");
            return;
        }

        if (targetRegion.name().equals("CIVILIZATION")) {
            var expectedInstances = 3;

            assertTrue(
                    uniqueRegionInstances.size() <= expectedInstances,
                    "Found too many unique region instances: " + uniqueRegionInstances.size() + ". Expected max "
                            + expectedInstances);

            var tolerance = 0.20f;

            assertDistribution(counts, totalSamplesInTarget, "CITY", 0.20f, tolerance);
            assertDistribution(counts, totalSamplesInTarget, "FARMLAND", 0.60f, tolerance);
            assertDistribution(counts, totalSamplesInTarget, "FOREST", 0.20f, tolerance);

            if (citySamples > 0) {
                System.out.println("    Checking CITY internals (Depth 3)");

                var nestedTolerance = 0.25f;
                assertDistribution(cityCounts, citySamples, "DOWNTOWN", 0.50f, nestedTolerance);
                assertDistribution(cityCounts, citySamples, "SUBURBS", 0.50f, nestedTolerance);
            }

            for (Long regionId : uniqueRegionInstances) {
                checkConnectivity(regionId, regionPixels.get(regionId), step, 0.90f);
            }
        } else {

            assertDistribution(counts, totalSamplesInTarget, "WILDERNESS", 1.00f, 0.01f);
        }
    }

    private void assertDistribution(
            Map<String, Integer> counts, long total, String name, float expectedPct, float tolerance) {
        var count = counts.getOrDefault(name, 0);
        var actualPct = (float) count / total;

        System.out.printf("Region %s: Expected %.2f, Actual %.2f%n", name, expectedPct, actualPct);

        assertTrue(
                actualPct >= expectedPct - tolerance && actualPct <= expectedPct + tolerance,
                String.format(
                        "Region %s distribution mismatch. Expected %.2f, got %.2f", name, expectedPct, actualPct));
    }

    private void checkConnectivity(long regionId, Set<Point> pixels, int step, float thresholdRatio) {
        if (pixels == null || pixels.isEmpty()) return;

        var maxComponentSize = 0;
        var componentCount = 0;
        var visited = new HashSet<Point>();

        for (Point p : pixels) {
            if (!visited.contains(p)) {
                componentCount++;
                var componentSize = floodFill(p, pixels, visited, step);
                if (componentSize > maxComponentSize) {
                    maxComponentSize = componentSize;
                }
            }
        }

        var ratio = (float) maxComponentSize / pixels.size();
        System.out.printf(
                "  Region ID %d Connectivity: %d Components. Largest = %d / %d (%.2f%%)%n",
                regionId, componentCount, maxComponentSize, pixels.size(), ratio * 100);

        assertTrue(
                ratio >= thresholdRatio,
                String.format(
                        "Region ID %d is too fragmented! Found %d components. Largest component only has %.2f%% of pixels (Threshold: %.2f%%)",
                        regionId, componentCount, ratio * 100, thresholdRatio * 100));

        assertTrue(
                componentCount <= 3,
                String.format("Region ID %d has too many disconnected components: %d", regionId, componentCount));
    }

    private int floodFill(Point start, Set<Point> allPixels, Set<Point> visited, int step) {
        var size = 0;
        var queue = new java.util.LinkedList<Point>();
        queue.add(start);
        visited.add(start);

        int[] dx = {step, -step, 0, 0};
        int[] dz = {0, 0, step, -step};

        while (!queue.isEmpty()) {
            var current = queue.poll();
            size++;

            for (var i = 0; i < 4; i++) {
                var neighbor = new Point(current.x + dx[i], current.z + dz[i]);
                if (allPixels.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return size;
    }

    private record Point(int x, int z) {
    }
}
