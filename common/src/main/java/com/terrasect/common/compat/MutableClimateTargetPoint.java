package com.terrasect.common.compat;

/**
 * Mixin-backed mutator interface for {@code Climate.TargetPoint}.
 *
 * <p>Implemented via loader mixins so hot paths can modify the sampler return value without
 * allocating a new {@code TargetPoint}.
 */
public interface MutableClimateTargetPoint {
    void terrasect$setTemperature(long temperature);
    void terrasect$setHumidity(long humidity);
    void terrasect$setContinentalness(long continentalness);
    void terrasect$setErosion(long erosion);
    void terrasect$setDepth(long depth);
    void terrasect$setWeirdness(long weirdness);
}

