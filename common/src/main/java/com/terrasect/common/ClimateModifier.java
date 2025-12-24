package com.terrasect.common;

import com.terrasect.common.definition.ClimateSettings;
import com.terrasect.common.definition.ClimateSettings.ClimateRange;

/**
 * Common climate modification logic that can be used by both Fabric and NeoForge.
 * 
 * <p>This class computes climate parameter adjustments based on a region's ClimateSettings.
 * The adjustments are returned as offsets that mod loaders can apply to Minecraft's
 * Climate.TargetPoint values.
 * 
 * <p>Climate values in Minecraft are quantized longs in the range of roughly [-1, 1]
 * multiplied by 10000. Our ClimateSettings use ClimateRange to define allowed bounds.
 * 
 * <p>Range Mapping: Original world values are mapped from the full [-1, 1] range into
 * the specified target range, preserving relative variation:
 * <ul>
 *   <li>Original -1.0 → target min</li>
 *   <li>Original +1.0 → target max</li>
 *   <li>Values in between are linearly interpolated</li>
 * </ul>
 * 
 * <p>All 6 climate parameters:
 * <ul>
 *   <li><b>temperature</b>: [-1,1] - affects biome selection (cold to hot)</li>
 *   <li><b>humidity</b>: [-1,1] - affects biome selection (dry to wet)</li>
 *   <li><b>continentalness</b>: [-1,1] - controls terrain height (ocean to inland)</li>
 *   <li><b>erosion</b>: [-1,1] - controls terrain steepness (steep to flat)</li>
 *   <li><b>depth</b>: [-1,1] - affects underground features</li>
 *   <li><b>weirdness</b>: [-1,1] - affects biome variants (shattered, etc.)</li>
 * </ul>
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
        Long humidityOffset,
        Long continentalnessOffset,
        Long erosionOffset,
        Long depthOffset,
        Long weirdnessOffset
    ) {
        public static final ClimateOffset NONE = new ClimateOffset(null, null, null, null, null, null);
        
        /**
         * @return true if any climate parameter should be modified
         */
        public boolean hasModifications() {
            return temperatureOffset != null || humidityOffset != null || 
                   continentalnessOffset != null || erosionOffset != null ||
                   depthOffset != null || weirdnessOffset != null;
        }
    }
    
    /**
     * Calculate climate offsets based on the region's climate settings.
     * 
     * <p>For each parameter with a defined range, the original value is mapped
     * from the full [-1, 1] range into the target range, preserving variation.
     * 
     * @param climate The resolved climate settings from the region (may be null)
     * @param originalTemperature The original temperature value from vanilla sampler
     * @param originalHumidity The original humidity value from vanilla sampler
     * @param originalContinentalness The original continentalness value from vanilla sampler
     * @param originalErosion The original erosion value from vanilla sampler
     * @param originalDepth The original depth value from vanilla sampler
     * @param originalWeirdness The original weirdness value from vanilla sampler
     * @param edgeFactor Edge proximity factor (0.0 = deep inside region, 1.0 = at edge)
     *                   Currently unused to enforce hard climate edges.
     * @return Climate offsets to apply, or ClimateOffset.NONE if no modifications needed
     */
    public static ClimateOffset calculateOffset(
            ClimateSettings climate,
            long originalTemperature,
            long originalHumidity,
            long originalContinentalness,
            long originalErosion,
            long originalDepth,
            long originalWeirdness,
            float edgeFactor
    ) {
        if (climate == null) {
            return ClimateOffset.NONE;
        }
        
        ClimateRange targetTemp = climate.temperature();
        ClimateRange targetHumidity = climate.humidity();
        ClimateRange targetContinentalness = climate.continentalness();
        ClimateRange targetErosion = climate.erosion();
        ClimateRange targetDepth = climate.depth();
        ClimateRange targetWeirdness = climate.weirdness();
        
        // If no climate values are set, don't modify anything
        if (targetTemp == null && targetHumidity == null && targetContinentalness == null &&
            targetErosion == null && targetDepth == null && targetWeirdness == null) {
            return ClimateOffset.NONE;
        }
        
        Long tempOffset = null;
        Long humidityOffset = null;
        Long continentalnessOffset = null;
        Long erosionOffset = null;
        Long depthOffset = null;
        Long weirdnessOffset = null;
        
        // Map each parameter from full range into target range
        if (targetTemp != null) {
            tempOffset = calculateRangeOffset(targetTemp, originalTemperature);
        }
        
        if (targetHumidity != null) {
            humidityOffset = calculateRangeOffset(targetHumidity, originalHumidity);
        }
        
        if (targetContinentalness != null) {
            continentalnessOffset = calculateRangeOffset(targetContinentalness, originalContinentalness);
        }
        
        if (targetErosion != null) {
            erosionOffset = calculateRangeOffset(targetErosion, originalErosion);
        }
        
        if (targetDepth != null) {
            depthOffset = calculateRangeOffset(targetDepth, originalDepth);
        }
        
        if (targetWeirdness != null) {
            weirdnessOffset = calculateRangeOffset(targetWeirdness, originalWeirdness);
        }
        
        return new ClimateOffset(tempOffset, humidityOffset, continentalnessOffset, 
                                  erosionOffset, depthOffset, weirdnessOffset);
    }
    
    /**
     * Calculate the offset to map an original value into a target range.
     * 
     * <p>The original value (in quantized [-10000, 10000] range) is mapped from
     * the full [-1, 1] range into the target range, preserving relative position.
     * 
     * <p>Example: If target range is [-1.0, -0.5] (deep ocean):
     * <ul>
     *   <li>Original -10000 (-1.0) → -10000 (stays at min)</li>
     *   <li>Original 0 (0.0) → -7500 (maps to -0.75, center of target)</li>
     *   <li>Original +10000 (+1.0) → -5000 (maps to -0.5, max of target)</li>
     * </ul>
     * 
     * @param targetRange The allowed range for this parameter
     * @param originalValue Original quantized value from vanilla [-10000, 10000]
     * @return Offset to add to the original value to map it into the target range
     */
    private static long calculateRangeOffset(ClimateRange targetRange, long originalValue) {
        // Normalize original from [-10000, 10000] to [0, 1]
        float normalized = (originalValue + CLIMATE_SCALE) / (2.0f * CLIMATE_SCALE);
        normalized = Math.max(0.0f, Math.min(1.0f, normalized)); // Clamp to [0, 1]
        
        // Map into target range
        float mapped = targetRange.min() + normalized * (targetRange.max() - targetRange.min());
        
        // Clamp to valid climate range [-1, 1]
        mapped = Math.max(-1.0f, Math.min(1.0f, mapped));
        
        // Convert back to quantized value
        long targetQuantized = (long) (mapped * CLIMATE_SCALE);
        
        // Return the offset needed to reach the target
        return targetQuantized - originalValue;
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
        long value = originalTemperature + offset.temperatureOffset();
        return Math.max(-CLIMATE_SCALE, Math.min(CLIMATE_SCALE, value));
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
        long value = originalHumidity + offset.humidityOffset();
        return Math.max(-CLIMATE_SCALE, Math.min(CLIMATE_SCALE, value));
    }
    
    /**
     * Apply a climate offset to create a new continentalness value.
     * 
     * <p>Continentalness controls terrain height:
     * <ul>
     *   <li>-10000 = deep ocean (lowest terrain)</li>
     *   <li>0 = coast/sea level</li>
     *   <li>+10000 = inland/mountains (highest terrain)</li>
     * </ul>
     * 
     * @param originalContinentalness Original continentalness from Climate.TargetPoint
     * @param offset The offset to apply (may be null for no change)
     * @return Modified continentalness value
     */
    public static long applyContinentalnessOffset(long originalContinentalness, ClimateOffset offset) {
        if (offset.continentalnessOffset() == null) {
            return originalContinentalness;
        }
        long value = originalContinentalness + offset.continentalnessOffset();
        return Math.max(-CLIMATE_SCALE, Math.min(CLIMATE_SCALE, value));
    }
    
    /**
     * Apply a climate offset to create a new erosion value.
     * 
     * <p>Erosion controls terrain steepness:
     * <ul>
     *   <li>-10000 = very steep terrain, mountains</li>
     *   <li>0 = normal terrain</li>
     *   <li>+10000 = very flat, eroded plains</li>
     * </ul>
     * 
     * @param originalErosion Original erosion from Climate.TargetPoint
     * @param offset The offset to apply (may be null for no change)
     * @return Modified erosion value
     */
    public static long applyErosionOffset(long originalErosion, ClimateOffset offset) {
        if (offset.erosionOffset() == null) {
            return originalErosion;
        }
        long value = originalErosion + offset.erosionOffset();
        return Math.max(-CLIMATE_SCALE, Math.min(CLIMATE_SCALE, value));
    }
    
    /**
     * Apply a climate offset to create a new depth value.
     * 
     * @param originalDepth Original depth from Climate.TargetPoint
     * @param offset The offset to apply (may be null for no change)
     * @return Modified depth value
     */
    public static long applyDepthOffset(long originalDepth, ClimateOffset offset) {
        if (offset.depthOffset() == null) {
            return originalDepth;
        }
        long value = originalDepth + offset.depthOffset();
        return Math.max(-CLIMATE_SCALE, Math.min(CLIMATE_SCALE, value));
    }
    
    /**
     * Apply a climate offset to create a new weirdness value.
     * 
     * <p>Weirdness affects biome variant selection (e.g., shattered savanna vs normal).
     * 
     * @param originalWeirdness Original weirdness from Climate.TargetPoint
     * @param offset The offset to apply (may be null for no change)
     * @return Modified weirdness value
     */
    public static long applyWeirdnessOffset(long originalWeirdness, ClimateOffset offset) {
        if (offset.weirdnessOffset() == null) {
            return originalWeirdness;
        }
        long value = originalWeirdness + offset.weirdnessOffset();
        return Math.max(-CLIMATE_SCALE, Math.min(CLIMATE_SCALE, value));
    }
    
    private ClimateModifier() {
        // Utility class
    }
}
