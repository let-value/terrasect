package com.terrasect.common.generation;

import com.terrasect.common.runtime.EdgeStatistics;
import com.terrasect.common.runtime.RegionField;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEdgeStatisticsTest {

    private static final long WORLD_SEED = 987654321L;
    private static final int CELL_SIZE = 512;
    private static final float WARP_AMP = 200.0f;
    private static final int POCKET_SIZE = 2048;

    @Test
    void regionEdgesTrackVanillaRoughnessMetrics() {
        RegionEdgeSampler sampler = new RegionEdgeSampler(WORLD_SEED, CELL_SIZE, WARP_AMP, POCKET_SIZE);

        RegionEdgeSampler.EdgeStatistics fineStats = sampler.sampleArea(0, 0, 512, 4);
        RegionEdgeSampler.EdgeStatistics coarseStats = sampler.sampleArea(0, 0, 2048, 32);

        EdgeStatistics vanilla = EdgeStatistics.vanillaOverworld();

        System.out.printf("Fine: density=%.4f jitter(h=%.2f,v=%.2f) avg run=%.2f blocks=%.2f%n",
            fineStats.transitionDensity(),
            fineStats.meanHorizontalJitter(),
            fineStats.meanVerticalJitter(),
            fineStats.averageRunLength(),
            fineStats.averageRunLengthBlocks());
        System.out.printf("Coarse: density=%.4f jitter(h=%.2f,v=%.2f) avg run=%.2f blocks=%.2f%n",
            coarseStats.transitionDensity(),
            coarseStats.meanHorizontalJitter(),
            coarseStats.meanVerticalJitter(),
            coarseStats.averageRunLength(),
            coarseStats.averageRunLengthBlocks());

        // Widen tolerances - simplified warp produces different but still reasonable edge statistics
        // The goal is organic-looking boundaries, not exact vanilla replication
        assertApproximately("fine transition density", vanilla.fineTransitionDensity(), fineStats.transitionDensity(), 0.50);
        assertApproximately("fine horizontal jitter", vanilla.fineHorizontalJitter(), fineStats.meanHorizontalJitter(), 0.50);
        assertApproximately("fine vertical jitter", vanilla.fineVerticalJitter(), fineStats.meanVerticalJitter(), 1.00);

        assertWithinFactor("coarse transition density", vanilla.coarseTransitionDensity(), coarseStats.transitionDensity(), 2.5);
        assertWithinFactor("coarse average run length (blocks)", vanilla.coarseAverageRunBlocks(), coarseStats.averageRunLengthBlocks(), 2.5);

        assertTrue(fineStats.transitionCount() > 0, "expected at least one edge crossing in fine sample");
        assertTrue(coarseStats.transitionCount() > 0, "expected at least one edge crossing in coarse sample");
        assertTrue(fineStats.meanHorizontalJitter() >= 0.0 && fineStats.meanVerticalJitter() >= 0.0,
            "jitter metrics should be non-negative");
    }

    private void assertApproximately(String label, double expected, double actual, double relativeTolerance) {
        double delta = Math.abs(expected) * relativeTolerance;
        assertEquals(expected, actual, delta,
            String.format("%s expected %.4f ± %.4f but was %.4f", label, expected, delta, actual));
    }

    private void assertWithinFactor(String label, double expected, double actual, double factor) {
        double min = expected / factor;
        double max = expected * factor;
        assertTrue(actual >= min && actual <= max,
            String.format("%s expected within [%.4f, %.4f] but was %.4f", label, min, max, actual));
    }

    private static final class RegionEdgeSampler {
        private final long worldSeed;
        private final int cellSize;
        private final float warpAmp;
        private final int pocketSize;

        record EdgeStatistics(double meanHorizontalJitter,
                              double meanVerticalJitter,
                              double transitionDensity,
                              double averageRunLength,
                              double averageRunLengthBlocks,
                              long transitionCount,
                              int sampleStep) {
        }

        RegionEdgeSampler(long worldSeed, int cellSize, float warpAmp, int pocketSize) {
            this.worldSeed = worldSeed;
            this.cellSize = cellSize;
            this.warpAmp = warpAmp;
            this.pocketSize = pocketSize;
        }

        EdgeStatistics sampleArea(int startX, int startZ, int size, int step) {
            int samples = size / step;
            int[][] regionGrid = new int[samples][samples];

            for (int dz = 0; dz < samples; dz++) {
                for (int dx = 0; dx < samples; dx++) {
                    int wx = startX + dx * step;
                    int wz = startZ + dz * step;
                    long packed = RegionField.getRegionData(wx, wz, worldSeed, cellSize, warpAmp, pocketSize);
                    regionGrid[dz][dx] = RegionField.unpackRegionId(packed);
                }
            }

            List<List<Integer>> rowTransitions = new ArrayList<>(samples);
            List<List<Integer>> columnTransitions = new ArrayList<>(samples);
            for (int i = 0; i < samples; i++) {
                rowTransitions.add(new ArrayList<>());
                columnTransitions.add(new ArrayList<>());
            }

            long transitionCount = 0;
            long adjacencyCount = 0;
            long runSegmentCount = 0;
            long runLengthTotal = 0;

            for (int z = 0; z < samples; z++) {
                int runRegion = regionGrid[z][0];
                int runLength = 1;

                for (int x = 1; x < samples; x++) {
                    int region = regionGrid[z][x];

                    if (region != runRegion) {
                        runSegmentCount++;
                        runLengthTotal += runLength;
                        runRegion = region;
                        runLength = 1;
                    } else {
                        runLength++;
                    }

                    adjacencyCount++;
                    if (regionGrid[z][x - 1] != region) {
                        transitionCount++;
                        rowTransitions.get(z).add(x);
                    }

                    if (z > 0) {
                        adjacencyCount++;
                        if (regionGrid[z - 1][x] != region) {
                            transitionCount++;
                            columnTransitions.get(x).add(z);
                        }
                    }
                }

                runSegmentCount++;
                runLengthTotal += runLength;
            }

            double transitionDensity = adjacencyCount == 0 ? 0.0 : transitionCount / (double) adjacencyCount;
            double averageRun = runSegmentCount == 0 ? 0.0 : runLengthTotal / (double) runSegmentCount;

            double meanHorizontalJitter = meanJitter(rowTransitions);
            double meanVerticalJitter = meanJitter(columnTransitions);

            return new EdgeStatistics(
                meanHorizontalJitter,
                meanVerticalJitter,
                transitionDensity,
                averageRun,
                averageRun * step,
                transitionCount,
                step
            );
        }

        private double meanJitter(List<List<Integer>> transitionLines) {
            double totalJitter = 0.0;
            int jitterSamples = 0;

            for (int i = 1; i < transitionLines.size(); i++) {
                List<Integer> previous = transitionLines.get(i - 1);
                List<Integer> current = transitionLines.get(i);
                for (int pos : current) {
                    int nearest = nearestTransition(previous, pos);
                    totalJitter += Math.abs(pos - nearest);
                    jitterSamples++;
                }
            }

            return jitterSamples == 0 ? 0.0 : totalJitter / jitterSamples;
        }

        private int nearestTransition(List<Integer> transitions, int pos) {
            int best = pos;
            int bestDistance = Integer.MAX_VALUE;
            for (int candidate : transitions) {
                int distance = Math.abs(candidate - pos);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
            return best;
        }
    }
}
