package com.terrasect.common.generation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.terrasect.common.Context;
import com.terrasect.common.definition.GenerationStrategyType;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class StrategyComparisonTest {

    @Test
    public void compareStrategies() {
        long seed = 987654321L;
        Context context = new SnapshotTest.MockStrategy(seed);

        System.out.println("=== Strategy Comparison Test ===\n");

        System.out.println("--- VORONOI Strategy ---");
        testWithStrategy(GenerationStrategyType.VORONOI, context);

        System.out.println("\n--- SUBDIVISION Strategy ---");
        testWithStrategy(GenerationStrategyType.SUBDIVISION, context);

        System.out.println("\n--- TEMPLATE Strategy ---");
        testWithStrategy(GenerationStrategyType.TEMPLATE, context);
    }

    private void testWithStrategy(GenerationStrategyType strategy, Context context) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
                .child("CIVILIZATION", civ -> civ.strategy(strategy)
                        .child("CITY", city -> city.radius(1000)
                                .strategy(strategy)
                                .child("DOWNTOWN", d -> d.radius(700))
                                .child("SUBURBS", s -> s.radius(700)))
                        .child("FARMLAND", farm -> farm.radius(1732))
                        .child("FOREST", forest -> forest.radius(1000)))
                .child("WILDERNESS", wild -> wild.radius(1000));

        World.register(registry.build("ROOT"), World.OVERWORLD);

        float hexSize = World.getRoot(World.OVERWORLD).radius();

        int range = (int) (hexSize * 1.5f);
        int step = 100;

        Map<String, Integer> depth2Counts = new HashMap<>();
        long totalSamples = 0;

        for (int z = -range; z <= range; z += step) {
            for (int x = -range; x <= range; x += step) {

                long rootSeed = World.traverse(context, x, z, 1).seed;
                long centerSeed = World.traverse(context, 0, 0, 1).seed;

                if (rootSeed == centerSeed) {
                    Region child = World.traverse(context, x, z, 2).region;
                    depth2Counts.merge(child.name(), 1, Integer::sum);
                    totalSamples++;
                }
            }
        }

        System.out.println("  Samples: " + totalSamples);
        System.out.println("  Expected: CITY=20%, FARMLAND=60%, FOREST=20%");

        if (totalSamples > 0) {
            for (var entry : depth2Counts.entrySet()) {
                float pct = 100.0f * entry.getValue() / totalSamples;
                System.out.printf("  %s: %.1f%% (%d samples)%n", entry.getKey(), pct, entry.getValue());
            }

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

        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
                .child("CIVILIZATION", civ -> civ.strategy(GenerationStrategyType.SUBDIVISION)
                        .child("CITY", city -> city.radius(1000)
                                .strategy(GenerationStrategyType.SUBDIVISION)
                                .child("DOWNTOWN", d -> d.radius(700))
                                .child("SUBURBS", s -> s.radius(700)))
                        .child("FARMLAND", farm -> farm.radius(1732))
                        .child("FOREST", forest -> forest.radius(1000)))
                .child("WILDERNESS", wild -> wild.radius(1000));

        World.register(registry.build("ROOT"), World.OVERWORLD);

        long[] seeds = {12345L, 98765L, 112233L};
        float totalError = 0;
        int testCount = 0;

        for (long seed : seeds) {
            Context context = new SnapshotTest.MockStrategy(seed);

            float hexSize = World.getRoot(World.OVERWORLD).radius();
            int range = (int) (hexSize * 1.2f);
            int step = 100;

            Map<String, Integer> counts = new HashMap<>();
            long samples = 0;

            for (int z = -range; z <= range; z += step) {
                for (int x = -range; x <= range; x += step) {
                    long rootSeed = World.traverse(context, x, z, 1).seed;
                    long centerSeed = World.traverse(context, 0, 0, 1).seed;

                    if (rootSeed == centerSeed) {
                        Region child = World.traverse(context, x, z, 2).region;
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

                System.out.printf(
                        "Seed %d: CITY=%.1f%% FARMLAND=%.1f%% FOREST=%.1f%% Error=%.1f%%%n",
                        seed, cityPct, farmPct, forestPct, error);
            }
        }

        float avgError = totalError / testCount;
        System.out.printf("Average error: %.1f%%%n", avgError);

        assertTrue(avgError < 25, "SUBDIVISION strategy should have less than 25% total error, got " + avgError);
    }
}
