package com.terrasect.common.mixin;

/**
 * Mixin-backed mutator interface for {@code Climate.TargetPoint}.
 *
 * <p>Implemented via loader mixins so hot paths can modify the sampler return value without
 * allocating a new {@code TargetPoint}.
 */
public interface ClimateTargetPointAccessor {
    void terrasect$setTemperature(long temperature);

    void terrasect$setHumidity(long humidity);

    void terrasect$setContinentalness(long continentalness);

    void terrasect$setErosion(long erosion);

    void terrasect$setDepth(long depth);

    void terrasect$setWeirdness(long weirdness);
}
