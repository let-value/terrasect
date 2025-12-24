package com.terrasect.common;

import com.terrasect.common.compat.MutableClimateTargetPoint;
import com.terrasect.common.definition.ClimateSettings;
import com.terrasect.common.definition.ClimateSettings.ClimateRange;

import net.minecraft.world.level.biome.Climate;

/**
 * Maps Minecraft climate parameters into region-defined {@link ClimateRange}s.
 *
 * <p>Prefer in-place mutation: when mixins are present, this modifies the sampler return value
 * without allocating.
 */
public final class ClimateModifier {
    public static final long CLIMATE_SCALE = 10000L;

    /**
     * Apply region climate constraints to a {@link Climate.TargetPoint}.
     *
     * <p>If {@code point} is mixin-extended to implement {@link MutableClimateTargetPoint}, it is mutated in place and
     * returned. Otherwise, a new {@link Climate.TargetPoint} is allocated when modifications are needed.
     */
    public static Climate.TargetPoint modify(ClimateSettings climate, Climate.TargetPoint point, float edgeFactor) {
        if (climate == null || point == null) {
            return point;
        }

        ClimateRange temperature = climate.temperature();
        ClimateRange humidity = climate.humidity();
        ClimateRange continentalness = climate.continentalness();
        ClimateRange erosion = climate.erosion();
        ClimateRange depth = climate.depth();
        ClimateRange weirdness = climate.weirdness();

        if (temperature == null
                && humidity == null
                && continentalness == null
                && erosion == null
                && depth == null
                && weirdness == null) {
            return point;
        }

        long originalTemp = point.temperature();
        long originalHumidity = point.humidity();
        long originalContinentalness = point.continentalness();
        long originalErosion = point.erosion();
        long originalDepth = point.depth();
        long originalWeirdness = point.weirdness();

        long newTemp = mapToRange(temperature, originalTemp);
        long newHumidity = mapToRange(humidity, originalHumidity);
        long newContinentalness = mapToRange(continentalness, originalContinentalness);
        long newErosion = mapToRange(erosion, originalErosion);
        long newDepth = mapToRange(depth, originalDepth);
        long newWeirdness = mapToRange(weirdness, originalWeirdness);

        if (newTemp == originalTemp
                && newHumidity == originalHumidity
                && newContinentalness == originalContinentalness
                && newErosion == originalErosion
                && newDepth == originalDepth
                && newWeirdness == originalWeirdness) {
            return point;
        }

        if ((Object) point instanceof MutableClimateTargetPoint mutable) {
            mutable.terrasect$setTemperature(newTemp);
            mutable.terrasect$setHumidity(newHumidity);
            mutable.terrasect$setContinentalness(newContinentalness);
            mutable.terrasect$setErosion(newErosion);
            mutable.terrasect$setDepth(newDepth);
            mutable.terrasect$setWeirdness(newWeirdness);
            return point;
        }

        return new Climate.TargetPoint(
                newTemp,
                newHumidity,
                newContinentalness,
                newErosion,
                newDepth,
                newWeirdness);
    }

    private static long mapToRange(ClimateRange range, long originalValue) {
        long clampedOriginal = clamp(originalValue);
        if (range == null) {
            return clampedOriginal;
        }

        float min = range.min();
        float max = range.max();
        if (min <= -1.0f && max >= 1.0f) {
            return clampedOriginal;
        }

        float normalized = (clampedOriginal + CLIMATE_SCALE) / (2.0f * CLIMATE_SCALE);
        if (normalized < 0.0f) {
            normalized = 0.0f;
        } else if (normalized > 1.0f) {
            normalized = 1.0f;
        }

        float mapped = min + normalized * (max - min);
        if (mapped < -1.0f) {
            mapped = -1.0f;
        } else if (mapped > 1.0f) {
            mapped = 1.0f;
        }

        return clamp((long) (mapped * CLIMATE_SCALE));
    }

    private static long clamp(long value) {
        if (value < -CLIMATE_SCALE) return -CLIMATE_SCALE;
        if (value > CLIMATE_SCALE) return CLIMATE_SCALE;
        return value;
    }

    private ClimateModifier() {}
}
