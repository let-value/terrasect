package com.terrasect.common.integration;

import java.util.List;
import java.util.Objects;

/**
 * Bridges Terrasect's narrative constraints into the parameter cubes TerraBlender expects for biome
 * registration. This class intentionally avoids depending on the TerraBlender jar so the logic can be
 * exercised in isolation through unit tests.
 */
public final class TerraBlenderRegionAdapter {
    private final String regionId;
    private final RegionWeight weight;

    public TerraBlenderRegionAdapter(String regionId, RegionWeight weight) {
        if (regionId == null || regionId.isBlank()) {
            throw new IllegalArgumentException("regionId is required");
        }
        this.regionId = regionId;
        this.weight = Objects.requireNonNull(weight, "weight");
    }

    public String regionId() {
        return regionId;
    }

    public RegionWeight weight() {
        return weight;
    }

    /**
     * A real TerraBlender {@code Region#addBiomes} implementation would emit many parameter points
     * to reflect distinct biome bands. Terrasect works at a larger narrative scale, so the adapter
     * emits a single combined envelope capturing every axis from the {@link RegionConstraints}.
     */
    public List<ClimateParameterPoint> toParameterPoints(RegionConstraints constraints) {
        Objects.requireNonNull(constraints, "constraints");
        ClimateParameterPoint point = new ClimateParameterPoint(
            constraints.temperatureRange(),
            constraints.humidityRange(),
            constraints.continentalnessRange(),
            constraints.erosionRange(),
            constraints.weirdnessRange(),
            constraints.depthRange()
        );
        return List.of(point);
    }
}
