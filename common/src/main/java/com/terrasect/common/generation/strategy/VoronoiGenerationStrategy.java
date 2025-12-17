package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VoronoiGenerationStrategy implements RegionGenerationStrategy {

    private static final float NARRATIVE_AREA_SCALE = 0.25f;
    private static final float ANGLE_JITTER_SCALE = 0.25f;
    private static final float GOLDEN_ANGLE = (float) (Math.PI * (3 - Math.sqrt(5)));
    private static final float WEIGHT_PRIORITY = 0.30f;
    private static final float COVERAGE_PRIOR = 0.12f;
    private static final float BUDGET_BIAS = 0.9f;
    private static final float MIN_SPREAD = 0.6f;
    private static final float DISTANCE_PENALTY = 1.25f;
    private static final float MAX_SPREAD = 0.95f;
    private static final float MIN_RADIUS_WEIGHT = 0.65f;
    private static final float MAX_RADIUS_WEIGHT = 1.15f;

    private final List<Site> sites = new ArrayList<>();
    private float lastRotation;

    @Override
    public void traverse(List<Region> children, TraversalScratch scratch) {
        List<Site> layout = computeNarrativeLayout(children, scratch.currentSeed(), scratch.currentRadius());

        float totalBudget = getTotalWeight(children);
        Region bestChild = layout.isEmpty() ? children.get(0) : layout.get(0).region;
        int bestChildIndex = layout.isEmpty() ? 0 : layout.get(0).index;
        Site bestSite = null;

        for (Site site : layout) {
            site.score = Float.NEGATIVE_INFINITY;
            float dx = scratch.warpedX() - (scratch.centerX() + site.x);
            float dz = scratch.warpedZ() - (scratch.centerZ() + site.z);
            float distSq = dx * dx + dz * dz;

            float weight = site.region.areaBudget() / totalBudget;
            float spread = MathUtils.lerp(MIN_SPREAD, MAX_SPREAD, (float) Math.sqrt(MathUtils.clamp01(weight)));
            float normDist = (float) Math.sqrt(distSq) / (scratch.currentRadius() * spread + 0.0001f);

            // Gaussian-like falloff keeps territories round while honoring area budgets.
            float distPenalty = normDist * normDist;
            float influence = (float) Math.exp(-distPenalty * 0.5f);
            float budgetTilt = (float) Math.sqrt(weight + COVERAGE_PRIOR) * BUDGET_BIAS + weight * WEIGHT_PRIORITY;
            float score = budgetTilt + influence - distPenalty * DISTANCE_PENALTY;

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

        lastRotation = (MathUtils.hash64(seed, orderedChildren.size(), 0, 0) & 0xFFFF) / 65535.0f * (float) (Math.PI * 2);
        float totalBudget = getTotalWeight(orderedChildren);
        float radiusScale = hexRadius * (float) Math.sqrt(NARRATIVE_AREA_SCALE);

        ensureSiteCapacity(orderedChildren.size());

        for (int i = 0; i < orderedChildren.size(); i++) {
            Region region = orderedChildren.get(i);
            float budgetFraction = region.areaBudget() / totalBudget;

            float radiusWeight = MathUtils.lerp(MIN_RADIUS_WEIGHT, MAX_RADIUS_WEIGHT, MathUtils.clamp01((float) Math.sqrt(budgetFraction)));
            float radialNoise = (MathUtils.hash64(seed, i, 1, 0) & 0xFFFF) / 65535.0f;
            float radius = radiusScale * radiusWeight * (0.5f + 0.5f * radialNoise);

            // Golden-angle spiral provides even angular spacing; hash-based jitter fans sites out
            // so blobs originate from throughout the disk instead of the center.
            float jitter = ((MathUtils.hash64(seed, i, 0, 0) & 0xFFFF) / 65535.0f - 0.5f) * ANGLE_JITTER_SCALE * GOLDEN_ANGLE;
            float angle = lastRotation + GOLDEN_ANGLE * i + jitter;

            Site site = sites.get(i);
            site.x = (float) Math.cos(angle) * radius;
            site.z = (float) Math.sin(angle) * radius;
            site.region = region;
            site.index = children.indexOf(region);
        }

        trimSites(orderedChildren.size());
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

    private static class Site {
        float x, z;
        Region region;
        int index;
        float score;
    }
}

