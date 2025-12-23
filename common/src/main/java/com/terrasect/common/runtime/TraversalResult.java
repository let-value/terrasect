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
    
    /** Reset all fields to defaults */
    public void reset() {
        region = null;
        seed = 0;
        edgeDistance = 1.0f;
    }
}
