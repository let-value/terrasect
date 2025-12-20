package com.terrasect.common.generation;

import com.terrasect.common.Terrasect;
import com.terrasect.common.generation.definition.RegionDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for dimension-specific root regions.
 * 
 * <p>This allows mods to define different narrative region hierarchies for different
 * dimensions. For example, you might have:
 * <ul>
 *   <li>A detailed region hierarchy for the Overworld with climate zones</li>
 *   <li>A simpler hierarchy for the End with custom biome filtering</li>
 *   <li>Support for modded dimensions that clone Overworld generation</li>
 * </ul>
 * 
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // 1. Register regions in a shared RegionRegistry
 * RegionRegistry registry = new RegionRegistry();
 * 
 * // Define world regions
 * registry.region("OVERWORLD_ROOT")
 *     .child("SCORCHED_WASTES", r -> r.radius(1000).climate(...))
 *     .child("FROZEN_REACHES", r -> r.radius(1000).climate(...));
 *     
 * registry.region("END_ROOT")
 *     .child("OUTER_ISLANDS", r -> r.radius(500).biomes(...));
 * 
 * // 2. Build regions
 * Region overworldRoot = registry.build("OVERWORLD_ROOT");
 * Region endRoot = registry.build("END_ROOT");
 * 
 * // 3. Register roots for dimensions using dimension IDs
 * DimensionRoots.register("minecraft:overworld", overworldRoot);
 * DimensionRoots.register("minecraft:the_end", endRoot);
 * 
 * // 4. Modded dimensions can share the same root
 * DimensionRoots.register("mymod:overworld_copy", overworldRoot);
 * }</pre>
 * 
 * <h2>Dimension ID Format</h2>
 * <p>Dimension IDs follow Minecraft's ResourceLocation format: {@code namespace:path}.
 * Common vanilla dimensions are:
 * <ul>
 *   <li>{@code minecraft:overworld}</li>
 *   <li>{@code minecraft:the_nether}</li>
 *   <li>{@code minecraft:the_end}</li>
 * </ul>
 * 
 * @see RegionRegistry for building region hierarchies
 * @see World for looking up regions at coordinates
 */
public final class DimensionRoots {

    /** Default dimension ID for backwards compatibility */
    public static final String OVERWORLD = "minecraft:overworld";
    public static final String THE_NETHER = "minecraft:the_nether";
    public static final String THE_END = "minecraft:the_end";
    
    /**
     * Map from dimension ID (e.g., "minecraft:overworld") to root region.
     * Using ConcurrentHashMap for thread-safe access during world loading.
     */
    private static final Map<String, Region> DIMENSION_ROOTS = new ConcurrentHashMap<>();
    
    /**
     * Fallback root used when a dimension doesn't have a specific root registered.
     * This maintains backwards compatibility with the old single-root API.
     */
    private static volatile Region fallbackRoot;
    
    private DimensionRoots() {
        // Utility class
    }
    
    /**
     * Register a root region for a specific dimension.
     * 
     * @param dimensionId The dimension ID (e.g., "minecraft:overworld", "mymod:custom_dim")
     * @param root The root region for this dimension
     */
    public static void register(String dimensionId, Region root) {
        Objects.requireNonNull(dimensionId, "dimensionId cannot be null");
        Objects.requireNonNull(root, "root region cannot be null");
        
        DIMENSION_ROOTS.put(dimensionId, root);
        Terrasect.LOGGER.info("Registered root region '{}' for dimension '{}'", root.name(), dimensionId);
        
        // Automatically set fallback if this is the first registration or Overworld
        if (fallbackRoot == null || OVERWORLD.equals(dimensionId)) {
            fallbackRoot = root;
        }
    }
    
    /**
     * Register the same root region for multiple dimensions.
     * Useful when modded dimensions should use the same region configuration.
     * 
     * @param root The root region to register
     * @param dimensionIds The dimension IDs to register this root for
     */
    public static void registerForDimensions(Region root, String... dimensionIds) {
        Objects.requireNonNull(root, "root region cannot be null");
        for (String dimId : dimensionIds) {
            register(dimId, root);
        }
    }
    
    /**
     * Get the root region for a specific dimension.
     * 
     * @param dimensionId The dimension ID
     * @return The root region for this dimension, or the fallback if none registered
     * @throws IllegalStateException if no root is registered and no fallback exists
     */
    public static Region getRoot(String dimensionId) {
        Region root = DIMENSION_ROOTS.get(dimensionId);
        if (root != null) {
            return root;
        }
        
        if (fallbackRoot != null) {
            return fallbackRoot;
        }
        
        throw new IllegalStateException(
            "No root region registered for dimension '" + dimensionId + 
            "' and no fallback available. Call DimensionRoots.register() or DimensionRoots.setFallback() first.");
    }
    
    /**
     * Get the root region for a dimension, returning null if not found.
     * 
     * @param dimensionId The dimension ID
     * @return The root region, or null if not registered (ignores fallback)
     */
    public static Region getRootOrNull(String dimensionId) {
        return DIMENSION_ROOTS.get(dimensionId);
    }
    
    /**
     * Check if a specific dimension has a root registered.
     */
    public static boolean hasRoot(String dimensionId) {
        return DIMENSION_ROOTS.containsKey(dimensionId);
    }
    
    /**
     * Get all registered dimension IDs.
     */
    public static Set<String> getRegisteredDimensions() {
        return Collections.unmodifiableSet(DIMENSION_ROOTS.keySet());
    }
    
    /**
     * Set the fallback root used when a dimension doesn't have a specific root.
     * This is also called automatically when registering the first root or Overworld root.
     * 
     * @param root The fallback root region
     */
    public static void setFallback(Region root) {
        fallbackRoot = root;
        Terrasect.LOGGER.debug("Set fallback root region: {}", root != null ? root.name() : "null");
    }
    
    /**
     * Get the fallback root region.
     */
    public static Region getFallback() {
        return fallbackRoot;
    }
    
    /**
     * Clear all registrations. Primarily for testing.
     */
    public static void clear() {
        DIMENSION_ROOTS.clear();
        fallbackRoot = null;
    }
    
    /**
     * Legacy compatibility: set root for Overworld dimension.
     * Equivalent to {@code register(OVERWORLD, root)}.
     * 
     * @deprecated Use {@link #register(String, Region)} with explicit dimension ID
     */
    @Deprecated
    public static void setRoot(Region root) {
        register(OVERWORLD, root);
    }
    
    /**
     * Legacy compatibility: get root for Overworld dimension.
     * Equivalent to {@code getRoot(OVERWORLD)}.
     * 
     * @deprecated Use {@link #getRoot(String)} with explicit dimension ID
     */
    @Deprecated
    public static Region getRoot() {
        return getRoot(OVERWORLD);
    }
    
    /**
     * Create an empty root region for dimensions where Terrasect should have no effect.
     * This is useful for dimensions like the Nether where you might want vanilla behavior.
     */
    public static Region emptyRoot(String name) {
        return new Region(name, 10000, RegionDefinition.empty(), Collections.emptySet(), List.of(), List.of());
    }
}
