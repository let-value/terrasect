package com.terrasect.common.generation;

import org.jetbrains.annotations.Nullable;

import com.terrasect.common.Context;
import com.terrasect.common.Terrasect;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionDefinition;
import com.terrasect.common.lookup.CompiledNoiseRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class World {
    public static final String OVERWORLD = "minecraft:overworld";
    public static final String THE_NETHER = "minecraft:the_nether";
    public static final String THE_END = "minecraft:the_end";

    private static final Map<String, DimensionState> DIMENSIONS = new ConcurrentHashMap<>();

    private World() {}

    public static void register(Region root, String... dimensionIds) {
        Objects.requireNonNull(root, "root region cannot be null");
        CompiledNoiseRegistry noiseRegistry = CompiledNoiseRegistry.build(root);
        for (String dimensionId : dimensionIds) {
            DIMENSIONS.put(dimensionId, new DimensionState(root, noiseRegistry));
        }
    }

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

    /**
     * Get the pre-compiled noise registry for a dimension.
     * 
     * @param dimensionId The dimension ID
     * @return The noise registry, or null if not registered
     */
    public static @Nullable CompiledNoiseRegistry getNoiseRegistry(String dimensionId) {
        DimensionState state = DIMENSIONS.get(dimensionId);
        return state != null ? state.noiseRegistry : null;
    }

    /**
     * Traverse the region hierarchy and return full result (leaf depth).
     * 
     * <p>
     * Returns thread-local result - caller must use values before next call on same
     * thread.
     * Contains: region, seed, edgeDistance, and edgeInfluence.
     * 
     * @param context The generation context
     * @param x       Block X coordinate
     * @param z       Block Z coordinate
     * @return TraversalResult, or null if dimension not registered
     */
    public static @Nullable TraversalResult traverse(Context context, int x, int z) {
        return traverse(context, x, z, Layout.MAX_DEPTH);
    }

    /**
     * Traverse the region hierarchy to a specific depth.
     * 
     * <p>
     * Returns thread-local result - caller must use values before next call on same
     * thread.
     * Contains: region, seed, edgeDistance, and edgeInfluence.
     * 
     * @param context The generation context
     * @param x       Block X coordinate
     * @param z       Block Z coordinate
     * @param depth   Maximum depth to traverse (1 = first children, 2 =
     *                grandchildren, etc.)
     * @return TraversalResult, or null if dimension not registered
     */
    public static @Nullable TraversalResult traverse(Context context, int x, int z, int depth) {
        DimensionState state = DIMENSIONS.get(context.getDimensionId());
        if (state == null) {
            return null;
        }
    
        TraversalResult result = Layout.traverse(
                state.root, x, z, context, depth,
                state.offsetX, state.offsetZ);
        return result;
    }

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

        float baseRadius = root.radius();
        int stepSize = Math.max(100, (int) (baseRadius * 0.5f));

        int targetDepth = findAnchoredDepth(root, anchored);
        if (targetDepth < 0) {
            Terrasect.LOGGER.warn("Could not determine depth of anchored region '{}'", anchored.name());
            return new float[] { 0, 0 };
        }

        String targetName = anchored.name();

        TraversalResult atOrigin = Layout.traverse(root, 0, 0, context, targetDepth, 0, 0);
        if (atOrigin.region != null && atOrigin.region.name().equals(targetName)) {
            Terrasect.LOGGER.info("Anchored region '{}' already at origin", targetName);
            return new float[] { 0, 0 };
        }

        int maxRings = 20;
        for (int ring = 1; ring <= maxRings; ring++) {
            int ringRadius = ring * stepSize;
            int pointsPerRing = Math.max(8, ring * 4);

            for (int i = 0; i < pointsPerRing; i++) {
                double angle = (2.0 * Math.PI * i) / pointsPerRing;
                int sampleX = (int) (Math.cos(angle) * ringRadius);
                int sampleZ = (int) (Math.sin(angle) * ringRadius);

                TraversalResult sampled = Layout.traverse(root, sampleX, sampleZ, context, targetDepth, 0, 0);
                if (sampled.region != null && sampled.region.name().equals(targetName)) {

                    Terrasect.LOGGER.info("Found anchored region '{}' at ({}, {})",
                            targetName, sampleX, sampleZ);
                    return new float[] { sampleX, sampleZ };
                }
            }
        }

        Terrasect.LOGGER.warn("Could not find anchored region '{}' within {} rings", targetName, maxRings);
        return new float[] { 0, 0 };
    }

    private static class DimensionState {
        final Region root;
        final CompiledNoiseRegistry noiseRegistry;
        volatile long seed;
        volatile float offsetX;
        volatile float offsetZ;
        volatile boolean initialized;

        DimensionState(Region root, CompiledNoiseRegistry noiseRegistry) {
            this.root = root;
            this.noiseRegistry = noiseRegistry;
        }

        boolean needsInitialization(long seed) {
            return !initialized || this.seed != seed;
        }

        void initialize(long seed, float offsetX, float offsetZ) {
            this.seed = seed;
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
            this.initialized = true;
        }
    }
}
