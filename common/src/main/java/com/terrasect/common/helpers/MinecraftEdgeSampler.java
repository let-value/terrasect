package com.terrasect.common.helpers;

import com.terrasect.common.compat.BiomeCompat;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility for collecting biome adjacency/run-length statistics from the vanilla noise sampler.
 * <p>
 * The gathered statistics mirror the metrics our own world partitioner uses: large-scale edge
 * jitter (from warping), transition density, and per-parameter climate deltas that describe how
 * aggressive vanilla noise blending is at biome borders.
 */
public class MinecraftEdgeSampler {

    public record EdgeStatistics(Map<String, Integer> biomeCounts,
                                 Map<String, Integer> transitionCounts,
                                 Map<Integer, Integer> runLengthHistogram,
                                 EdgeRoughness roughness,
                                 ClimateEdgeDeltas climateEdgeDeltas,
                                 int sampleStep) {
        public double averageRunLength() {
            long totalCells = 0;
            long weighted = 0;
            for (Map.Entry<Integer, Integer> entry : runLengthHistogram.entrySet()) {
                weighted += (long) entry.getKey() * entry.getValue();
                totalCells += entry.getValue();
            }
            return totalCells == 0 ? 0.0 : (double) weighted / totalCells;
        }

        public double averageRunLengthBlocks() {
            return averageRunLength() * sampleStep;
        }

        public int distinctBiomes() {
            return biomeCounts.size();
        }

        public int transitionTotal() {
            return transitionCounts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public record EdgeRoughness(double meanHorizontalJitter,
                                double meanVerticalJitter,
                                double transitionDensity) {
    }

    public record ClimateEdgeDeltas(long samples,
                                    double averageTemperatureDelta,
                                    double averageHumidityDelta,
                                    double averageContinentalnessDelta,
                                    double averageErosionDelta,
                                    double averageWeirdnessDelta,
                                    double averageDepthDelta) {
        public boolean hasSamples() {
            return samples > 0;
        }
    }

    private record ClimateSample(double temperature, double humidity, double continentalness,
                                 double erosion, double weirdness, double depth) {
    }

    private final MultiNoiseBiomeSource biomeSource;
    private final Climate.Sampler climateSampler;

    public MinecraftEdgeSampler(MultiNoiseBiomeSource biomeSource, Climate.Sampler climateSampler) {
        this.biomeSource = biomeSource;
        this.climateSampler = climateSampler;
    }

    public EdgeStatistics sampleArea(int startX, int startZ, int size, int step) {
        int samples = size / step;
        String[][] biomeGrid = new String[samples][samples];
        ClimateSample[][] climateGrid = new ClimateSample[samples][samples];

        for (int dz = 0; dz < samples; dz++) {
            for (int dx = 0; dx < samples; dx++) {
                int wx = startX + dx * step;
                int wz = startZ + dz * step;
                biomeGrid[dz][dx] = biomeKeyAt(wx, wz);
                climateGrid[dz][dx] = climateAt(wx, wz);
            }
        }

        Map<String, Integer> biomeCounts = new HashMap<>();
        Map<String, Integer> transitionCounts = new HashMap<>();
        Map<Integer, Integer> runLengthHistogram = new HashMap<>();

        List<List<Integer>> rowTransitions = new ArrayList<>(samples);
        List<List<Integer>> columnTransitions = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            rowTransitions.add(new ArrayList<>());
            columnTransitions.add(new ArrayList<>());
        }

        ClimateDeltaAccumulator climateAccumulator = new ClimateDeltaAccumulator();
        long totalAdjacencies = 0;

        for (int z = 0; z < samples; z++) {
            String runBiome = null;
            int runLength = 0;
            for (int x = 0; x < samples; x++) {
                String biome = biomeGrid[z][x];
                biomeCounts.merge(biome, 1, Integer::sum);

                if (!Objects.equals(runBiome, biome)) {
                    if (runLength > 0) {
                        runLengthHistogram.merge(runLength, 1, Integer::sum);
                    }
                    runBiome = biome;
                    runLength = 1;
                } else {
                    runLength++;
                }

                if (x > 0) {
                    totalAdjacencies++;
                    if (!biomeGrid[z][x - 1].equals(biome)) {
                        incrementTransition(transitionCounts, biomeGrid[z][x - 1], biome);
                        rowTransitions.get(z).add(x);
                        climateAccumulator.add(climateGrid[z][x - 1], climateGrid[z][x]);
                    }
                }
                if (z > 0) {
                    totalAdjacencies++;
                    if (!biomeGrid[z - 1][x].equals(biome)) {
                        incrementTransition(transitionCounts, biomeGrid[z - 1][x], biome);
                        columnTransitions.get(x).add(z);
                        climateAccumulator.add(climateGrid[z - 1][x], climateGrid[z][x]);
                    }
                }
            }
            if (runLength > 0) {
                runLengthHistogram.merge(runLength, 1, Integer::sum);
            }
        }

        EdgeRoughness roughness = new EdgeRoughness(
            meanJitter(rowTransitions),
            meanJitter(columnTransitions),
            transitionCounts.isEmpty() || totalAdjacencies == 0
                ? 0.0
                : transitionCounts.values().stream().mapToLong(Integer::longValue).sum() / (double) totalAdjacencies
        );

        return new EdgeStatistics(biomeCounts, transitionCounts, runLengthHistogram, roughness, climateAccumulator.finish(), step);
    }

    private String biomeKeyAt(int x, int z) {
        Holder<Biome> biome = biomeSource.getNoiseBiome(x >> 2, 0, z >> 2, climateSampler);
        return BiomeCompat.getBiomeId(biome);
    }

    private ClimateSample climateAt(int x, int z) {
        Climate.TargetPoint point = climateSampler.sample(x, 0, z);
        return new ClimateSample(
            point.temperature(),
            point.humidity(),
            point.continentalness(),
            point.erosion(),
            point.weirdness(),
            point.depth()
        );
    }

    private void incrementTransition(Map<String, Integer> transitionCounts, String a, String b) {
        String key = a.compareTo(b) <= 0 ? a + "->" + b : b + "->" + a;
        transitionCounts.merge(key, 1, Integer::sum);
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

    private static final class ClimateDeltaAccumulator {
        private long samples = 0;
        private double temperature = 0.0;
        private double humidity = 0.0;
        private double continentalness = 0.0;
        private double erosion = 0.0;
        private double weirdness = 0.0;
        private double depth = 0.0;

        void add(ClimateSample a, ClimateSample b) {
            samples++;
            temperature += Math.abs(a.temperature - b.temperature);
            humidity += Math.abs(a.humidity - b.humidity);
            continentalness += Math.abs(a.continentalness - b.continentalness);
            erosion += Math.abs(a.erosion - b.erosion);
            weirdness += Math.abs(a.weirdness - b.weirdness);
            depth += Math.abs(a.depth - b.depth);
        }

        ClimateEdgeDeltas finish() {
            double inv = samples == 0 ? 0.0 : 1.0 / samples;
            return new ClimateEdgeDeltas(
                samples,
                temperature * inv,
                humidity * inv,
                continentalness * inv,
                erosion * inv,
                weirdness * inv,
                depth * inv
            );
        }
    }
}
