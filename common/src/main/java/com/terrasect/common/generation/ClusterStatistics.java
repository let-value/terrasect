package com.terrasect.common.generation;

import java.util.HashMap;
import java.util.Map;

/**
 * Computes aggregate statistics for a generated cluster area. The metrics are intended to help
 * tune the underlying math so clusters do not collapse into noisy pixels or grow unbounded. This
 * class is purely diagnostic: it walks a sample area, counts how many samples map to each cluster
 * site, and measures how frequently cluster ownership changes between adjacent samples.
 */
public final class ClusterStatistics {
    private final int width;
    private final int height;
    private final long samples;
    private final int uniqueSites;
    private final long minSamplesPerSite;
    private final long maxSamplesPerSite;
    private final double meanSamplesPerSite;
    private final double samplesPerSiteStdDev;
    private final double edgeChangeRatio;
    private final double meanSquaredDistanceToSite;
    private final int uniqueRegions;
    private final long minSamplesPerRegion;
    private final long maxSamplesPerRegion;
    private final double meanSamplesPerRegion;
    private final double samplesPerRegionStdDev;

    private ClusterStatistics(
        int width,
        int height,
        long samples,
        int uniqueSites,
        long minSamplesPerSite,
        long maxSamplesPerSite,
        double meanSamplesPerSite,
        double samplesPerSiteStdDev,
        double edgeChangeRatio,
        double meanSquaredDistanceToSite,
        int uniqueRegions,
        long minSamplesPerRegion,
        long maxSamplesPerRegion,
        double meanSamplesPerRegion,
        double samplesPerRegionStdDev
    ) {
        this.width = width;
        this.height = height;
        this.samples = samples;
        this.uniqueSites = uniqueSites;
        this.minSamplesPerSite = minSamplesPerSite;
        this.maxSamplesPerSite = maxSamplesPerSite;
        this.meanSamplesPerSite = meanSamplesPerSite;
        this.samplesPerSiteStdDev = samplesPerSiteStdDev;
        this.edgeChangeRatio = edgeChangeRatio;
        this.meanSquaredDistanceToSite = meanSquaredDistanceToSite;
        this.uniqueRegions = uniqueRegions;
        this.minSamplesPerRegion = minSamplesPerRegion;
        this.maxSamplesPerRegion = maxSamplesPerRegion;
        this.meanSamplesPerRegion = meanSamplesPerRegion;
        this.samplesPerRegionStdDev = samplesPerRegionStdDev;
    }

    /**
        * Samples a rectangular area and returns statistics describing cluster layout inside it.
        */
    public static ClusterStatistics gather(ClusterMapGenerator.ClusterPattern pattern, int width, int height) {
        long samples = (long) width * height;
        Map<Long, Long> siteCounts = new HashMap<>();
        Map<RegionKey, Long> regionCounts = new HashMap<>();

        long[] previousRow = new long[width];
        boolean hasPreviousRow = false;
        long horizontalTransitions = 0L;
        long verticalTransitions = 0L;
        double totalSquaredDistance = 0.0;

        for (int y = 0; y < height; y++) {
            long lastKey = Long.MIN_VALUE;
            for (int x = 0; x < width; x++) {
                ClusterMapGenerator.ClusterLocation location = pattern.locateClusterAndRegion(x, y);
                ClusterMapGenerator.ClusterSite site = location.site();
                long key = site.key();
                siteCounts.merge(key, 1L, Long::sum);
                regionCounts.merge(new RegionKey(key, location.regionIndex()), 1L, Long::sum);

                if (x > 0 && key != lastKey) {
                    horizontalTransitions++;
                }
                if (hasPreviousRow && key != previousRow[x]) {
                    verticalTransitions++;
                }

                totalSquaredDistance += site.squaredDistanceTo(x, y);
                previousRow[x] = key;
                lastKey = key;
            }
            hasPreviousRow = true;
        }

        StatsSummary siteSummary = summarizeCounts(siteCounts, samples);
        StatsSummary regionSummary = summarizeCounts(regionCounts, samples);

        long adjacencyPairs = (long) (width - 1) * height + (long) width * (height - 1);
        double edgeChangeRatio = adjacencyPairs == 0 ? 0.0 : (horizontalTransitions + verticalTransitions) / (double) adjacencyPairs;
        double meanSquaredDistanceToSite = samples == 0 ? 0.0 : totalSquaredDistance / samples;

        return new ClusterStatistics(
            width,
            height,
            samples,
            siteSummary.unique,
            siteSummary.min,
            siteSummary.max,
            siteSummary.mean,
            siteSummary.stdDev,
            edgeChangeRatio,
            meanSquaredDistanceToSite,
            regionSummary.unique,
            regionSummary.min,
            regionSummary.max,
            regionSummary.mean,
            regionSummary.stdDev
        );
    }

    private static StatsSummary summarizeCounts(Map<?, Long> counts, long samples) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        double sumSquares = 0.0;
        for (long count : counts.values()) {
            min = Math.min(min, count);
            max = Math.max(max, count);
            sumSquares += (double) count * count;
        }

        int unique = counts.size();
        double mean = unique == 0 ? 0.0 : (double) samples / unique;
        double variance = unique == 0 ? 0.0 : sumSquares / unique - mean * mean;
        double stdDev = Math.sqrt(Math.max(variance, 0.0));

        return new StatsSummary(unique, unique == 0 ? 0L : min, unique == 0 ? 0L : max, mean, stdDev);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public long samples() {
        return samples;
    }

    public int uniqueSites() {
        return uniqueSites;
    }

    public long minSamplesPerSite() {
        return minSamplesPerSite;
    }

    public long maxSamplesPerSite() {
        return maxSamplesPerSite;
    }

    public double meanSamplesPerSite() {
        return meanSamplesPerSite;
    }

    public double samplesPerSiteStdDev() {
        return samplesPerSiteStdDev;
    }

    public double edgeChangeRatio() {
        return edgeChangeRatio;
    }

    public double meanSquaredDistanceToSite() {
        return meanSquaredDistanceToSite;
    }

    public int uniqueRegions() {
        return uniqueRegions;
    }

    public long minSamplesPerRegion() {
        return minSamplesPerRegion;
    }

    public long maxSamplesPerRegion() {
        return maxSamplesPerRegion;
    }

    public double meanSamplesPerRegion() {
        return meanSamplesPerRegion;
    }

    public double samplesPerRegionStdDev() {
        return samplesPerRegionStdDev;
    }

    @Override
    public String toString() {
        return "ClusterStatistics{" +
            "width=" + width +
            ", height=" + height +
            ", samples=" + samples +
            ", uniqueSites=" + uniqueSites +
            ", minSamplesPerSite=" + minSamplesPerSite +
            ", maxSamplesPerSite=" + maxSamplesPerSite +
            ", meanSamplesPerSite=" + String.format("%.2f", meanSamplesPerSite) +
            ", samplesPerSiteStdDev=" + String.format("%.2f", samplesPerSiteStdDev) +
            ", edgeChangeRatio=" + String.format("%.4f", edgeChangeRatio) +
            ", meanSquaredDistanceToSite=" + String.format("%.2f", meanSquaredDistanceToSite) +
            ", uniqueRegions=" + uniqueRegions +
            ", minSamplesPerRegion=" + minSamplesPerRegion +
            ", maxSamplesPerRegion=" + maxSamplesPerRegion +
            ", meanSamplesPerRegion=" + String.format("%.2f", meanSamplesPerRegion) +
            ", samplesPerRegionStdDev=" + String.format("%.2f", samplesPerRegionStdDev) +
            '}';
    }

    private record RegionKey(long siteKey, int regionIndex) {
    }

    private record StatsSummary(int unique, long min, long max, double mean, double stdDev) {
    }
}
