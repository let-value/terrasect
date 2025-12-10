package com.terrasect.common;

import com.terrasect.common.generation.ClusterDefinition;
import com.terrasect.common.generation.ClusterMapGenerator;
import com.terrasect.common.generation.ClusterStatistics;
import com.terrasect.common.generation.RegionDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterStatisticsTest {
    private static final long SEED = 123456789L;

    @Test
    void shouldComputeReasonableSiteStatistics() {
        ClusterDefinition cluster = ClusterDefinition.autoSize(List.of(
            RegionDefinition.of("village-1", 62_000, List.of("village-2")),
            RegionDefinition.of("village-2", 58_000, List.of("orchard")),
            RegionDefinition.of("orchard", 42_000, List.of("wilds")),
            RegionDefinition.of("wilds", 77_000, List.of("village-1")),
            RegionDefinition.of("ruins", 21_000, List.of("wilds"))
        ));

        ClusterMapGenerator generator = new ClusterMapGenerator();
        ClusterMapGenerator.ClusterPattern pattern = generator.generate(cluster, SEED);
        ClusterStatistics stats = ClusterStatistics.gather(pattern, 1_200, 1_200);

        System.out.println(stats);
        assertTrue(stats.uniqueSites() > 0, "Statistics should see at least one cluster site");
        assertTrue(stats.uniqueSites() <= 8, "Cluster basins should stay coarse-grained over the sample");
        assertTrue(stats.minSamplesPerSite() >= 50_000, "Each basin should cover a meaningful footprint");
        assertTrue(stats.minSamplesPerRegion() >= 200, "Regions should avoid collapsing into single-pixel noise");
        assertTrue(stats.edgeChangeRatio() < 0.05, "Cluster ownership should not flip every few pixels");
    }
}
