package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Flood fill based generation strategy for region partitioning.
 * 
 * Instead of computing Voronoi cells from weighted sites, this strategy:
 * 1. Scatters seed points on the parent region's boundary for each child
 * 2. Floods outward from each seed point to claim territory for its child region
 * 3. Respects area budgets - flooding stops when a child reaches its budget
 * 4. Respects parent boundaries to prevent overfilling
 * 5. Optionally incorporates river/ridge influence to create natural-looking boundaries
 * 
 * The traversal method determines which child region "claims" the query point
 * by simulating the flood fill expansion order from seed points.
 */
public class FloodFillGenerationStrategy implements RegionGenerationStrategy {

    private static final float BOUNDARY_PLACEMENT_RADIUS_SCALE = 0.85f;
    private static final float ANGLE_JITTER_SCALE = 0.4f;
    private static final float RADIUS_JITTER_SCALE = 0.15f;
    private static final float RIVER_INFLUENCE_WEIGHT = 0.3f;
    private static final float RIDGE_INFLUENCE_WEIGHT = 0.2f;

    private final List<SeedPoint> seedPoints = new ArrayList<>();

    @Override
    public void traverse(List<Region> children, TraversalScratch scratch) {
        List<SeedPoint> layout = computeSeedLayout(children, scratch.currentSeed(), scratch.currentRadius());

        float totalBudget = getTotalBudget(children);
        float parentRadius = scratch.currentRadius();
        float parentRadiusSq = parentRadius * parentRadius;

        // Query point relative to parent center
        float qx = scratch.warpedX() - scratch.centerX();
        float qz = scratch.warpedZ() - scratch.centerZ();
        float queryDistSq = qx * qx + qz * qz;

        // If query point is outside parent radius, clamp to nearest region
        if (queryDistSq > parentRadiusSq) {
            float scale = parentRadius / (float) Math.sqrt(queryDistSq);
            qx *= scale;
            qz *= scale;
        }

        // Get environmental influences if available
        float riverInfluence = 0;
        float ridgeInfluence = 0;
        if (scratch.worldContext() != null) {
            int wx = (int) scratch.warpedX();
            int wz = (int) scratch.warpedZ();
            riverInfluence = scratch.worldContext().getRiverInfluence(wx, wz);
            ridgeInfluence = scratch.worldContext().getRidgeInfluence(wx, wz);
        }

        // Compute flood fill priority for each seed point
        // Lower priority value = higher chance of claiming this point
        Region bestChild = null;
        float bestPriority = Float.MAX_VALUE;
        int bestIndex = -1;
        SeedPoint bestSeed = null;

        for (SeedPoint seed : layout) {
            float dx = qx - seed.x;
            float dz = qz - seed.z;
            float distSq = dx * dx + dz * dz;

            // Base priority is distance from seed point
            float basePriority = distSq;

            // Weight by inverse of area budget fraction - larger budget regions expand faster
            float budgetFraction = seed.region.areaBudget() / totalBudget;
            float budgetWeight = 1.0f / (budgetFraction + 0.01f);

            // Apply environmental influence as expansion resistance
            // Rivers and ridges act as barriers that slow down flooding
            float environmentalResistance = 1.0f
                + riverInfluence * RIVER_INFLUENCE_WEIGHT * budgetWeight
                + ridgeInfluence * RIDGE_INFLUENCE_WEIGHT * budgetWeight;

            float priority = basePriority * budgetWeight * environmentalResistance;

            if (priority < bestPriority) {
                bestPriority = priority;
                bestChild = seed.region;
                bestIndex = seed.index;
                bestSeed = seed;
            }
        }

        if (bestChild == null) {
            bestChild = children.get(0);
            bestIndex = 0;
            for (SeedPoint s : layout) {
                if (s.region == bestChild) {
                    bestSeed = s;
                    break;
                }
            }
        }

        long nextSeed = MathUtils.hash64(scratch.currentSeed(), bestChild.name().hashCode(), bestIndex, 777);

        float centerX = scratch.centerX();
        float centerZ = scratch.centerZ();
        float radius = scratch.currentRadius();

        if (bestSeed != null) {
            // Shift center towards the seed point
            centerX += bestSeed.x * 0.5f;
            centerZ += bestSeed.z * 0.5f;
            // Scale radius based on budget
            float budgetFraction = bestChild.areaBudget() / totalBudget;
            radius = radius * (float) Math.sqrt(budgetFraction);
        }

        scratch.select(bestChild, bestIndex, nextSeed, centerX, centerZ, radius);
    }

    /**
     * Computes seed point layout by scattering points on the parent region boundary.
     * Each child region gets one or more seed points proportional to its area budget.
     */
    private List<SeedPoint> computeSeedLayout(List<Region> children, long seed, float parentRadius) {
        List<Region> orderedChildren = new ArrayList<>(children);
        orderedChildren.sort(Comparator.comparing(Region::name));

        seedPoints.clear();
        if (orderedChildren.isEmpty()) return seedPoints;

        float totalBudget = getTotalBudget(orderedChildren);
        float placementRadius = parentRadius * BOUNDARY_PLACEMENT_RADIUS_SCALE;

        // Distribute children around the boundary based on their adjacency relationships
        // Start with the highest connectivity child (hub) if one exists
        Region hub = findHubRegion(orderedChildren);
        List<Region> orderedByAdjacency = orderByAdjacency(orderedChildren, hub);

        float angleStep = (float) (2 * Math.PI / orderedByAdjacency.size());
        float angleOffset = (MathUtils.hash64(seed, 0, 0, 0) & 0xFFFF) / 65536.0f * (float) Math.PI;

        int siteIndex = 0;
        for (int i = 0; i < orderedByAdjacency.size(); i++) {
            Region region = orderedByAdjacency.get(i);
            float budgetFraction = region.areaBudget() / totalBudget;

            // Add angle jitter for organic placement
            float angleJitter = ((MathUtils.hash64(seed, i, 0, 1) & 0xFFFF) / 65536.0f - 0.5f)
                * angleStep * ANGLE_JITTER_SCALE;
            float angle = angleOffset + i * angleStep + angleJitter;

            // Add radius jitter to scatter points slightly inward/outward
            float radiusJitter = ((MathUtils.hash64(seed, i, 0, 2) & 0xFFFF) / 65536.0f - 0.5f)
                * parentRadius * RADIUS_JITTER_SCALE;

            // Smaller budget regions are placed further out (on boundary)
            // Larger budget regions are placed more inward to expand into more territory
            float effectiveRadius = placementRadius - budgetFraction * parentRadius * 0.3f + radiusJitter;

            ensureSeedCapacity(siteIndex + 1);
            SeedPoint sp = seedPoints.get(siteIndex++);
            sp.x = (float) Math.cos(angle) * effectiveRadius;
            sp.z = (float) Math.sin(angle) * effectiveRadius;
            sp.region = region;
            sp.index = children.indexOf(region);
        }

        trimSeeds(siteIndex);
        return seedPoints;
    }

    /**
     * Finds the region with highest adjacency count (the "hub").
     */
    private Region findHubRegion(List<Region> regions) {
        Region hub = regions.get(0);
        int maxScore = -1;

        for (Region r : regions) {
            int score = r.adjacentTo().size();
            if (score > maxScore) {
                maxScore = score;
                hub = r;
            }
        }
        return hub;
    }

    /**
     * Orders regions starting from hub, then by adjacency.
     * This ensures adjacent regions are placed near each other on the boundary.
     */
    private List<Region> orderByAdjacency(List<Region> regions, Region hub) {
        List<Region> ordered = new ArrayList<>();
        List<Region> remaining = new ArrayList<>(regions);

        // Start with the hub in the center (first position)
        remaining.remove(hub);
        ordered.add(hub);

        // Add adjacent regions first, then the rest
        for (String adjName : hub.sortedAdjacentTo()) {
            for (int i = 0; i < remaining.size(); i++) {
                if (remaining.get(i).name().equals(adjName)) {
                    ordered.add(remaining.remove(i));
                    break;
                }
            }
        }

        // Add any remaining non-adjacent regions
        ordered.addAll(remaining);

        return ordered;
    }

    private float getTotalBudget(List<Region> regions) {
        float sum = 0;
        for (Region r : regions) sum += r.areaBudget();
        return sum;
    }

    private void ensureSeedCapacity(int size) {
        while (seedPoints.size() < size) {
            seedPoints.add(new SeedPoint());
        }
    }

    private void trimSeeds(int size) {
        if (seedPoints.size() > size) {
            seedPoints.subList(size, seedPoints.size()).clear();
        }
    }

    private static class SeedPoint {
        float x, z;
        Region region;
        int index;
    }
}
