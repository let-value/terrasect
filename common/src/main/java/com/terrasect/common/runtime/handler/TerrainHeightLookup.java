package com.terrasect.common.runtime.handler;

import com.terrasect.common.api.Context;
import com.terrasect.common.api.Region;
import com.terrasect.common.devtools.Profiler;
import com.terrasect.common.generation.definition.ClimateSettings;
import com.terrasect.common.runtime.TraversalResult;
import com.terrasect.common.runtime.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.ToIntBiFunction;

/**
 * Pre-computed height lookup table for a single chunk (16x16 blocks).
 * 
 * <p>This class is allocated once per NoiseChunk and pre-calculates the maxHeight
 * for every (x, z) coordinate in the chunk. This avoids repeated region lookups
 * and climate checks during terrain generation hot paths.
 * 
 * <p>The height constraints preserve Minecraft's natural terrain variation by
 * linearly mapping the original surface level into the configured height range.
 * For example, if a region specifies height(40, 50), natural terrain that varies
 * from Y=60 to Y=80 will be mapped to Y=40 to Y=50, creating an interesting
 * ocean floor instead of a flat plane.
 * 
 * <p>Region boundaries are smoothly blended using {@code edgeInfluence} from the
 * traversal system. This value is 0 when more than 8 blocks from any boundary,
 * and ramps to 1 at the boundary itself, enabling efficient boundary detection
 * without expensive neighbor sampling.
 * 
 * <p>Design principles:
 * <ul>
 *   <li>O(1) lookup after construction - just array indexing</li>
 *   <li>No allocations at query time - uses primitive int array</li>
 *   <li>Uses Integer.MIN_VALUE as sentinel for "no constraint"</li>
 *   <li>Leverages edgeInfluence for efficient boundary blending</li>
 * </ul>
 */
public final class TerrainHeightLookup {
    
    /** Sentinel value indicating no height constraint */
    public static final int NO_CONSTRAINT = Integer.MIN_VALUE;
    
    /** Pre-computed heights: heights[(x & 15) + (z & 15) * 16] */
    private final int[] heights;
    
    /** Pre-computed edge influences for blending: [0,1] where 1 = at boundary */
    private final float[] edgeInfluences;
    
    /** The chunk's minimum block X coordinate */
    private final int chunkMinX;
    
    /** The chunk's minimum block Z coordinate */
    private final int chunkMinZ;
    
    /** Whether any position in this chunk has a height constraint */
    private final boolean hasAnyConstraint;
    
    /** Whether any position needs blending (edgeInfluence > 0) */
    private final boolean hasAnyBlending;
    
    private TerrainHeightLookup(int[] heights, float[] edgeInfluences, 
                                 int chunkMinX, int chunkMinZ, 
                                 boolean hasAnyConstraint, boolean hasAnyBlending) {
        this.heights = heights;
        this.edgeInfluences = edgeInfluences;
        this.chunkMinX = chunkMinX;
        this.chunkMinZ = chunkMinZ;
        this.hasAnyConstraint = hasAnyConstraint;
        this.hasAnyBlending = hasAnyBlending;
    }
    
    /**
     * Build a height lookup table for a chunk.
     * 
     * <p>This simplified overload creates flat terrain at the max height.
     * Use {@link #build(Context, int, int, ToIntBiFunction)} for terrain that
     * preserves natural variation.
     * 
     * @param context The generation context (may be null)
     * @param chunkMinX The chunk's minimum block X coordinate
     * @param chunkMinZ The chunk's minimum block Z coordinate
     * @return A populated lookup table, or null if no context or no constraints
     */
    public static @Nullable TerrainHeightLookup build(@Nullable Context context, int chunkMinX, int chunkMinZ) {
        return build(context, chunkMinX, chunkMinZ, null);
    }
    
    /**
     * Build a height lookup table for a chunk, using natural surface levels to
     * create interesting terrain variation within the height constraints.
     * 
     * <p>When a surface sampler is provided, the natural surface level at each
     * position is linearly mapped into the region's height range. This preserves
     * Minecraft's terrain variation while constraining it to the desired bounds.
     * 
     * <p>Uses {@code edgeInfluence} from the traversal to efficiently detect
     * boundary positions that need blending, avoiding expensive neighbor scans.
     * 
     * @param context The generation context (may be null)
     * @param chunkMinX The chunk's minimum block X coordinate
     * @param chunkMinZ The chunk's minimum block Z coordinate
     * @param surfaceSampler Function to sample natural surface level at (x, z), may be null
     * @return A populated lookup table, or null if no context or no constraints
     */
    public static @Nullable TerrainHeightLookup build(
            @Nullable Context context, 
            int chunkMinX, 
            int chunkMinZ,
            @Nullable ToIntBiFunction<Integer, Integer> surfaceSampler) {
        long t0 = Profiler.begin();
        
        if (context == null) {
            Profiler.end(Profiler.TERRAIN_LOOKUP_BUILD, t0);
            return null;
        }
        
        int[] heights = new int[256];
        float[] edgeInfluences = new float[256];
        boolean hasAnyConstraint = false;
        boolean hasAnyBlending = false;
        
        // First pass: sample regions and get constraints + edge influence
        for (int localZ = 0; localZ < 16; localZ++) {
            int blockZ = chunkMinZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int blockX = chunkMinX + localX;
                int index = localX + localZ * 16;
                
                // Get traversal result with region AND edgeInfluence in one call
                TraversalResult traversal = World.getTraversalResult(context, blockX, blockZ);
                if (traversal == null || traversal.region == null) {
                    heights[index] = NO_CONSTRAINT;
                    edgeInfluences[index] = 0.0f;
                    continue;
                }
                
                // Store edge influence for potential blending
                edgeInfluences[index] = traversal.edgeInfluence;
                if (traversal.edgeInfluence > 0) {
                    hasAnyBlending = true;
                }
                
                // Check for height constraint
                ClimateSettings climate = traversal.region.definition().climate();
                if (climate == null || !climate.hasHeightConstraints()) {
                    heights[index] = NO_CONSTRAINT;
                    continue;
                }
                
                Integer min = climate.minHeight();
                Integer max = climate.maxHeight();
                if (min == null || max == null) {
                    heights[index] = NO_CONSTRAINT;
                    continue;
                }
                
                // Compute height with natural variation
                int height;
                if (surfaceSampler != null) {
                    int natural = surfaceSampler.applyAsInt(blockX, blockZ);
                    height = computeHeightWithVariation(natural, min, max);
                } else {
                    height = (min + max) >> 1;
                }
                
                heights[index] = height;
                hasAnyConstraint = true;
            }
        }
        
        // Early exit if no constraints anywhere
        if (!hasAnyConstraint) {
            Profiler.end(Profiler.TERRAIN_LOOKUP_BUILD, t0);
            return null;
        }
        
        // Second pass: blend at boundaries if needed
        // Only positions with edgeInfluence > 0 need blending
        if (hasAnyBlending) {
            int[] blendedHeights = new int[256];
            System.arraycopy(heights, 0, blendedHeights, 0, 256);
            
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int index = localX + localZ * 16;
                    float influence = edgeInfluences[index];
                    
                    if (influence > 0 && heights[index] != NO_CONSTRAINT) {
                        // This position is near a boundary - blend with neighbors
                        blendedHeights[index] = computeBlendedHeight(
                            localX, localZ, heights, edgeInfluences, influence);
                    }
                }
            }
            
            heights = blendedHeights;
        }
        
        Profiler.end(Profiler.TERRAIN_LOOKUP_BUILD, t0);
        
        return new TerrainHeightLookup(heights, edgeInfluences, chunkMinX, chunkMinZ, 
                                        hasAnyConstraint, hasAnyBlending);
    }
    
    /**
     * Compute blended height for a position near a boundary.
     * 
     * <p>Uses a simple box blur within the chunk, weighted by edge influence.
     * Positions with edgeInfluence = 1 (right at boundary) get maximum blending,
     * while positions with low edgeInfluence get minimal smoothing.
     */
    private static int computeBlendedHeight(int localX, int localZ, 
                                             int[] heights, float[] edgeInfluences,
                                             float selfInfluence) {
        int selfIndex = localX + localZ * 16;
        int selfHeight = heights[selfIndex];
        
        // Blend radius based on influence (max 4 blocks when at boundary)
        int blendRadius = Math.max(1, (int)(selfInfluence * 4));
        
        float weightSum = 1.0f;
        float heightSum = selfHeight;
        
        for (int dz = -blendRadius; dz <= blendRadius; dz++) {
            int nz = localZ + dz;
            if (nz < 0 || nz >= 16) continue;
            
            for (int dx = -blendRadius; dx <= blendRadius; dx++) {
                if (dx == 0 && dz == 0) continue;
                
                int nx = localX + dx;
                if (nx < 0 || nx >= 16) continue;
                
                int nIndex = nx + nz * 16;
                int nHeight = heights[nIndex];
                
                if (nHeight != NO_CONSTRAINT) {
                    float dist = (float) Math.sqrt(dx * dx + dz * dz);
                    float weight = 1.0f - (dist / (blendRadius + 1));
                    if (weight > 0) {
                        heightSum += nHeight * weight;
                        weightSum += weight;
                    }
                }
            }
        }
        
        return Math.round(heightSum / weightSum);
    }
    
    /**
     * Compute height with natural terrain variation mapped into constraint range.
     */
    private static int computeHeightWithVariation(int natural, int minHeight, int maxHeight) {
        int constraintMid = (minHeight + maxHeight) >> 1;
        int constraintRange = maxHeight - minHeight;
        
        // Deviation from sea level, clamped to ±32
        int deviation = natural - 64;
        if (deviation > 32) deviation = 32;
        if (deviation < -32) deviation = -32;
        
        // Scale deviation into constraint range: (deviation * range) / 64
        int scaled = (deviation * constraintRange) >> 6;
        
        int result = constraintMid + scaled;
        
        // Clamp to bounds
        if (result < minHeight) return minHeight;
        if (result > maxHeight) return maxHeight;
        return result;
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
     * Get the edge influence for a block position (0 = interior, 1 = at boundary).
     * 
     * @param blockX World block X coordinate
     * @param blockZ World block Z coordinate
     * @return Edge influence [0,1], or 0 if out of bounds
     */
    public float getEdgeInfluence(int blockX, int blockZ) {
        int localX = blockX - chunkMinX;
        int localZ = blockZ - chunkMinZ;
        
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            return 0.0f;
        }
        
        return edgeInfluences[localX + localZ * 16];
    }
    
    /**
     * Check if this lookup has any height constraints at all.
     * Can be used for early-exit optimization.
     */
    public boolean hasAnyConstraint() {
        return hasAnyConstraint;
    }
    
    /**
     * Check if this lookup has any positions that need blending.
     */
    public boolean hasAnyBlending() {
        return hasAnyBlending;
    }
}
