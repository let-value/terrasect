package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VoronoiGenerationStrategy implements RegionGenerationStrategy {

    private static final float NARRATIVE_AREA_SCALE = 0.06f;
    private static final float SHELL_PLACEMENT_RADIUS_SCALE = 0.70f;
    private static final float ANGLE_JITTER_SCALE = 0.5f;
    private static final float RADIUS_JITTER_SCALE = 0.2f;

    private final List<Site> sites = new ArrayList<>();
    private final Map<Region, Integer> shells = new HashMap<>();
    private final List<Region> queue = new ArrayList<>();
    private final Set<String> visited = new HashSet<>();
    private final Map<Integer, List<Region>> byShell = new HashMap<>();
    private final List<Integer> sortedShells = new ArrayList<>();

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
        orderedChildren.sort(Comparator.comparing(Region::name));

        sites.clear();
        if (orderedChildren.isEmpty()) return sites;

        Region hub = orderedChildren.get(0);
        int maxScore = -1;

        for (Region r : orderedChildren) {
            int score = r.adjacentTo().size();
            if (score > maxScore) {
                maxScore = score;
                hub = r;
            }
        }

        shells.clear();
        shells.put(hub, 0);

        queue.clear();
        queue.add(hub);

        visited.clear();
        visited.add(hub.name());

        int queueIndex = 0;
        while (queueIndex < queue.size()) {
            Region current = queue.get(queueIndex++);
            int currentShell = shells.get(current);

            for (String neighborName : current.sortedAdjacentTo()) {
                if (!visited.contains(neighborName)) {
                    for (Region r : orderedChildren) {
                        if (r.name().equals(neighborName)) {
                            visited.add(neighborName);
                            shells.put(r, currentShell + 1);
                            queue.add(r);
                            break;
                        }
                    }
                }
            }
        }

        int maxShell = 0;
        for (int s : shells.values()) maxShell = Math.max(maxShell, s);

        for (Region r : orderedChildren) {
            if (!shells.containsKey(r)) {
                shells.put(r, maxShell + 1);
            }
        }

        for (List<Region> list : byShell.values()) {
            list.clear();
        }
        shells.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(Region::name)))
            .forEach(entry -> byShell.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey()));
        byShell.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        float totalBudget = getTotalWeight(orderedChildren);
        float currentInnerRadius = 0;

        sortedShells.clear();
        sortedShells.addAll(byShell.keySet());
        Collections.sort(sortedShells);

        int siteIndex = 0;
        for (int shell : sortedShells) {
            List<Region> shellRegions = byShell.get(shell);
            shellRegions.sort(Comparator.comparing(Region::name));

            float shellBudget = 0;
            for (Region r : shellRegions) shellBudget += r.areaBudget();

            float budgetFraction = shellBudget / totalBudget;

            float nextInnerRadius = (float) Math.sqrt(currentInnerRadius * currentInnerRadius + hexRadius * hexRadius * budgetFraction);

            float placementRadius = shell == 0 ? 0 : nextInnerRadius * SHELL_PLACEMENT_RADIUS_SCALE;

            float angleStep = (float) (2 * Math.PI / shellRegions.size());
            float angleOffset = (MathUtils.hash64(seed, shell, 0, 0) & 0xFFFF) / 65536.0f * (float) Math.PI;

            for (int i = 0; i < shellRegions.size(); i++) {
                Region region = shellRegions.get(i);

                float angleJitter = ((MathUtils.hash64(seed, shell, i, 1) & 0xFFFF) / 65536.0f - 0.5f) * angleStep * ANGLE_JITTER_SCALE;
                float angle = angleOffset + i * angleStep + angleJitter;

                float radiusJitter = ((MathUtils.hash64(seed, shell, i, 2) & 0xFFFF) / 65536.0f - 0.5f) * (nextInnerRadius - currentInnerRadius) * RADIUS_JITTER_SCALE;
                float rRadius = placementRadius + radiusJitter;

                ensureSiteCapacity(siteIndex + 1);
                Site site = sites.get(siteIndex++);
                if (shell == 0) {
                    site.x = 0;
                    site.z = 0;
                } else {
                    site.x = (float) Math.cos(angle) * rRadius;
                    site.z = (float) Math.sin(angle) * rRadius;
                }
                site.region = region;
                site.index = children.indexOf(region);
            }

            currentInnerRadius = nextInnerRadius;
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

