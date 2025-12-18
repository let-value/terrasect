package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BSP-style recursive subdivision strategy for organic territory generation.
 * 
 * Instead of Voronoi cells, this splits the parent region recursively along
 * noise-warped axes, producing irregular polygonal territories that respect
 * budget ratios exactly.
 * 
 * Layout format: float[n * 6] where each region has:
 *   [minX, minZ, maxX, maxZ, childIndex, splitSeed]
 * All coordinates are normalized to [-1, 1] range.
 */
public final class SubdivisionStrategy {

    private static final int CACHE_SIZE = 4096;
    private static final Map<Long, float[]> LAYOUT_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
                return size() > CACHE_SIZE;
            }
        }
    );

    // Constants for organic edge warping
    private static final float WARP_AMPLITUDE = 0.15f;
    private static final int WARP_OCTAVES = 3;

    private SubdivisionStrategy() {}

    public static float[] getLayout(long seed, List<Region> children) {
        long cacheKey = computeCacheKey(seed, children);
        float[] layout = LAYOUT_CACHE.get(cacheKey);
        if (layout == null) {
            layout = computeLayout(children, seed);
            LAYOUT_CACHE.put(cacheKey, layout);
        }
        return layout;
    }

    private static long computeCacheKey(long seed, List<Region> children) {
        long hash = seed;
        for (Region child : children) {
            hash = hash * 31 + child.name().hashCode();
            hash = hash * 31 + child.areaBudget();
        }
        return hash;
    }

    /**
     * Find which region contains the point (dx, dz) relative to parent center.
     * Returns the index into the layout array (multiple of 6), or -1 if outside.
     */
    public static int getCell(float[] layout, float dx, float dz, float radius) {
        // Normalize to [-1, 1]
        float nx = dx / radius;
        float nz = dz / radius;

        // Check each region's bounding box with edge warping
        for (int i = 0; i < layout.length; i += 6) {
            float minX = layout[i];
            float minZ = layout[i + 1];
            float maxX = layout[i + 2];
            float maxZ = layout[i + 3];
            long splitSeed = Float.floatToRawIntBits(layout[i + 5]);

            if (containsPointWarped(nx, nz, minX, minZ, maxX, maxZ, splitSeed)) {
                return i;
            }
        }

        // Fallback: find closest region center
        return findClosestRegion(layout, nx, nz);
    }

    public static Region getRegion(List<Region> children, float[] layout, int index) {
        if (index < 0 || index >= layout.length) return children.get(0);
        int childIndex = (int) layout[index + 4];
        return children.get(Math.min(childIndex, children.size() - 1));
    }

    public static long getSeed(long parentSeed, float[] layout, int index, Region region) {
        if (index < 0) return MathUtils.hash64(parentSeed, region.name().hashCode(), 0, 777);
        int childIndex = (int) layout[index + 4];
        return MathUtils.hash64(parentSeed, region.name().hashCode(), childIndex, 777);
    }

    public static float getNextCx(float cx, float radius, float[] layout, int index) {
        if (index < 0) return cx;
        float minX = layout[index];
        float maxX = layout[index + 2];
        return cx + ((minX + maxX) / 2.0f) * radius;
    }

    public static float getNextCz(float cz, float radius, float[] layout, int index) {
        if (index < 0) return cz;
        float minZ = layout[index + 1];
        float maxZ = layout[index + 3];
        return cz + ((minZ + maxZ) / 2.0f) * radius;
    }

    public static float getNextRadius(float radius, float[] layout, int index) {
        if (index < 0) return radius * 0.5f;
        float minX = layout[index];
        float minZ = layout[index + 1];
        float maxX = layout[index + 2];
        float maxZ = layout[index + 3];
        // Use the smaller dimension as radius for child
        float width = (maxX - minX) / 2.0f;
        float height = (maxZ - minZ) / 2.0f;
        return radius * Math.min(width, height);
    }

    // ========== Layout Computation (done once per seed) ==========

    private static float[] computeLayout(List<Region> children, long seed) {
        if (children.isEmpty()) return new float[0];
        if (children.size() == 1) {
            // Single child fills entire space
            return new float[] { -1, -1, 1, 1, 0, Float.intBitsToFloat((int) seed) };
        }

        int count = children.size();
        float[] layout = new float[count * 6];
        float[] budgets = new float[count];
        int[] indices = new int[count];  // Map sorted position -> original index
        float totalBudget = 0;

        for (int i = 0; i < count; i++) {
            budgets[i] = children.get(i).areaBudget();
            indices[i] = i;
            totalBudget += budgets[i];
        }

        // Sort indices by budget descending (largest regions first for better packing)
        for (int i = 0; i < count - 1; i++) {
            for (int j = i + 1; j < count; j++) {
                if (budgets[indices[j]] > budgets[indices[i]]) {
                    int tmp = indices[i];
                    indices[i] = indices[j];
                    indices[j] = tmp;
                }
            }
        }

        // Normalize budgets to fractions (area percentages)
        float[] sortedBudgets = new float[count];
        for (int i = 0; i < count; i++) {
            sortedBudgets[i] = budgets[indices[i]] / totalBudget;
        }

        // Recursive subdivision with sorted order
        subdivide(layout, sortedBudgets, indices, 0, count, -1, -1, 1, 1, seed, 0);

        return layout;
    }

    /**
     * Recursively subdivide a rectangular region among children.
     * Uses binary space partitioning with budget-weighted splits.
     */
    private static void subdivide(float[] layout, float[] budgets, int[] indices,
                                   int start, int end,
                                   float minX, float minZ, float maxX, float maxZ,
                                   long seed, int depth) {
        int count = end - start;

        if (count == 1) {
            // Base case: assign this region to the single child
            int originalIdx = indices[start];
            int layoutIdx = originalIdx * 6;  // Store at original index position
            layout[layoutIdx] = minX;
            layout[layoutIdx + 1] = minZ;
            layout[layoutIdx + 2] = maxX;
            layout[layoutIdx + 3] = maxZ;
            layout[layoutIdx + 4] = originalIdx;
            layout[layoutIdx + 5] = Float.intBitsToFloat((int) MathUtils.hash64(seed, originalIdx, depth, 123));
            return;
        }

        // Find optimal split point that best matches budget ratios
        float totalBudget = 0;
        for (int i = start; i < end; i++) {
            totalBudget += budgets[i];
        }

        // Find split point closest to 50% of total budget
        float accumulated = 0;
        int bestMid = start + 1;
        float bestDiff = Float.MAX_VALUE;
        
        for (int i = start; i < end - 1; i++) {
            accumulated += budgets[i];
            float diff = Math.abs(accumulated - totalBudget / 2);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestMid = i + 1;
            }
        }

        int mid = bestMid;
        
        // Calculate exact split ratio based on accumulated budgets
        float leftBudget = 0;
        for (int i = start; i < mid; i++) {
            leftBudget += budgets[i];
        }
        float splitRatio = leftBudget / totalBudget;

        // Minimal jitter to keep organic feel without hurting accuracy
        float jitter = (hashToFloat(seed, depth, 0) - 0.5f) * 0.05f;
        splitRatio = clamp(splitRatio + jitter, 0.15f, 0.85f);

        // Choose split axis based on aspect ratio + randomness
        float width = maxX - minX;
        float height = maxZ - minZ;
        boolean splitVertical = (width > height) ^ (hashToFloat(seed, depth, 1) > 0.7f);

        // Apply split
        long leftSeed = MathUtils.hash64(seed, depth, 0, 1);
        long rightSeed = MathUtils.hash64(seed, depth, 1, 1);

        if (splitVertical) {
            float splitX = minX + width * splitRatio;
            subdivide(layout, budgets, indices, start, mid, minX, minZ, splitX, maxZ, leftSeed, depth + 1);
            subdivide(layout, budgets, indices, mid, end, splitX, minZ, maxX, maxZ, rightSeed, depth + 1);
        } else {
            float splitZ = minZ + height * splitRatio;
            subdivide(layout, budgets, indices, start, mid, minX, minZ, maxX, splitZ, leftSeed, depth + 1);
            subdivide(layout, budgets, indices, mid, end, minX, splitZ, maxX, maxZ, rightSeed, depth + 1);
        }
    }

    // ========== Point-in-Region Testing with Organic Edges ==========

    private static boolean containsPointWarped(float px, float pz,
                                                float minX, float minZ, float maxX, float maxZ,
                                                long seed) {
        // Basic bounds check first (fast path)
        float margin = WARP_AMPLITUDE;
        if (px < minX - margin || px > maxX + margin ||
            pz < minZ - margin || pz > maxZ + margin) {
            return false;
        }

        // Warp the edges for organic boundaries
        float warpedMinX = minX + edgeWarp(pz, seed, 0);
        float warpedMaxX = maxX + edgeWarp(pz, seed, 1);
        float warpedMinZ = minZ + edgeWarp(px, seed, 2);
        float warpedMaxZ = maxZ + edgeWarp(px, seed, 3);

        return px >= warpedMinX && px <= warpedMaxX &&
               pz >= warpedMinZ && pz <= warpedMaxZ;
    }

    /**
     * Compute edge warp using layered noise (no allocations).
     */
    private static float edgeWarp(float coord, long seed, int edgeId) {
        float warp = 0;
        float amplitude = WARP_AMPLITUDE;
        float frequency = 4.0f;

        for (int octave = 0; octave < WARP_OCTAVES; octave++) {
            // Simple hash-based noise
            int coordInt = (int) (coord * frequency * 1000);
            float noise = hashToFloat(seed, coordInt, edgeId + octave * 100);
            warp += (noise - 0.5f) * amplitude;

            amplitude *= 0.5f;
            frequency *= 2.0f;
        }

        return warp;
    }

    private static int findClosestRegion(float[] layout, float nx, float nz) {
        int closest = 0;
        float closestDist = Float.MAX_VALUE;

        for (int i = 0; i < layout.length; i += 6) {
            float centerX = (layout[i] + layout[i + 2]) / 2;
            float centerZ = (layout[i + 1] + layout[i + 3]) / 2;
            float dx = nx - centerX;
            float dz = nz - centerZ;
            float dist = dx * dx + dz * dz;

            if (dist < closestDist) {
                closestDist = dist;
                closest = i;
            }
        }

        return closest;
    }

    // ========== Utilities ==========

    private static float hashToFloat(long seed, int a, int b) {
        long h = MathUtils.hash64(seed, a, b, 0);
        return (h & 0xFFFF) / 65536.0f;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
