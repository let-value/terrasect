package com.terrasect.common.integration;

/**
 * Simple weight wrapper mirroring TerraBlender's region weighting system while keeping the common
 * module isolated from loader-specific dependencies.
 */
public record RegionWeight(int value) {
    public RegionWeight {
        if (value <= 0) {
            throw new IllegalArgumentException("Region weight must be positive");
        }
    }

    public static RegionWeight of(int value) {
        return new RegionWeight(value);
    }
}
