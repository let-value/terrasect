package com.terrasect.common.integration;

/**
 * Snapshot of the values that matter when deciding whether a world generation location belongs to a
 * Terrasect-constrained region. The primitive fields mirror the climate axes used by Minecraft and
 * TerraBlender while keeping tests independent of the actual game classes.
 */
public record GenerationContext(
    String biomeId,
    String structureId,
    double temperature,
    double humidity,
    double continentalness,
    double erosion,
    double weirdness,
    double depth,
    int altitude
) {
    public GenerationContext {
        if (biomeId == null || biomeId.isBlank()) {
            throw new IllegalArgumentException("biomeId is required");
        }
        if (structureId == null) {
            throw new IllegalArgumentException("structureId cannot be null; use an empty string for none");
        }
    }

    public GenerationContext withBiome(String biome) {
        return new GenerationContext(biome, structureId, temperature, humidity, continentalness, erosion, weirdness, depth, altitude);
    }

    public GenerationContext withStructure(String structure) {
        return new GenerationContext(biomeId, structure, temperature, humidity, continentalness, erosion, weirdness, depth, altitude);
    }

    public GenerationContext withAltitude(int newAltitude) {
        return new GenerationContext(biomeId, structureId, temperature, humidity, continentalness, erosion, weirdness, depth, newAltitude);
    }

    public GenerationContext withTemperature(double newTemperature) {
        return new GenerationContext(biomeId, structureId, newTemperature, humidity, continentalness, erosion, weirdness, depth, altitude);
    }
}
