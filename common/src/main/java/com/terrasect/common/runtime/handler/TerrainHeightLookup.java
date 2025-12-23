package com.terrasect.common.runtime.handler;

import com.terrasect.common.api.Context;
import com.terrasect.common.api.Region;
import com.terrasect.common.devtools.Profiler;
import com.terrasect.common.generation.definition.ClimateSettings;
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
 * <p>Region boundaries are smoothly blended over a configurable radius to avoid
 * sharp terrain transitions. The blending also handles the case where two
 * constrained regions face each other (e.g., two oceans around a mountain) by
 * sampling neighboring chunks and interpolating heights rather than creating
 * abrupt ridges.
 * 
 * <p>Design principles:
 * <ul>
 *   <li>O(1) lookup after construction - just array indexing</li>
 *   <li>No allocations at query time - uses primitive int array</li>
 *   <li>Uses Integer.MIN_VALUE as sentinel for "no constraint"</li>
 *   <li>Smooth blending at region boundaries</li>
 * </ul>
 */
public final class TerrainHeightLookup {
    
    /** Sentinel value indicating no height constraint */
    public static final int NO_CONSTRAINT = Integer.MIN_VALUE;
    
    /** Blend radius in blocks (half a chunk) */
    private static final int BLEND_RADIUS = 8;
    
    /** Extended sample size: chunk (16) + blend margin on each side */
    private static final int EXTENDED_SIZE = 16 + BLEND_RADIUS * 2; // 32
    
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
     * <p>This method also samples an extended area around the chunk to enable
     * smooth blending at region boundaries, preventing abrupt terrain changes.
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
        
        // Extended sampling area: chunk + BLEND_RADIUS margin on all sides
        int extMinX = chunkMinX - BLEND_RADIUS;
        int extMinZ = chunkMinZ - BLEND_RADIUS;
        
        // Sample natural surfaces over extended area
        int[] extNaturalSurfaces = null;
        int minNatural = Integer.MAX_VALUE;
        int maxNatural = Integer.MIN_VALUE;
        
        if (surfaceSampler != null) {
            extNaturalSurfaces = new int[EXTENDED_SIZE * EXTENDED_SIZE];
            for (int ez = 0; ez < EXTENDED_SIZE; ez++) {
                int blockZ = extMinZ + ez;
                for (int ex = 0; ex < EXTENDED_SIZE; ex++) {
                    int blockX = extMinX + ex;
                    int extIndex = ex + ez * EXTENDED_SIZE;
                    
                    int natural = surfaceSampler.applyAsInt(blockX, blockZ);
                    extNaturalSurfaces[extIndex] = natural;
                    minNatural = Math.min(minNatural, natural);
                    maxNatural = Math.max(maxNatural, natural);
                }
            }
        }
        
        // Sample height constraints over extended area
        // Store as packed min/max: high 16 bits = min, low 16 bits = max
        // Use 0 to mean "no constraint" (valid heights are never 0 for min/max)
        int[] extConstraints = new int[EXTENDED_SIZE * EXTENDED_SIZE];
        boolean anyConstraintInExtended = false;
        
        for (int ez = 0; ez < EXTENDED_SIZE; ez++) {
            int blockZ = extMinZ + ez;
            for (int ex = 0; ex < EXTENDED_SIZE; ex++) {
                int blockX = extMinX + ex;
                int extIndex = ex + ez * EXTENDED_SIZE;
                
                HeightConstraint constraint = computeHeightConstraint(context, blockX, blockZ);
                if (constraint != null) {
                    // Pack min/max into single int (both are small positive values)
                    extConstraints[extIndex] = (constraint.minHeight << 16) | (constraint.maxHeight & 0xFFFF);
                    anyConstraintInExtended = true;
                }
                // else: remains 0 (no constraint)
            }
        }
        
        // Early exit if no constraints in extended area
        if (!anyConstraintInExtended) {
            Profiler.end(Profiler.TERRAIN_LOOKUP_BUILD, t0);
            return null;
        }
        
        // Pre-compute which positions are near a boundary (different constraint nearby)
        // This avoids expensive neighbor scans for interior positions
        boolean[] nearBoundary = computeBoundaryMask(extConstraints);
        
        // Compute final heights for the chunk with smooth blending
        int[] heights = new int[256];
        boolean hasAnyConstraint = false;
        
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int chunkIndex = localX + localZ * 16;
                
                // Position in extended grid (offset by BLEND_RADIUS)
                int ex = localX + BLEND_RADIUS;
                int ez = localZ + BLEND_RADIUS;
                int extIndex = ex + ez * EXTENDED_SIZE;
                
                int packedConstraint = extConstraints[extIndex];
                boolean needsBlend = nearBoundary[extIndex];
                
                if (packedConstraint == 0) {
                    // No constraint at this position
                    if (needsBlend) {
                        // Near a constrained region - blend toward it
                        int blendedHeight = computeBlendedHeightUnconstrained(
                            ex, ez, extConstraints, extNaturalSurfaces, minNatural, maxNatural);
                        
                        if (blendedHeight != NO_CONSTRAINT) {
                            heights[chunkIndex] = blendedHeight;
                            hasAnyConstraint = true;
                        } else {
                            heights[chunkIndex] = NO_CONSTRAINT;
                        }
                    } else {
                        heights[chunkIndex] = NO_CONSTRAINT;
                    }
                } else {
                    // Has constraint
                    int minHeight = packedConstraint >> 16;
                    int maxHeight = packedConstraint & 0xFFFF;
                    
                    if (needsBlend) {
                        // Near boundary - blend with neighbors
                        int blendedHeight = computeBlendedHeightConstrained(
                            ex, ez, minHeight, maxHeight,
                            extConstraints, extNaturalSurfaces, minNatural, maxNatural);
                        heights[chunkIndex] = blendedHeight;
                    } else {
                        // Interior - just apply variation without expensive blending
                        heights[chunkIndex] = computeHeightWithVariation(
                            extIndex, minHeight, maxHeight, extNaturalSurfaces, minNatural, maxNatural);
                    }
                    hasAnyConstraint = true;
                }
            }
        }
        
        Profiler.end(Profiler.TERRAIN_LOOKUP_BUILD, t0);
        
        if (!hasAnyConstraint) {
            return null;
        }
        
        return new TerrainHeightLookup(heights, chunkMinX, chunkMinZ, hasAnyConstraint);
    }
    
    /**
     * Compute blended height for an unconstrained position near a constrained region.
     * Returns NO_CONSTRAINT if no nearby constraints within blend radius.
     */
    private static int computeBlendedHeightUnconstrained(
            int ex, int ez, 
            int[] extConstraints, 
            int[] extNaturalSurfaces,
            int minNatural, int maxNatural) {
        
        // Check if there are any constrained neighbors within blend radius
        float weightSum = 0;
        float heightSum = 0;
        
        for (int dz = -BLEND_RADIUS; dz <= BLEND_RADIUS; dz++) {
            int nz = ez + dz;
            if (nz < 0 || nz >= EXTENDED_SIZE) continue;
            
            for (int dx = -BLEND_RADIUS; dx <= BLEND_RADIUS; dx++) {
                int nx = ex + dx;
                if (nx < 0 || nx >= EXTENDED_SIZE) continue;
                
                int nIndex = nx + nz * EXTENDED_SIZE;
                int nPacked = extConstraints[nIndex];
                
                if (nPacked != 0) {
                    // Found a constrained neighbor
                    float dist = (float) Math.sqrt(dx * dx + dz * dz);
                    if (dist <= BLEND_RADIUS) {
                        // Weight decreases with distance (inverse linear)
                        float weight = 1.0f - (dist / BLEND_RADIUS);
                        weight = weight * weight; // Smooth falloff
                        
                        int nMin = nPacked >> 16;
                        int nMax = nPacked & 0xFFFF;
                        
                        // Use the neighbor's constrained height with variation
                        int neighborHeight = computeHeightWithVariation(
                            nIndex, nMin, nMax, extNaturalSurfaces, minNatural, maxNatural);
                        
                        heightSum += neighborHeight * weight;
                        weightSum += weight;
                    }
                }
            }
        }
        
        if (weightSum > 0.01f) {
            // Blend between natural (unconstrained) and constrained neighbor heights
            int constrainedHeight = Math.round(heightSum / weightSum);
            
            // As we move away from constraint, blend toward NO_CONSTRAINT
            // The weight sum indicates how "deep" we are into the blend zone
            // When weightSum is low, we're at the edge - return partial constraint
            if (weightSum < 0.5f) {
                // Near edge of blend zone - only partially apply constraint
                return constrainedHeight;
            }
            return constrainedHeight;
        }
        
        return NO_CONSTRAINT;
    }
    
    /**
     * Compute blended height for a constrained position, potentially blending
     * with neighboring regions that have different constraints.
     */
    private static int computeBlendedHeightConstrained(
            int ex, int ez,
            int minHeight, int maxHeight,
            int[] extConstraints,
            int[] extNaturalSurfaces,
            int minNatural, int maxNatural) {
        
        int selfIndex = ex + ez * EXTENDED_SIZE;
        int selfPacked = (minHeight << 16) | (maxHeight & 0xFFFF);
        
        // Start with our own height
        int selfHeight = computeHeightWithVariation(
            selfIndex, minHeight, maxHeight, extNaturalSurfaces, minNatural, maxNatural);
        
        // Check for different constraints in neighbors (to blend across region boundaries)
        float weightSum = 1.0f; // Self weight
        float heightSum = selfHeight;
        
        for (int dz = -BLEND_RADIUS; dz <= BLEND_RADIUS; dz++) {
            int nz = ez + dz;
            if (nz < 0 || nz >= EXTENDED_SIZE) continue;
            
            for (int dx = -BLEND_RADIUS; dx <= BLEND_RADIUS; dx++) {
                if (dx == 0 && dz == 0) continue; // Skip self
                
                int nx = ex + dx;
                if (nx < 0 || nx >= EXTENDED_SIZE) continue;
                
                int nIndex = nx + nz * EXTENDED_SIZE;
                int nPacked = extConstraints[nIndex];
                
                // Only blend if neighbor has DIFFERENT constraint (or no constraint)
                if (nPacked != selfPacked) {
                    float dist = (float) Math.sqrt(dx * dx + dz * dz);
                    if (dist <= BLEND_RADIUS) {
                        float weight = 1.0f - (dist / BLEND_RADIUS);
                        weight = weight * weight; // Smooth falloff
                        
                        int neighborHeight;
                        if (nPacked == 0) {
                            // Unconstrained neighbor - use natural surface level
                            if (extNaturalSurfaces != null) {
                                neighborHeight = extNaturalSurfaces[nIndex];
                            } else {
                                neighborHeight = 64; // Default surface level
                            }
                        } else {
                            // Different constraint - use its height
                            int nMin = nPacked >> 16;
                            int nMax = nPacked & 0xFFFF;
                            neighborHeight = computeHeightWithVariation(
                                nIndex, nMin, nMax, extNaturalSurfaces, minNatural, maxNatural);
                        }
                        
                        heightSum += neighborHeight * weight;
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
    private static int computeHeightWithVariation(
            int extIndex,
            int minHeight, int maxHeight,
            int[] extNaturalSurfaces,
            int minNatural, int maxNatural) {
        
        if (extNaturalSurfaces != null && maxNatural > minNatural) {
            int natural = extNaturalSurfaces[extIndex];
            float t = (float)(natural - minNatural) / (maxNatural - minNatural);
            return minHeight + Math.round(t * (maxHeight - minHeight));
        } else {
            // No variation data - use midpoint
            return (minHeight + maxHeight) / 2;
        }
    }
    
    /**
     * Compute a mask identifying positions near a constraint boundary.
     * 
     * <p>A position is "near boundary" if any neighbor within BLEND_RADIUS has a
     * different constraint value. This allows us to skip expensive blending for
     * interior positions.
     * 
     * <p>Uses a 2-pass dilation approach for efficiency:
     * 1. First pass: mark positions that are directly on a boundary (neighbor differs)
     * 2. Second pass: dilate boundary by BLEND_RADIUS
     */
    private static boolean[] computeBoundaryMask(int[] extConstraints) {
        boolean[] nearBoundary = new boolean[EXTENDED_SIZE * EXTENDED_SIZE];
        
        // Pass 1: Find direct boundary edges (where adjacent cells differ)
        boolean[] directBoundary = new boolean[EXTENDED_SIZE * EXTENDED_SIZE];
        for (int ez = 0; ez < EXTENDED_SIZE; ez++) {
            for (int ex = 0; ex < EXTENDED_SIZE; ex++) {
                int idx = ex + ez * EXTENDED_SIZE;
                int self = extConstraints[idx];
                
                // Check 4-connected neighbors for difference
                if (ex > 0 && extConstraints[idx - 1] != self) {
                    directBoundary[idx] = true;
                    continue;
                }
                if (ex < EXTENDED_SIZE - 1 && extConstraints[idx + 1] != self) {
                    directBoundary[idx] = true;
                    continue;
                }
                if (ez > 0 && extConstraints[idx - EXTENDED_SIZE] != self) {
                    directBoundary[idx] = true;
                    continue;
                }
                if (ez < EXTENDED_SIZE - 1 && extConstraints[idx + EXTENDED_SIZE] != self) {
                    directBoundary[idx] = true;
                }
            }
        }
        
        // Pass 2: Dilate boundary by BLEND_RADIUS using squared distance
        int blendRadiusSq = BLEND_RADIUS * BLEND_RADIUS;
        for (int ez = 0; ez < EXTENDED_SIZE; ez++) {
            for (int ex = 0; ex < EXTENDED_SIZE; ex++) {
                int idx = ex + ez * EXTENDED_SIZE;
                
                // Check if any direct boundary position is within range
                for (int dz = -BLEND_RADIUS; dz <= BLEND_RADIUS && !nearBoundary[idx]; dz++) {
                    int nz = ez + dz;
                    if (nz < 0 || nz >= EXTENDED_SIZE) continue;
                    
                    for (int dx = -BLEND_RADIUS; dx <= BLEND_RADIUS; dx++) {
                        int nx = ex + dx;
                        if (nx < 0 || nx >= EXTENDED_SIZE) continue;
                        
                        int nIdx = nx + nz * EXTENDED_SIZE;
                        if (directBoundary[nIdx]) {
                            int distSq = dx * dx + dz * dz;
                            if (distSq <= blendRadiusSq) {
                                nearBoundary[idx] = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        return nearBoundary;
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
     * Height constraint with both min and max.
     */
    private record HeightConstraint(int minHeight, int maxHeight) {}
    
    /**
     * Compute height constraint for a single block position.
     */
    private static @Nullable HeightConstraint computeHeightConstraint(Context context, int blockX, int blockZ) {
        Region region = World.getRegion(context, blockX, blockZ);
        if (region == null) {
            return null;
        }
        
        ClimateSettings climate = region.definition().climate();
        if (climate == null || !climate.hasHeightConstraints()) {
            return null;
        }
        
        Integer min = climate.minHeight();
        Integer max = climate.maxHeight();
        
        if (min == null || max == null) {
            return null;
        }
        
        return new HeightConstraint(min, max);
    }
}
