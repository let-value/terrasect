package com.terrasect.common.strategy;

import com.terrasect.common.definition.Region;
import com.terrasect.common.util.MathUtils;

import java.util.List;

/**
 * Cache-free power diagram strategy with inline relaxation.
 * 
 * Uses deterministic initial placement followed by configurable relaxation
 * iterations to achieve budget-weighted Voronoi cells. The relaxation is
 * fast because n is small (typically 2-8 children).
 * 
 * All computations are O(n²·k) where n = children, k = relaxation iterations.
 */
public final class VoronoiStrategy {

    private static final int DEFAULT_RELAXATION_ITERATIONS = 5;

    private static final ThreadLocal<float[]> RADII_BUFFER = ThreadLocal.withInitial(() -> new float[8]);
    private static final ThreadLocal<float[]> SITES_BUFFER = ThreadLocal.withInitial(() -> new float[16]);

    private VoronoiStrategy() {}

    /**
     * Query which child region contains the point, writing results to output buffer.
     * Uses default relaxation iterations.
     */
    public static void query(long seed, List<Region> children, float dx, float dz, 
                             float radius, int childrenTotalBudget, QueryResult out) {
        query(seed, children, dx, dz, radius, childrenTotalBudget, DEFAULT_RELAXATION_ITERATIONS, out);
    }

    /**
     * Query which child region contains the point, writing results to output buffer.
     * 
     * @param seed Parent region seed
     * @param children List of child regions
     * @param dx X offset from parent center
     * @param dz Z offset from parent center
     * @param radius Parent region radius
     * @param childrenTotalBudget Pre-computed sum of children's area budgets
     * @param relaxationIterations Number of relaxation iterations (0-20)
     * @param out Output buffer with childIndex, centerX, centerZ, radius, siteX, siteZ
     */
    public static void query(long seed, List<Region> children, float dx, float dz, 
                             float radius, int childrenTotalBudget, int relaxationIterations, QueryResult out) {
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

        // Normalize query point
        float nx = dx / radius;
        float nz = dz / radius;

        // Pre-compute inverse sqrt of total budget for radii calculation
        float invSqrtTotal = 1.0f / (float) Math.sqrt(childrenTotalBudget);

        // Compute normalized radii using pre-baked child radius
        float[] radii = getRadiiBuffer(count);
        for (int i = 0; i < count; i++) {
            // child.radius() = sqrt(areaBudget), so:
            // sqrt(areaBudget / totalBudget) = radius * invSqrtTotal
            radii[i] = children.get(i).radius() * invSqrtTotal;
        }

        // Compute relaxed site positions (2 floats per site: x, z)
        float[] sites = getSitesBuffer(count);
        computeRelaxedSites(seed, count, radii, relaxationIterations, sites);

        // Find best cell using power diagram metric
        int bestIndex = 0;
        float bestMetric = Float.MAX_VALUE;
        float secondBestMetric = Float.MAX_VALUE;
        float bestX = 0, bestZ = 0;

        for (int i = 0; i < count; i++) {
            float sx = sites[i * 2];
            float sz = sites[i * 2 + 1];
            float r = radii[i];

            float ddx = nx - sx;
            float ddz = nz - sz;
            float distSq = ddx * ddx + ddz * ddz;
            float metric = distSq - r * r;

            if (metric < bestMetric) {
                secondBestMetric = bestMetric;
                bestMetric = metric;
                bestIndex = i;
                bestX = sx;
                bestZ = sz;
            } else if (metric < secondBestMetric) {
                secondBestMetric = metric;
            }
        }

        out.childIndex = bestIndex;
        // Voronoi cells have irregular shapes. To ensure children are properly
        // subdivided within each cell, we transform to cell-local coordinates.
        // Center at the site, radius based on distance to nearest neighbor.
        float minNeighborDist = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            if (i != bestIndex) {
                float sx = sites[i * 2];
                float sz = sites[i * 2 + 1];
                float ddx = bestX - sx;
                float ddz = bestZ - sz;
                float dist = (float) Math.sqrt(ddx * ddx + ddz * ddz);
                if (dist < minNeighborDist) {
                    minNeighborDist = dist;
                }
            }
        }
        // Cell "radius" is half distance to nearest neighbor (Voronoi edge is at midpoint)
        // Use 0.45x for some safety margin
        float cellRadius = Math.max(minNeighborDist * 0.45f, 0.15f);
        
        out.centerX = bestX;    // Center at voronoi site
        out.centerZ = bestZ;
        out.radius = cellRadius; // Approximate cell radius
        // Store site position for seed uniqueness
        out.siteX = bestX;
        out.siteZ = bestZ;
        
        // Edge distance: difference between second-best and best metric
        // Larger value = deeper inside cell. Normalize to approximate [0,1] range.
        float rawEdge = secondBestMetric - bestMetric;
        out.edgeDistance = Math.min(1.0f, rawEdge * 2.0f);
    }

    /**
     * Compute relaxed site positions deterministically.
     * Uses initial ring placement + quick push-apart relaxation.
     */
    private static void computeRelaxedSites(long seed, int count, float[] radii, int iterations, float[] sites) {
        
        // Initial placement: ring distribution
        float baseAngle = hashToFloat(seed, 0) * (float) (Math.PI * 2);
        for (int i = 0; i < count; i++) {
            float angle = baseAngle + i * (float) (Math.PI * 2) / count;
            float dist = 0.35f;
            sites[i * 2] = dist * (float) Math.cos(angle);
            sites[i * 2 + 1] = dist * (float) Math.sin(angle);
        }

        // Quick relaxation: push sites apart based on their radii
        for (int iter = 0; iter < iterations; iter++) {
            for (int i = 0; i < count; i++) {
                float x1 = sites[i * 2];
                float z1 = sites[i * 2 + 1];
                float r1 = radii[i];

                for (int j = i + 1; j < count; j++) {
                    float x2 = sites[j * 2];
                    float z2 = sites[j * 2 + 1];
                    float r2 = radii[j];

                    float dx = x1 - x2;
                    float dz = z1 - z2;
                    float distSq = dx * dx + dz * dz;
                    float dist = (float) Math.sqrt(distSq);

                    // Desired distance based on radii
                    float desiredDist = (r1 + r2) * 0.85f;

                    if (dist < desiredDist && dist > 0.0001f) {
                        float push = (desiredDist - dist) * 0.5f;
                        float pushX = (dx / dist) * push;
                        float pushZ = (dz / dist) * push;

                        sites[i * 2] += pushX;
                        sites[i * 2 + 1] += pushZ;
                        sites[j * 2] -= pushX;
                        sites[j * 2 + 1] -= pushZ;

                        x1 = sites[i * 2];
                        z1 = sites[i * 2 + 1];
                    }
                }

                // Boundary constraint and centering
                float distFromCenter = (float) Math.sqrt(x1 * x1 + z1 * z1);
                float maxDist = 0.85f - r1 * 0.3f;
                if (distFromCenter > maxDist && distFromCenter > 0.0001f) {
                    float pull = distFromCenter - maxDist;
                    sites[i * 2] -= (x1 / distFromCenter) * pull;
                    sites[i * 2 + 1] -= (z1 / distFromCenter) * pull;
                }

                // Slight centering pull
                sites[i * 2] *= 0.97f;
                sites[i * 2 + 1] *= 0.97f;
            }
        }

        // Center the sites
        float cx = 0, cz = 0;
        for (int i = 0; i < count; i++) {
            cx += sites[i * 2];
            cz += sites[i * 2 + 1];
        }
        cx /= count;
        cz /= count;
        for (int i = 0; i < count; i++) {
            sites[i * 2] -= cx;
            sites[i * 2 + 1] -= cz;
        }
    }

    /**
     * Compute child seed deterministically.
     */
    public static long getSeed(long parentSeed, int childIndex, Region region) {
        return MathUtils.hash64(parentSeed, region.name().hashCode(), childIndex, 999);
    }

    private static float hashToFloat(long seed, int a) {
        long h = MathUtils.hash64(seed, a, 0, 0);
        return (h & 0xFFFF) / 65536.0f;
    }

    private static float[] getRadiiBuffer(int count) {
        float[] buffer = RADII_BUFFER.get();
        if (buffer.length < count) {
            buffer = new float[count];
            RADII_BUFFER.set(buffer);
        }
        return buffer;
    }

    private static float[] getSitesBuffer(int count) {
        int needed = count * 2;
        float[] buffer = SITES_BUFFER.get();
        if (buffer.length < needed) {
            buffer = new float[needed];
            SITES_BUFFER.set(buffer);
        }
        return buffer;
    }
}
