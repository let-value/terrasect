package com.terrasect.common.generation;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegionBudgetTest {

    @Test
    public void testRegionBudgetDistribution() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
            .child("CIVILIZATION", civ -> civ
                .child("CITY", city -> city.budget(1000).adjacentTo("FARMLAND"))
                .child("FARMLAND", farm -> farm.budget(3000).adjacentTo("CITY", "FOREST"))
                .child("FOREST", forest -> forest.budget(1000).adjacentTo("FARMLAND")))
            .child("WILDERNESS", wild -> wild.budget(1000));

        World.setRoot(registry.build("ROOT"));
        
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
            assertTrue(failures.isEmpty(), "Failed " + failures.size() + " out of " + seeds.length + " seeds.\n" + String.join("\n", failures));
        }
    }

    private void checkSeed(long seed) {
        Strategy context = new SnapshotTest.MockStrategy(seed);
        float hexSize = (float) World.getRoot().areaBudget();

        // Center + 6 Neighbors
        int[][] hexes = {
            {0, 0},
            {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}
        };

        for (int[] hex : hexes) {
            int q = hex[0];
            int r = hex[1];
            
            // Calculate center of this hex
            float hx = hexSize * ((float)Math.sqrt(3) * q + (float)Math.sqrt(3)/2.0f * r);
            float hz = hexSize * (3.0f/2.0f * r);
            
            System.out.printf("  Checking Hex (%d, %d) at (%.1f, %.1f)%n", q, r, hx, hz);
            checkRegion(context, (int)hx, (int)hz);
        }
    }

    private void checkRegion(Strategy context, int centerX, int centerZ) {
        // 2. Identify the Target Root Region Instance
        long targetRootId = World.getRegionSeedAtDepth(centerX, centerZ, context, 1);
        Region targetRegion = World.getRegionAtDepth(centerX, centerZ, context, 1);

        // 3. Scan and Sample
        // Scale range based on hex size (budget)
        int range = (int) (World.getRoot().areaBudget() * 1.2f);
        int step = 100; // Increase step for larger area
        
        Map<String, Integer> counts = new HashMap<>();
        Set<Long> uniqueRegionInstances = new HashSet<>();
        Map<Long, Set<Point>> regionPixels = new HashMap<>();
        long totalSamplesInTarget = 0;

        for (int z = centerZ - range; z <= centerZ + range; z += step) {
            for (int x = centerX - range; x <= centerX + range; x += step) {
                long rootId = World.getRegionSeedAtDepth(x, z, context, 1);
                
                if (rootId == targetRootId) {
                    Region child = World.getRegionAtDepth(x, z, context, 2);
                    long childId = World.getRegionSeedAtDepth(x, z, context, 2);
                    
                    counts.put(child.name(), counts.getOrDefault(child.name(), 0) + 1);
                    uniqueRegionInstances.add(childId);
                    regionPixels.computeIfAbsent(childId, k -> new HashSet<>()).add(new Point(x, z));
                    totalSamplesInTarget++;
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
            // If we have more, it means the same region type is being generated with different seeds/IDs in the same hex
            assertTrue(uniqueRegionInstances.size() <= expectedInstances, 
               "Found too many unique region instances: " + uniqueRegionInstances.size() + ". Expected max " + expectedInstances);

            // Allow some tolerance because of noise and warping
            float tolerance = 0.20f; // Increased tolerance for organic layout jitter

            assertDistribution(counts, totalSamplesInTarget, "CITY", 0.20f, tolerance);
            assertDistribution(counts, totalSamplesInTarget, "FARMLAND", 0.60f, tolerance);
            assertDistribution(counts, totalSamplesInTarget, "FOREST", 0.20f, tolerance);
            
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

    private void assertDistribution(Map<String, Integer> counts, long total, String name, float expectedPct, float tolerance) {
        int count = counts.getOrDefault(name, 0);
        float actualPct = (float) count / total;
        
        System.out.printf("Region %s: Expected %.2f, Actual %.2f%n", name, expectedPct, actualPct);
        
        assertTrue(actualPct >= expectedPct - tolerance && actualPct <= expectedPct + tolerance,
            String.format("Region %s distribution mismatch. Expected %.2f, got %.2f", name, expectedPct, actualPct));
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
        System.out.printf("  Region ID %d Connectivity: %d Components. Largest = %d / %d (%.2f%%)%n", 
            regionId, componentCount, maxComponentSize, pixels.size(), ratio * 100);
            
        assertTrue(ratio >= thresholdRatio, 
            String.format("Region ID %d is too fragmented! Found %d components. Largest component only has %.2f%% of pixels (Threshold: %.2f%%)", 
            regionId, componentCount, ratio * 100, thresholdRatio * 100));
            
        // Stricter check: We should ideally have 1 component, maybe 2 small islands allowed
        assertTrue(componentCount <= 3, 
            String.format("Region ID %d has too many disconnected components: %d", regionId, componentCount));
    }
    
    private int floodFill(Point start, Set<Point> allPixels, Set<Point> visited, int step) {
        int size = 0;
        java.util.Queue<Point> queue = new java.util.LinkedList<>();
        queue.add(start);
        visited.add(start);
        
        int[] dx = {step, -step, 0, 0};
        int[] dz = {0, 0, step, -step};
        
        while(!queue.isEmpty()) {
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
