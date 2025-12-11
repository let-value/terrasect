package com.terrasect.common.integration;

/**
 * Inclusive climate slice used to mirror TerraBlender's {@code Climate.Parameter} ranges without
 * depending on the Minecraft classes at test time.
 */
public record ClimateRange(double min, double max) {
    public ClimateRange {
        if (Double.isNaN(min) || Double.isNaN(max)) {
            throw new IllegalArgumentException("Climate bounds must be numbers");
        }
        if (min > max) {
            throw new IllegalArgumentException("min cannot be greater than max");
        }
    }

    public boolean contains(double value) {
        return value >= min && value <= max;
    }

    public ClimateRange clamp(double lower, double upper) {
        double clampedMin = Math.max(min, lower);
        double clampedMax = Math.min(max, upper);
        if (clampedMin > clampedMax) {
            clampedMin = clampedMax;
        }
        return new ClimateRange(clampedMin, clampedMax);
    }
}
