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
        double meanSquaredDistanceToSite
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
    }

    /**
        * Samples a rectangular area and returns statistics describing cluster layout inside it.
        */
    public static ClusterStatistics gather(ClusterMapGenerator.ClusterPattern pattern, int width, int height) {
        long samples = (long) width * height;
        Map<Long, Long> counts = new HashMap<>();

        long[] previousRow = new long[width];
        boolean hasPreviousRow = false;
        long horizontalTransitions = 0L;
        long verticalTransitions = 0L;
        double totalSquaredDistance = 0.0;

        for (int y = 0; y < height; y++) {
            long lastKey = Long.MIN_VALUE;
            for (int x = 0; x < width; x++) {
                ClusterMapGenerator.ClusterSite site = pattern.siteForPoint(x, y);
                long key = site.key();
                counts.merge(key, 1L, Long::sum);

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

        int uniqueSites = counts.size();
        long minSamplesPerSite = Long.MAX_VALUE;
        long maxSamplesPerSite = Long.MIN_VALUE;
        double sumSquares = 0.0;
        for (long count : counts.values()) {
            minSamplesPerSite = Math.min(minSamplesPerSite, count);
            maxSamplesPerSite = Math.max(maxSamplesPerSite, count);
            sumSquares += (double) count * count;
        }

        double meanSamplesPerSite = counts.isEmpty() ? 0.0 : (double) samples / uniqueSites;
        double variance = counts.isEmpty() ? 0.0 : sumSquares / uniqueSites - meanSamplesPerSite * meanSamplesPerSite;
        double stdDev = Math.sqrt(Math.max(variance, 0.0));

        long adjacencyPairs = (long) (width - 1) * height + (long) width * (height - 1);
        double edgeChangeRatio = adjacencyPairs == 0 ? 0.0 : (horizontalTransitions + verticalTransitions) / (double) adjacencyPairs;
        double meanSquaredDistanceToSite = samples == 0 ? 0.0 : totalSquaredDistance / samples;

        return new ClusterStatistics(
            width,
            height,
            samples,
            uniqueSites,
            counts.isEmpty() ? 0L : minSamplesPerSite,
            counts.isEmpty() ? 0L : maxSamplesPerSite,
            meanSamplesPerSite,
            stdDev,
            edgeChangeRatio,
            meanSquaredDistanceToSite
        );
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
            '}';
    }
}
