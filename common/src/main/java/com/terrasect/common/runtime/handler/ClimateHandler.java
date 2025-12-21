package com.terrasect.common.runtime.handler;

import com.terrasect.common.Terrasect;
import com.terrasect.common.runtime.Config;
import com.terrasect.common.runtime.ClimateModifier;
import com.terrasect.common.api.Region;
import com.terrasect.common.runtime.RegionField;
import com.terrasect.common.api.Context;
import com.terrasect.common.runtime.World;
import com.terrasect.common.devtools.MixinSampler;
import com.terrasect.common.generation.definition.ClimateSettings;
import net.minecraft.world.level.biome.Climate;

/**
 * Shared climate modification logic for platform mixins.
 * 
 * This class contains all the common climate handling code that is shared
 * between Fabric and NeoForge ClimateMixin implementations.
 * 
 * <p>Mixins should be thin - they only redirect calls to this handler.
 * All debug logging and statistics tracking is centralized here.
 */
public final class ClimateHandler {
    
    // Debug statistics
    private static int totalCalls = 0;
    private static int modifiedCount = 0;
    private static int noContextCount = 0;
    
    private ClimateHandler() {
        // Static utility class
    }
    
    /**
     * Reset debug statistics. Useful for testing.
     */
    public static void resetStats() {
        totalCalls = 0;
        modifiedCount = 0;
        noContextCount = 0;
    }
    
    /**
     * Get current statistics for debugging.
     */
    public static String getStats() {
        return String.format("total=%d, modified=%d, noContext=%d", 
            totalCalls, modifiedCount, noContextCount);
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
     * <p>This method handles all debug logging internally - mixins should
     * just call this method and return the result.
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
        
        totalCalls++;
        
        // Log first call to confirm handler is active
        if (totalCalls == 1) {
            Terrasect.LOGGER.info("ClimateHandler: First climate modification call at quart coords ({}, {}, {})", x, y, z);
        }
        
        // Log stats periodically
        if (totalCalls % 10000 == 0) {
            Terrasect.LOGGER.debug("ClimateHandler: Stats - {}", getStats());
        }
        
        if (context == null) {
            noContextCount++;
            // Log occasionally when context is missing
            if (noContextCount % 10000 == 1) {
                Terrasect.LOGGER.warn("ClimateHandler: No context available (count={})", noContextCount);
            }
            return ClimateResult.unmodified(originalTemperature, originalHumidity);
        }

        long seed = context.getSeed();
        int blockX = x << 2;
        int blockZ = z << 2;
        
        // Only record to sampler if explicitly enabled by tests
        boolean sampling = MixinSampler.isEnabled();
        if (sampling) {
            MixinSampler.recordSeed(seed);
        }
        
        // Get the region at this location using dimension from context
        String dimensionId = context.getDimensionId();
        Region region = World.getRegion(dimensionId, blockX, blockZ, context);
        if (region == null) {
            if (sampling) {
                MixinSampler.recordClimateCall(x, y, z, originalTemperature, originalHumidity,
                    originalTemperature, originalHumidity, null, false);
            }
            return ClimateResult.unmodified(originalTemperature, originalHumidity);
        }
        
        // Record region query for sampling (only if enabled)
        if (sampling) {
            MixinSampler.recordRegionQuery(blockX, blockZ, region.name());
        }
        
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
        
        modifiedCount++;
        
        // Record the climate modification for sampling (only if enabled)
        if (sampling) {
            MixinSampler.recordClimateCall(x, y, z, originalTemperature, originalHumidity,
                modifiedTemp, modifiedHumid, region.name(), true);
            MixinSampler.recordCoordinate(blockX, blockZ, region.name(), modifiedTemp, modifiedHumid);
        }
        
        // Log occasionally when we actually modify
        if (modifiedCount % 5000 == 1) {
            Terrasect.LOGGER.debug("ClimateHandler: Modified at ({}, {}) - temp: {} -> {}, humid: {} -> {}, region: {}",
                blockX, blockZ, originalTemperature, modifiedTemp,
                originalHumidity, modifiedHumid, region.name());
        }
        
        return new ClimateResult(modifiedTemp, modifiedHumid, true, region.name());
    }
    
    /**
     * Modify a Climate.TargetPoint based on region settings.
     * 
     * <p>This is the main entry point for ClimateMixin - it handles the entire flow:
     * <ol>
     *   <li>Get climate result from modifyClimate</li>
     *   <li>If modified, create new TargetPoint with updated temp/humidity</li>
     *   <li>If not modified, return the original TargetPoint</li>
     * </ol>
     * 
     * <p>This moves TargetPoint construction into common code, making mixins thinner.
     * 
     * @param context The generation context
     * @param x Quart X coordinate
     * @param y Quart Y coordinate
     * @param z Quart Z coordinate
     * @param original The original TargetPoint from vanilla sampling
     * @return The potentially modified TargetPoint
     */
    public static Climate.TargetPoint modifyTargetPoint(
            Context context,
            int x, int y, int z,
            Climate.TargetPoint original) {
        
        ClimateResult result = modifyClimate(
            context, x, y, z,
            original.temperature(),
            original.humidity()
        );
        
        if (!result.modified()) {
            return original;
        }
        
        return new Climate.TargetPoint(
            result.temperature(),
            result.humidity(),
            original.continentalness(),
            original.erosion(),
            original.depth(),
            original.weirdness()
        );
    }
}
