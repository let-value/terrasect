package com.terrasect.common.runtime.handler;

import com.terrasect.common.Terrasect;
import com.terrasect.common.runtime.Config;
import com.terrasect.common.runtime.ClimateModifier;
import com.terrasect.common.api.Region;
import com.terrasect.common.runtime.TraversalResult;
import com.terrasect.common.api.Context;
import com.terrasect.common.runtime.World;
import com.terrasect.common.devtools.MixinSampler;
import com.terrasect.common.devtools.Profiler;
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
     * Result of climate modification containing all 6 climate values.
     */
    public record ClimateResult(
        long temperature,
        long humidity,
        long continentalness,
        long erosion,
        long depth,
        long weirdness,
        boolean modified,
        String regionName
    ) {
        public static ClimateResult unmodified(long temperature, long humidity, long continentalness,
                                                long erosion, long depth, long weirdness) {
            return new ClimateResult(temperature, humidity, continentalness, erosion, depth, weirdness, false, null);
        }
        
        public static ClimateResult unmodifiedWithRegion(long temperature, long humidity, long continentalness,
                                                          long erosion, long depth, long weirdness, String regionName) {
            return new ClimateResult(temperature, humidity, continentalness, erosion, depth, weirdness, false, regionName);
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
     * @param originalContinentalness Original continentalness value from sampler
     * @param originalErosion Original erosion value from sampler
     * @param originalDepth Original depth value from sampler
     * @param originalWeirdness Original weirdness value from sampler
     * @return ClimateResult with potentially modified climate values
     */
    public static ClimateResult modifyClimate(
            Context context,
            int x, int y, int z,
            long originalTemperature,
            long originalHumidity,
            long originalContinentalness,
            long originalErosion,
            long originalDepth,
            long originalWeirdness) {
        
        long t0 = Profiler.begin();
        
        totalCalls++;
        
        // Log first call to confirm handler is active
        if (totalCalls == 1) {
            Terrasect.LOGGER.info("ClimateHandler: First climate modification call at quart coords ({}, {}, {})", x, y, z);
        }
        
        
        if (context == null) {
            noContextCount++;
            // Log occasionally when context is missing
            if (noContextCount % 10000 == 1) {
                Terrasect.LOGGER.warn("ClimateHandler: No context available (count={})", noContextCount);
            }
            Profiler.end(Profiler.CLIMATE_MODIFY, t0);
            return ClimateResult.unmodified(originalTemperature, originalHumidity, originalContinentalness,
                originalErosion, originalDepth, originalWeirdness);
        }

        long seed = context.getSeed();
        int blockX = x << 2;
        int blockZ = z << 2;
                
        boolean sampling = MixinSampler.isEnabled();
        if (sampling) {
            MixinSampler.recordSeed(seed);
        }

        long t1 = Profiler.begin();
        TraversalResult traversal = World.getTraversalResult(context, blockX, blockZ);
        Profiler.end(Profiler.CLIMATE_REGION_LOOKUP, t1);
        
        if (traversal == null || traversal.region == null) {
            if (sampling) {
                MixinSampler.recordClimateCall(x, y, z, originalTemperature, originalHumidity,
                    originalTemperature, originalHumidity, null, false);
            }
            Profiler.end(Profiler.CLIMATE_MODIFY, t0);
            return ClimateResult.unmodified(originalTemperature, originalHumidity, originalContinentalness,
                originalErosion, originalDepth, originalWeirdness);
        }
        
        Region region = traversal.region;
        
        // Record region query for sampling (only if enabled)
        if (sampling) {
            MixinSampler.recordRegionQuery(blockX, blockZ, region.name());
        }
        
        // Get climate settings for this region
        ClimateSettings climate = region.definition().climate();
        if (climate == null) {
            Profiler.end(Profiler.CLIMATE_MODIFY, t0);
            return ClimateResult.unmodifiedWithRegion(originalTemperature, originalHumidity, originalContinentalness,
                originalErosion, originalDepth, originalWeirdness, region.name());
        }
        
        // Check if there are any climate modifications to apply
        if (climate.temperature() == null && climate.humidity() == null && climate.continentalness() == null &&
            climate.erosion() == null && climate.depth() == null && climate.weirdness() == null) {
            Profiler.end(Profiler.CLIMATE_MODIFY, t0);
            return ClimateResult.unmodifiedWithRegion(originalTemperature, originalHumidity, originalContinentalness,
                originalErosion, originalDepth, originalWeirdness, region.name());
        }
        
        // Calculate edge factor for smooth transitions between regions
        // edgeDistance is 1 at center, 0 at boundary
        // We invert it so edgeFactor=0 means center, edgeFactor=1 means at edge
        float edgeFactor = 1.0f - traversal.edgeDistance;

        // Calculate climate offsets for all 6 parameters
        long t3 = Profiler.begin();
        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
            climate,
            originalTemperature,
            originalHumidity,
            originalContinentalness,
            originalErosion,
            originalDepth,
            originalWeirdness,
            edgeFactor
        );
        Profiler.end(Profiler.CLIMATE_OFFSET_CALC, t3);
        
        // If no modifications needed, return original
        if (!offset.hasModifications()) {
            Profiler.end(Profiler.CLIMATE_MODIFY, t0);
            return ClimateResult.unmodifiedWithRegion(originalTemperature, originalHumidity, originalContinentalness,
                originalErosion, originalDepth, originalWeirdness, region.name());
        }
        
        // Apply all offsets
        long modifiedTemp = ClimateModifier.applyTemperatureOffset(originalTemperature, offset);
        long modifiedHumid = ClimateModifier.applyHumidityOffset(originalHumidity, offset);
        long modifiedCont = ClimateModifier.applyContinentalnessOffset(originalContinentalness, offset);
        long modifiedErosion = ClimateModifier.applyErosionOffset(originalErosion, offset);
        long modifiedDepth = ClimateModifier.applyDepthOffset(originalDepth, offset);
        long modifiedWeirdness = ClimateModifier.applyWeirdnessOffset(originalWeirdness, offset);
        
        modifiedCount++;
        
        // Record the climate modification for sampling (only if enabled)
        if (sampling) {
            MixinSampler.recordClimateCall(x, y, z, originalTemperature, originalHumidity,
                modifiedTemp, modifiedHumid, region.name(), true);
            MixinSampler.recordCoordinate(blockX, blockZ, region.name(), modifiedTemp, modifiedHumid);
        }
        
        Profiler.count(Profiler.ALLOC_CLIMATE_RESULT);
        Profiler.end(Profiler.CLIMATE_MODIFY, t0);
        return new ClimateResult(modifiedTemp, modifiedHumid, modifiedCont, 
            modifiedErosion, modifiedDepth, modifiedWeirdness, true, region.name());
    }
    
    /**
     * Modify a Climate.TargetPoint based on region settings.
     * 
     * <p>This is the main entry point for ClimateMixin - it handles the entire flow:
     * <ol>
     *   <li>Get climate result from modifyClimate</li>
     *   <li>If modified, create new TargetPoint with all 6 updated climate values</li>
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
            original.humidity(),
            original.continentalness(),
            original.erosion(),
            original.depth(),
            original.weirdness()
        );
        
        if (!result.modified()) {
            return original;
        }
        
        Profiler.count(Profiler.ALLOC_TARGET_POINT);
        return new Climate.TargetPoint(
            result.temperature(),
            result.humidity(),
            result.continentalness(),
            result.erosion(),
            result.depth(),
            result.weirdness()
        );
    }
}
