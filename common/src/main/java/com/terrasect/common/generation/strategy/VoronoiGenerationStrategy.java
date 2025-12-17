package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VoronoiGenerationStrategy implements RegionGenerationStrategy {

    private static final float GOLDEN_ANGLE = (float) (Math.PI * (3 - Math.sqrt(5)));
    private static final float WEIGHT_PRIORITY = 0.32f;
    private static final float RADIAL_SPREAD = 0.95f;
    private static final float BLOB_WOBBLE_FREQUENCY = 2.0f;

    private final List<Site> sites = new ArrayList<>();
    private float lastRotation;

    @Override
    public void traverse(List<Region> children, TraversalScratch scratch) {
        List<Site> layout = computeNarrativeLayout(children, scratch.currentSeed(), scratch.currentRadius());

        float totalBudget = getTotalWeight(children);
        Region bestChild = layout.isEmpty() ? children.get(0) : layout.get(0).region;
        int bestChildIndex = layout.isEmpty() ? 0 : layout.get(0).index;
        Site bestSite = null;

        float maxWeight = 0.0f;
        for (Site site : layout) {
            if (site.weight > maxWeight) maxWeight = site.weight;
        }

        for (Site site : layout) {
            float dx = scratch.warpedX() - (scratch.centerX() + site.x);
            float dz = scratch.warpedZ() - (scratch.centerZ() + site.z);
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            float angle = (float) Math.atan2(dz, dx);
            float wobble = (float) Math.sin(angle * BLOB_WOBBLE_FREQUENCY + site.wobblePhase) * site.wobbleScale;
            float adjustedDist = Math.max(0.0f, dist - wobble);

            float radiusNorm = site.softRadius * site.softRadius + 0.0001f;
            float weightBias = (float) Math.sqrt(site.weight) * 0.2f + site.weight * WEIGHT_PRIORITY;
            if (maxWeight > 0.0f) {
                weightBias += (site.weight / maxWeight) * 0.02f;
            }
            float score = weightBias - (adjustedDist * adjustedDist) / radiusNorm;

            if (bestSite == null || score > bestSite.score) {
                site.score = score;
                bestSite = site;
                bestChild = site.region;
                bestChildIndex = site.index;
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
            float budgetScale = (float) Math.sqrt(bestChild.areaBudget() / totalBudget);
            float softnessScale = MathUtils.clamp01(bestSite.softRadius / (scratch.currentRadius() * RADIAL_SPREAD));
            radius = radius * MathUtils.lerp(0.4f, 0.95f, Math.max(budgetScale, softnessScale));
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

        ensureSiteCapacity(orderedChildren.size());

        for (int i = 0; i < orderedChildren.size(); i++) {
            Region region = orderedChildren.get(i);
            float budgetFraction = region.areaBudget() / totalBudget;

            float radialSample = (float) Math.sqrt(MathUtils.randomFloat(seed, i, 0, 11));
            float baseRadius = hexRadius * RADIAL_SPREAD * MathUtils.lerp(0.35f, 1.0f, radialSample);
            float budgetPull = MathUtils.lerp(0.7f, 1.0f, 1.0f - MathUtils.clamp01(budgetFraction));
            float radius = baseRadius * budgetPull;

            float jitter = (MathUtils.randomFloat(seed, i, 1, 7) - 0.5f) * GOLDEN_ANGLE * 0.55f;
            float angle = lastRotation + GOLDEN_ANGLE * i + jitter;

            Site site = sites.get(i);
            site.x = (float) Math.cos(angle) * radius;
            site.z = (float) Math.sin(angle) * radius;
            site.region = region;
            site.index = children.indexOf(region);
            site.softRadius = hexRadius * RADIAL_SPREAD * Math.max(0.5f, (float) Math.pow(MathUtils.clamp01(budgetFraction), 0.35f));
            site.weight = budgetFraction;
            site.wobblePhase = (MathUtils.hash64(seed, i, 2, 3) & 0xFFFF) / 65535.0f * (float) (Math.PI * 2);
            site.wobbleScale = site.softRadius * 0.08f * MathUtils.lerp(0.3f, 1.0f, MathUtils.randomFloat(seed, i, 3, 17));
            site.score = Float.NEGATIVE_INFINITY;
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
        float softRadius;
        float wobblePhase;
        float wobbleScale;
        float weight;
    }
}
