package com.terrasect.common.generation;

import java.util.List;
import java.util.Objects;

/**
 * Captures the high level configuration for a cluster generation run. The {@code clusterSize}
 * represents the width and height of a single cluster tile in blocks. If the size is set to zero
 * the generator will compute a sensible square based on the requested region areas.
 */
public final class ClusterDefinition {
    private final List<RegionDefinition> regions;
    private final int clusterSize;

    private ClusterDefinition(List<RegionDefinition> regions, int clusterSize) {
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("At least one region is required");
        }
        this.regions = List.copyOf(regions);
        this.clusterSize = clusterSize;
    }

    public static ClusterDefinition of(List<RegionDefinition> regions, int clusterSize) {
        if (clusterSize < 0) {
            throw new IllegalArgumentException("clusterSize must be positive or zero");
        }
        return new ClusterDefinition(Objects.requireNonNull(regions, "regions"), clusterSize);
    }

    public static ClusterDefinition autoSize(List<RegionDefinition> regions) {
        return new ClusterDefinition(regions, 0);
    }

    public List<RegionDefinition> regions() {
        return regions;
    }

    public int clusterSize() {
        return clusterSize;
    }

    public long totalTargetArea() {
        return regions.stream().mapToLong(RegionDefinition::targetArea).sum();
    }
}
