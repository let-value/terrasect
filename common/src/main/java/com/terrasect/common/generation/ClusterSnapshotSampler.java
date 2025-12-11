package com.terrasect.common.generation;

import java.util.DoubleSummaryStatistics;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Samples a rendered cluster grid and derives descriptive statistics about cluster and region sizing.
 * The sampler is deterministic with respect to a {@link ClusterMapGenerator.ClusterPattern} so it can be
 * exercised in tests to flag unexpected shifts in the geographic partitioning produced by the generator.
 */
public final class ClusterSnapshotSampler {
    private ClusterSnapshotSampler() {
    }

    public static SnapshotStatistics sample(ClusterMapGenerator.ClusterPattern pattern, int width, int height) {
        Objects.requireNonNull(pattern, "pattern");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }

        ConcurrentHashMap<Long, LongAdder> clusterAreas = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, LongAdder> regionAreas = new ConcurrentHashMap<>();

        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                ClusterMapGenerator.ClusterLocation location = pattern.locateClusterAndRegion(x, y);
                clusterAreas.computeIfAbsent(location.site().key(), key -> new LongAdder()).increment();
                regionAreas.computeIfAbsent(location.regionIndex(), key -> new LongAdder()).increment();
            }
        });

        DistributionStatistics clusterStats = computeStats(clusterAreas);
        DistributionStatistics regionStats = computeStats(regionAreas);
        double targetMeanArea = pattern.targetClusterArea();
        TargetDeviationStatistics clusterDeviation = computeDeviationStats(clusterAreas, targetMeanArea);

        return new SnapshotStatistics(width * height, clusterStats, regionStats, clusterDeviation);
    }

    private static DistributionStatistics computeStats(Map<?, LongAdder> areas) {
        LongSummaryStatistics summary = areas.values().stream()
            .mapToLong(LongAdder::sum)
            .summaryStatistics();

        double mean = summary.getCount() == 0 ? 0.0 : summary.getAverage();
        double variance = mean == 0.0
            ? 0.0
            : areas.values().stream()
                .flatMapToDouble(v -> DoubleStream.of(Math.pow(v.sum() - mean, 2)))
                .sum() / summary.getCount();
        double stdDev = Math.sqrt(variance);
        double coefficientOfVariation = mean == 0.0 ? 0.0 : stdDev / mean;

        return new DistributionStatistics(
            summary.getCount(),
            summary.getSum(),
            mean,
            stdDev,
            coefficientOfVariation,
            summary.getMin(),
            summary.getMax()
        );
    }

    private static TargetDeviationStatistics computeDeviationStats(Map<?, LongAdder> areas, double targetArea) {
        if (targetArea <= 0.0 || areas.isEmpty()) {
            return new TargetDeviationStatistics(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        DoubleSummaryStatistics ratios = areas.values().stream()
            .mapToDouble(v -> v.sum() / targetArea)
            .summaryStatistics();

        double meanRatio = ratios.getAverage();
        double varianceRatio = meanRatio == 0.0
            ? 0.0
            : areas.values().stream()
                .flatMapToDouble(v -> DoubleStream.of(Math.pow((v.sum() / targetArea) - meanRatio, 2)))
                .sum() / ratios.getCount();
        double stdDevRatio = Math.sqrt(varianceRatio);
        double coefficientOfVariation = meanRatio == 0.0 ? 0.0 : stdDevRatio / meanRatio;

        double meanAbsoluteError = areas.values().stream()
            .mapToDouble(v -> Math.abs(v.sum() - targetArea))
            .average()
            .orElse(0.0);

        return new TargetDeviationStatistics(
            ratios.getCount(),
            Math.round(targetArea),
            meanRatio,
            stdDevRatio,
            coefficientOfVariation,
            ratios.getMin(),
            ratios.getMax(),
            meanAbsoluteError
        );
    }

    /**
     * Aggregated statistics for a set of sampled areas.
     * @param categoryCount number of clusters/regions seen in the sample
     * @param totalArea total pixel count seen for the set
     * @param mean arithmetic mean of area per category
     * @param standardDeviation standard deviation of area per category
     * @param coefficientOfVariation normalized dispersion of the distribution (std dev divided by mean)
     * @param min smallest observed area
     * @param max largest observed area
     */
    public record DistributionStatistics(long categoryCount, long totalArea, double mean, double standardDeviation,
                                         double coefficientOfVariation, long min, long max) {
    }

    /**
     * Summary of cluster- and region-level sizing for a sampled snapshot.
     * @param totalSamples number of pixels sampled from the snapshot
     * @param clusters statistics about cluster footprint size distribution
     * @param regions statistics about region footprint size distribution
     * @param clusterDeviation dispersion of actual cluster sizes compared to the configured target area
     */
    public record SnapshotStatistics(long totalSamples, DistributionStatistics clusters,
                                     DistributionStatistics regions, TargetDeviationStatistics clusterDeviation) {
    }

    /**
     * Comparison metrics for measured cluster areas relative to their requested target area.
     * @param observedClusters number of clusters with sampled area
     * @param targetArea target area requested for each cluster
     * @param meanRatio average ratio of observed cluster area to the target area (1.0 equals target)
     * @param standardDeviationRatio standard deviation of the ratios to show spread in over/under sizing
     * @param coefficientOfVariation normalized dispersion of the ratios
     * @param minRatio smallest observed ratio (under-sized clusters)
     * @param maxRatio largest observed ratio (over-sized clusters)
     * @param meanAbsoluteError average absolute difference between observed area and target area
     */
    public record TargetDeviationStatistics(long observedClusters, long targetArea, double meanRatio,
                                            double standardDeviationRatio, double coefficientOfVariation,
                                            double minRatio, double maxRatio, double meanAbsoluteError) {
    }
}
