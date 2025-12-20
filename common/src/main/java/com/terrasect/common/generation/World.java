package com.terrasect.common.generation;

/**
 * Facade for world generation that supports dimension-aware region hierarchies.
 * 
 * <p>This class provides both dimension-aware methods (recommended) and legacy static
 * methods for backwards compatibility. The dimension-aware API allows different
 * region hierarchies per dimension (Overworld, End, modded dimensions).
 * 
 * <h2>Dimension-Aware API (Recommended)</h2>
 * <pre>{@code
 * // Register roots for dimensions
 * DimensionRoots.register("minecraft:overworld", overworldRoot);
 * DimensionRoots.register("minecraft:the_end", endRoot);
 * 
 * // Query with dimension context
 * Region region = World.getRegion("minecraft:overworld", x, z, context);
 * }</pre>
 * 
 * <h2>Legacy API</h2>
 * <p>The legacy static methods remain for backwards compatibility and default to
 * the Overworld dimension or fallback root.
 * 
 * @see DimensionRoots for registering dimension-specific roots
 * @see RegionLocator for the underlying traversal logic
 */
public class World {

    private static final NarrativeSpace NARRATIVE_SPACE = new NarrativeSpace();
    
    // Legacy single-root support (deprecated, use DimensionRoots)
    private static final RegionLocator LEGACY_LOCATOR = new RegionLocator(NARRATIVE_SPACE);

    // ==================== DIMENSION-AWARE API ====================

    /**
     * Get the root region for a specific dimension.
     * 
     * @param dimensionId The dimension ID (e.g., "minecraft:overworld")
     * @return The root region for this dimension
     * @see DimensionRoots#getRoot(String)
     */
    public static Region getRoot(String dimensionId) {
        return DimensionRoots.getRoot(dimensionId);
    }

    /**
     * Get the leaf Region at a given location in a specific dimension.
     * 
     * @param dimensionId The dimension ID
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @param context The generation strategy context
     * @return The deepest region at this location
     */
    public static Region getRegion(String dimensionId, int x, int z, Strategy context) {
        return getRegionAtDepth(dimensionId, x, z, context, 100);
    }

    /**
     * Get the Region at a specific depth in a dimension's hierarchy.
     * 
     * @param dimensionId The dimension ID
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @param context The generation strategy context
     * @param targetDepth Maximum depth to traverse
     * @return The region at the specified depth
     */
    public static Region getRegionAtDepth(String dimensionId, int x, int z, Strategy context, int targetDepth) {
        Region root = DimensionRoots.getRoot(dimensionId);
        return NARRATIVE_SPACE.getRegionAtDepth(root, x, z, context, targetDepth);
    }

    /**
     * Get the region seed at a specific depth in a dimension's hierarchy.
     * 
     * @param dimensionId The dimension ID
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @param context The generation strategy context
     * @param targetDepth Maximum depth to traverse
     * @return The region seed at the specified depth
     */
    public static long getRegionSeedAtDepth(String dimensionId, int x, int z, Strategy context, int targetDepth) {
        Region root = DimensionRoots.getRoot(dimensionId);
        return NARRATIVE_SPACE.getRegionSeedAtDepth(root, x, z, context, targetDepth);
    }

    // ==================== LEGACY API (for backwards compatibility) ====================

    /**
     * Set the root region (legacy single-root API).
     * 
     * @deprecated Use {@link DimensionRoots#register(String, Region)} instead
     */
    @Deprecated
    public static void setRoot(Region newRoot) {
        LEGACY_LOCATOR.setRoot(newRoot);
        // Also register with DimensionRoots for new API compatibility
        DimensionRoots.setFallback(newRoot);
    }

    /**
     * Get the root region (legacy single-root API).
     * 
     * @deprecated Use {@link #getRoot(String)} with dimension ID
     */
    @Deprecated
    public static Region getRoot() {
        // Try DimensionRoots first, fall back to legacy locator
        Region root = DimensionRoots.getFallback();
        if (root != null) {
            return root;
        }
        return LEGACY_LOCATOR.getRoot();
    }

    /**
     * Get the leaf Region at a given location (legacy API, uses fallback root).
     * 
     * @deprecated Use {@link #getRegion(String, int, int, Strategy)} with dimension ID
     */
    @Deprecated
    public static Region getRegion(int x, int z, Strategy context) {
        return LEGACY_LOCATOR.getRegion(x, z, context);
    }

    /**
     * Get the Region at a specific depth (legacy API, uses fallback root).
     * 
     * @deprecated Use {@link #getRegionAtDepth(String, int, int, Strategy, int)} with dimension ID
     */
    @Deprecated
    public static Region getRegionAtDepth(int x, int z, Strategy context, int targetDepth) {
        return LEGACY_LOCATOR.getRegionAtDepth(x, z, context, targetDepth);
    }

    /**
     * Get the region seed at a specific depth (legacy API, uses fallback root).
     * 
     * @deprecated Use {@link #getRegionSeedAtDepth(String, int, int, Strategy, int)} with dimension ID
     */
    @Deprecated
    public static long getRegionSeedAtDepth(int x, int z, Strategy context, int targetDepth) {
        return LEGACY_LOCATOR.getRegionSeedAtDepth(x, z, context, targetDepth);
    }
}
