package com.terrasect.common.definition;

public enum GenerationStrategyType {
    /**
     * Infinite hex grid tiling. Each hex cell picks a child weighted by budget.
     * Best for root level to create repeating narrative "stories" across the world.
     */
    HEX,
    
    /**
     * Power Voronoi diagram with relaxation. Produces organic blob-like regions.
     */
    VORONOI,
    
    /**
     * BSP-style recursive subdivision. Produces irregular polygonal territories
     * that respect budget ratios accurately. Good for high-level partitioning.
     */
    SUBDIVISION,
    
    /**
     * Template-based layouts (center-surround, radial, etc.).
     * Best for narrative-driven regions where spatial relationships matter.
     */
    TEMPLATE
}
