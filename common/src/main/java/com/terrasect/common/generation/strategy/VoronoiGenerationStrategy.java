package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VoronoiGenerationStrategy implements RegionGenerationStrategy {

    private static final float BASE_SEED_COUNT = 5.0f;
    private static final float SEED_WEIGHT_EXPONENT = 0.5f;
    private static final float SEED_RING_JITTER = 0.12f;
    private static final float ANGLE_JITTER_SCALE = 0.35f;
    private static final float GROWTH_BASE_SPEED = 0.60f;
    private static final float AREA_GROWTH_SCALE = 1.10f;
    private static final float INFLUENCE_RESISTANCE = 0.6f;
    private static final float CONTAINMENT_STRENGTH = 1.4f;
    private static final float CENTER_PULLBACK = 0.35f;

    private final List<FloodSeed> seeds = new ArrayList<>();

    @Override
    public void traverse(List<Region> children, TraversalScratch scratch) {
        if (children.isEmpty()) return;

        float totalBudget = getTotalWeight(children);
        buildSeeds(children, scratch.currentSeed(), scratch.currentRadius());

        float river = scratch.worldContext().getRiverInfluence((int) scratch.warpedX(), (int) scratch.warpedZ());
        float ridge = scratch.worldContext().getRidgeInfluence((int) scratch.warpedX(), (int) scratch.warpedZ());
        float influenceResistance = 1.0f + (river + ridge) * INFLUENCE_RESISTANCE;

        float distToCenter = (float) Math.sqrt((scratch.warpedX() - scratch.centerX()) * (scratch.warpedX() - scratch.centerX())
            + (scratch.warpedZ() - scratch.centerZ()) * (scratch.warpedZ() - scratch.centerZ()));
        float containmentFactor = 1.0f + (float) Math.pow(Math.max(0.0f, distToCenter / scratch.currentRadius()), 2) * CONTAINMENT_STRENGTH;

        Region bestChild = children.get(0);
        FloodSeed bestSeed = null;
        float bestScore = Float.MAX_VALUE;

        for (FloodSeed seed : seeds) {
            float dx = scratch.warpedX() - (scratch.centerX() + seed.x);
            float dz = scratch.warpedZ() - (scratch.centerZ() + seed.z);
            float distance = (float) Math.sqrt(dx * dx + dz * dz) + 0.001f;

            float budgetFraction = seed.region.areaBudget() / totalBudget;
            float growthSpeed = GROWTH_BASE_SPEED + (float) Math.pow(budgetFraction, 0.6f) * AREA_GROWTH_SCALE;

            float travelCost = distance / growthSpeed;
            float score = travelCost * influenceResistance * containmentFactor;

            if (score < bestScore) {
                bestScore = score;
                bestChild = seed.region;
                bestSeed = seed;
            }
        }

        int bestIndex = children.indexOf(bestChild);
        long nextSeed = MathUtils.hash64(scratch.currentSeed(), bestChild.name().hashCode(), bestIndex, 999);

        float centerX = scratch.centerX();
        float centerZ = scratch.centerZ();
        float radius = scratch.currentRadius() * (float) Math.sqrt(bestChild.areaBudget() / totalBudget);

        if (bestSeed != null) {
            centerX += bestSeed.x * CENTER_PULLBACK;
            centerZ += bestSeed.z * CENTER_PULLBACK;
        }

        scratch.select(bestChild, bestIndex, nextSeed, centerX, centerZ, radius);
    }

    private void buildSeeds(List<Region> children, long seed, float parentRadius) {
        seeds.clear();

        List<Region> sortedChildren = new ArrayList<>(children);
        sortedChildren.sort(Comparator.comparing(Region::name));

        float totalBudget = getTotalWeight(sortedChildren);
        int totalSeedCount = 0;
        for (Region child : sortedChildren) {
            float weight = (float) Math.pow(child.areaBudget() / totalBudget, SEED_WEIGHT_EXPONENT);
            totalSeedCount += Math.max(1, Math.round(BASE_SEED_COUNT * weight));
        }

        if (totalSeedCount == 0) {
            totalSeedCount = sortedChildren.size();
        }

        float angleStep = (float) (Math.PI * 2 / totalSeedCount);
        float baseAngle = ((seed & 0xFFFF) / 65536.0f) * (float) Math.PI * 2.0f;

        int placed = 0;
        for (Region child : sortedChildren) {
            float weight = (float) Math.pow(child.areaBudget() / totalBudget, SEED_WEIGHT_EXPONENT);
            int seedCount = Math.max(1, Math.round(BASE_SEED_COUNT * weight));

            for (int i = 0; i < seedCount; i++) {
                float jitter = ((MathUtils.hash64(seed, placed, i, 1) & 0xFFFF) / 65536.0f - 0.5f) * angleStep * ANGLE_JITTER_SCALE;
                float angle = baseAngle + placed * angleStep + jitter;

                float radiusJitter = ((MathUtils.hash64(seed, placed, i, 2) & 0xFFFF) / 65536.0f - 0.5f) * SEED_RING_JITTER;
                float r = parentRadius * (1.0f + radiusJitter);

                ensureSeedCapacity(placed + 1);
                FloodSeed floodSeed = seeds.get(placed);
                floodSeed.x = (float) Math.cos(angle) * r;
                floodSeed.z = (float) Math.sin(angle) * r;
                floodSeed.region = child;
                placed++;
            }
        }

        trimSeeds(placed);
    }

    private float getTotalWeight(List<Region> regions) {
        float sum = 0;
        for (Region r : regions) sum += r.areaBudget();
        return sum;
    }

    private void ensureSeedCapacity(int size) {
        while (seeds.size() < size) {
            seeds.add(new FloodSeed());
        }
    }

    private void trimSeeds(int size) {
        if (seeds.size() > size) {
            seeds.subList(size, seeds.size()).clear();
        }
    }

    private static class FloodSeed {
        float x;
        float z;
        Region region;
    }
}

