package com.terrasect.common.runtime.handler;

import com.terrasect.common.runtime.Config;
import com.terrasect.common.runtime.ClimateModifier;
import com.terrasect.common.api.Region;
import com.terrasect.common.runtime.RegionField;
import com.terrasect.common.api.Context;
import com.terrasect.common.runtime.World;
import com.terrasect.common.devtools.MixinSampler;
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
        boolean modified,
        String regionName
    ) {
        public static ClimateResult unmodified(long temperature, long humidity) {
            return new ClimateResult(temperature, humidity, false, null);
        }
        
        public static ClimateResult unmodifiedWithRegion(long temperature, long humidity, String regionName) {
            return new ClimateResult(temperature, humidity, false, regionName);
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
            Context context,
            int x, int y, int z,
            long originalTemperature,
            long originalHumidity) {
        
        if (context == null) {
            return ClimateResult.unmodified(originalTemperature, originalHumidity);
        }

        long seed = context.getSeed();
        MixinSampler.recordSeed(seed);
        int blockX = x << 2;
        int blockZ = z << 2;
        
        // Get the region at this location using dimension from context
        String dimensionId = context.getDimensionId();
        Region region = World.getRegion(dimensionId, blockX, blockZ, context);
        if (region == null) {
            MixinSampler.recordClimateCall(x, y, z, originalTemperature, originalHumidity,
                originalTemperature, originalHumidity, null, false);
            return ClimateResult.unmodified(originalTemperature, originalHumidity);
        }
        
        // Record region query for sampling
        MixinSampler.recordRegionQuery(blockX, blockZ, region.name());
        
        // Get climate settings for this region
        ClimateSettings climate = region.definition().climate();
        if (climate == null) {
            return ClimateResult.unmodifiedWithRegion(originalTemperature, originalHumidity, region.name());
        }
        
        // Check if there are any climate modifications to apply
        if (climate.temperature() == null && climate.humidity() == null) {
            return ClimateResult.unmodifiedWithRegion(originalTemperature, originalHumidity, region.name());
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
            return ClimateResult.unmodifiedWithRegion(originalTemperature, originalHumidity, region.name());
        }
        
        // Apply the offsets
        long modifiedTemp = ClimateModifier.applyTemperatureOffset(originalTemperature, offset);
        long modifiedHumid = ClimateModifier.applyHumidityOffset(originalHumidity, offset);
        
        // Record the climate modification for sampling
        MixinSampler.recordClimateCall(x, y, z, originalTemperature, originalHumidity,
            modifiedTemp, modifiedHumid, region.name(), true);
        MixinSampler.recordCoordinate(blockX, blockZ, region.name(), modifiedTemp, modifiedHumid);
        
        return new ClimateResult(modifiedTemp, modifiedHumid, true, region.name());
    }
}
