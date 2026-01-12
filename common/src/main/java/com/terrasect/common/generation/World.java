package com.terrasect.common.generation;

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
import org.jetbrains.annotations.Nullable;

public final class World {
    public static final String OVERWORLD = "minecraft:overworld";
    public static final String THE_NETHER = "minecraft:the_nether";
    public static final String THE_END = "minecraft:the_end";

    private static final Map<String, DimensionState> DIMENSIONS = new ConcurrentHashMap<>();

    private World() {
    }

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

        var state = DIMENSIONS.get(dimensionId);
        if (state == null) {
            Terrasect.LOGGER.debug("No region hierarchy registered for dimension '{}'", dimensionId);
            return;
        }

        if (!state.needsInitialization(seed)) {
            return;
        }

        Region anchored = findAnchoredRegion(state.root);
        if (anchored != null) {
            Terrasect.LOGGER.info(
                    "Calculating grid offset for anchored region '{}' in '{}'", anchored.name(), dimensionId);

            float[] offset = calculateAnchorOffset(state.root, anchored, seed, context);
            state.initialize(seed, offset[0], offset[1]);

            Terrasect.LOGGER.info("Grid offset for '{}': dx={}, dz={}", dimensionId, offset[0], offset[1]);
        } else {
            state.initialize(seed, 0, 0);
        }
    }

    public static @Nullable Region getRoot(String dimensionId) {
        var state = DIMENSIONS.get(dimensionId);
        return state != null ? state.root : null;
    }

    public static @Nullable CompiledNoiseRegistry getNoiseRegistry(String dimensionId) {
        var state = DIMENSIONS.get(dimensionId);
        return state != null ? state.noiseRegistry : null;
    }

    public static @Nullable TraversalResult traverse(Context context, int x, int z) {
        TraversalIterator iter = traverseIterator(context, x, z);
        if (iter == null) {
            return null;
        }
        return iter.toLeaf().current();
    }

    public static @Nullable TraversalResult traverse(Context context, int x, int z, int depth) {
        TraversalIterator iter = traverseIterator(context, x, z);
        if (iter == null) {
            return null;
        }

        var currentDepth = 0;
        while (iter.hasNext() && currentDepth < depth) {
            iter.next();
            currentDepth++;
        }

        return iter.current();
    }

    public static @Nullable TraversalIterator traverseIterator(Context context, int x, int z) {
        var state = DIMENSIONS.get(context.getDimensionId());
        if (state == null) {
            return null;
        }

        return Layout.startTraversal(state.root, x, z, context, state.offsetX, state.offsetZ);
    }

    public static boolean hasRoot(String dimensionId) {
        return DIMENSIONS.containsKey(dimensionId);
    }

    public static Set<String> getRegisteredDimensions() {
        return Collections.unmodifiableSet(DIMENSIONS.keySet());
    }

    public static void clear() {
        DIMENSIONS.clear();
    }

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
            var found = findAnchoredDepthRecursive(child, anchored, depth + 1);
            if (found >= 0) {
                return found;
            }
        }
        return -1;
    }

    private static float[] calculateAnchorOffset(Region root, Region anchored, long seed, Context context) {

        var baseRadius = root.radius();
        var stepSize = Math.max(100, (int) (baseRadius * 0.5f));

        var targetDepth = findAnchoredDepth(root, anchored);
        if (targetDepth < 0) {
            Terrasect.LOGGER.warn("Could not determine depth of anchored region '{}'", anchored.name());
            return new float[]{0, 0};
        }

        var targetName = anchored.name();

        TraversalIterator iter = Layout.startTraversal(root, 0, 0, context, 0, 0);
        for (var d = 0; d < targetDepth && iter.hasNext(); d++) {
            iter.next();
        }
        var atOrigin = iter.current();
        if (atOrigin.region != null && atOrigin.region.name().equals(targetName)) {
            Terrasect.LOGGER.info("Anchored region '{}' already at origin", targetName);
            return new float[]{0, 0};
        }

        var maxRings = 20;
        for (var ring = 1; ring <= maxRings; ring++) {
            var ringRadius = ring * stepSize;
            var pointsPerRing = Math.max(8, ring * 4);

            for (var i = 0; i < pointsPerRing; i++) {
                var angle = (2.0 * Math.PI * i) / pointsPerRing;
                var sampleX = (int) (Math.cos(angle) * ringRadius);
                var sampleZ = (int) (Math.sin(angle) * ringRadius);

                iter = Layout.startTraversal(root, sampleX, sampleZ, context, 0, 0);
                for (var d = 0; d < targetDepth && iter.hasNext(); d++) {
                    iter.next();
                }
                var sampled = iter.current();
                if (sampled.region != null && sampled.region.name().equals(targetName)) {

                    Terrasect.LOGGER.info("Found anchored region '{}' at ({}, {})", targetName, sampleX, sampleZ);
                    return new float[]{sampleX, sampleZ};
                }
            }
        }

        Terrasect.LOGGER.warn("Could not find anchored region '{}' within {} rings", targetName, maxRings);
        return new float[]{0, 0};
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
