package com.terrasect.common.generation;

import java.util.ArrayList;
import java.util.List;

public class World {

    public record TraversalStep(Region region, float edgeDistance) {}

    private static Region root;

    public static void setRoot(Region newRoot) {
        root = newRoot;
    }

    public static Region getRoot() {
        if (root == null) {
            throw new IllegalStateException("NarrativeWorld root not initialized!");
        }
        return root;
    }

    /**
     * Get the leaf Region at a given location by traversing the hierarchy.
     */
    public static Region getRegion(int x, int z, Strategy context) {
        return getRegionRecursive(getRoot(), x, z, context, 0);
    }

    /**
     * Get the full hierarchy of regions and edge distances at a given location.
     * Useful for debugging and visualization.
     */
    public static List<TraversalStep> getRegionHierarchy(int x, int z, Strategy context) {
        List<TraversalStep> steps = new ArrayList<>();
        getRegionHierarchyRecursive(getRoot(), x, z, context, 0, steps);
        return steps;
    }

    private static void getRegionHierarchyRecursive(Region parent, int x, int z, Strategy context, int depth, List<TraversalStep> steps) {
        if (!parent.hasChildren()) {
            return;
        }

        // Determine scale based on depth
        float scale = (depth == 0) ? 400.0f : 200.0f;
        int cellSize = (depth == 0) ? 2048 : 512;

        // Generate noise for this layer
        long layerSeed = MathUtils.hash64(context.getSeed(), depth, 0, 0);
        long regionData = RegionField.getRegionData(x, z, layerSeed, cellSize, scale);
        int regionId = RegionField.unpackRegionId(regionData);
        float edge = RegionField.unpackEdge(regionData);

        // Pick a child
        float river = context.getRiverInfluence(x, z);
        Region selectedChild = pickChild(parent, regionId, river);

        steps.add(new TraversalStep(selectedChild, edge));

        // Recurse
        getRegionHierarchyRecursive(selectedChild, x, z, context, depth + 1, steps);
    }

    private static Region getRegionRecursive(Region parent, int x, int z, Strategy context, int depth) {
        if (!parent.hasChildren()) {
            return parent;
        }

        // Determine scale based on depth
        // Depth 0 (Root -> Cluster): Large scale
        // Depth 1 (Cluster -> Region): Medium scale
        float scale = (depth == 0) ? 400.0f : 200.0f;
        int cellSize = (depth == 0) ? 2048 : 512;

        // Generate noise for this layer
        // We mix the depth into the seed to ensure different noise patterns per layer
        long layerSeed = MathUtils.hash64(context.getSeed(), depth, 0, 0);
        long regionData = RegionField.getRegionData(x, z, layerSeed, cellSize, scale);
        int regionId = RegionField.unpackRegionId(regionData);

        // Pick a child
        float river = context.getRiverInfluence(x, z);
        Region selectedChild = pickChild(parent, regionId, river);

        // Recurse
        return getRegionRecursive(selectedChild, x, z, context, depth + 1);
    }

    private static Region pickChild(Region parent, int regionId, float river) {
        // Deterministic RNG from regionId
        long rng = MathUtils.hash64(regionId, parent.name().hashCode(), 0, 0);
        float randomVal = (rng & 0xFFFF) / 65536.0f; // 0..1

        // Calculate total weight with bias
        float totalWeight = 0;
        List<Region> children = parent.children();
        float[] weights = new float[children.size()];
        
        for (int i = 0; i < children.size(); i++) {
            Region r = children.get(i);
            float weight = r.areaBudget();
            
            // River bias (only applies to specific regions for now)
            if (r.name().equals("HARBOR") || r.name().equals("CRYSTAL_CANYON")) {
                weight *= (1.0f + river * 2.0f); // Up to 3x weight near rivers
            }
            
            weights[i] = weight;
            totalWeight += weight;
        }

        // Weighted choice
        float target = randomVal * totalWeight;
        float current = 0;
        for (int i = 0; i < children.size(); i++) {
            current += weights[i];
            if (current >= target) {
                return children.get(i);
            }
        }
        return children.get(0);
    }
}
