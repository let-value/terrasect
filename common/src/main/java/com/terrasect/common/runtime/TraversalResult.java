package com.terrasect.common.runtime;

import com.terrasect.common.api.Region;

/**
 * Result of traversing the region hierarchy.
 * 
 * <p>Contains the leaf region, its seed, and edge distance for climate blending.
 * Thread-local instances are reused to avoid allocations in hot paths.
 */
public final class TraversalResult {
    
    /** The leaf region at the target depth */
    public Region region;
    
    /** Computed seed for the leaf region */
    public long seed;
    
    /**
     * Minimum edge distance encountered during traversal (0 = at boundary, 1 = at center).
     * Used for smooth climate transitions between regions at any level of the hierarchy.
     */
    public float edgeDistance;
    
    /**
     * Edge influence for terrain blending (0 = inside region, 1 = at boundary).
     * 
     * <p>Unlike edgeDistance which is normalized to region size, edgeInfluence is based on
     * actual block distance: it's 0 when more than 8 blocks from any region boundary,
     * and ramps linearly to 1 at the boundary itself.
     * 
     * <p>This is useful for terrain height blending where we want a fixed-width
     * transition zone regardless of region size.
     */
    public float edgeInfluence;
    
    /** Reset all fields to defaults */
    public void reset() {
        region = null;
        seed = 0;
        edgeDistance = 1.0f;
        edgeInfluence = 0.0f;
    }
}
