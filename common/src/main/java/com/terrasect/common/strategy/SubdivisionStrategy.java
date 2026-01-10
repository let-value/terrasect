package com.terrasect.common.strategy;

import com.terrasect.common.definition.Region;
import com.terrasect.common.util.MathUtils;
import java.util.List;

public final class SubdivisionStrategy {

    private static final float WARP_AMPLITUDE = 0.12f;
    private static final int WARP_OCTAVES = 2;

    private static final ThreadLocal<float[]> BUDGETS_BUFFER = ThreadLocal.withInitial(() -> new float[8]);
    private static final ThreadLocal<int[]> INDICES_BUFFER = ThreadLocal.withInitial(() -> new int[8]);

    private SubdivisionStrategy() {}

    public static void query(
            long seed, List<Region> children, float dx, float dz, float radius, float jitter, QueryResult out) {
        if (children.isEmpty()) {
            out.childIndex = 0;
            out.centerX = 0;
            out.centerZ = 0;
            out.radius = 0.5f;
            return;
        }

        int count = children.size();
        if (count == 1) {
            out.childIndex = 0;
            out.centerX = 0;
            out.centerZ = 0;
            out.radius = 1.0f;
            return;
        }

        float nx = dx / radius;
        float nz = dz / radius;

        float totalBudget = 0;
        for (int i = 0; i < count; i++) {
            totalBudget += children.get(i).areaBudget();
        }

        float[] budgets = getBudgetsBuffer(count);
        for (int i = 0; i < count; i++) {
            budgets[i] = children.get(i).areaBudget() / totalBudget;
        }

        int[] indices = getIndicesBuffer(count);
        sortByBudgetDescending(budgets, indices, count);

        traverseBSP(nx, nz, budgets, indices, 0, count, -1, -1, 1, 1, seed, 0, jitter, out);
    }

    private static void traverseBSP(
            float px,
            float pz,
            float[] budgets,
            int[] indices,
            int start,
            int end,
            float minX,
            float minZ,
            float maxX,
            float maxZ,
            long seed,
            int depth,
            float jitterAmount,
            QueryResult out) {
        int count = end - start;

        if (count == 1) {

            int originalIdx = indices[start];
            out.childIndex = originalIdx;

            float centerX = (minX + maxX) / 2.0f;
            float centerZ = (minZ + maxZ) / 2.0f;
            float halfWidth = (maxX - minX) / 2.0f;
            float halfHeight = (maxZ - minZ) / 2.0f;

            float cellRadius = Math.min(halfWidth, halfHeight);
            cellRadius = Math.max(cellRadius, 0.1f);

            out.centerX = centerX;
            out.centerZ = centerZ;
            out.radius = cellRadius;

            out.siteX = centerX;
            out.siteZ = centerZ;

            float distToLeft = px - minX;
            float distToRight = maxX - px;
            float distToBottom = pz - minZ;
            float distToTop = maxZ - pz;
            float minDist = Math.min(Math.min(distToLeft, distToRight), Math.min(distToBottom, distToTop));

            out.edgeDistance = Math.min(1.0f, minDist / cellRadius);
            return;
        }

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

        float leftBudget = 0;
        for (int i = start; i < mid; i++) {
            leftBudget += budgets[indices[i]];
        }
        float splitRatio = leftBudget / totalBudget;

        float jitterVal = hashToFloat(seed, depth, 0);
        splitRatio = clamp(splitRatio + (jitterVal - 0.5f) * jitterAmount, 0.15f, 0.85f);

        float width = maxX - minX;
        float height = maxZ - minZ;
        boolean splitVertical = (width > height) ^ (hashToFloat(seed, depth, 1) > 0.7f);

        if (splitVertical) {
            float splitX = minX + width * splitRatio;

            long leftSeed = MathUtils.hash64(seed, depth, 0, 1);
            long rightSeed = MathUtils.hash64(seed, depth, 1, 1);

            if (px < splitX) {
                traverseBSP(
                        px,
                        pz,
                        budgets,
                        indices,
                        start,
                        mid,
                        minX,
                        minZ,
                        splitX,
                        maxZ,
                        leftSeed,
                        depth + 1,
                        jitterAmount,
                        out);
            } else {
                traverseBSP(
                        px,
                        pz,
                        budgets,
                        indices,
                        mid,
                        end,
                        splitX,
                        minZ,
                        maxX,
                        maxZ,
                        rightSeed,
                        depth + 1,
                        jitterAmount,
                        out);
            }
        } else {
            float splitZ = minZ + height * splitRatio;

            long leftSeed = MathUtils.hash64(seed, depth, 0, 1);
            long rightSeed = MathUtils.hash64(seed, depth, 1, 1);

            if (pz < splitZ) {
                traverseBSP(
                        px,
                        pz,
                        budgets,
                        indices,
                        start,
                        mid,
                        minX,
                        minZ,
                        maxX,
                        splitZ,
                        leftSeed,
                        depth + 1,
                        jitterAmount,
                        out);
            } else {
                traverseBSP(
                        px,
                        pz,
                        budgets,
                        indices,
                        mid,
                        end,
                        minX,
                        splitZ,
                        maxX,
                        maxZ,
                        rightSeed,
                        depth + 1,
                        jitterAmount,
                        out);
            }
        }
    }

    private static void sortByBudgetDescending(float[] budgets, int[] indices, int count) {
        for (int i = 0; i < count; i++) {
            indices[i] = i;
        }

        for (int i = 0; i < count - 1; i++) {
            for (int j = i + 1; j < count; j++) {
                if (budgets[indices[j]] > budgets[indices[i]]) {
                    int tmp = indices[i];
                    indices[i] = indices[j];
                    indices[j] = tmp;
                }
            }
        }
    }

    @SuppressWarnings("unused")
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

    private static float[] getBudgetsBuffer(int count) {
        float[] buffer = BUDGETS_BUFFER.get();
        if (buffer.length < count) {
            buffer = new float[count];
            BUDGETS_BUFFER.set(buffer);
        }
        return buffer;
    }

    private static int[] getIndicesBuffer(int count) {
        int[] buffer = INDICES_BUFFER.get();
        if (buffer.length < count) {
            buffer = new int[count];
            INDICES_BUFFER.set(buffer);
        }
        return buffer;
    }
}
