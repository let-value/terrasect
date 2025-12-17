package com.terrasect.common.generation;

import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.generation.strategy.FloodFillGenerationStrategy;
import com.terrasect.common.generation.strategy.HexGenerationStrategy;
import com.terrasect.common.generation.strategy.RegionGenerationStrategy;
import com.terrasect.common.generation.strategy.TraversalScratch;
import com.terrasect.common.generation.strategy.VoronoiGenerationStrategy;

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

    private static final ThreadLocal<WarpResult> WARP_SCRATCH = ThreadLocal.withInitial(WarpResult::new);
    private static final ThreadLocal<TraversalScratch> TRAVERSAL_SCRATCH = ThreadLocal.withInitial(TraversalScratch::new);

    private final EdgeStatistics edgeStats = EdgeStatistics.vanillaOverworld();
    private final RegionGenerationStrategy hexStrategy = new HexGenerationStrategy();
    private final RegionGenerationStrategy voronoiStrategy = new VoronoiGenerationStrategy();
    private final RegionGenerationStrategy floodFillStrategy = new FloodFillGenerationStrategy();

    Region getRegionAtDepth(Region root, int x, int z, Strategy context, int targetDepth) {
        TraversalScratch scratch = traverseToDepth(root, x, z, context, targetDepth);
        return scratch.selectedRegion();
    }

    long getRegionSeedAtDepth(Region root, int x, int z, Strategy context, int targetDepth) {
        TraversalScratch scratch = traverseToDepth(root, x, z, context, targetDepth);
        return scratch.currentSeed();
    }

    private TraversalScratch traverseToDepth(Region root, int x, int z, Strategy context, int targetDepth) {
        WarpResult warped = getWarpedCoordinates(x, z, context);
        TraversalScratch scratch = TRAVERSAL_SCRATCH.get();
        scratch.reset(warped.x, warped.z, (float) root.areaBudget(), context.getSeed(), context, root);

        int currentDepth = 0;
        Region current = root;
        while (current.hasChildren() && currentDepth < targetDepth) {
            List<Region> children = current.children();
            RegionGenerationStrategy generationStrategy = selectStrategy(current.definition().generationStrategy());

            generationStrategy.traverse(children, scratch);

            current = scratch.selectedRegion();
            currentDepth++;
        }

        return scratch;
    }

    private RegionGenerationStrategy selectStrategy(GenerationStrategyType type) {
        return switch (type) {
            case HEX -> hexStrategy;
            case FLOOD_FILL -> floodFillStrategy;
            default -> voronoiStrategy;
        };
    }

    private WarpResult getWarpedCoordinates(int x, int z, Strategy context) {
        WarpResult result = WARP_SCRATCH.get();

        float river = context.getRiverInfluence(x, z);
        float ridge = context.getRidgeInfluence(x, z);
        long seed = context.getSeed();

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

        result.x = mx + ((n1 - 0.5f) * baseAmp + (r1 - 0.5f) * microAmp) * dampFactor + riverWarpX * dampFactor + macroEdgeX + microEdgeX;
        result.z = mz + ((n2 - 0.5f) * baseAmp + (r2 - 0.5f) * microAmp) * dampFactor + riverWarpZ * dampFactor + macroEdgeZ + microEdgeZ;

        return result;
    }

    private static class WarpResult {
        float x;
        float z;
    }


}
