package com.terrasect.common.generation;

import com.terrasect.common.api.Region;
import com.terrasect.common.api.RegionRegistry;
import com.terrasect.common.api.Strategy;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.runtime.World;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares budget distribution accuracy across different generation strategies.
 */
public class StrategyComparisonTest {

    @Test
    public void compareStrategies() {
        long seed = 987654321L;
        Strategy context = new SnapshotTest.MockStrategy(seed);

        System.out.println("=== Strategy Comparison Test ===\n");

        // Test VORONOI (legacy)
        System.out.println("--- VORONOI Strategy ---");
        testWithStrategy(GenerationStrategyType.VORONOI, context);

        // Test SUBDIVISION
        System.out.println("\n--- SUBDIVISION Strategy ---");
        testWithStrategy(GenerationStrategyType.SUBDIVISION, context);

        // Test TEMPLATE
        System.out.println("\n--- TEMPLATE Strategy ---");
        testWithStrategy(GenerationStrategyType.TEMPLATE, context);
    }

    private void testWithStrategy(GenerationStrategyType strategy, Strategy context) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
            .child("CIVILIZATION", civ -> civ
                .strategy(strategy)
                .child("CITY", city -> city.radius(1000)
                    .strategy(strategy)
                    .child("DOWNTOWN", d -> d.radius(700))
                    .child("SUBURBS", s -> s.radius(700)))
                .child("FARMLAND", farm -> farm.radius(1732))
                .child("FOREST", forest -> forest.radius(1000)))
            .child("WILDERNESS", wild -> wild.radius(1000));

        World.setRoot(registry.build("ROOT"));

        // areaBudget is radius^2, so sqrt gives us the actual radius
        float hexSize = (float) Math.sqrt(World.getRoot().areaBudget());
        
        // Sample the center hex
        int range = (int) (hexSize * 1.5f);
        int step = 100;
        
        Map<String, Integer> depth2Counts = new HashMap<>();
        long totalSamples = 0;

        for (int z = -range; z <= range; z += step) {
            for (int x = -range; x <= range; x += step) {
                // Only sample within the center hex
                long rootSeed = World.getRegionSeedAtDepth(x, z, context, 1);
                long centerSeed = World.getRegionSeedAtDepth(0, 0, context, 1);
                
                if (rootSeed == centerSeed) {
                    Region child = World.getRegionAtDepth(x, z, context, 2);
                    depth2Counts.merge(child.name(), 1, Integer::sum);
                    totalSamples++;
                }
            }
        }

        // Print results
        System.out.println("  Samples: " + totalSamples);
        System.out.println("  Expected: CITY=20%, FARMLAND=60%, FOREST=20%");
        
        if (totalSamples > 0) {
            for (var entry : depth2Counts.entrySet()) {
                float pct = 100.0f * entry.getValue() / totalSamples;
                System.out.printf("  %s: %.1f%% (%d samples)%n", entry.getKey(), pct, entry.getValue());
            }
            
            // Calculate error from expected
            float cityError = Math.abs(getPercent(depth2Counts, "CITY", totalSamples) - 20.0f);
            float farmError = Math.abs(getPercent(depth2Counts, "FARMLAND", totalSamples) - 60.0f);
            float forestError = Math.abs(getPercent(depth2Counts, "FOREST", totalSamples) - 20.0f);
            float totalError = cityError + farmError + forestError;
            
            System.out.printf("  Total error from expected: %.1f%%%n", totalError);
        }
    }
    
    private float getPercent(Map<String, Integer> counts, String name, long total) {
        return 100.0f * counts.getOrDefault(name, 0) / total;
    }

    @Test
    public void testSubdivisionBudgetAccuracy() {
        // Test that SUBDIVISION strategy produces accurate budget distributions
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
            .child("CIVILIZATION", civ -> civ
                .strategy(GenerationStrategyType.SUBDIVISION)
                .child("CITY", city -> city.radius(1000)
                    .strategy(GenerationStrategyType.SUBDIVISION)
                    .child("DOWNTOWN", d -> d.radius(700))
                    .child("SUBURBS", s -> s.radius(700)))
                .child("FARMLAND", farm -> farm.radius(1732))
                .child("FOREST", forest -> forest.radius(1000)))
            .child("WILDERNESS", wild -> wild.radius(1000));

        World.setRoot(registry.build("ROOT"));

        long[] seeds = {12345L, 98765L, 112233L};
        float totalError = 0;
        int testCount = 0;

        for (long seed : seeds) {
            Strategy context = new SnapshotTest.MockStrategy(seed);
            // areaBudget is radius^2, so sqrt gives us the actual radius
            float hexSize = (float) Math.sqrt(World.getRoot().areaBudget());
            int range = (int) (hexSize * 1.2f);
            int step = 100;

            Map<String, Integer> counts = new HashMap<>();
            long samples = 0;

            for (int z = -range; z <= range; z += step) {
                for (int x = -range; x <= range; x += step) {
                    long rootSeed = World.getRegionSeedAtDepth(x, z, context, 1);
                    long centerSeed = World.getRegionSeedAtDepth(0, 0, context, 1);
                    
                    if (rootSeed == centerSeed) {
                        Region child = World.getRegionAtDepth(x, z, context, 2);
                        counts.merge(child.name(), 1, Integer::sum);
                        samples++;
                    }
                }
            }

            if (samples > 0) {
                float cityPct = getPercent(counts, "CITY", samples);
                float farmPct = getPercent(counts, "FARMLAND", samples);
                float forestPct = getPercent(counts, "FOREST", samples);
                
                float error = Math.abs(cityPct - 20) + Math.abs(farmPct - 60) + Math.abs(forestPct - 20);
                totalError += error;
                testCount++;
                
                System.out.printf("Seed %d: CITY=%.1f%% FARMLAND=%.1f%% FOREST=%.1f%% Error=%.1f%%%n",
                    seed, cityPct, farmPct, forestPct, error);
            }
        }

        float avgError = totalError / testCount;
        System.out.printf("Average error: %.1f%%%n", avgError);
        
        // Subdivision should have lower error than Voronoi (which was ~30-40%)
        assertTrue(avgError < 25, "SUBDIVISION strategy should have less than 25% total error, got " + avgError);
    }
}
