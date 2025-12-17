package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VoronoiGenerationStrategy implements RegionGenerationStrategy {

    private static final float NARRATIVE_AREA_SCALE = 0.24f;
    private static final float COVERAGE_PRIOR = 0.25f;
    private static final float BUDGET_POWER = 1.0f;
    private static final float BUDGET_PULL = 2.00f;
    private static final float MIN_SPREAD = 0.80f;
    private static final float MAX_SPREAD = 0.80f;
    private static final float DISTANCE_PENALTY = 1.20f;
    private static final float EDGE_NOISE_SCALE = 0.0f;
    private static final float FLOW_WARP = 0.0f;
    private static final float FLOW_SCALE = 850.0f;
    private static final float RIVER_PULL = 0.10f;
    private static final float RIDGE_PUSH = 0.05f;

    private final List<Site> sites = new ArrayList<>();

    @Override
    public void traverse(List<Region> children, TraversalScratch scratch) {
        List<Site> layout = computeNarrativeLayout(children, scratch.currentSeed(), scratch.currentRadius());
        float totalBudget = getTotalWeight(children);

        Region bestChild = layout.isEmpty() ? children.get(0) : layout.get(0).region;
        int bestChildIndex = layout.isEmpty() ? 0 : layout.get(0).index;
        Site bestSite = null;

        float riverInfluence = scratch.worldContext() == null ? 0.0f : scratch.worldContext().getRiverInfluence((int) scratch.warpedX(), (int) scratch.warpedZ());
        float ridgeInfluence = scratch.worldContext() == null ? 0.0f : scratch.worldContext().getRidgeInfluence((int) scratch.warpedX(), (int) scratch.warpedZ());

        for (Site site : layout) {
            site.score = Float.NEGATIVE_INFINITY;

            float dx = scratch.warpedX() - (scratch.centerX() + site.x);
            float dz = scratch.warpedZ() - (scratch.centerZ() + site.z);

            // Domain-warp the evaluation point to create blobby, non-linear fronts without angular spokes.
            float warpScale = scratch.currentRadius() * FLOW_WARP * site.spread;
            dx += signedNoise(scratch.currentSeed(), dx, dz, FLOW_SCALE, site.index * 31 + 7) * warpScale;
            dz += signedNoise(scratch.currentSeed(), dx + 17.0f, dz + 13.0f, FLOW_SCALE, site.index * 17 + 11) * warpScale;

            float distSq = dx * dx + dz * dz;
            float spreadRadius = scratch.currentRadius() * site.spread;
            float distance = distSq / (spreadRadius * spreadRadius + 0.0001f);

            float terrainPenalty = 1.0f + ridgeInfluence * RIDGE_PUSH - riverInfluence * RIVER_PULL;
            float budgetTilt = (float) Math.pow(site.weight + COVERAGE_PRIOR, BUDGET_POWER);
            float budgetScale = 1.0f + budgetTilt * BUDGET_PULL;

            float metric = distance;
            metric *= terrainPenalty;
            metric /= budgetScale;
            metric *= 1.0f + signedNoise(scratch.currentSeed(), scratch.warpedX(), scratch.warpedZ(), spreadRadius * 0.95f + 1.0f, site.index * 13 + 3) * EDGE_NOISE_SCALE;

            float score = -metric;

            if (score > (bestSite == null ? Float.NEGATIVE_INFINITY : bestSite.score)) {
                bestChild = site.region;
                bestChildIndex = site.index;
                bestSite = site;
                site.score = score;
            } else {
                site.score = Float.NEGATIVE_INFINITY;
            }
        }

        long nextSeed = MathUtils.hash64(scratch.currentSeed(), bestChild.name().hashCode(), bestChildIndex, 999);

        float centerX = scratch.centerX();
        float centerZ = scratch.centerZ();
        float radius = scratch.currentRadius();
        if (bestSite != null) {
            centerX += bestSite.x;
            centerZ += bestSite.z;
            radius = radius * (float) Math.sqrt(bestChild.areaBudget() / totalBudget);
        }

        scratch.select(bestChild, bestChildIndex, nextSeed, centerX, centerZ, radius);
    }

    private List<Site> computeNarrativeLayout(List<Region> children, long seed, float hexRadius) {
        List<Region> orderedChildren = new ArrayList<>(children);
        orderedChildren.sort(Comparator.comparing(Region::name));

        sites.clear();
        if (orderedChildren.isEmpty()) return sites;

        float totalBudget = getTotalWeight(orderedChildren);
        float radiusScale = hexRadius * (float) Math.sqrt(NARRATIVE_AREA_SCALE);

        int siteCount = orderedChildren.size();

        ensureSiteCapacity(siteCount);

        int siteCursor = 0;
        for (int i = 0; i < orderedChildren.size(); i++) {
            Region region = orderedChildren.get(i);
            float budgetFraction = region.areaBudget() / totalBudget;
            int slots = 1;
            float slotWeight = budgetFraction;
            float baseSpread = MIN_SPREAD;
            float budgetSpreadScale = 1.0f;
            float slotSpread = baseSpread * budgetSpreadScale;

            for (int s = 0; s < slots; s++) {
                float angle = (float) (i * (Math.PI * 2.0 / orderedChildren.size()));
                float r = radiusScale;

                float x = (float) (Math.cos(angle) * r);
                float z = (float) (Math.sin(angle) * r);

                Site site = sites.get(siteCursor++);
                site.x = x;
                site.z = z;
                site.spread = slotSpread;
                site.weight = slotWeight;
                site.region = region;
                site.index = children.indexOf(region);
            }
        }

        trimSites(siteCount);
        return sites;
    }

    private float getTotalWeight(List<Region> regions) {
        float sum = 0;
        for (Region r : regions) sum += r.areaBudget();
        return sum;
    }

    private void ensureSiteCapacity(int size) {
        while (sites.size() < size) {
            sites.add(new Site());
        }
    }

    private void trimSites(int size) {
        if (sites.size() > size) {
            sites.subList(size, sites.size()).clear();
        }
    }

    private float signedNoise(long seed, float x, float z, float scale, int salt) {
        return smoothNoise(seed, x, z, scale, salt) * 2.0f - 1.0f;
    }

    private float smoothNoise(long seed, float x, float z, float scale, int salt) {
        float scaledX = x / scale;
        float scaledZ = z / scale;

        int x0 = (int) Math.floor(scaledX);
        int z0 = (int) Math.floor(scaledZ);
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        float tx = scaledX - x0;
        float tz = scaledZ - z0;

        float n00 = MathUtils.randomFloat(seed, x0, z0, salt);
        float n10 = MathUtils.randomFloat(seed, x1, z0, salt);
        float n01 = MathUtils.randomFloat(seed, x0, z1, salt);
        float n11 = MathUtils.randomFloat(seed, x1, z1, salt);

        float nx0 = MathUtils.lerp(tx, n00, n10);
        float nx1 = MathUtils.lerp(tx, n01, n11);
        return MathUtils.lerp(tz, nx0, nx1);
    }

    private static class Site {
        float x, z;
        Region region;
        int index;
        float score;
        float spread;
        float weight;
    }
}

