package com.terrasect.common.generation;

import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.generation.strategy.HexStrategy;
import com.terrasect.common.generation.strategy.VoronoiStrategy;

import java.util.List;

/**
 * Encapsulates the math for traversing the region hierarchy. Separating this from
 * {@link World} makes it easier to reason about the generation math independently
 * from the global static state.
 */
final class NarrativeSpace {

    private static final float WORLD_ORIGIN_DAMP_RADIUS = 600.0f;
    private static final int MACRO_WARP_NOISE_SCALE = 4000;
    private static final float DOMAIN_WARP_AMPLITUDE = 1500.0f;
    private static final int DOMAIN_WARP_NOISE_SCALE = 600;
    private static final int MICRO_WARP_NOISE_SCALE = 150;
    private static final int EDGE_MICRO_NOISE_SCALE = 8;
    private static final float BASE_WARP_AMPLITUDE = 42.0f;
    private static final float MICRO_WARP_AMPLITUDE = 15.0f;
    private static final float INFLUENCE_WARP_AMPLITUDE = 55.0f;
    private static final int WARP_ANGLE_NOISE_SCALE = 350;
    private static final float COARSE_SCALE_FACTOR = 0.5f;
    private static final float COARSE_EDGE_AMPLITUDE_SCALE = 2.5f;

    private final EdgeStatistics edgeStats = EdgeStatistics.vanillaOverworld();

    Region getRegionAtDepth(Region root, int x, int z, Strategy context, int targetDepth) {
        return (Region) traverse(root, x, z, context, targetDepth, false);
    }

    long getRegionSeedAtDepth(Region root, int x, int z, Strategy context, int targetDepth) {
        return (Long) traverse(root, x, z, context, targetDepth, true);
    }

    private Object traverse(Region root, int x, int z, Strategy context, int targetDepth, boolean returnSeed) {
        long packedWarp = getWarpedPoint(x, z, context.getSeed(), context);
        float wx = Float.intBitsToFloat((int) (packedWarp >> 32));
        float wz = Float.intBitsToFloat((int) packedWarp);

        Region currentRegion = root;
        long currentSeed = context.getSeed();
        float cx = 0;
        float cz = 0;
        float radius = (float) root.areaBudget();

        int currentDepth = 0;
        while (currentRegion.hasChildren() && currentDepth < targetDepth) {
            GenerationStrategyType type = currentRegion.definition().generationStrategy();
            List<Region> children = currentRegion.children();

            Region nextRegion;
            long nextSeed;
            float nextCx, nextCz, nextRadius;

            if (type == GenerationStrategyType.HEX) {
                long hexPacked = HexStrategy.getCell(wx, wz, radius);
                
                nextRegion = HexStrategy.getRegion(children, currentSeed, hexPacked);
                nextSeed = HexStrategy.getSeed(currentSeed, hexPacked);
                nextCx = HexStrategy.getNextCx(cx, radius, hexPacked);
                nextCz = HexStrategy.getNextCz(cz, radius, hexPacked);
                nextRadius = radius;
            } else {
                float[] layout = VoronoiStrategy.getLayout(currentSeed, children);
                int bestIndex = VoronoiStrategy.getCell(layout, wx - cx, wz - cz, radius);
                
                nextRegion = VoronoiStrategy.getRegion(children, layout, bestIndex);
                nextSeed = VoronoiStrategy.getSeed(currentSeed, layout, bestIndex, nextRegion);
                nextCx = VoronoiStrategy.getNextCx(cx, radius, layout, bestIndex);
                nextCz = VoronoiStrategy.getNextCz(cz, radius, layout, bestIndex);
                nextRadius = VoronoiStrategy.getNextRadius(radius, children, nextRegion);
            }

            currentRegion = nextRegion;
            currentSeed = nextSeed;
            cx = nextCx;
            cz = nextCz;
            radius = nextRadius;
            currentDepth++;
        }

        return returnSeed ? currentSeed : currentRegion;
    }

    private long getWarpedPoint(int x, int z, long seed, Strategy context) {
        float river = context.getRiverInfluence(x, z);
        float ridge = context.getRidgeInfluence(x, z);

        float dist = (float) Math.sqrt(x * x + z * z);
        float dampFactor = Math.min(1.0f, dist / WORLD_ORIGIN_DAMP_RADIUS);

        float m1 = NoiseUtils.valueNoise(x, z, seed, 10001, MACRO_WARP_NOISE_SCALE);
        float m2 = NoiseUtils.valueNoise(x, z, seed, 10002, MACRO_WARP_NOISE_SCALE);

        float mx = x + (m1 - 0.5f) * DOMAIN_WARP_AMPLITUDE * dampFactor;
        float mz = z + (m2 - 0.5f) * DOMAIN_WARP_AMPLITUDE * dampFactor;

        float n1 = NoiseUtils.warpNoise1((int) mx, (int) mz, seed, DOMAIN_WARP_NOISE_SCALE);
        float n2 = NoiseUtils.warpNoise2((int) mx, (int) mz, seed, DOMAIN_WARP_NOISE_SCALE);

        float r1 = NoiseUtils.valueNoise((int) mx, (int) mz, seed, 5001, MICRO_WARP_NOISE_SCALE);
        float r2 = NoiseUtils.valueNoise((int) mx, (int) mz, seed, 5002, MICRO_WARP_NOISE_SCALE);

        float baseAmp = BASE_WARP_AMPLITUDE;
        float microAmp = MICRO_WARP_AMPLITUDE;
        float influenceAmp = INFLUENCE_WARP_AMPLITUDE;

        float warpAngle = NoiseUtils.valueNoise(x, z, seed, 9999, WARP_ANGLE_NOISE_SCALE) * (float) Math.PI * 2.0f;
        float riverWarpX = (float) Math.cos(warpAngle) * (river + ridge) * influenceAmp;
        float riverWarpZ = (float) Math.sin(warpAngle) * (river + ridge) * influenceAmp;

        float coarseScale = edgeStats.coarseAverageRunBlocks() * COARSE_SCALE_FACTOR;
        float coarseAmplitude = edgeStats.coarseTransitionDensity() * Config.EDGE_SCALE * COARSE_EDGE_AMPLITUDE_SCALE;

        float macroEdgeX = (NoiseUtils.valueNoise(x, z, seed, 9201, (int) coarseScale) - 0.5f) * coarseAmplitude;
        float macroEdgeZ = (NoiseUtils.valueNoise(z, x, seed, 9202, (int) coarseScale) - 0.5f) * coarseAmplitude;

        float microEdgeX = (NoiseUtils.valueNoise(x, z, seed, 9203, EDGE_MICRO_NOISE_SCALE) - 0.5f) * edgeStats.fineHorizontalJitter();
        float microEdgeZ = (NoiseUtils.valueNoise(z, x, seed, 9204, EDGE_MICRO_NOISE_SCALE) - 0.5f) * edgeStats.fineVerticalJitter();

        float wx = mx + ((n1 - 0.5f) * baseAmp + (r1 - 0.5f) * microAmp) * dampFactor + riverWarpX * dampFactor + macroEdgeX + microEdgeX;
        float wz = mz + ((n2 - 0.5f) * baseAmp + (r2 - 0.5f) * microAmp) * dampFactor + riverWarpZ * dampFactor + macroEdgeZ + microEdgeZ;

        return ((long) Float.floatToRawIntBits(wx) << 32) | (Float.floatToRawIntBits(wz) & 0xFFFFFFFFL);
    }
}
