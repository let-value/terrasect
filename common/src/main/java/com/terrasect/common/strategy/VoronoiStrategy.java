package com.terrasect.common.strategy;

import com.terrasect.common.definition.Region;
import com.terrasect.common.util.MathUtils;
import java.util.List;

public final class VoronoiStrategy {

    private static final int DEFAULT_RELAXATION_ITERATIONS = 5;

    private static final ThreadLocal<float[]> RADII_BUFFER = ThreadLocal.withInitial(() -> new float[8]);
    private static final ThreadLocal<float[]> SITES_BUFFER = ThreadLocal.withInitial(() -> new float[16]);

    private VoronoiStrategy() {
    }

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

        var count = children.size();
        if (count == 1) {
            out.childIndex = 0;
            out.centerX = 0;
            out.centerZ = 0;
            out.radius = 1.0f;
            return;
        }

        var nx = dx / radius;
        var nz = dz / radius;

        var invSqrtTotal = 1.0f / (float) Math.sqrt(childrenTotalBudget);

        float[] radii = getRadiiBuffer(count);
        for (var i = 0; i < count; i++) {

            radii[i] = children.get(i).radius() * invSqrtTotal;
        }

        float[] sites = getSitesBuffer(count);
        computeRelaxedSites(seed, count, radii, relaxationIterations, sites);

        var bestIndex = 0;
        var bestMetric = Float.MAX_VALUE;
        var secondBestMetric = Float.MAX_VALUE;
        float bestX = 0, bestZ = 0;

        for (var i = 0; i < count; i++) {
            var sx = sites[i * 2];
            var sz = sites[i * 2 + 1];
            var r = radii[i];

            var ddx = nx - sx;
            var ddz = nz - sz;
            var distSq = ddx * ddx + ddz * ddz;
            var metric = distSq - r * r;

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

        var minNeighborDist = Float.MAX_VALUE;
        for (var i = 0; i < count; i++) {
            if (i != bestIndex) {
                var sx = sites[i * 2];
                var sz = sites[i * 2 + 1];
                var ddx = bestX - sx;
                var ddz = bestZ - sz;
                var dist = (float) Math.sqrt(ddx * ddx + ddz * ddz);
                if (dist < minNeighborDist) {
                    minNeighborDist = dist;
                }
            }
        }

        var cellRadius = Math.max(minNeighborDist * 0.45f, 0.15f);

        out.centerX = bestX;
        out.centerZ = bestZ;
        out.radius = cellRadius;

        out.siteX = bestX;
        out.siteZ = bestZ;

        var rawEdge = secondBestMetric - bestMetric;
        out.edgeDistance = Math.min(1.0f, rawEdge * 2.0f);
    }

    private static void computeRelaxedSites(long seed, int count, float[] radii, int iterations, float[] sites) {

        var baseAngle = hashToFloat(seed, 0) * (float) (Math.PI * 2);
        for (var i = 0; i < count; i++) {
            var angle = baseAngle + i * (float) (Math.PI * 2) / count;
            var dist = 0.35f;
            sites[i * 2] = dist * (float) Math.cos(angle);
            sites[i * 2 + 1] = dist * (float) Math.sin(angle);
        }

        for (var iter = 0; iter < iterations; iter++) {
            for (var i = 0; i < count; i++) {
                var x1 = sites[i * 2];
                var z1 = sites[i * 2 + 1];
                var r1 = radii[i];

                for (var j = i + 1; j < count; j++) {
                    var x2 = sites[j * 2];
                    var z2 = sites[j * 2 + 1];
                    var r2 = radii[j];

                    var dx = x1 - x2;
                    var dz = z1 - z2;
                    var distSq = dx * dx + dz * dz;
                    var dist = (float) Math.sqrt(distSq);

                    var desiredDist = (r1 + r2) * 0.85f;

                    if (dist < desiredDist && dist > 0.0001f) {
                        var push = (desiredDist - dist) * 0.5f;
                        var pushX = (dx / dist) * push;
                        var pushZ = (dz / dist) * push;

                        sites[i * 2] += pushX;
                        sites[i * 2 + 1] += pushZ;
                        sites[j * 2] -= pushX;
                        sites[j * 2 + 1] -= pushZ;

                        x1 = sites[i * 2];
                        z1 = sites[i * 2 + 1];
                    }
                }

                var distFromCenter = (float) Math.sqrt(x1 * x1 + z1 * z1);
                var maxDist = 0.85f - r1 * 0.3f;
                if (distFromCenter > maxDist && distFromCenter > 0.0001f) {
                    var pull = distFromCenter - maxDist;
                    sites[i * 2] -= (x1 / distFromCenter) * pull;
                    sites[i * 2 + 1] -= (z1 / distFromCenter) * pull;
                }

                sites[i * 2] *= 0.97f;
                sites[i * 2 + 1] *= 0.97f;
            }
        }

        float cx = 0, cz = 0;
        for (var i = 0; i < count; i++) {
            cx += sites[i * 2];
            cz += sites[i * 2 + 1];
        }
        cx /= count;
        cz /= count;
        for (var i = 0; i < count; i++) {
            sites[i * 2] -= cx;
            sites[i * 2 + 1] -= cz;
        }
    }

    public static long getSeed(long parentSeed, int childIndex, Region region) {
        return MathUtils.hash64(parentSeed, region.name().hashCode(), childIndex, 999);
    }

    private static float hashToFloat(long seed, int a) {
        var h = MathUtils.hash64(seed, a, 0, 0);
        return (h & 0xFFFF) / 65536.0f;
    }

    private static float[] getRadiiBuffer(int count) {
        var buffer = RADII_BUFFER.get();
        if (buffer.length < count) {
            buffer = new float[count];
            RADII_BUFFER.set(buffer);
        }
        return buffer;
    }

    private static float[] getSitesBuffer(int count) {
        var needed = count * 2;
        var buffer = SITES_BUFFER.get();
        if (buffer.length < needed) {
            buffer = new float[needed];
            SITES_BUFFER.set(buffer);
        }
        return buffer;
    }
}
