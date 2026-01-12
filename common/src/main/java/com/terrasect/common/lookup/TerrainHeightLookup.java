package com.terrasect.common.lookup;

import com.terrasect.common.Context;
import com.terrasect.common.definition.HeightConstraints;
import com.terrasect.common.generation.TraversalResult;
import com.terrasect.common.generation.World;
import java.util.function.IntBinaryOperator;
import org.jetbrains.annotations.Nullable;

public final class TerrainHeightLookup {
    public static final int NO_CONSTRAINT = Integer.MIN_VALUE;
    private final int[] heights;
    private final float[] edgeInfluences;
    private final int chunkMinX;
    private final int chunkMinZ;

    private TerrainHeightLookup(int[] heights, float[] edgeInfluences, int chunkMinX, int chunkMinZ) {
        this.heights = heights;
        this.edgeInfluences = edgeInfluences;
        this.chunkMinX = chunkMinX;
        this.chunkMinZ = chunkMinZ;
    }

    public static @Nullable TerrainHeightLookup build(@Nullable Context context, int chunkMinX, int chunkMinZ) {
        return build(context, chunkMinX, chunkMinZ, null);
    }

    public static @Nullable TerrainHeightLookup build(
            @Nullable Context context, int chunkMinX, int chunkMinZ, @Nullable IntBinaryOperator surfaceSampler) {

        if (context == null) {
            return null;
        }

        int[] heights = new int[256];
        float[] edgeInfluences = new float[256];
        var hasAnyConstraint = false;
        var hasAnyBlending = false;

        for (var localZ = 0; localZ < 16; localZ++) {
            var blockZ = chunkMinZ + localZ;
            for (var localX = 0; localX < 16; localX++) {
                var blockX = chunkMinX + localX;
                var index = localX + localZ * 16;

                TraversalResult traversal = World.traverse(context, blockX, blockZ);
                if (traversal == null || traversal.region == null) {
                    heights[index] = NO_CONSTRAINT;
                    edgeInfluences[index] = 0.0f;
                    continue;
                }

                edgeInfluences[index] = traversal.edgeInfluence;
                if (traversal.edgeInfluence > 0) {
                    hasAnyBlending = true;
                }

                var constraints = traversal.region.definition().height();
                if (!constraints.hasConstraints()) {
                    heights[index] = NO_CONSTRAINT;
                    continue;
                }

                var min = constraints.minY();
                var max = constraints.maxY();

                int height;
                if (surfaceSampler != null) {
                    var natural = surfaceSampler.applyAsInt(blockX, blockZ);
                    height = computeHeightWithVariation(natural, min, max);
                } else {
                    height = (min + max) >> 1;
                }

                heights[index] = height;
                hasAnyConstraint = true;
            }
        }

        if (!hasAnyConstraint) {
            return null;
        }

        if (hasAnyBlending) {
            int[] blendedHeights = new int[256];
            System.arraycopy(heights, 0, blendedHeights, 0, 256);

            for (var localZ = 0; localZ < 16; localZ++) {
                for (var localX = 0; localX < 16; localX++) {
                    var index = localX + localZ * 16;
                    var influence = edgeInfluences[index];

                    if (influence > 0 && heights[index] != NO_CONSTRAINT) {
                        blendedHeights[index] =
                                computeBlendedHeight(localX, localZ, heights, edgeInfluences, influence);
                    }
                }
            }

            heights = blendedHeights;
        }

        return new TerrainHeightLookup(heights, edgeInfluences, chunkMinX, chunkMinZ);
    }

    private static int computeBlendedHeight(
            int localX, int localZ, int[] heights, float[] edgeInfluences, float selfInfluence) {
        var selfIndex = localX + localZ * 16;
        var selfHeight = heights[selfIndex];

        var blendRadius = Math.max(1, (int) (selfInfluence * 4));

        var weightSum = 1.0f;
        var heightSum = selfHeight;

        for (var dz = -blendRadius; dz <= blendRadius; dz++) {
            var nz = localZ + dz;
            if (nz < 0 || nz >= 16) continue;

            for (var dx = -blendRadius; dx <= blendRadius; dx++) {
                if (dx == 0 && dz == 0) continue;

                var nx = localX + dx;
                if (nx < 0 || nx >= 16) continue;

                var nIndex = nx + nz * 16;
                var nHeight = heights[nIndex];

                if (nHeight != NO_CONSTRAINT) {
                    var dist = (float) Math.sqrt(dx * dx + dz * dz);
                    var weight = 1.0f - (dist / (blendRadius + 1));
                    if (weight > 0) {
                        heightSum += nHeight * weight;
                        weightSum += weight;
                    }
                }
            }
        }

        return Math.round(heightSum / weightSum);
    }

    private static int computeHeightWithVariation(int natural, int minHeight, int maxHeight) {
        var constraintMid = (minHeight + maxHeight) >> 1;
        var constraintRange = maxHeight - minHeight;

        var deviation = natural - 64;
        if (deviation > 32) deviation = 32;
        if (deviation < -32) deviation = -32;

        var scaled = (deviation * constraintRange) >> 6;

        var result = constraintMid + scaled;

        if (result < minHeight) return minHeight;
        if (result > maxHeight) return maxHeight;
        return result;
    }

    public int getMaxHeight(int blockX, int blockZ) {
        var localX = blockX - chunkMinX;
        var localZ = blockZ - chunkMinZ;

        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            return NO_CONSTRAINT;
        }

        return heights[localX + localZ * 16];
    }

    public float getEdgeInfluence(int blockX, int blockZ) {
        var localX = blockX - chunkMinX;
        var localZ = blockZ - chunkMinZ;

        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            return 0.0f;
        }

        return edgeInfluences[localX + localZ * 16];
    }
}
