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
        double clusterMeanToTargetRatio = targetMeanArea == 0.0
            ? 0.0
            : clusterStats.mean() / targetMeanArea;

        return new SnapshotStatistics(width * height, clusterStats, regionStats, clusterMeanToTargetRatio);
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
     * @param clusterMeanToTargetRatio comparison of the mean sampled cluster size relative to the configured target
     */
    public record SnapshotStatistics(long totalSamples, DistributionStatistics clusters,
                                     DistributionStatistics regions, double clusterMeanToTargetRatio) {
    }
}
