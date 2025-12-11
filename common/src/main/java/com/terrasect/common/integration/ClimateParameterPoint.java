package com.terrasect.common.integration;

/**
 * Compact representation of the six-dimensional climate cube used by Minecraft's multi-noise biome
 * selection. Terrasect builds these points to translate {@link RegionConstraints} into the data shape
 * expected by TerraBlender's {@code Region#addBiomes} contract.
 */
public record ClimateParameterPoint(
    ClimateRange temperature,
    ClimateRange humidity,
    ClimateRange continentalness,
    ClimateRange erosion,
    ClimateRange weirdness,
    ClimateRange depth
) {
    public ClimateParameterPoint {
        if (temperature == null || humidity == null || continentalness == null
            || erosion == null || weirdness == null || depth == null) {
            throw new IllegalArgumentException("All climate ranges are required");
        }
    }
}
