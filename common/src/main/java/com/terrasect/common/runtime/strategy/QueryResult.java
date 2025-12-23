package com.terrasect.common.runtime.strategy;

/**
 * Reusable result buffer for strategy queries.
 * 
 * <p>One instance per thread via ThreadLocal, avoiding allocations in hot paths.
 * Named fields replace magic array indices for clarity and type safety.
 */
public final class QueryResult {
    
    /** Index of selected child region in parent's children list */
    public int childIndex;
    
    /** Cell center X (normalized, relative to parent center) */
    public float centerX;
    
    /** Cell center Z (normalized, relative to parent center) */
    public float centerZ;
    
    /** Cell radius scale (multiply by parent radius) */
    public float radius;
    
    /** Site X position for seed computation (or hex Q coordinate for HEX strategy) */
    public float siteX;
    
    /** Site Z position for seed computation (or hex R coordinate for HEX strategy) */
    public float siteZ;
    
    /** True if this is a ring region (HEX strategy only) */
    public boolean isRing;
    
    /** Computed seed for the child region */
    public long childSeed;
    
    /** 
     * Distance to nearest cell boundary (normalized, 0 = at edge, 1 = at center).
     * Used for smooth climate transitions between regions.
     */
    public float edgeDistance;
    
    /** Reset all fields to defaults */
    public void reset() {
        childIndex = 0;
        centerX = 0;
        centerZ = 0;
        radius = 0.5f;
        siteX = 0;
        siteZ = 0;
        isRing = false;
        childSeed = 0;
        edgeDistance = 1.0f;  // Default to center (no edge blending)
    }
}
