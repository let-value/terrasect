package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VoronoiGenerationStrategy implements RegionGenerationStrategy {

    private static final float NARRATIVE_AREA_SCALE = 0.06f;
    private static final float SHELL_PLACEMENT_RADIUS_SCALE = 0.90f;
    private static final float ANGLE_JITTER_SCALE = 0.35f;
    private static final float RADIUS_JITTER_SCALE = 0.1f;

    private final List<Site> sites = new ArrayList<>();
    @Override
    public void traverse(List<Region> children, TraversalScratch scratch) {
        List<Site> layout = computeNarrativeLayout(children, scratch.currentSeed(), scratch.currentRadius());

        float totalBudget = getTotalWeight(children);
        float areaScale = scratch.currentRadius() * scratch.currentRadius() * NARRATIVE_AREA_SCALE;

        Region bestChild = null;
        float minMetric = Float.MAX_VALUE;
        int bestChildIndex = -1;
        Site bestSite = null;

        for (Site site : layout) {
            float distSq = (scratch.warpedX() - (scratch.centerX() + site.x)) * (scratch.warpedX() - (scratch.centerX() + site.x))
                + (scratch.warpedZ() - (scratch.centerZ() + site.z)) * (scratch.warpedZ() - (scratch.centerZ() + site.z));
            float weight = (site.region.areaBudget() / totalBudget) * areaScale;
            float metric = distSq - weight;

            if (metric < minMetric) {
                minMetric = metric;
                bestChild = site.region;
                bestChildIndex = site.index;
                bestSite = site;
            }
        }

        if (bestChild == null) {
            bestChild = children.get(0);
            for (Site s : layout) {
                if (s.region == bestChild) {
                    bestSite = s;
                    break;
                }
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
        orderedChildren.sort(Comparator.comparing(Region::areaBudget).reversed().thenComparing(Region::name));

        sites.clear();
        if (orderedChildren.isEmpty()) return sites;

        float totalBudget = getTotalWeight(orderedChildren);
        float cumulativeWeight = 0.0f;
        float goldenAngle = (float) (Math.PI * (3.0 - Math.sqrt(5.0)));
        float radiusScale = hexRadius * SHELL_PLACEMENT_RADIUS_SCALE;

        int siteIndex = 0;
        for (int i = 0; i < orderedChildren.size(); i++) {
            Region region = orderedChildren.get(i);
            float weight = region.areaBudget() / totalBudget;

            ensureSiteCapacity(siteIndex + 1);
            Site site = sites.get(siteIndex++);

            if (i == 0) {
                site.x = 0;
                site.z = 0;
            } else {
                float anchor = cumulativeWeight + weight * 0.5f;
                float baseRadius = hexRadius * (0.2f + MathUtils.clamp01(anchor) * SHELL_PLACEMENT_RADIUS_SCALE);

                float angleJitter = ((MathUtils.hash64(seed, i, 1, 0) & 0xFFFF) / 65536.0f - 0.5f) * ANGLE_JITTER_SCALE;
                float angle = goldenAngle * i + angleJitter * goldenAngle;

                float radialJitter = ((MathUtils.hash64(seed, i, 2, 0) & 0xFFFF) / 65536.0f - 0.5f)
                    * hexRadius * RADIUS_JITTER_SCALE * weight;

                float rRadius = baseRadius + radialJitter;
                site.x = (float) Math.cos(angle) * rRadius;
                site.z = (float) Math.sin(angle) * rRadius;
            }

            site.region = region;
            site.index = children.indexOf(region);
            cumulativeWeight += weight;
        }

        trimSites(siteIndex);
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
    }
}

