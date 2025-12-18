package com.terrasect.common.generation.definition;

public enum GenerationStrategyType {
    /**
     * Infinite hex grid tiling. Best for root level to encapsulate repeating stories.
     */
    HEX,
    
    /**
     * Power Voronoi diagram with relaxation. Legacy, use SUBDIVISION or TEMPLATE instead.
     */
    VORONOI,
    
    /**
     * BSP-style recursive subdivision. Produces irregular polygonal territories
     * that respect budget ratios exactly. Good for high-level partitioning.
     */
    SUBDIVISION,
    
    /**
     * Template-based layouts (center-surround, radial, etc.).
     * Best for narrative-driven regions where spatial relationships matter.
     */
    TEMPLATE
}
