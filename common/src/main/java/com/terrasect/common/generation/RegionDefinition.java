package com.terrasect.common.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes an individual region within a cluster. The {@code targetArea} is expressed in blocks
 * and is used as a relative weight when shaping the cluster. The {@code adjacentTo} list allows
 * callers to request that a region spawn near another region's origin inside a cluster.
 */
public final class RegionDefinition {
    private final String name;
    private final int targetArea;
    private final List<String> adjacentTo;

    private RegionDefinition(String name, int targetArea, List<String> adjacentTo) {
        this.name = Objects.requireNonNull(name, "name");
        this.targetArea = targetArea;
        this.adjacentTo = List.copyOf(adjacentTo);
    }

    public static RegionDefinition of(String name, int targetArea, List<String> adjacentTo) {
        if (targetArea <= 0) {
            throw new IllegalArgumentException("targetArea must be positive");
        }
        List<String> safeAdjacent = new ArrayList<>(Objects.requireNonNull(adjacentTo, "adjacentTo"));
        safeAdjacent.removeIf(String::isBlank);
        return new RegionDefinition(name, targetArea, safeAdjacent);
    }

    public static RegionDefinition of(String name, int targetArea) {
        return new RegionDefinition(name, targetArea, Collections.emptyList());
    }

    public String name() {
        return name;
    }

    public int targetArea() {
        return targetArea;
    }

    public List<String> adjacentTo() {
        return adjacentTo;
    }
}
