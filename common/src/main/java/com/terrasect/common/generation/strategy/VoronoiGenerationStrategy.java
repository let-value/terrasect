package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VoronoiGenerationStrategy implements RegionGenerationStrategy {

    private static final float NARRATIVE_AREA_SCALE = 0.06f;
    private static final float GOLDEN_ANGLE = (float) (Math.PI * (3 - Math.sqrt(5))); // 137.5 degrees in radians
    private static final float ANGLE_JITTER_SCALE = 0.25f;
    private static final float RADIUS_JITTER_SCALE = 0.10f;

    @Override
    public void traverse(List<Region> children, TraversalScratch scratch) {
        float totalBudget = getTotalWeight(children);
        float areaScale = scratch.currentRadius() * scratch.currentRadius() * NARRATIVE_AREA_SCALE;

        // Sort deterministically so that the layout is stable across seeds
        List<Region> orderedChildren = new ArrayList<>(children);
        orderedChildren.sort(Comparator.comparing(Region::name));

        Region bestChild = null;
        float minMetric = Float.MAX_VALUE;
        int bestChildIndex = -1;
        float bestX = 0;
        float bestZ = 0;

        float cumulativeBudget = 0;
        long seed = scratch.currentSeed();

        for (int i = 0; i < orderedChildren.size(); i++) {
            Region region = orderedChildren.get(i);

            // Use a phyllotaxis pattern to place regions radially outward based on accumulated budget.
            float budgetFraction = region.areaBudget() / totalBudget;
            float midBudget = cumulativeBudget + budgetFraction * 0.5f;

            float radialScale = scratch.currentRadius() * (float) Math.pow(MathUtils.clamp01(midBudget), 0.35f);
            float baseAngle = GOLDEN_ANGLE * i;

            // Jitter in both angle and radius keeps shapes organic while remaining deterministic
            float angleNoise = ((MathUtils.hash64(seed, i, 1, 0) & 0xFFFF) / 65536.0f - 0.5f) * ANGLE_JITTER_SCALE;
            float radiusNoise = ((MathUtils.hash64(seed, i, 2, 0) & 0xFFFF) / 65536.0f - 0.5f) * RADIUS_JITTER_SCALE * scratch.currentRadius();

            float angle = baseAngle + angleNoise;
            float r = radialScale + radiusNoise;

            float offsetX = (float) Math.cos(angle) * r;
            float offsetZ = (float) Math.sin(angle) * r;

            cumulativeBudget += budgetFraction;

            float dx = scratch.warpedX() - (scratch.centerX() + offsetX);
            float dz = scratch.warpedZ() - (scratch.centerZ() + offsetZ);
            float distSq = dx * dx + dz * dz;

            float normalizedWeight = Math.max(budgetFraction, 0.0001f);
            float metric = distSq / normalizedWeight - areaScale * normalizedWeight;

            if (metric < minMetric) {
                minMetric = metric;
                bestChild = region;
                bestChildIndex = children.indexOf(region);
                bestX = offsetX;
                bestZ = offsetZ;
            }
        }

        if (bestChild == null) {
            bestChild = children.get(0);
            bestChildIndex = 0;
        }

        long nextSeed = MathUtils.hash64(scratch.currentSeed(), bestChild.name().hashCode(), bestChildIndex, 999);

        float centerX = scratch.centerX();
        float centerZ = scratch.centerZ();
        float radius = scratch.currentRadius();
        centerX += bestX;
        centerZ += bestZ;
        radius = radius * (float) Math.sqrt(bestChild.areaBudget() / totalBudget);

        scratch.select(bestChild, bestChildIndex, nextSeed, centerX, centerZ, radius);
    }

    private float getTotalWeight(List<Region> regions) {
        float sum = 0;
        for (Region r : regions) sum += r.areaBudget();
        return sum;
    }
}

