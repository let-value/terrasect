package com.terrasect.common.runtime.handler;

import com.terrasect.common.Terrasect;
import com.terrasect.common.api.Context;
import com.terrasect.common.api.Region;
import com.terrasect.common.generation.definition.ClimateSettings;
import com.terrasect.common.runtime.World;

/**
 * Shared terrain modification logic for platform mixins.
 * 
 * <p>This handler constrains terrain height by intercepting block placement
 * in NoiseBasedChunkGenerator.doFill(). When a solid block would be placed
 * above the region's maxHeight, we return null so the aquifer can decide
 * whether to fill with water (below sea level) or air (above sea level).
 */
public final class TerrainHandler {
    
    // Debug statistics
    private static int totalCalls = 0;
    private static int constrainedCount = 0;
    private static boolean loggedFirstCall = false;
    private static boolean loggedFirstConstrain = false;
    
    private TerrainHandler() {
        // Static utility class
    }
    
    /**
     * Reset debug statistics. Useful for testing.
     */
    public static void resetStats() {
        totalCalls = 0;
        constrainedCount = 0;
        loggedFirstCall = false;
        loggedFirstConstrain = false;
    }
    
    /**
     * Get current statistics for debugging.
     */
    public static String getStats() {
        return String.format("total=%d, constrained=%d", totalCalls, constrainedCount);
    }
    
    /**
     * Get the max height constraint for a specific location.
     * Returns null if no constraint applies.
     * 
     * @param context The generation context
     * @param blockX The block X coordinate
     * @param blockZ The block Z coordinate
     * @return The max height, or null if unconstrained
     */
    public static Integer getMaxHeight(Context context, int blockX, int blockZ) {
        totalCalls++;
        
        // Log first call to confirm handler is active
        if (!loggedFirstCall) {
            loggedFirstCall = true;
            Terrasect.LOGGER.info("TerrainHandler: First getMaxHeight call at ({}, {})", blockX, blockZ);
        }
        
        if (context == null) {
            return null;
        }
        
        Region region = World.getRegion(context.getDimensionId(), blockX, blockZ, context);
        if (region == null) {
            return null;
        }
        
        ClimateSettings climate = region.definition().climate();
        if (climate == null || !climate.hasHeightConstraints()) {
            return null;
        }
        
        Integer maxHeight = climate.maxHeight();
        
        // Log first constrained location
        if (maxHeight != null && !loggedFirstConstrain) {
            loggedFirstConstrain = true;
            constrainedCount++;
            Terrasect.LOGGER.info("TerrainHandler: First height constraint at ({}, {}), region={}, maxHeight={}", 
                blockX, blockZ, region.name(), maxHeight);
        } else if (maxHeight != null) {
            constrainedCount++;
        }
        
        return maxHeight;
    }
}
