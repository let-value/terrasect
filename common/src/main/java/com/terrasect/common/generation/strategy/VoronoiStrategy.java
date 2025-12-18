package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class VoronoiStrategy {

    // Cache for Voronoi layouts: Key = combined seed + children hash, Value = float[] (interleaved x, z, radius, regionIndex)
    private static final int CACHE_SIZE = 4096;
    private static final Map<Long, float[]> VORONOI_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    private VoronoiStrategy() {}

    public static float[] getLayout(long seed, List<Region> children) {
        long cacheKey = computeCacheKey(seed, children);
        float[] layout = VORONOI_CACHE.get(cacheKey);
        if (layout == null) {
            layout = computeVoronoiLayout(children, seed);
            VORONOI_CACHE.put(cacheKey, layout);
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

    public static int getCell(float[] layout, float dx, float dz, float radius) {
        return getBestIndex(layout, dx, dz, radius);
    }

    public static Region getRegion(List<Region> children, float[] layout, int index) {
        if (index == -1) return children.get(0);
        int childIndex = (int) layout[index + 3];
        return children.get(childIndex);
    }

    public static long getSeed(long parentSeed, float[] layout, int index, Region region) {
        if (index == -1) return MathUtils.hash64(parentSeed, region.name().hashCode(), 0, 999);
        int childIndex = (int) layout[index + 3];
        return MathUtils.hash64(parentSeed, region.name().hashCode(), childIndex, 999);
    }

    public static float getNextCx(float cx, float radius, float[] layout, int index) {
        if (index == -1) return cx;
        return cx + layout[index] * radius;
    }

    public static float getNextCz(float cz, float radius, float[] layout, int index) {
        if (index == -1) return cz;
        return cz + layout[index + 1] * radius;
    }

    public static float getNextRadius(float radius, List<Region> children, Region nextRegion) {
        float totalBudget = getTotalWeight(children);
        return radius * (float) Math.sqrt(nextRegion.areaBudget() / totalBudget);
    }

    public static float getTotalWeight(List<Region> regions) {
        float sum = 0;
        for (Region r : regions) sum += r.areaBudget();
        return sum;
    }

    public static int getBestIndex(float[] layout, float relX, float relZ, float radius) {
        float bestMetric = Float.MAX_VALUE;
        int bestIndex = -1;

        for (int i = 0; i < layout.length; i += 4) {
            float sx = layout[i] * radius;
            float sz = layout[i+1] * radius;
            float sr = layout[i+2] * radius;
            
            float dx = relX - sx;
            float dz = relZ - sz;
            float distSq = dx * dx + dz * dz;
            
            // Metric = d^2 - r^2
            float metric = distSq - (sr * sr);
            
            if (metric < bestMetric) {
                bestMetric = metric;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static float[] computeVoronoiLayout(List<Region> children, long seed) {
        if (children.isEmpty()) return new float[0];

        Random rng = new Random(seed);
        float totalBudget = getTotalWeight(children);
        int count = children.size();
        
        // 4 floats per site: x, z, radius, childIndex
        float[] layout = new float[count * 4];
        
        // 1. Initialize sites in a Ring
        for (int i = 0; i < count; i++) {
            Region r = children.get(i);
            float rNorm = (float) Math.sqrt(r.areaBudget() / totalBudget); // Normalized radius (for parent radius=1)

            float angle = (i / (float) count) * (float) Math.PI * 2;
            angle += rng.nextFloat() * (float) Math.PI * 2;
            
            float dist = 0.3f; // Normalized distance
            dist += (rng.nextFloat() - 0.5f) * 0.05f;
            angle += (rng.nextFloat() - 0.5f) * 0.2f;

            layout[i*4] = dist * (float) Math.cos(angle);     // x
            layout[i*4+1] = dist * (float) Math.sin(angle);   // z
            layout[i*4+2] = rNorm;                            // radius
            layout[i*4+3] = i;                                // childIndex
        }

        // 2. Relax sites
        int iterations = 15;
        for (int iter = 0; iter < iterations; iter++) {
            for (int i = 0; i < count; i++) {
                float x1 = layout[i*4];
                float z1 = layout[i*4+1];
                float r1 = layout[i*4+2];
                
                for (int j = i + 1; j < count; j++) {
                    float x2 = layout[j*4];
                    float z2 = layout[j*4+1];
                    float r2 = layout[j*4+2];
                    
                    float dx = x1 - x2;
                    float dz = z1 - z2;
                    float dSq = dx*dx + dz*dz;
                    float d = (float) Math.sqrt(dSq);
                    
                    float desiredDist = (r1 + r2) * 0.9f;
                    
                    if (d < desiredDist) {
                        if (d < 0.0001f) {
                            d = 0.0001f;
                            dx = 0.0001f;
                        }

                        float push = (desiredDist - d) * 0.5f;
                        float pushX = (dx / d) * push;
                        float pushZ = (dz / d) * push;
                        
                        layout[i*4] += pushX;
                        layout[i*4+1] += pushZ;
                        layout[j*4] -= pushX;
                        layout[j*4+1] -= pushZ;
                        
                        // Update local vars for next inner loop check
                        x1 = layout[i*4];
                        z1 = layout[i*4+1];
                    }
                }
                
                // Boundary & Centering
                float distFromCenter = (float) Math.sqrt(x1*x1 + z1*z1);
                float maxDist = 1.0f - r1 * 0.5f; // Parent radius is 1.0
                
                if (distFromCenter > maxDist && distFromCenter > 0.0001f) {
                    float pull = distFromCenter - maxDist;
                    layout[i*4] -= (x1 / distFromCenter) * pull;
                    layout[i*4+1] -= (z1 / distFromCenter) * pull;
                }
                
                layout[i*4] *= 0.98f;
                layout[i*4+1] *= 0.98f;
            }
        }
        
        // 3. Center
        float cx = 0, cz = 0;
        for (int i = 0; i < count; i++) {
            cx += layout[i*4];
            cz += layout[i*4+1];
        }
        cx /= count;
        cz /= count;
        for (int i = 0; i < count; i++) {
            layout[i*4] -= cx;
            layout[i*4+1] -= cz;
        }
        
        return layout;
    }
}
