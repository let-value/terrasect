package com.terrasect.common.generation.mixin;

import com.terrasect.common.generation.Config;
import com.terrasect.common.generation.ClimateModifier;
import com.terrasect.common.generation.Region;
import com.terrasect.common.generation.RegionField;
import com.terrasect.common.generation.Strategy;
import com.terrasect.common.generation.World;
import com.terrasect.common.generation.definition.ClimateSettings;

/**
 * Shared climate modification logic for platform mixins.
 * 
 * This class contains all the common climate handling code that is shared
 * between Fabric and NeoForge ClimateMixin implementations.
 */
public final class ClimateHandler {
    
    private ClimateHandler() {
        // Static utility class
    }
    
    /**
     * Result of climate modification containing the new temperature and humidity values.
     */
    public record ClimateResult(
        long temperature,
        long humidity,
        boolean modified
    ) {
        public static ClimateResult unmodified(long temperature, long humidity) {
            return new ClimateResult(temperature, humidity, false);
        }
    }
    
    /**
     * Calculate modified climate values based on region settings.
     * 
     * @param context The generation strategy context (null if not available)
     * @param x Biome coordinate X (not block coordinate)
     * @param y Biome coordinate Y
     * @param z Biome coordinate Z
     * @param originalTemperature Original temperature value from sampler
     * @param originalHumidity Original humidity value from sampler
     * @return ClimateResult with potentially modified temperature/humidity
     */
    public static ClimateResult modifyClimate(
            Strategy context,
            int x, int y, int z,
            long originalTemperature,
            long originalHumidity) {
        
        if (context == null) {
            return ClimateResult.unmodified(originalTemperature, originalHumidity);
        }

        long seed = context.getSeed();
        int blockX = x << 2;
        int blockZ = z << 2;
        
        // Get the region at this location
        Region region = World.getRegion(blockX, blockZ, context);
        if (region == null) {
            return ClimateResult.unmodified(originalTemperature, originalHumidity);
        }
        
        // Get climate settings for this region
        ClimateSettings climate = region.definition().climate();
        if (climate == null) {
            return ClimateResult.unmodified(originalTemperature, originalHumidity);
        }
        
        // Check if there are any climate modifications to apply
        if (climate.temperature() == null && climate.humidity() == null) {
            return ClimateResult.unmodified(originalTemperature, originalHumidity);
        }
        
        // Calculate edge factor for smooth transitions between regions
        // edge value is HIGH when deep inside region, LOW at boundaries
        // We invert it so edgeFactor=0 means center, edgeFactor=1 means at edge
        long regionData = RegionField.getRegionData(blockX, blockZ, seed, 512, 200.0f, 2048);
        float edge = RegionField.unpackEdge(regionData);
        float normalizedEdge = Math.min(1.0f, edge / Config.EDGE_SCALE);
        float edgeFactor = 1.0f - normalizedEdge; // Invert: 0 at center, 1 at boundary

        // Calculate climate offsets using common logic
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            climate,
            originalTemperature,
            originalHumidity,
            edgeFactor
        );
        
        // If no modifications needed, return original
        if (!offset.hasModifications()) {
            return ClimateResult.unmodified(originalTemperature, originalHumidity);
        }
        
        // Apply the offsets
        return new ClimateResult(
            ClimateModifier.applyTemperatureOffset(originalTemperature, offset),
            ClimateModifier.applyHumidityOffset(originalHumidity, offset),
            true
        );
    }
}
