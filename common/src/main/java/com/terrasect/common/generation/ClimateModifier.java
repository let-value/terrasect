package com.terrasect.common.generation;

import com.terrasect.common.generation.definition.ClimateSettings;

/**
 * Common climate modification logic that can be used by both Fabric and NeoForge.
 * 
 * This class computes climate parameter adjustments based on a region's ClimateSettings.
 * The adjustments are returned as offsets that mod loaders can apply to Minecraft's
 * Climate.TargetPoint values.
 * 
 * Climate values in Minecraft are quantized longs in the range of roughly [-1, 1]
 * multiplied by 10000. Our ClimateSettings use normalized floats [0, 1] which
 * we convert to the appropriate offset.
 */
public final class ClimateModifier {
    
    /**
     * The scale factor Minecraft uses for climate parameters.
     * Climate.quantizeCoord multiplies by 10000.
     */
    public static final long CLIMATE_SCALE = 10000L;
    
    /**
     * Result of climate modification calculation.
     * Contains offsets to apply to each climate parameter.
     * Null values indicate no modification should be applied.
     */
    public record ClimateOffset(
        Long temperatureOffset,
        Long humidityOffset
    ) {
        public static final ClimateOffset NONE = new ClimateOffset(null, null);
        
        /**
         * @return true if any climate parameter should be modified
         */
        public boolean hasModifications() {
            return temperatureOffset != null || humidityOffset != null;
        }
    }
    
    /**
     * Calculate climate offsets based on the region's climate settings.
     * 
     * @param climate The resolved climate settings from the region (may be null)
     * @param originalTemperature The original temperature value from vanilla sampler
     * @param originalHumidity The original humidity value from vanilla sampler
     * @param edgeFactor Edge proximity factor (0.0 = deep inside region, 1.0 = at edge)
     * @return Climate offsets to apply, or ClimateOffset.NONE if no modifications needed
     */
    public static ClimateOffset calculateOffset(
            ClimateSettings climate,
            long originalTemperature,
            long originalHumidity,
            float edgeFactor
    ) {
        if (climate == null) {
            return ClimateOffset.NONE;
        }
        
        Float targetTemp = climate.temperature();
        Float targetHumidity = climate.humidity();
        
        // If no climate values are set, don't modify anything
        if (targetTemp == null && targetHumidity == null) {
            return ClimateOffset.NONE;
        }
        
        Long tempOffset = null;
        Long humidityOffset = null;
        
        if (targetTemp != null) {
            tempOffset = calculateParameterOffset(targetTemp, originalTemperature, edgeFactor);
        }
        
        if (targetHumidity != null) {
            humidityOffset = calculateParameterOffset(targetHumidity, originalHumidity, edgeFactor);
        }
        
        return new ClimateOffset(tempOffset, humidityOffset);
    }
    
    /**
     * Calculate the offset needed to shift a climate parameter towards a target value.
     * 
     * The target is specified as a normalized value [0, 1] where:
     * - 0.0 = coldest/driest (maps to -1.0 in Minecraft's internal scale)
     * - 0.5 = neutral (maps to 0.0)
     * - 1.0 = hottest/wettest (maps to 1.0)
     * 
     * The offset is blended using the edge factor so that edges transition smoothly
     * between regions with different climate settings.
     * 
     * @param targetNormalized Target value in [0, 1] range
     * @param originalValue Original quantized value from vanilla
     * @param edgeFactor Edge proximity (0 = center of region, 1 = at boundary)
     * @return Offset to add to the original value
     */
    private static long calculateParameterOffset(float targetNormalized, long originalValue, float edgeFactor) {
        // Convert target from [0,1] to [-1,1] and then to quantized form
        float targetMapped = (targetNormalized * 2.0f) - 1.0f;
        long targetQuantized = (long) (targetMapped * CLIMATE_SCALE);
        
        // Calculate how much we need to shift to reach the target
        long delta = targetQuantized - originalValue;
        
        // Blend strength: full effect in region center, reduced at edges for smooth transitions
        // At edge (edgeFactor=1), we want less influence to blend with neighbor
        float blendStrength = 1.0f - (edgeFactor * 0.7f);
        
        return (long) (delta * blendStrength);
    }
    
    /**
     * Apply a climate offset to create a new temperature value.
     * 
     * @param originalTemperature Original temperature from Climate.TargetPoint
     * @param offset The offset to apply (may be null for no change)
     * @return Modified temperature value
     */
    public static long applyTemperatureOffset(long originalTemperature, ClimateOffset offset) {
        if (offset.temperatureOffset() == null) {
            return originalTemperature;
        }
        return originalTemperature + offset.temperatureOffset();
    }
    
    /**
     * Apply a climate offset to create a new humidity value.
     * 
     * @param originalHumidity Original humidity from Climate.TargetPoint
     * @param offset The offset to apply (may be null for no change)
     * @return Modified humidity value
     */
    public static long applyHumidityOffset(long originalHumidity, ClimateOffset offset) {
        if (offset.humidityOffset() == null) {
            return originalHumidity;
        }
        return originalHumidity + offset.humidityOffset();
    }
    
    private ClimateModifier() {
        // Utility class
    }
}
