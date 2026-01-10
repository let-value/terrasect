package com.terrasect.common.strategy;

import com.terrasect.common.definition.Region;
import com.terrasect.common.util.MathUtils;
import java.util.List;

public final class VoronoiStrategy {

    private static final int DEFAULT_RELAXATION_ITERATIONS = 5;

    private static final ThreadLocal<float[]> RADII_BUFFER = ThreadLocal.withInitial(() -> new float[8]);
    private static final ThreadLocal<float[]> SITES_BUFFER = ThreadLocal.withInitial(() -> new float[16]);

    private VoronoiStrategy() {}

    public static void query(
            long seed,
            List<Region> children,
            float dx,
            float dz,
            float radius,
            int childrenTotalBudget,
            QueryResult out) {
        query(seed, children, dx, dz, radius, childrenTotalBudget, DEFAULT_RELAXATION_ITERATIONS, out);
    }

    public static void query(
            long seed,
            List<Region> children,
            float dx,
            float dz,
            float radius,
            int childrenTotalBudget,
            int relaxationIterations,
            QueryResult out) {
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

        float invSqrtTotal = 1.0f / (float) Math.sqrt(childrenTotalBudget);

        float[] radii = getRadiiBuffer(count);
        for (int i = 0; i < count; i++) {

            radii[i] = children.get(i).radius() * invSqrtTotal;
        }

        float[] sites = getSitesBuffer(count);
        computeRelaxedSites(seed, count, radii, relaxationIterations, sites);

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

        float cellRadius = Math.max(minNeighborDist * 0.45f, 0.15f);

        out.centerX = bestX;
        out.centerZ = bestZ;
        out.radius = cellRadius;

        out.siteX = bestX;
        out.siteZ = bestZ;

        float rawEdge = secondBestMetric - bestMetric;
        out.edgeDistance = Math.min(1.0f, rawEdge * 2.0f);
    }

    private static void computeRelaxedSites(long seed, int count, float[] radii, int iterations, float[] sites) {

        float baseAngle = hashToFloat(seed, 0) * (float) (Math.PI * 2);
        for (int i = 0; i < count; i++) {
            float angle = baseAngle + i * (float) (Math.PI * 2) / count;
            float dist = 0.35f;
            sites[i * 2] = dist * (float) Math.cos(angle);
            sites[i * 2 + 1] = dist * (float) Math.sin(angle);
        }

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

                float distFromCenter = (float) Math.sqrt(x1 * x1 + z1 * z1);
                float maxDist = 0.85f - r1 * 0.3f;
                if (distFromCenter > maxDist && distFromCenter > 0.0001f) {
                    float pull = distFromCenter - maxDist;
                    sites[i * 2] -= (x1 / distFromCenter) * pull;
                    sites[i * 2 + 1] -= (z1 / distFromCenter) * pull;
                }

                sites[i * 2] *= 0.97f;
                sites[i * 2 + 1] *= 0.97f;
            }
        }

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
