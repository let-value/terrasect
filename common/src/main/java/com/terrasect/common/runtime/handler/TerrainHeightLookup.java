package com.terrasect.common.runtime.handler;

import com.terrasect.common.api.Context;
import com.terrasect.common.api.Region;
import com.terrasect.common.devtools.Profiler;
import com.terrasect.common.generation.definition.ClimateSettings;
import com.terrasect.common.runtime.World;
import org.jetbrains.annotations.Nullable;

/**
 * Pre-computed height lookup table for a single chunk (16x16 blocks).
 * 
 * <p>This class is allocated once per NoiseChunk and pre-calculates the maxHeight
 * for every (x, z) coordinate in the chunk. This avoids repeated region lookups
 * and climate checks during terrain generation hot paths.
 * 
 * <p>Design principles:
 * <ul>
 *   <li>O(1) lookup after construction - just array indexing</li>
 *   <li>No allocations at query time - uses primitive int array</li>
 *   <li>Uses Integer.MIN_VALUE as sentinel for "no constraint"</li>
 * </ul>
 */
public final class TerrainHeightLookup {
    
    /** Sentinel value indicating no height constraint */
    public static final int NO_CONSTRAINT = Integer.MIN_VALUE;
    
    /** Pre-computed heights: heights[(x & 15) + (z & 15) * 16] */
    private final int[] heights;
    
    /** The chunk's minimum block X coordinate */
    private final int chunkMinX;
    
    /** The chunk's minimum block Z coordinate */
    private final int chunkMinZ;
    
    /** Whether any position in this chunk has a height constraint */
    private final boolean hasAnyConstraint;
    
    private TerrainHeightLookup(int[] heights, int chunkMinX, int chunkMinZ, boolean hasAnyConstraint) {
        this.heights = heights;
        this.chunkMinX = chunkMinX;
        this.chunkMinZ = chunkMinZ;
        this.hasAnyConstraint = hasAnyConstraint;
    }
    
    /**
     * Build a height lookup table for a chunk.
     * 
     * @param context The generation context (may be null)
     * @param chunkMinX The chunk's minimum block X coordinate
     * @param chunkMinZ The chunk's minimum block Z coordinate
     * @return A populated lookup table, or null if no context
     */
    public static @Nullable TerrainHeightLookup build(@Nullable Context context, int chunkMinX, int chunkMinZ) {
        long t0 = Profiler.begin();
        
        if (context == null) {
            Profiler.end(Profiler.TERRAIN_LOOKUP_BUILD, t0);
            return null;
        }
        
        int[] heights = new int[256]; // 16 * 16
        boolean hasAnyConstraint = false;
        
        for (int localZ = 0; localZ < 16; localZ++) {
            int blockZ = chunkMinZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int blockX = chunkMinX + localX;
                int index = localX + localZ * 16;
                
                Integer maxHeight = computeMaxHeight(context, blockX, blockZ);
                if (maxHeight != null) {
                    heights[index] = maxHeight;
                    hasAnyConstraint = true;
                } else {
                    heights[index] = NO_CONSTRAINT;
                }
            }
        }
        
        Profiler.end(Profiler.TERRAIN_LOOKUP_BUILD, t0);
        
        // If no constraints exist, return null to allow fast-path in density functions
        if (!hasAnyConstraint) {
            return null;
        }
        
        return new TerrainHeightLookup(heights, chunkMinX, chunkMinZ, hasAnyConstraint);
    }
    
    /**
     * Get the max height constraint for a block position.
     * Returns NO_CONSTRAINT if there is no height limit or if coordinates are outside chunk bounds.
     * 
     * @param blockX World block X coordinate
     * @param blockZ World block Z coordinate
     * @return The max height, or NO_CONSTRAINT if unconstrained or out of bounds
     */
    public int getMaxHeight(int blockX, int blockZ) {
        int localX = blockX - chunkMinX;
        int localZ = blockZ - chunkMinZ;
        
        // Return NO_CONSTRAINT for out-of-bounds coordinates
        // (density functions may sample outside chunk bounds for blending/aquifers)
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            return NO_CONSTRAINT;
        }
        
        return heights[localX + localZ * 16];
    }
    
    /**
     * Check if this lookup has any height constraints at all.
     * Can be used for early-exit optimization.
     */
    public boolean hasAnyConstraint() {
        return hasAnyConstraint;
    }
    
    /**
     * Compute max height for a single block position.
     * Mirrors TerrainHandler.getMaxHeight logic but returns null-safe Integer.
     */
    private static @Nullable Integer computeMaxHeight(Context context, int blockX, int blockZ) {
        Region region = World.getRegion(context, blockX, blockZ);
        if (region == null) {
            return null;
        }
        
        ClimateSettings climate = region.definition().climate();
        if (climate == null || !climate.hasHeightConstraints()) {
            return null;
        }
        
        return climate.maxHeight();
    }
}
