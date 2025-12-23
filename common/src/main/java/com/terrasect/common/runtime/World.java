package com.terrasect.common.runtime;

import com.terrasect.common.Terrasect;
import com.terrasect.common.api.Context;
import com.terrasect.common.api.Region;
import com.terrasect.common.devtools.Profiler;
import com.terrasect.common.generation.definition.RegionDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class World {

    // ========== Dimension ID Constants ==========
    
    /** Minecraft Overworld dimension ID */
    public static final String OVERWORLD = "minecraft:overworld";
    
    /** Minecraft Nether dimension ID */
    public static final String THE_NETHER = "minecraft:the_nether";
    
    /** Minecraft End dimension ID */
    public static final String THE_END = "minecraft:the_end";

    // ========== Internal State ==========
    
    private static final Layout LAYOUT = new Layout();
    
    /** Per-dimension configuration (root region + grid offset) */
    private static final Map<String, DimensionState> DIMENSIONS = new ConcurrentHashMap<>();
    
    private World() {}

    // ========== Registration API ==========
    
    /**
     * Register a root region for a dimension.
     * 
     * <p>Call this during mod initialization to define region hierarchies.
     * Grid offset calculation will be deferred until {@link #initialize} is called.
     * 
     * @param dimensionId The dimension ID (e.g., "minecraft:overworld")
     * @param root The root region for this dimension
     */
    public static void register(String dimensionId, Region root) {
        Objects.requireNonNull(dimensionId, "dimensionId cannot be null");
        Objects.requireNonNull(root, "root region cannot be null");
        
        DIMENSIONS.put(dimensionId, new DimensionState(root));
        Terrasect.LOGGER.info("Registered region hierarchy '{}' for dimension '{}'", root.name(), dimensionId);
    }
    
    /**
     * Register the same root region for multiple dimensions.
     * 
     * @param root The root region to register
     * @param dimensionIds The dimension IDs to register this root for
     */
    public static void register(Region root, String... dimensionIds) {
        Objects.requireNonNull(root, "root region cannot be null");
        for (String dimId : dimensionIds) {
            register(dimId, root);
        }
    }

    // ========== Initialization API ==========
    
    /**
     * Initialize a dimension with the world seed.
     * 
     * <p>This calculates grid offsets for any anchored regions. Call this when the
     * dimension's level is created (typically from LevelMixin/LevelHandler).
     * 
     * @param dimensionId The dimension ID
     * @param seed The world seed
     * @param context The generation context (for sampling during offset calculation)
     */
    public static void initialize(Context context) {
        var dimensionId = context.getDimensionId();
        var seed = context.getSeed();

        DimensionState state = DIMENSIONS.get(dimensionId);
        if (state == null) {
            Terrasect.LOGGER.debug("No region hierarchy registered for dimension '{}'", dimensionId);
            return;
        }
        
        if (!state.needsInitialization(seed)) {
            return;
        }
        
        // Calculate grid offset if there's an anchored region
        Region anchored = findAnchoredRegion(state.root);
        if (anchored != null) {
            Terrasect.LOGGER.info("Calculating grid offset for anchored region '{}' in '{}'", 
                anchored.name(), dimensionId);
            
            float[] offset = calculateAnchorOffset(state.root, anchored, seed, context);
            state.initialize(seed, offset[0], offset[1]);
            
            Terrasect.LOGGER.info("Grid offset for '{}': dx={}, dz={}", dimensionId, offset[0], offset[1]);
        } else {
            state.initialize(seed, 0, 0);
        }
    }

    // ========== Query API ==========
    
    /**
     * Get the root region for a dimension.
     * 
     * @param dimensionId The dimension ID
     * @return The root region, or null if not registered
     */
    public static @Nullable Region getRoot(String dimensionId) {
        DimensionState state = DIMENSIONS.get(dimensionId);
        return state != null ? state.root : null;
    }
    
    public static @Nullable Region getRegion(Context context, int x, int z) {
        long t0 = Profiler.begin();
        Region result = getRegionAtDepth(context, x, z, 100);
        Profiler.end(Profiler.WORLD_GET_REGION, t0);
        return result;
    }
    
    /**
     * Get the full traversal result including region, seed, and edge distance.
     * 
     * <p>Returns thread-local result - caller must use values before next call on same thread.
     * 
     * @param context The generation context
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @return TraversalResult with region, seed, and edgeDistance, or null if dimension not registered
     */
    public static @Nullable TraversalResult getTraversalResult(Context context, int x, int z) {
        return getTraversalResultAtDepth(context, x, z, 100);
    }
    
    /**
     * Get the full traversal result at a specific depth.
     * 
     * <p>Returns thread-local result - caller must use values before next call on same thread.
     * 
     * @param context The generation context
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @param targetDepth Maximum depth to traverse
     * @return TraversalResult with region, seed, and edgeDistance, or null if dimension not registered
     */
    public static @Nullable TraversalResult getTraversalResultAtDepth(Context context, int x, int z, int targetDepth) {
        DimensionState state = DIMENSIONS.get(context.getDimensionId());
        if (state == null) {
            return null;
        }
        long t0 = Profiler.begin();
        TraversalResult result = LAYOUT.getTraversalResult(
            state.root, x, z, context, targetDepth,
            state.offsetX, state.offsetZ
        );
        Profiler.end(Profiler.WORLD_TRAVERSE, t0);
        return result;
    }
    
    /**
     * Get the region at a specific depth.
     * 
     * @param dimensionId The dimension ID
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @param context The generation context
     * @param targetDepth Maximum depth to traverse
     * @return The region at the specified depth, or null if dimension not registered
     */
    public static @Nullable Region getRegionAtDepth(Context context, int x, int z,int targetDepth) {
        DimensionState state = DIMENSIONS.get(context.getDimensionId());
        if (state == null) {
            return null;
        }
        long t0 = Profiler.begin();
        Region result = LAYOUT.getRegionAtDepth(
            state.root, x, z, context, targetDepth,
            state.offsetX, state.offsetZ
        );
        Profiler.end(Profiler.WORLD_TRAVERSE, t0);
        return result;
    }
    
    /**
     * Get the region seed at a specific depth.
     * 
     * @param dimensionId The dimension ID
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @param context The generation context
     * @param targetDepth Maximum depth to traverse
     * @return The region seed, or 0 if dimension not registered
     */
    public static long getRegionSeedAtDepth(Context context, int x, int z, 
                                              int targetDepth) {
        DimensionState state = DIMENSIONS.get(context.getDimensionId());
        if (state == null) {
            return 0L;
        }
        return LAYOUT.getRegionSeedAtDepth(
            state.root, x, z, context, targetDepth,
            state.offsetX, state.offsetZ
        );
    }

    // ========== Utility API ==========
    
    /**
     * Check if a dimension has a registered hierarchy.
     */
    public static boolean hasRoot(String dimensionId) {
        return DIMENSIONS.containsKey(dimensionId);
    }
    
    /**
     * Get all registered dimension IDs.
     */
    public static Set<String> getRegisteredDimensions() {
        return Collections.unmodifiableSet(DIMENSIONS.keySet());
    }
    
    /**
     * Clear all registrations. Primarily for testing.
     */
    public static void clear() {
        DIMENSIONS.clear();
    }
    
    /**
     * Create an empty root region for dimensions that should use vanilla behavior.
     */
    public static Region emptyRoot(String name) {
        return new Region(name, 10000, RegionDefinition.empty(), Collections.emptySet(), List.of(), List.of(), false);
    }

    // ========== Internal: Anchor Offset Calculation ==========
    
    private static Region findAnchoredRegion(Region root) {
        if (root.anchoredToOrigin()) {
            return root;
        }
        for (Region child : root.children()) {
            if (child.anchoredToOrigin()) {
                return child;
            }
        }
        for (Region child : root.children()) {
            Region found = findAnchoredRegionDeep(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
    
    private static Region findAnchoredRegionDeep(Region region) {
        for (Region child : region.children()) {
            if (child.anchoredToOrigin()) {
                return child;
            }
            Region found = findAnchoredRegionDeep(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
    
    private static int findAnchoredDepth(Region root, Region anchored) {
        if (root.anchoredToOrigin() && root.name().equals(anchored.name())) {
            return 0;
        }
        return findAnchoredDepthRecursive(root, anchored, 1);
    }
    
    private static int findAnchoredDepthRecursive(Region current, Region anchored, int depth) {
        for (Region child : current.children()) {
            if (child.name().equals(anchored.name())) {
                return depth;
            }
            int found = findAnchoredDepthRecursive(child, anchored, depth + 1);
            if (found >= 0) {
                return found;
            }
        }
        return -1;
    }
    
    /**
     * Calculate grid offset by sampling the world in expanding rings.
     * The offset is applied BEFORE warping, so it's simply the negative of
     * where we find the anchored region.
     */
    private static float[] calculateAnchorOffset(Region root, Region anchored, 
                                                   long seed, Context context) {
        // Use pre-baked radius from Region
        float baseRadius = root.radius();
        int stepSize = Math.max(100, (int) (baseRadius * 0.5f));
        
        int targetDepth = findAnchoredDepth(root, anchored);
        if (targetDepth < 0) {
            Terrasect.LOGGER.warn("Could not determine depth of anchored region '{}'", anchored.name());
            return new float[]{0, 0};
        }
        
        String targetName = anchored.name();
        
        // Check origin first
        Region atOrigin = LAYOUT.getRegionAtDepth(root, 0, 0, context, targetDepth, 0, 0);
        if (atOrigin != null && atOrigin.name().equals(targetName)) {
            Terrasect.LOGGER.info("Anchored region '{}' already at origin", targetName);
            return new float[]{0, 0};
        }
        
        // Search in expanding rings
        int maxRings = 20;
        for (int ring = 1; ring <= maxRings; ring++) {
            int ringRadius = ring * stepSize;
            int pointsPerRing = Math.max(8, ring * 4);
            
            for (int i = 0; i < pointsPerRing; i++) {
                double angle = (2.0 * Math.PI * i) / pointsPerRing;
                int sampleX = (int) (Math.cos(angle) * ringRadius);
                int sampleZ = (int) (Math.sin(angle) * ringRadius);
                
                Region sampled = LAYOUT.getRegionAtDepth(root, sampleX, sampleZ, context, targetDepth, 0, 0);
                if (sampled != null && sampled.name().equals(targetName)) {
                    // Offset is applied before warp, so simply negate the sample coords
                    // When querying (0,0) with offset (-sampleX, -sampleZ), we get:
                    // warp(0 + (-sampleX), 0 + (-sampleZ)) = warp(-sampleX, -sampleZ)
                    // Wait, that's wrong. We want warp(sampleX, sampleZ) when querying origin.
                    // So offset should shift origin TO sampleX: offset = (sampleX, sampleZ)
                    // Query: warp(0 + sampleX, 0 + sampleZ) = warp(sampleX, sampleZ) ✓
                    Terrasect.LOGGER.info("Found anchored region '{}' at ({}, {})", 
                        targetName, sampleX, sampleZ);
                    return new float[]{sampleX, sampleZ};
                }
            }
        }
        
        Terrasect.LOGGER.warn("Could not find anchored region '{}' within {} rings", targetName, maxRings);
        return new float[]{0, 0};
    }

    // ========== Internal: Dimension State ==========
    
    private static class DimensionState {
        final Region root;
        volatile long seed;
        volatile float offsetX;
        volatile float offsetZ;
        volatile boolean initialized;
        
        DimensionState(Region root) {
            this.root = root;
            this.initialized = false;
        }
        
        boolean needsInitialization(long newSeed) {
            return !initialized || this.seed != newSeed;
        }
        
        void initialize(long seed, float offsetX, float offsetZ) {
            this.seed = seed;
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
            this.initialized = true;
        }
    }
}
