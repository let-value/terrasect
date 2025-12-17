package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.ArrayList;
import java.util.List;

public class VoronoiGenerationStrategy implements RegionGenerationStrategy {

    private static final float BOUNDARY_RADIUS_SCALE = 0.96f;
    private static final float ANGLE_JITTER_SCALE = 0.35f;
    private static final float AREA_RADIUS_SCALE = 1.05f;
    private static final float INFLUENCE_SLOW_FACTOR = 0.45f;
    private static final float BUDGET_PRIORITY_SCALE = 0.18f;

    private final List<Seed> seeds = new ArrayList<>();

    @Override
    public void traverse(List<Region> children, TraversalScratch scratch) {
        if (children.isEmpty()) return;

        float parentRadius = scratch.currentRadius();
        float parentX = scratch.centerX();
        float parentZ = scratch.centerZ();
        long seed = scratch.currentSeed();

        float totalBudget = getTotalWeight(children);
        if (totalBudget <= 0) return;

        ensureSeedCapacity(children.size());

        float angleStep = (float) (Math.PI * 2.0f / children.size());
        float angleOffset = getAngleOffset(seed);
        float scatterRadius = parentRadius * BOUNDARY_RADIUS_SCALE;

        float influenceSample = sampleInfluence(scratch);
        float bestScore = Float.NEGATIVE_INFINITY;
        Region bestChild = null;
        Seed bestSeed = null;
        int bestIndex = -1;

        float distToParentCenter = distance(scratch.warpedX(), scratch.warpedZ(), parentX, parentZ);
        float boundaryAllowance = Math.max(0.0f, parentRadius - distToParentCenter);

        for (int i = 0; i < children.size(); i++) {
            Region region = children.get(i);
            Seed regionSeed = seeds.get(i);

            float angle = angleOffset + i * angleStep + angleStep * jitter(seed, i) * ANGLE_JITTER_SCALE;
            regionSeed.x = parentX + (float) Math.cos(angle) * scatterRadius;
            regionSeed.z = parentZ + (float) Math.sin(angle) * scatterRadius;

            float budgetFraction = region.areaBudget() / totalBudget;
            regionSeed.targetRadius = parentRadius * (float) Math.sqrt(budgetFraction) * AREA_RADIUS_SCALE;

            float distance = distance(scratch.warpedX(), scratch.warpedZ(), regionSeed.x, regionSeed.z);
            float influencePenalty = influenceSample * parentRadius * INFLUENCE_SLOW_FACTOR;

            float fillScore = regionSeed.targetRadius - distance - influencePenalty;
            fillScore = Math.min(fillScore, boundaryAllowance);
            fillScore += budgetFraction * parentRadius * BUDGET_PRIORITY_SCALE;

            if (fillScore > bestScore) {
                bestScore = fillScore;
                bestChild = region;
                bestSeed = regionSeed;
                bestIndex = i;
            }
        }

        if (bestChild == null) {
            bestChild = children.get(0);
            bestSeed = seeds.get(0);
            bestIndex = 0;
        }

        long nextSeed = MathUtils.hash64(seed, bestChild.name().hashCode(), bestIndex, 999);
        scratch.select(bestChild, bestIndex, nextSeed, bestSeed.x, bestSeed.z, Math.max(1.0f, bestSeed.targetRadius));
    }

    private float getTotalWeight(List<Region> regions) {
        float sum = 0;
        for (Region r : regions) sum += r.areaBudget();
        return sum;
    }

    private float getAngleOffset(long seed) {
        return (MathUtils.hash64(seed, 1111, 2222, 3333) & 0xFFFF) / 65536.0f * (float) Math.PI * 2.0f;
    }

    private float jitter(long seed, int index) {
        return ((MathUtils.hash64(seed, index, 10101, 20202) & 0xFFFF) / 65536.0f) - 0.5f;
    }

    private float sampleInfluence(TraversalScratch scratch) {
        int x = Math.round(scratch.warpedX());
        int z = Math.round(scratch.warpedZ());
        float river = scratch.worldContext().getRiverInfluence(x, z);
        float ridge = scratch.worldContext().getRidgeInfluence(x, z);
        return Math.max(0.0f, river + ridge);
    }

    private float distance(float x1, float z1, float x2, float z2) {
        float dx = x1 - x2;
        float dz = z1 - z2;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private void ensureSeedCapacity(int size) {
        while (seeds.size() < size) {
            seeds.add(new Seed());
        }
    }

    private static class Seed {
        float x;
        float z;
        float targetRadius;
    }
}

