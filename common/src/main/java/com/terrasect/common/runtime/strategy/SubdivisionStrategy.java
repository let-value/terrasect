package com.terrasect.common.runtime.strategy;

import com.terrasect.common.util.MathUtils;
import com.terrasect.common.api.Region;

import java.util.List;

/**
 * Cache-free BSP subdivision using recursive traversal.
 * 
 * Instead of pre-computing and caching the full BSP tree, we traverse it
 * on-the-fly for each query. Since BSP depth is O(log n) and n is typically
 * 2-8 children, this is just 1-3 recursive calls per query.
 * 
 * The split decisions are fully deterministic from seed, so repeated queries
 * with the same inputs produce identical results without caching.
 */
public final class SubdivisionStrategy {

    // Constants for organic edge warping
    private static final float WARP_AMPLITUDE = 0.12f;
    private static final int WARP_OCTAVES = 2;

    private SubdivisionStrategy() {}

    /**
     * Query which child region contains the point, writing results to scratch buffer.
     * 
     * @param seed Parent seed for deterministic splits
     * @param children Child regions with budget weights
     * @param dx X offset from parent center
     * @param dz Z offset from parent center  
     * @param radius Parent radius
     * @param jitter Split jitter amount (0.0 = precise, 0.5 = very organic)
     * @param scratch Output: [childIndex, centerX, centerZ, radiusScale, -, -, -]
     */
    public static void query(long seed, List<Region> children, float dx, float dz,
                             float radius, float jitter, float[] scratch) {
        if (children.isEmpty()) {
            scratch[0] = 0;
            scratch[1] = 0;
            scratch[2] = 0;
            scratch[3] = 0.5f;
            return;
        }

        int count = children.size();
        if (count == 1) {
            scratch[0] = 0;
            scratch[1] = 0;
            scratch[2] = 0;
            scratch[3] = 1.0f;
            return;
        }

        // Normalize query point to [-1, 1]
        float nx = dx / radius;
        float nz = dz / radius;

        // Pre-compute budgets
        float totalBudget = 0;
        for (int i = 0; i < count; i++) {
            totalBudget += children.get(i).areaBudget();
        }

        float[] budgets = new float[count];
        for (int i = 0; i < count; i++) {
            budgets[i] = children.get(i).areaBudget() / totalBudget;
        }

        // Sort indices by budget descending (deterministic)
        int[] indices = sortByBudgetDescending(budgets, count);

        // Traverse BSP to find containing region
        traverseBSP(nx, nz, budgets, indices, 0, count, -1, -1, 1, 1, seed, 0, jitter, scratch);
    }

    /**
     * Recursively traverse BSP tree to find containing region.
     * Returns immediately when leaf node is reached.
     */
    private static void traverseBSP(float px, float pz,
                                     float[] budgets, int[] indices,
                                     int start, int end,
                                     float minX, float minZ, float maxX, float maxZ,
                                     long seed, int depth, float jitterAmount,
                                     float[] scratch) {
        int count = end - start;

        if (count == 1) {
            // Leaf node - this is our region
            int originalIdx = indices[start];
            scratch[0] = originalIdx;
            
            // Subdivision produces rectangles. Transform to cell-local coordinates.
            float centerX = (minX + maxX) / 2.0f;
            float centerZ = (minZ + maxZ) / 2.0f;
            float halfWidth = (maxX - minX) / 2.0f;
            float halfHeight = (maxZ - minZ) / 2.0f;
            // Use the smaller dimension as the "radius" for circular child layout
            float cellRadius = Math.min(halfWidth, halfHeight);
            cellRadius = Math.max(cellRadius, 0.1f); // Minimum size
            
            scratch[1] = centerX;
            scratch[2] = centerZ;
            scratch[3] = cellRadius;
            // Store cell center for seed uniqueness
            scratch[4] = centerX;
            scratch[5] = centerZ;
            return;
        }

        // Find split point based on budgets
        float totalBudget = 0;
        for (int i = start; i < end; i++) {
            totalBudget += budgets[indices[i]];
        }

        float accumulated = 0;
        int bestMid = start + 1;
        float bestDiff = Float.MAX_VALUE;
        
        for (int i = start; i < end - 1; i++) {
            accumulated += budgets[indices[i]];
            float diff = Math.abs(accumulated - totalBudget / 2);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestMid = i + 1;
            }
        }

        int mid = bestMid;
        
        // Calculate split ratio
        float leftBudget = 0;
        for (int i = start; i < mid; i++) {
            leftBudget += budgets[indices[i]];
        }
        float splitRatio = leftBudget / totalBudget;

        // Apply jitter
        float jitterVal = hashToFloat(seed, depth, 0);
        splitRatio = clamp(splitRatio + (jitterVal - 0.5f) * jitterAmount, 0.15f, 0.85f);

        // Choose split axis
        float width = maxX - minX;
        float height = maxZ - minZ;
        boolean splitVertical = (width > height) ^ (hashToFloat(seed, depth, 1) > 0.7f);

        // Compute split position (no warp for debugging)
        if (splitVertical) {
            float splitX = minX + width * splitRatio;
            
            long leftSeed = MathUtils.hash64(seed, depth, 0, 1);
            long rightSeed = MathUtils.hash64(seed, depth, 1, 1);
            
            if (px < splitX) {
                traverseBSP(px, pz, budgets, indices, start, mid, minX, minZ, splitX, maxZ, leftSeed, depth + 1, jitterAmount, scratch);
            } else {
                traverseBSP(px, pz, budgets, indices, mid, end, splitX, minZ, maxX, maxZ, rightSeed, depth + 1, jitterAmount, scratch);
            }
        } else {
            float splitZ = minZ + height * splitRatio;
            
            long leftSeed = MathUtils.hash64(seed, depth, 0, 1);
            long rightSeed = MathUtils.hash64(seed, depth, 1, 1);
            
            if (pz < splitZ) {
                traverseBSP(px, pz, budgets, indices, start, mid, minX, minZ, maxX, splitZ, leftSeed, depth + 1, jitterAmount, scratch);
            } else {
                traverseBSP(px, pz, budgets, indices, mid, end, minX, splitZ, maxX, maxZ, rightSeed, depth + 1, jitterAmount, scratch);
            }
        }
    }

    /**
     * Sort indices by budget descending. Simple O(n²) fine for n < 10.
     */
    private static int[] sortByBudgetDescending(float[] budgets, int count) {
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) indices[i] = i;
        
        for (int i = 0; i < count - 1; i++) {
            for (int j = i + 1; j < count; j++) {
                if (budgets[indices[j]] > budgets[indices[i]]) {
                    int tmp = indices[i];
                    indices[i] = indices[j];
                    indices[j] = tmp;
                }
            }
        }
        return indices;
    }

    /**
     * Compute edge warp using layered noise.
     * Currently unused - reserved for organic edge warping feature.
     */
    @SuppressWarnings("unused") // Reserved for future organic edge warping
    private static float edgeWarp(float coord, long seed, int depth) {
        float warp = 0;
        float amplitude = WARP_AMPLITUDE;
        float frequency = 4.0f;

        for (int octave = 0; octave < WARP_OCTAVES; octave++) {
            int coordInt = (int) (coord * frequency * 1000);
            float noise = hashToFloat(seed, coordInt, depth + octave * 100);
            warp += (noise - 0.5f) * amplitude;
            amplitude *= 0.5f;
            frequency *= 2.0f;
        }

        return warp;
    }

    /**
     * Compute child seed deterministically.
     */
    public static long getSeed(long parentSeed, int childIndex, Region region) {
        return MathUtils.hash64(parentSeed, region.name().hashCode(), childIndex, 777);
    }

    private static float hashToFloat(long seed, int a, int b) {
        long h = MathUtils.hash64(seed, a, b, 0);
        return (h & 0xFFFF) / 65536.0f;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
