package com.terrasect.common.integration;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * High level definition of the limits a Terrasect region imposes on generation. This mirrors
 * TerraBlender's Region hooks by covering biomes, structures, height ranges, and the six climate axes
 * used by Minecraft's multi-noise system.
 */
public final class RegionConstraints {
    private final Set<String> allowedBiomes;
    private final Set<String> blockedBiomes;
    private final Set<String> allowedStructures;
    private final Set<String> blockedStructures;
    private final ClimateRange temperatureRange;
    private final ClimateRange humidityRange;
    private final ClimateRange continentalnessRange;
    private final ClimateRange erosionRange;
    private final ClimateRange weirdnessRange;
    private final ClimateRange depthRange;
    private final AltitudeRange altitudeRange;

    private RegionConstraints(Set<String> allowedBiomes, Set<String> blockedBiomes, Set<String> allowedStructures,
                              Set<String> blockedStructures, ClimateRange temperatureRange, ClimateRange humidityRange,
                              ClimateRange continentalnessRange, ClimateRange erosionRange, ClimateRange weirdnessRange,
                              ClimateRange depthRange, AltitudeRange altitudeRange) {
        this.allowedBiomes = allowedBiomes;
        this.blockedBiomes = blockedBiomes;
        this.allowedStructures = allowedStructures;
        this.blockedStructures = blockedStructures;
        this.temperatureRange = temperatureRange;
        this.humidityRange = humidityRange;
        this.continentalnessRange = continentalnessRange;
        this.erosionRange = erosionRange;
        this.weirdnessRange = weirdnessRange;
        this.depthRange = depthRange;
        this.altitudeRange = altitudeRange;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean allows(GenerationContext context) {
        Objects.requireNonNull(context, "context");
        if (!isAllowedValue(context.biomeId(), allowedBiomes, blockedBiomes)) {
            return false;
        }
        if (!isAllowedValue(context.structureId(), allowedStructures, blockedStructures)) {
            return false;
        }
        if (!temperatureRange.contains(context.temperature())) return false;
        if (!humidityRange.contains(context.humidity())) return false;
        if (!continentalnessRange.contains(context.continentalness())) return false;
        if (!erosionRange.contains(context.erosion())) return false;
        if (!weirdnessRange.contains(context.weirdness())) return false;
        if (!depthRange.contains(context.depth())) return false;
        return altitudeRange.contains(context.altitude());
    }

    public ClimateRange temperatureRange() {
        return temperatureRange;
    }

    public ClimateRange humidityRange() {
        return humidityRange;
    }

    public ClimateRange continentalnessRange() {
        return continentalnessRange;
    }

    public ClimateRange erosionRange() {
        return erosionRange;
    }

    public ClimateRange weirdnessRange() {
        return weirdnessRange;
    }

    public ClimateRange depthRange() {
        return depthRange;
    }

    public AltitudeRange altitudeRange() {
        return altitudeRange;
    }

    private boolean isAllowedValue(String value, Set<String> allowed, Set<String> blocked) {
        if (blocked.contains(value)) {
            return false;
        }
        if (allowed.isEmpty()) {
            return true;
        }
        return allowed.contains(value);
    }

    public static final class Builder {
        private static final ClimateRange DEFAULT = new ClimateRange(-2.0, 2.0);
        private final Set<String> allowedBiomes = new HashSet<>();
        private final Set<String> blockedBiomes = new HashSet<>();
        private final Set<String> allowedStructures = new HashSet<>();
        private final Set<String> blockedStructures = new HashSet<>();
        private ClimateRange temperatureRange = DEFAULT;
        private ClimateRange humidityRange = DEFAULT;
        private ClimateRange continentalnessRange = DEFAULT;
        private ClimateRange erosionRange = DEFAULT;
        private ClimateRange weirdnessRange = DEFAULT;
        private ClimateRange depthRange = DEFAULT;
        private AltitudeRange altitudeRange = new AltitudeRange(-64, 320);

        public Builder allowBiome(String biomeId) {
            allowedBiomes.add(requireNonBlank(biomeId, "biomeId"));
            return this;
        }

        public Builder blockBiome(String biomeId) {
            blockedBiomes.add(requireNonBlank(biomeId, "biomeId"));
            return this;
        }

        public Builder allowStructure(String structureId) {
            allowedStructures.add(requireNonBlank(structureId, "structureId"));
            return this;
        }

        public Builder blockStructure(String structureId) {
            blockedStructures.add(requireNonBlank(structureId, "structureId"));
            return this;
        }

        public Builder temperatureRange(double min, double max) {
            this.temperatureRange = new ClimateRange(min, max);
            return this;
        }

        public Builder humidityRange(double min, double max) {
            this.humidityRange = new ClimateRange(min, max);
            return this;
        }

        public Builder continentalnessRange(double min, double max) {
            this.continentalnessRange = new ClimateRange(min, max);
            return this;
        }

        public Builder erosionRange(double min, double max) {
            this.erosionRange = new ClimateRange(min, max);
            return this;
        }

        public Builder weirdnessRange(double min, double max) {
            this.weirdnessRange = new ClimateRange(min, max);
            return this;
        }

        public Builder depthRange(double min, double max) {
            this.depthRange = new ClimateRange(min, max);
            return this;
        }

        public Builder altitudeRange(int min, int max) {
            this.altitudeRange = new AltitudeRange(min, max);
            return this;
        }

        public RegionConstraints build() {
            return new RegionConstraints(
                Collections.unmodifiableSet(new HashSet<>(allowedBiomes)),
                Collections.unmodifiableSet(new HashSet<>(blockedBiomes)),
                Collections.unmodifiableSet(new HashSet<>(allowedStructures)),
                Collections.unmodifiableSet(new HashSet<>(blockedStructures)),
                temperatureRange,
                humidityRange,
                continentalnessRange,
                erosionRange,
                weirdnessRange,
                depthRange,
                altitudeRange
            );
        }

        private String requireNonBlank(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value;
        }
    }

    /**
     * Inclusive altitude range to allow downstream checks against Y-level based narrative gating.
     */
    public record AltitudeRange(int min, int max) {
        public AltitudeRange {
            if (min > max) {
                throw new IllegalArgumentException("min cannot be greater than max");
            }
        }

        public boolean contains(int value) {
            return value >= min && value <= max;
        }
    }
}
