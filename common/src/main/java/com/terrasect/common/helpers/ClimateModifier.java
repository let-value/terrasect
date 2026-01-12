package com.terrasect.common.helpers;

import com.terrasect.common.definition.ClimateSettings;
import com.terrasect.common.definition.ClimateSettings.ClimateRange;
import com.terrasect.common.mixin.ClimateTargetPointAccessor;
import net.minecraft.world.level.biome.Climate;

public final class ClimateModifier {
    public static final long CLIMATE_SCALE = 10000L;

    public static Climate.TargetPoint modify(ClimateSettings climate, Climate.TargetPoint point, float edgeFactor) {
        if (climate == null || point == null) {
            return point;
        }

        var temperature = climate.temperature();
        var humidity = climate.humidity();
        var continentalness = climate.continentalness();
        var erosion = climate.erosion();
        var depth = climate.depth();
        var weirdness = climate.weirdness();

        if (temperature == null
                && humidity == null
                && continentalness == null
                && erosion == null
                && depth == null
                && weirdness == null) {
            return point;
        }

        var originalTemp = point.temperature();
        var originalHumidity = point.humidity();
        var originalContinentalness = point.continentalness();
        var originalErosion = point.erosion();
        var originalDepth = point.depth();
        var originalWeirdness = point.weirdness();

        var newTemp = mapToRange(temperature, originalTemp);
        var newHumidity = mapToRange(humidity, originalHumidity);
        var newContinentalness = mapToRange(continentalness, originalContinentalness);
        var newErosion = mapToRange(erosion, originalErosion);
        var newDepth = mapToRange(depth, originalDepth);
        var newWeirdness = mapToRange(weirdness, originalWeirdness);

        if (newTemp == originalTemp
                && newHumidity == originalHumidity
                && newContinentalness == originalContinentalness
                && newErosion == originalErosion
                && newDepth == originalDepth
                && newWeirdness == originalWeirdness) {
            return point;
        }

        if ((Object) point instanceof ClimateTargetPointAccessor mutable) {
            mutable.terrasect$setTemperature(newTemp);
            mutable.terrasect$setHumidity(newHumidity);
            mutable.terrasect$setContinentalness(newContinentalness);
            mutable.terrasect$setErosion(newErosion);
            mutable.terrasect$setDepth(newDepth);
            mutable.terrasect$setWeirdness(newWeirdness);
            return point;
        }

        return new Climate.TargetPoint(newTemp, newHumidity, newContinentalness, newErosion, newDepth, newWeirdness);
    }

    private static long mapToRange(ClimateRange range, long originalValue) {
        var clampedOriginal = clamp(originalValue);
        if (range == null) {
            return clampedOriginal;
        }

        var min = range.min();
        var max = range.max();
        if (min <= -1.0f && max >= 1.0f) {
            return clampedOriginal;
        }

        var normalized = (clampedOriginal + CLIMATE_SCALE) / (2.0f * CLIMATE_SCALE);
        if (normalized < 0.0f) {
            normalized = 0.0f;
        } else if (normalized > 1.0f) {
            normalized = 1.0f;
        }

        var mapped = min + normalized * (max - min);
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

    private ClimateModifier() {
    }
}
