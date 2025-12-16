package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VoronoiGenerationStrategy implements RegionGenerationStrategy {

    private static final float NARRATIVE_AREA_SCALE = 0.06f;
    private static final float ANGLE_JITTER_SCALE = 0.35f;
    private static final float GOLDEN_ANGLE = (float) (Math.PI * (3 - Math.sqrt(5)));
    private static final float WEIGHT_PRIORITY = 0.25f;

    private final List<Site> sites = new ArrayList<>();
    private float lastRotation;

    @Override
    public void traverse(List<Region> children, TraversalScratch scratch) {
        List<Site> layout = computeNarrativeLayout(children, scratch.currentSeed(), scratch.currentRadius());

        float totalBudget = getTotalWeight(children);
        Region bestChild = layout.isEmpty() ? children.get(0) : layout.get(0).region;
        int bestChildIndex = layout.isEmpty() ? 0 : layout.get(0).index;
        Site bestSite = null;

        float radiusNorm = scratch.currentRadius() * scratch.currentRadius() + 0.0001f;

        for (Site site : layout) {
            site.score = Float.NEGATIVE_INFINITY;
            float distSq = (scratch.warpedX() - (scratch.centerX() + site.x)) * (scratch.warpedX() - (scratch.centerX() + site.x))
                + (scratch.warpedZ() - (scratch.centerZ() + site.z)) * (scratch.warpedZ() - (scratch.centerZ() + site.z));

            float weight = site.region.areaBudget() / totalBudget;
            float score = weight * WEIGHT_PRIORITY - distSq / radiusNorm;

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

        float cumulativeAngle = 0.0f;
        for (int i = 0; i < orderedChildren.size(); i++) {
            Region region = orderedChildren.get(i);
            float budgetFraction = region.areaBudget() / totalBudget;

            // Larger budgets sit closer to the center to maximize their weighted influence.
            float radiusBias = 1.0f - (float) Math.sqrt(MathUtils.clamp01(budgetFraction));
            float radius = radiusScale * radiusBias;

            // Golden-angle spiral provides even angular spacing; a small hash-based jitter keeps
            // layouts from looking too rigid while remaining deterministic.
            float jitter = ((MathUtils.hash64(seed, i, 0, 0) & 0xFFFF) / 65535.0f - 0.5f) * ANGLE_JITTER_SCALE * GOLDEN_ANGLE;
            float angle = lastRotation + cumulativeAngle + budgetFraction * (float) Math.PI + jitter;

            Site site = sites.get(i);
            site.x = (float) Math.cos(angle) * radius;
            site.z = (float) Math.sin(angle) * radius;
            site.region = region;
            site.index = children.indexOf(region);

            cumulativeAngle += budgetFraction * (float) (Math.PI * 2);
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

