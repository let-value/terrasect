package com.terrasect.common.generation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.terrasect.common.Context;
import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class StrategyComparisonTest {

    @Test public void compareStrategies() {
        var seed = 987654321L;
        var context = new SnapshotTest.MockStrategy(seed);

        System.out.println("=== Strategy Comparison Test ===\n");

        System.out.println("--- VORONOI Strategy ---");
        testWithStrategy(GenerationStrategy.voronoi(), context);

        System.out.println("\n--- SUBDIVISION Strategy ---");
        testWithStrategy(GenerationStrategy.subdivision(), context);

        System.out.println("\n--- TEMPLATE Strategy ---");
        testWithStrategy(GenerationStrategy.template(), context);
    }

    private void testWithStrategy(GenerationStrategy strategy, Context context) {
        var registry = new RegionRegistry();
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

        var hexSize = World.getRoot(World.OVERWORLD).radius();

        var range = (int) (hexSize * 1.5f);
        var step = 100;

        var depth2Counts = new HashMap<String, Integer>();
        var totalSamples = 0L;

        for (var z = -range; z <= range; z += step) {
            for (var x = -range; x <= range; x += step) {

                var rootSeed = World.traverse(context, x, z, 1).seed;
                var centerSeed = World.traverse(context, 0, 0, 1).seed;

                if (rootSeed == centerSeed) {
                    var child = World.traverse(context, x, z, 2).region;
                    depth2Counts.merge(child.name(), 1, Integer::sum);
                    totalSamples++;
                }
            }
        }

        System.out.println("  Samples: " + totalSamples);
        System.out.println("  Expected: CITY=20%, FARMLAND=60%, FOREST=20%");

        if (totalSamples > 0) {
            for (var entry : depth2Counts.entrySet()) {
                var pct = 100.0f * entry.getValue() / totalSamples;
                System.out.printf("  %s: %.1f%% (%d samples)%n", entry.getKey(), pct, entry.getValue());
            }

            var cityError = Math.abs(getPercent(depth2Counts, "CITY", totalSamples) - 20.0f);
            var farmError = Math.abs(getPercent(depth2Counts, "FARMLAND", totalSamples) - 60.0f);
            var forestError = Math.abs(getPercent(depth2Counts, "FOREST", totalSamples) - 20.0f);
            var totalError = cityError + farmError + forestError;

            System.out.printf("  Total error from expected: %.1f%%%n", totalError);
        }
    }

    private float getPercent(Map<String, Integer> counts, String name, long total) {
        return 100.0f * counts.getOrDefault(name, 0) / total;
    }

    @Test public void testSubdivisionBudgetAccuracy() {

        var registry = new RegionRegistry();
        registry.region("ROOT")
                .child("CIVILIZATION", civ -> civ.strategy(GenerationStrategy.subdivision())
                        .child("CITY", city -> city.radius(1000)
                                .strategy(GenerationStrategy.subdivision())
                                .child("DOWNTOWN", d -> d.radius(700))
                                .child("SUBURBS", s -> s.radius(700)))
                        .child("FARMLAND", farm -> farm.radius(1732))
                        .child("FOREST", forest -> forest.radius(1000)))
                .child("WILDERNESS", wild -> wild.radius(1000));

        World.register(registry.build("ROOT"), World.OVERWORLD);

        long[] seeds = {12345L, 98765L, 112233L};
        var totalError = 0F;
        var testCount = 0;

        for (long seed : seeds) {
            var context = new SnapshotTest.MockStrategy(seed);

            var hexSize = World.getRoot(World.OVERWORLD).radius();
            var range = (int) (hexSize * 1.2f);
            var step = 100;

            var counts = new HashMap<String, Integer>();
            var samples = 0L;

            for (var z = -range; z <= range; z += step) {
                for (var x = -range; x <= range; x += step) {
                    var rootSeed = World.traverse(context, x, z, 1).seed;
                    var centerSeed = World.traverse(context, 0, 0, 1).seed;

                    if (rootSeed == centerSeed) {
                        var child = World.traverse(context, x, z, 2).region;
                        counts.merge(child.name(), 1, Integer::sum);
                        samples++;
                    }
                }
            }

            if (samples > 0) {
                var cityPct = getPercent(counts, "CITY", samples);
                var farmPct = getPercent(counts, "FARMLAND", samples);
                var forestPct = getPercent(counts, "FOREST", samples);

                var error = Math.abs(cityPct - 20) + Math.abs(farmPct - 60) + Math.abs(forestPct - 20);
                totalError += error;
                testCount++;

                System.out.printf(
                        "Seed %d: CITY=%.1f%% FARMLAND=%.1f%% FOREST=%.1f%% Error=%.1f%%%n",
                        seed, cityPct, farmPct, forestPct, error);
            }
        }

        var avgError = totalError / testCount;
        System.out.printf("Average error: %.1f%%%n", avgError);

        assertTrue(avgError < 25, "SUBDIVISION strategy should have less than 25% total error, got " + avgError);
    }
}
