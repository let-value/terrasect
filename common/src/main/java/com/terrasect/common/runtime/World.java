package com.terrasect.common.runtime;

import com.terrasect.common.api.DimensionRoots;
import com.terrasect.common.api.Region;
import com.terrasect.common.api.Context;
import org.jetbrains.annotations.Nullable;

/**
 * Facade for world generation that supports dimension-aware region hierarchies.
 * 
 * <p>This class provides dimension-aware methods that allow different
 * region hierarchies per dimension (Overworld, End, modded dimensions).
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * // Register roots for dimensions
 * DimensionRoots.register("minecraft:overworld", overworldRoot);
 * DimensionRoots.register("minecraft:the_end", endRoot);
 * 
 * // Query with dimension context
 * Region region = World.getRegion("minecraft:overworld", x, z, context);
 * }</pre>
 * 
 * @see DimensionRoots for registering dimension-specific roots
 * @see NarrativeSpace for the underlying traversal logic
 */
public class World {

    private static final NarrativeSpace NARRATIVE_SPACE = new NarrativeSpace();

    /**
     * Get the root region for a specific dimension.
     * 
     * @param dimensionId The dimension ID (e.g., "minecraft:overworld")
     * @return The root region for this dimension, or null if not registered
     * @see DimensionRoots#getRoot(String)
     */
    public static @Nullable Region getRoot(String dimensionId) {
        return DimensionRoots.getRoot(dimensionId);
    }

    /**
     * Get the leaf Region at a given location in a specific dimension.
     * Returns null if no root is registered for this dimension.
     * 
     * @param dimensionId The dimension ID
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @param context The generation strategy context
     * @return The deepest region at this location, or null if dimension not registered
     */
    public static @Nullable Region getRegion(String dimensionId, int x, int z, Context context) {
        return getRegionAtDepth(dimensionId, x, z, context, 100);
    }

    /**
     * Get the Region at a specific depth in a dimension's hierarchy.
     * Returns null if no root is registered for this dimension.
     * 
     * @param dimensionId The dimension ID
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @param context The generation strategy context
     * @param targetDepth Maximum depth to traverse
     * @return The region at the specified depth, or null if dimension not registered
     */
    public static @Nullable Region getRegionAtDepth(String dimensionId, int x, int z, Context context, int targetDepth) {
        Region root = DimensionRoots.getRoot(dimensionId);
        if (root == null) {
            return null;
        }
        return NARRATIVE_SPACE.getRegionAtDepth(root, x, z, context, targetDepth);
    }

    /**
     * Get the region seed at a specific depth in a dimension's hierarchy.
     * Returns 0 if no root is registered for this dimension.
     * 
     * @param dimensionId The dimension ID
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @param context The generation strategy context
     * @param targetDepth Maximum depth to traverse
     * @return The region seed at the specified depth, or 0 if dimension not registered
     */
    public static long getRegionSeedAtDepth(String dimensionId, int x, int z, Context context, int targetDepth) {
        Region root = DimensionRoots.getRoot(dimensionId);
        if (root == null) {
            return 0L;
        }
        return NARRATIVE_SPACE.getRegionSeedAtDepth(root, x, z, context, targetDepth);
    }
}
