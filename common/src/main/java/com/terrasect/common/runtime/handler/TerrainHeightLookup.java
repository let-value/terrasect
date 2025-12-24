package com.terrasect.common.runtime.handler;

import com.terrasect.common.api.Context;
import com.terrasect.common.generation.definition.ClimateSettings;
import com.terrasect.common.runtime.TraversalResult;
import com.terrasect.common.runtime.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.ToIntBiFunction;

public final class TerrainHeightLookup {
    public static final int NO_CONSTRAINT = Integer.MIN_VALUE;
    private final int[] heights;
    private final float[] edgeInfluences;
    private final int chunkMinX;
    private final int chunkMinZ;
    
    private TerrainHeightLookup(int[] heights, float[] edgeInfluences, 
                                 int chunkMinX, int chunkMinZ) {
        this.heights = heights;
        this.edgeInfluences = edgeInfluences;
        this.chunkMinX = chunkMinX;
        this.chunkMinZ = chunkMinZ;
    }
    
    public static @Nullable TerrainHeightLookup build(@Nullable Context context, int chunkMinX, int chunkMinZ) {
        return build(context, chunkMinX, chunkMinZ, null);
    }
    
    public static @Nullable TerrainHeightLookup build(
            @Nullable Context context, 
            int chunkMinX, 
            int chunkMinZ,
            @Nullable ToIntBiFunction<Integer, Integer> surfaceSampler) {
    
        
        if (context == null) {
            return null;
        }
        
        int[] heights = new int[256];
        float[] edgeInfluences = new float[256];
        boolean hasAnyConstraint = false;
        boolean hasAnyBlending = false;
        
        for (int localZ = 0; localZ < 16; localZ++) {
            int blockZ = chunkMinZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int blockX = chunkMinX + localX;
                int index = localX + localZ * 16;
                
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
                
                ClimateSettings climate = traversal.region.definition().climate();
                if (climate == null || !climate.hasHeightConstraints()) {
                    heights[index] = NO_CONSTRAINT;
                    continue;
                }
                
                Integer min = climate.minHeight();
                Integer max = climate.maxHeight();
                if (min == null || max == null) {
                    heights[index] = NO_CONSTRAINT;
                    continue;
                }
                
                int height;
                if (surfaceSampler != null) {
                    int natural = surfaceSampler.applyAsInt(blockX, blockZ);
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
            
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int index = localX + localZ * 16;
                    float influence = edgeInfluences[index];
                    
                    if (influence > 0 && heights[index] != NO_CONSTRAINT) {
                        blendedHeights[index] = computeBlendedHeight(
                            localX, localZ, heights, edgeInfluences, influence);
                    }
                }
            }
            
            heights = blendedHeights;
        }
        
        return new TerrainHeightLookup(heights, edgeInfluences, chunkMinX, chunkMinZ);
    }
    
    private static int computeBlendedHeight(int localX, int localZ, 
                                             int[] heights, float[] edgeInfluences,
                                             float selfInfluence) {
        int selfIndex = localX + localZ * 16;
        int selfHeight = heights[selfIndex];
        
        int blendRadius = Math.max(1, (int)(selfInfluence * 4));
        
        float weightSum = 1.0f;
        float heightSum = selfHeight;
        
        for (int dz = -blendRadius; dz <= blendRadius; dz++) {
            int nz = localZ + dz;
            if (nz < 0 || nz >= 16) continue;
            
            for (int dx = -blendRadius; dx <= blendRadius; dx++) {
                if (dx == 0 && dz == 0) continue;
                
                int nx = localX + dx;
                if (nx < 0 || nx >= 16) continue;
                
                int nIndex = nx + nz * 16;
                int nHeight = heights[nIndex];
                
                if (nHeight != NO_CONSTRAINT) {
                    float dist = (float) Math.sqrt(dx * dx + dz * dz);
                    float weight = 1.0f - (dist / (blendRadius + 1));
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
        int constraintMid = (minHeight + maxHeight) >> 1;
        int constraintRange = maxHeight - minHeight;
        
        int deviation = natural - 64;
        if (deviation > 32) deviation = 32;
        if (deviation < -32) deviation = -32;
        
        int scaled = (deviation * constraintRange) >> 6;
        
        int result = constraintMid + scaled;
        
        if (result < minHeight) return minHeight;
        if (result > maxHeight) return maxHeight;
        return result;
    }
    
    public int getMaxHeight(int blockX, int blockZ) {
        int localX = blockX - chunkMinX;
        int localZ = blockZ - chunkMinZ;
        
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            return NO_CONSTRAINT;
        }
        
        return heights[localX + localZ * 16];
    }
    
    public float getEdgeInfluence(int blockX, int blockZ) {
        int localX = blockX - chunkMinX;
        int localZ = blockZ - chunkMinZ;
        
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            return 0.0f;
        }
        
        return edgeInfluences[localX + localZ * 16];
    }
}
