package com.terrasect.common.generation;

import com.terrasect.common.Context;
import com.terrasect.common.definition.Region;
import com.terrasect.common.strategy.LayoutStrategies;
import com.terrasect.common.strategy.QueryResult;
import com.terrasect.common.util.NoiseUtils;
import com.terrasect.common.util.Packer;

final class Layout {

    private static final ThreadLocal<TraversalIterator> ITERATOR = ThreadLocal.withInitial(TraversalIterator::new);

    private static final ThreadLocal<TraversalState> STATE = ThreadLocal.withInitial(TraversalState::new);

    private static final class TraversalState {
        long currentSeed;
        float cx, cz, radius;
        float wx, wz;
        float minBlockDistToEdge;
    }

    private static final int WARP_SCALE = 2000;
    private static final float WARP_AMPLITUDE = 200.0f;
    private static final float FEATURE_STRENGTH = 50.0f;
    private static final int DETAIL_SCALE = 500;
    private static final float DETAIL_AMPLITUDE = 20.0f;
    private static final float SPAWN_SAFE_RADIUS = 512.0f;
    private static final float INV_SPAWN_SAFE_RADIUS = 1.0f / SPAWN_SAFE_RADIUS;
    private static final long SPAWN_SAFE_RADIUS_SQ = (long) SPAWN_SAFE_RADIUS * (long) SPAWN_SAFE_RADIUS;

    static TraversalIterator startTraversal(
            Region root, int x, int z, Context context, float gridOffsetX, float gridOffsetZ) {
        long seed = context.getSeed();

        int offsetX = x + (int) gridOffsetX;
        int offsetZ = z + (int) gridOffsetZ;

        long packedWarp = getWarpedPoint(offsetX, offsetZ, seed, context);
        float wx = Packer.unpackPairSecond(packedWarp);
        float wz = Packer.unpackPairFirst(packedWarp);

        TraversalState state = STATE.get();
        state.currentSeed = seed;
        state.cx = 0;
        state.cz = 0;
        state.radius = root.radius();
        state.wx = wx;
        state.wz = wz;
        state.minBlockDistToEdge = Float.MAX_VALUE;

        TraversalIterator iter = ITERATOR.get();
        iter.currentRegion = root;
        iter.result.region = root;
        iter.result.seed = seed;
        iter.result.edgeDistance = 1.0f;
        iter.result.edgeInfluence = 0.0f;

        return iter;
    }

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

        iter.result.region = iter.currentRegion;
        iter.result.seed = state.currentSeed;
        iter.result.edgeDistance = Math.min(iter.result.edgeDistance, query.edgeDistance);

        float blockDist = query.edgeDistance * state.radius;
        state.minBlockDistToEdge = Math.min(state.minBlockDistToEdge, blockDist);

        float normalized = state.minBlockDistToEdge / 8.0f;
        iter.result.edgeInfluence = 1.0f - Math.min(normalized, 1.0f);

        return iter;
    }

    private static long getWarpedPoint(int x, int z, long seed, Context context) {

        long distSq = (long) x * x + (long) z * z;
        float damp = 1.0f;
        if (distSq < SPAWN_SAFE_RADIUS_SQ) {
            damp = (float) Math.sqrt((double) distSq) * INV_SPAWN_SAFE_RADIUS;
        }

        float baseX = (NoiseUtils.valueNoise(x, z, seed, 1001, WARP_SCALE) - 0.5f) * 2.0f;
        float baseZ = (NoiseUtils.valueNoise(x, z, seed, 1002, WARP_SCALE) - 0.5f) * 2.0f;

        long influence = context.getInfluence(x, z);
        float river = Packer.unpackPairFirst(influence);
        float ridge = Packer.unpackPairSecond(influence);
        float featureStrength = (river + ridge) * FEATURE_STRENGTH;

        float featureAngle = NoiseUtils.valueNoise(x, z, seed, 2001, WARP_SCALE / 2) * 6.283f;
        float featureX = (float) Math.cos(featureAngle) * featureStrength;
        float featureZ = (float) Math.sin(featureAngle) * featureStrength;

        float detailX = (NoiseUtils.valueNoise(x, z, seed, 3001, DETAIL_SCALE) - 0.5f) * 2.0f;
        float detailZ = (NoiseUtils.valueNoise(x, z, seed, 3002, DETAIL_SCALE) - 0.5f) * 2.0f;

        float wx = x + (baseX * WARP_AMPLITUDE + featureX) * damp + detailX * DETAIL_AMPLITUDE;
        float wz = z + (baseZ * WARP_AMPLITUDE + featureZ) * damp + detailZ * DETAIL_AMPLITUDE;

        return Packer.packPair(wz, wx);
    }
}
