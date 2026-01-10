package com.terrasect.common.generation;

import com.terrasect.common.Context;
import com.terrasect.common.definition.Region;
import com.terrasect.common.strategy.LayoutStrategies;
import com.terrasect.common.strategy.QueryResult;
import com.terrasect.common.util.NoiseUtils;
import com.terrasect.common.util.Packer;

final class Layout {

    // Thread-local iterator to avoid allocations
    private static final ThreadLocal<TraversalIterator> ITERATOR = ThreadLocal.withInitial(TraversalIterator::new);

    // Thread-local traversal state (kept separate from iterator for encapsulation)
    private static final ThreadLocal<TraversalState> STATE = ThreadLocal.withInitial(TraversalState::new);

    /** Internal traversal state - all layout logic stays here */
    private static final class TraversalState {
        long currentSeed;
        float cx, cz, radius;
        float wx, wz;
        float minBlockDistToEdge;
    }

    // Warp configuration - simple 3-layer approach:
    // 1. Base warp: organic region shapes (like vanilla biomes)
    // 2. Feature warp: rivers/ridges push boundaries toward them
    // 3. Detail jitter: fine edge noise for natural borders
    //
    // Key insight: warp must be SMOOTH - high frequency noise causes fragmentation.
    // The "jagged Minecraft look" comes from block-level terrain, not region boundaries.

    // IMPORTANT: Keep warp VERY smooth to avoid leopard-pattern fragmentation.
    // Region boundaries should be kilometers apart, not meters.
    private static final int WARP_SCALE = 2000; // Base noise scale (blocks) - MUCH larger for smooth transitions
    private static final float WARP_AMPLITUDE = 200.0f; // Base displacement (blocks) - scaled with region size
    private static final float FEATURE_STRENGTH = 50.0f; // River/ridge pull strength
    private static final int DETAIL_SCALE = 500; // Edge detail scale - keep large!
    private static final float DETAIL_AMPLITUDE = 20.0f; // Edge detail (blocks) - subtle
    private static final float SPAWN_SAFE_RADIUS = 512.0f; // Reduced warp near spawn
    private static final float INV_SPAWN_SAFE_RADIUS = 1.0f / SPAWN_SAFE_RADIUS;
    private static final long SPAWN_SAFE_RADIUS_SQ = (long) SPAWN_SAFE_RADIUS * (long) SPAWN_SAFE_RADIUS;

    /**
     * Start an iterative traversal of the region hierarchy.
     *
     * <p>Returns thread-local iterator - caller must consume values before next call on same thread.
     * Use {@link TraversalIterator#next()} to step through each depth level.
     *
     * @param root The root region
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @param context The generation context
     * @param gridOffsetX Grid offset X for anchor alignment
     * @param gridOffsetZ Grid offset Z for anchor alignment
     * @return TraversalIterator positioned at root, ready to iterate
     */
    static TraversalIterator startTraversal(
            Region root, int x, int z, Context context, float gridOffsetX, float gridOffsetZ) {
        long seed = context.getSeed();

        // Apply grid offset first (shifts input coords so anchored region is at origin)
        int offsetX = x + (int) gridOffsetX;
        int offsetZ = z + (int) gridOffsetZ;

        // Then apply warp to the offset coordinates
        long packedWarp = getWarpedPoint(offsetX, offsetZ, seed, context);
        float wx = Packer.unpackPairSecond(packedWarp);
        float wz = Packer.unpackPairFirst(packedWarp);

        // Initialize state
        TraversalState state = STATE.get();
        state.currentSeed = seed;
        state.cx = 0;
        state.cz = 0;
        state.radius = root.radius();
        state.wx = wx;
        state.wz = wz;
        state.minBlockDistToEdge = Float.MAX_VALUE;

        // Initialize iterator
        TraversalIterator iter = ITERATOR.get();
        iter.currentRegion = root;
        iter.result.region = root;
        iter.result.seed = seed;
        iter.result.edgeDistance = 1.0f;
        iter.result.edgeInfluence = 0.0f;

        return iter;
    }

    /**
     * Advance the iterator one depth level.
     * Called by TraversalIterator.next().
     */
    static TraversalIterator step(TraversalIterator iter) {
        if (!iter.currentRegion.hasChildren()) {
            return null;
        }

        TraversalState state = STATE.get();

        float dx = state.wx - state.cx;
        float dz = state.wz - state.cz;

        QueryResult query = LayoutStrategies.query(iter.currentRegion, state.currentSeed, dx, dz, state.radius);

        iter.currentRegion = iter.currentRegion.children().get(query.childIndex);
        state.currentSeed = query.childSeed;
        state.cx += query.centerX * state.radius;
        state.cz += query.centerZ * state.radius;
        state.radius *= query.radius;

        // Update result
        iter.result.region = iter.currentRegion;
        iter.result.seed = state.currentSeed;
        iter.result.edgeDistance = Math.min(iter.result.edgeDistance, query.edgeDistance);

        // Track block distance for edgeInfluence
        float blockDist = query.edgeDistance * state.radius;
        state.minBlockDistToEdge = Math.min(state.minBlockDistToEdge, blockDist);

        // Compute edgeInfluence: 0 when >8 blocks from edge, ramps to 1 at edge
        float normalized = state.minBlockDistToEdge / 8.0f;
        iter.result.edgeInfluence = 1.0f - Math.min(normalized, 1.0f);

        return iter;
    }

    /**
     * Warps a world coordinate to create organic region boundaries.
     *
     * Three layers of displacement:
     * 1. Base warp - smooth noise for natural-looking region shapes
     * 2. Feature warp - rivers and ridges pull boundaries toward them
     * 3. Detail jitter - fine noise for Minecraft-style jagged edges
     */
    private static long getWarpedPoint(int x, int z, long seed, Context context) {
        // Spawn protection: reduce warp near origin for predictable starting area
        long distSq = (long) x * x + (long) z * z;
        float damp = 1.0f;
        if (distSq < SPAWN_SAFE_RADIUS_SQ) {
            damp = (float) Math.sqrt((double) distSq) * INV_SPAWN_SAFE_RADIUS;
        }

        // Layer 1: Base warp - organic region shapes
        float baseX = (NoiseUtils.valueNoise(x, z, seed, 1001, WARP_SCALE) - 0.5f) * 2.0f;
        float baseZ = (NoiseUtils.valueNoise(x, z, seed, 1002, WARP_SCALE) - 0.5f) * 2.0f;

        // Layer 2: Feature influence - rivers/ridges attract boundaries
        long influence = context.getInfluence(x, z);
        float river = Packer.unpackPairFirst(influence);
        float ridge = Packer.unpackPairSecond(influence);
        float featureStrength = (river + ridge) * FEATURE_STRENGTH;

        // Push toward features using gradient approximation (noise derivatives)
        float featureAngle = NoiseUtils.valueNoise(x, z, seed, 2001, WARP_SCALE / 2) * 6.283f;
        float featureX = (float) Math.cos(featureAngle) * featureStrength;
        float featureZ = (float) Math.sin(featureAngle) * featureStrength;

        // Layer 3: Detail jitter - fine edge noise for natural borders
        float detailX = (NoiseUtils.valueNoise(x, z, seed, 3001, DETAIL_SCALE) - 0.5f) * 2.0f;
        float detailZ = (NoiseUtils.valueNoise(x, z, seed, 3002, DETAIL_SCALE) - 0.5f) * 2.0f;

        // Combine all layers
        float wx = x + (baseX * WARP_AMPLITUDE + featureX) * damp + detailX * DETAIL_AMPLITUDE;
        float wz = z + (baseZ * WARP_AMPLITUDE + featureZ) * damp + detailZ * DETAIL_AMPLITUDE;

        return Packer.packPair(wz, wx);
    }
}
