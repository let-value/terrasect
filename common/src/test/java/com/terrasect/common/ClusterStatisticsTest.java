package com.terrasect.common;

import com.terrasect.common.generation.ClusterDefinition;
import com.terrasect.common.generation.ClusterMapGenerator;
import com.terrasect.common.generation.ClusterSnapshotSampler;
import com.terrasect.common.generation.RegionDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterStatisticsTest {
    private static final long SEED = 123456789L;
    private static final int MAP_SIZE = 3_000;

    @Test
    void clusterSizingShouldStayWithinExpectedVariance() {
        ClusterDefinition cluster = ClusterDefinition.autoSize(java.util.List.of(
            RegionDefinition.of("village-1", 62_000, java.util.List.of("village-2")),
            RegionDefinition.of("village-2", 58_000, java.util.List.of("orchard")),
            RegionDefinition.of("orchard", 42_000, java.util.List.of("wilds")),
            RegionDefinition.of("wilds", 77_000, java.util.List.of("village-1")),
            RegionDefinition.of("ruins", 21_000, java.util.List.of("wilds"))
        ));

        ClusterMapGenerator.ClusterPattern pattern = new ClusterMapGenerator().generate(cluster, SEED);
        ClusterSnapshotSampler.SnapshotStatistics statistics = ClusterSnapshotSampler.sample(pattern, MAP_SIZE, MAP_SIZE);

        System.out.printf("Cluster stats: count=%d, mean=%.2f, std=%.2f, cv=%.2f%n",
            statistics.clusters().categoryCount(),
            statistics.clusters().mean(),
            statistics.clusters().standardDeviation(),
            statistics.clusters().coefficientOfVariation());
        System.out.printf("Cluster deviation: observed=%d, target=%d, meanRatio=%.2f, stdRatio=%.2f, cv=%.2f, minRatio=%.2f, maxRatio=%.2f, meanAbsError=%.2f%n",
            statistics.clusterDeviation().observedClusters(),
            statistics.clusterDeviation().targetArea(),
            statistics.clusterDeviation().meanRatio(),
            statistics.clusterDeviation().standardDeviationRatio(),
            statistics.clusterDeviation().coefficientOfVariation(),
            statistics.clusterDeviation().minRatio(),
            statistics.clusterDeviation().maxRatio(),
            statistics.clusterDeviation().meanAbsoluteError());
        System.out.printf("Region stats: count=%d, mean=%.2f, std=%.2f, cv=%.2f%n",
            statistics.regions().categoryCount(),
            statistics.regions().mean(),
            statistics.regions().standardDeviation(),
            statistics.regions().coefficientOfVariation());

        assertEquals((long) MAP_SIZE * MAP_SIZE, statistics.totalSamples(), "Sample coverage should match the image size");
        assertEquals(cluster.regions().size(), statistics.regions().categoryCount(), "All regions should be represented");
        assertTrue(statistics.clusters().categoryCount() >= 9, "Expect multiple clusters across the map");
        assertTrue(statistics.clusters().coefficientOfVariation() < 1.3,
            "Cluster sizing should remain within a reasonable spread for tuning");
        assertTrue(statistics.clusterDeviation().meanRatio() > 0.2 && statistics.clusterDeviation().meanRatio() < 4.0,
            "Mean cluster area should stay in a tunable range compared to the target");
        assertTrue(statistics.clusterDeviation().maxRatio() < 6.5,
            "Outlier clusters should not exceed a tolerable multiple of the target size");
        assertTrue(statistics.regions().coefficientOfVariation() < 1.05,
            "Region sizing spread should remain manageable for bias tuning");
    }
}
