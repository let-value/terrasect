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

    /**
     * Print detailed budget and area analysis for a region hierarchy.
     * This helps debug why regions appear larger/smaller than expected in game.
     *
     * KEY INSIGHT: radius() values are RELATIVE weights, not absolute sizes.
     * The system fills the available parent space proportionally based on budgets.
     * Actual size = parentRadius * sqrt(childBudget / totalSiblingBudgets)
     */
    @Test
    public void analyzeRegionBudgetsAndAreas() {
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

        // Root level
        int rootBudget = root.areaBudget();
        float rootRadius = (float) Math.sqrt(rootBudget);
        float rootDiameter = rootRadius * 2;

        System.out.printf("ROOT: %s%n", root.name());
        System.out.printf("  Total Budget: %,d (sum of children)%n", rootBudget);
        System.out.printf("  Implied Radius: %.0f blocks (%.1f chunks)%n", rootRadius, rootRadius / 16);
        System.out.printf("  Implied Diameter: %.0f blocks (%.1f chunks)%n", rootDiameter, rootDiameter / 16);
        System.out.println();

        // Analyze each level
        analyzeChildren(root, rootRadius, 1, "");
    }

    private void analyzeChildren(Region parent, float parentRadius, int depth, String indent) {
        if (!parent.hasChildren()) return;

        // Calculate total budget of all children
        int totalChildBudget =
                parent.children().stream().mapToInt(Region::areaBudget).sum();

        System.out.printf("%sChildren of %s (depth %d):%n", indent, parent.name(), depth);
        System.out.printf("%s  Total child budget: %,d%n", indent, totalChildBudget);
        System.out.printf("%s  Parent radius: %.1f blocks%n", indent, parentRadius);
        System.out.println();

        for (Region child : parent.children()) {
            int childBudget = child.areaBudget();
            float budgetRatio = (float) childBudget / totalChildBudget;

            // The actual radius in world space depends on how strategies interpret budgets
            // Most strategies use budget as proportional weight, not absolute size
            // So child radius ≈ parentRadius * sqrt(budgetRatio)
            float proportionalRadius = parentRadius * (float) Math.sqrt(budgetRatio);
            float proportionalDiameter = proportionalRadius * 2;

            // What the user specified
            float specifiedRadius = (float) Math.sqrt(childBudget);

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
                float scaleFactor = proportionalRadius / specifiedRadius;
                System.out.printf(
                        "%s    ⚠️  SIZE MISMATCH: Actual is %.1fx the specified value!%n", indent, scaleFactor);
                // Calculate what they should specify to get the specified size
                // If actual = parentRadius * sqrt(budget / totalBudget)
                // And we want actual = specifiedRadius
                // Then budget = (specifiedRadius / parentRadius)² * totalBudget
                // But we can't control totalBudget easily...
                // Simpler: show the correction factor
                System.out.printf(
                        "%s    💡 To get %.0f block radius, multiply all sibling radii by %.1fx%n",
                        indent, specifiedRadius, scaleFactor);
            }
            System.out.println();

            // Recurse into children
            if (child.hasChildren()) {
                analyzeChildren(child, proportionalRadius, depth + 1, indent + "    ");
            }
        }
    }

    /**
     * Sample specific coordinates in TestRegions to verify actual region boundaries.
     * This helps visualize where regions actually are in world space.
     */
    @Test
    public void sampleTestRegionsAtCoordinates() {
        Region root = TestRegions.buildTestWorld();
        World.register(root, World.OVERWORLD);

        long seed = 12345L;
        Context context = new SnapshotTest.MockStrategy(seed);

        World.initialize(context);

        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║         SAMPLING TESTREGIONS AT SPECIFIC COORDINATES          ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // First, let's find where SPAWN and SEASONS_HUB actually are
        System.out.println("=== Searching for SPAWN and SEASONS_HUB locations ===");
        findRegionLocations(context, "SEASONS_HUB", 2);
        findRegionLocations(context, "SPAWN", 3); // SPAWN is child of SEASONS_HUB
        System.out.println();

        // Sample at origin (should be SPAWN since it's anchored)
        System.out.println("At ORIGIN (0, 0):");
        samplePoint(0, 0, context);

        // Sample walking outward in each direction to find region transitions
        System.out.println("\nWalking EAST from origin (step=16 blocks):");
        walkDirection(0, 0, 16, 0, 30, context); // 30 steps = 480 blocks

        System.out.println("\nWalking NORTH from origin:");
        walkDirection(0, 0, 0, -16, 30, context);

        System.out.println("\nWalking SOUTH from origin:");
        walkDirection(0, 0, 0, 16, 30, context);

        System.out.println("\nWalking WEST from origin:");
        walkDirection(0, 0, -16, 0, 30, context);

        // Sample at large distances to see hex tiling
        System.out.println("\n\nSampling at larger distances from origin:");
        int[] distances = {500, 1000, 2000, 5000, 10000};
        for (int dist : distances) {
            System.out.printf("\nAt (%d, 0):%n", dist);
            samplePoint(dist, 0, context);
        }
    }

    private void samplePoint(int x, int z, Context context) {
        Region d1 = World.traverse(context, x, z, 1).region;
        Region d2 = World.traverse(context, x, z, 2).region;
        Region d3 = World.traverse(context, x, z, 3).region;
        Region d4 = World.traverse(context, x, z, 4).region;

        System.out.printf("  Depth 1: %s%n", d1.name());
        System.out.printf("  Depth 2: %s%n", d2.name());
        System.out.printf("  Depth 3: %s%n", d3.name());
        System.out.printf("  Depth 4: %s%n", d4.name());
    }

    private void walkDirection(int startX, int startZ, int stepX, int stepZ, int steps, Context context) {
        String lastRegion = "";
        int lastTransitionAt = 0;

        for (int i = 0; i <= steps; i++) {
            int x = startX + i * stepX;
            int z = startZ + i * stepZ;

            Region d3 = World.traverse(context, x, z, 3).region;
            String regionName = d3.name();

            if (!regionName.equals(lastRegion)) {
                if (i > 0) {
                    int blocksDist = (int) Math.sqrt((x - startX) * (x - startX) + (z - startZ) * (z - startZ));
                    int transitionDist = blocksDist - lastTransitionAt;
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

        int totalDist = (int) Math.sqrt((steps * stepX) * (steps * stepX) + (steps * stepZ) * (steps * stepZ));
        System.out.printf("  [%d blocks] Still in: %s%n", totalDist, lastRegion);
    }

    private void findRegionLocations(Context context, String targetRegion, int depth) {
        System.out.printf("Searching for %s at depth %d...%n", targetRegion, depth);

        // Search in a grid pattern
        int range = 2000;
        int step = 50;

        int foundCount = 0;
        int firstX = 0, firstZ = 0;

        for (int z = -range; z <= range && foundCount < 5; z += step) {
            for (int x = -range; x <= range && foundCount < 5; x += step) {
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

    @Test
    public void testRegionBudgetDistribution() {
        // Using radius() - user defines radius in blocks, internally stored as radius^2
        // Root has no explicit radius, so it's calculated from children's areas
        // Using larger regions to avoid warp effects dominating (warp amplitude is ~200 blocks)
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
                .child("CIVILIZATION", civ -> civ
                        // CITY: radius 1000 blocks
                        .child("CITY", city -> city.radius(1000)
                                .adjacentTo("FARMLAND")
                                .child("DOWNTOWN", d -> d.radius(700))
                                .child("SUBURBS", s -> s.radius(700)))
                        // FARMLAND: radius 1732 blocks (3x area of CITY)
                        .child("FARMLAND", farm -> farm.radius(1732).adjacentTo("CITY", "FOREST"))
                        // FOREST: radius 1000 blocks
                        .child("FOREST", forest -> forest.radius(1000).adjacentTo("FARMLAND")))
                // WILDERNESS: radius 1000 blocks
                .child("WILDERNESS", wild -> wild.radius(1000));

        World.register(registry.build("ROOT"), World.OVERWORLD);

        // Test across multiple seeds to ensure stability
        long[] seeds = {12345L, 98765L, 112233L, 55555L, 999999L, 101010L, 424242L, 777777L, 314159L, 271828L};
        List<String> failures = new java.util.ArrayList<>();

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
        Context context = new SnapshotTest.MockStrategy(seed);
        // Use pre-baked radius from Region
        float hexSize = World.getRoot(World.OVERWORLD).radius();

        // Center + 6 Neighbors
        int[][] hexes = {{0, 0}, {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};

        for (int[] hex : hexes) {
            int q = hex[0];
            int r = hex[1];

            // Calculate center of this hex
            float hx = hexSize * ((float) Math.sqrt(3) * q + (float) Math.sqrt(3) / 2.0f * r);
            float hz = hexSize * (3.0f / 2.0f * r);

            System.out.printf("  Checking Hex (%d, %d) at (%.1f, %.1f)%n", q, r, hx, hz);
            checkRegion(context, (int) hx, (int) hz);
        }
    }

    private void checkRegion(Context context, int centerX, int centerZ) {
        // 2. Identify the Target Root Region Instance
        TraversalResult target = World.traverse(context, centerX, centerZ, 1);
        long targetRootId = target.seed;
        Region targetRegion = target.region;

        // 3. Scan and Sample
        // Scale range based on root radius + warping buffer
        // Using 2.0x ensures we capture the entire warped region.
        float rootRadius = World.getRoot(World.OVERWORLD).radius();
        int range = (int) (rootRadius * 2.0f);
        int step = 100; // Reasonable step for km-scale regions

        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> cityCounts = new HashMap<>();
        Set<Long> uniqueRegionInstances = new HashSet<>();
        Map<Long, Set<Point>> regionPixels = new HashMap<>();
        long totalSamplesInTarget = 0;
        long citySamples = 0;

        for (int z = centerZ - range; z <= centerZ + range; z += step) {
            for (int x = centerX - range; x <= centerX + range; x += step) {
                long rootId = World.traverse(context, x, z, 1).seed;

                if (rootId == targetRootId) {
                    TraversalResult childTraversal = World.traverse(context, x, z, 2);
                    Region child = childTraversal.region;
                    long childId = childTraversal.seed;

                    counts.put(child.name(), counts.getOrDefault(child.name(), 0) + 1);
                    uniqueRegionInstances.add(childId);
                    regionPixels.computeIfAbsent(childId, k -> new HashSet<>()).add(new Point(x, z));
                    totalSamplesInTarget++;

                    if (child.name().equals("CITY")) {
                        Region grandChild = World.traverse(context, x, z, 3).region;
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
            int expectedInstances = 3;
            // Check that we don't have too many unique instances (which would imply fragmentation/duplication)
            // We expect exactly 3 unique IDs (one for each child type)
            // If we have more, it means the same region type is being generated with different seeds/IDs in the same
            // hex
            assertTrue(
                    uniqueRegionInstances.size() <= expectedInstances,
                    "Found too many unique region instances: " + uniqueRegionInstances.size() + ". Expected max "
                            + expectedInstances);

            // Allow some tolerance because of noise and warping
            float tolerance = 0.20f; // Increased tolerance for organic layout jitter

            assertDistribution(counts, totalSamplesInTarget, "CITY", 0.20f, tolerance);
            assertDistribution(counts, totalSamplesInTarget, "FARMLAND", 0.60f, tolerance);
            assertDistribution(counts, totalSamplesInTarget, "FOREST", 0.20f, tolerance);

            if (citySamples > 0) {
                System.out.println("    Checking CITY internals (Depth 3)");
                // Nested regions at depth 3 have higher variance due to smaller sample area.
                // CITY is only ~25% of hex, so its children have ~4x less samples.
                // Use a 25% tolerance to account for this statistical variance.
                float nestedTolerance = 0.25f;
                assertDistribution(cityCounts, citySamples, "DOWNTOWN", 0.50f, nestedTolerance);
                assertDistribution(cityCounts, citySamples, "SUBURBS", 0.50f, nestedTolerance);
            }

            // Check Connectivity (Enclaves/Exclaves)
            for (Long regionId : uniqueRegionInstances) {
                checkConnectivity(regionId, regionPixels.get(regionId), step, 0.90f);
            }
        } else {
            // WILDERNESS
            // Should be 100% WILDERNESS
            assertDistribution(counts, totalSamplesInTarget, "WILDERNESS", 1.00f, 0.01f);
        }
    }

    private void assertDistribution(
            Map<String, Integer> counts, long total, String name, float expectedPct, float tolerance) {
        int count = counts.getOrDefault(name, 0);
        float actualPct = (float) count / total;

        System.out.printf("Region %s: Expected %.2f, Actual %.2f%n", name, expectedPct, actualPct);

        assertTrue(
                actualPct >= expectedPct - tolerance && actualPct <= expectedPct + tolerance,
                String.format(
                        "Region %s distribution mismatch. Expected %.2f, got %.2f", name, expectedPct, actualPct));
    }

    private void checkConnectivity(long regionId, Set<Point> pixels, int step, float thresholdRatio) {
        if (pixels == null || pixels.isEmpty()) return;

        int maxComponentSize = 0;
        int componentCount = 0;
        Set<Point> visited = new HashSet<>();

        for (Point p : pixels) {
            if (!visited.contains(p)) {
                componentCount++;
                int componentSize = floodFill(p, pixels, visited, step);
                if (componentSize > maxComponentSize) {
                    maxComponentSize = componentSize;
                }
            }
        }

        float ratio = (float) maxComponentSize / pixels.size();
        System.out.printf(
                "  Region ID %d Connectivity: %d Components. Largest = %d / %d (%.2f%%)%n",
                regionId, componentCount, maxComponentSize, pixels.size(), ratio * 100);

        assertTrue(
                ratio >= thresholdRatio,
                String.format(
                        "Region ID %d is too fragmented! Found %d components. Largest component only has %.2f%% of pixels (Threshold: %.2f%%)",
                        regionId, componentCount, ratio * 100, thresholdRatio * 100));

        // Stricter check: We should ideally have 1 component, maybe 2 small islands allowed
        assertTrue(
                componentCount <= 3,
                String.format("Region ID %d has too many disconnected components: %d", regionId, componentCount));
    }

    private int floodFill(Point start, Set<Point> allPixels, Set<Point> visited, int step) {
        int size = 0;
        java.util.Queue<Point> queue = new java.util.LinkedList<>();
        queue.add(start);
        visited.add(start);

        int[] dx = {step, -step, 0, 0};
        int[] dz = {0, 0, step, -step};

        while (!queue.isEmpty()) {
            Point current = queue.poll();
            size++;

            for (int i = 0; i < 4; i++) {
                Point neighbor = new Point(current.x + dx[i], current.z + dz[i]);
                if (allPixels.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return size;
    }

    private record Point(int x, int z) {}
}
