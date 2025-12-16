package com.terrasect.common.generation;

import java.util.List;

/**
 * Stateless world navigation utilities.
 *
 * <p>The old implementation allocated several helper objects every time a region was
 * queried which quickly became a bottleneck when sampling millions of locations for
 * snapshots or biome placement. The new algorithm keeps the same narrative structure
 * (a warped hex tiling at depth 1 followed by organic Voronoi-like layouts), but it
 * drives everything from pre-sized primitive buffers to avoid allocations and reduce
 * branch overhead.</p>
 */
public class World {

    private static Region root;

    private static final float GOLDEN_ANGLE = (float) (Math.PI * 2 / (1 + Math.sqrt(5)));

    private static final ThreadLocal<TraversalState> TRAVERSAL =
        ThreadLocal.withInitial(TraversalState::new);

    private static final ThreadLocal<SiteBuffer> SITE_BUFFER =
        ThreadLocal.withInitial(() -> new SiteBuffer(8));

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
        return traverse(x, z, context, Integer.MAX_VALUE).region;
    }

    /**
     * Get the Region at a specific depth in the hierarchy.
     */
    public static Region getRegionAtDepth(int x, int z, Strategy context, int targetDepth) {
        return traverse(x, z, context, targetDepth).region;
    }

    public static long getRegionSeedAtDepth(int x, int z, Strategy context, int targetDepth) {
        return traverse(x, z, context, targetDepth).seed;
    }

    private static TraversalState traverse(int x, int z, Strategy context, int targetDepth) {
        TraversalState state = TRAVERSAL.get();
        state.reset();

        Region current = getRoot();
        long currentSeed = context.getSeed();

        // Warped coordinates (inlined to avoid temporary arrays)
        float wx;
        float wz;
        {
            float river = context.getRiverInfluence(x, z);
            float ridge = context.getRidgeInfluence(x, z);

            float dist = (float) Math.sqrt((float) x * x + (float) z * z);
            float dampFactor = Math.min(1.0f, dist / 600.0f);

            float m1 = NoiseUtils.valueNoise(x, z, currentSeed, 10001, 4000);
            float m2 = NoiseUtils.valueNoise(x, z, currentSeed, 10002, 4000);

            float mx = x + (m1 - 0.5f) * 1500.0f * dampFactor;
            float mz = z + (m2 - 0.5f) * 1500.0f * dampFactor;

            float n1 = NoiseUtils.warpNoise1((int) mx, (int) mz, currentSeed, 600);
            float n2 = NoiseUtils.warpNoise2((int) mx, (int) mz, currentSeed, 600);

            float r1 = NoiseUtils.valueNoise((int) mx, (int) mz, currentSeed, 5001, 150);
            float r2 = NoiseUtils.valueNoise((int) mx, (int) mz, currentSeed, 5002, 150);

            float warpAngle = NoiseUtils.valueNoise(x, z, currentSeed, 9999, 350) * (float) Math.PI * 2.0f;
            float influence = (river + ridge) * 55.0f * dampFactor;

            float baseAmp = 42.0f * dampFactor;
            float microAmp = 15.0f * dampFactor;

            wx = mx + ((n1 - 0.5f) * baseAmp + (r1 - 0.5f) * microAmp) + (float) Math.cos(warpAngle) * influence;
            wz = mz + ((n2 - 0.5f) * baseAmp + (r2 - 0.5f) * microAmp) + (float) Math.sin(warpAngle) * influence;
        }

        float centerX = 0;
        float centerZ = 0;
        float currentRadius = (float) current.areaBudget();

        int depth = 0;
        boolean tiled = false;

        // We keep traversing until we either run out of children or reach the desired depth.
        while (current.hasChildren() && depth < targetDepth) {
            List<Region> children = current.children();

            if (!tiled) {
                // Depth 1: hex tiling (no allocations, seeded by hex cell)
                float hexSize = currentRadius;
                long hexPacked = RegionField.getHexCell(wx, wz, hexSize);
                int q = (int) (hexPacked >> 32);
                int r = (int) hexPacked;

                float hx = hexSize * ((float) Math.sqrt(3) * q + (float) Math.sqrt(3) / 2.0f * r);
                float hz = hexSize * (3.0f / 2.0f * r);

                centerX = hx;
                centerZ = hz;

                int hexDist = (Math.abs(q) + Math.abs(q + r) + Math.abs(r)) / 2;

                if (hexDist == 0) {
                    current = children.get(0);
                    currentSeed = MathUtils.hash64(currentSeed, 0, 0, 0x9999);
                } else {
                    long hexSeed = MathUtils.hash64(currentSeed, q, r, 0x9999);
                    current = pickChildWeighted(children, hexSeed);
                    currentSeed = MathUtils.hash64(hexSeed, current.name().hashCode(), q + r, 0x55AA);
                }
                tiled = true;
            } else {
                // Organic Voronoi-like layout for deeper levels.
                SiteBuffer sites = SITE_BUFFER.get();
                sites.ensureCapacity(children.size());

                float totalWeight = 0.0f;
                for (int i = 0; i < children.size(); i++) {
                    float w = children.get(i).areaBudget();
                    sites.weight[i] = w;
                    totalWeight += w;
                }

                float radiusScale = currentRadius * 0.70f;
                float angleBase = (MathUtils.hash64(currentSeed, children.size(), 0, 0x2A2A) >>> 11) * 0x1.0p-53f * (float) Math.PI * 2.0f;

                for (int i = 0; i < children.size(); i++) {
                    float share = sites.weight[i] / totalWeight;
                    float radial = radiusScale * (0.35f + (1.0f - share) * 0.65f);
                    float angle = angleBase + GOLDEN_ANGLE * i;

                    sites.x[i] = (float) Math.cos(angle) * radial;
                    sites.z[i] = (float) Math.sin(angle) * radial;
                }

                int bestIndex = 0;
                float bestMetric = Float.MAX_VALUE;

                for (int i = 0; i < children.size(); i++) {
                    float dx = wx - (centerX + sites.x[i]);
                    float dz = wz - (centerZ + sites.z[i]);
                    float distSq = dx * dx + dz * dz;

                    // Weighted Voronoi metric: larger budgets claim more space.
                    float weightFrac = sites.weight[i] / totalWeight;
                    float metric = distSq / (0.08f + weightFrac * 0.92f);

                    if (metric < bestMetric) {
                        bestMetric = metric;
                        bestIndex = i;
                    }
                }

                centerX += sites.x[bestIndex];
                centerZ += sites.z[bestIndex];
                currentRadius = currentRadius * (float) Math.sqrt(sites.weight[bestIndex] / totalWeight);

                currentSeed = MathUtils.hash64(currentSeed, bestIndex, (int) centerX, (int) centerZ);
                current = children.get(bestIndex);
            }

            depth++;
            state.region = current;
            state.seed = currentSeed;
        }

        return state;
    }

    private static Region pickChildWeighted(List<Region> children, long seed) {
        float total = 0.0f;
        for (Region r : children) {
            total += r.areaBudget();
        }

        float target = (seed & 0xFFFFFFFFL) / (float) 0x1_0000_0000L * total;
        float acc = 0.0f;

        for (Region r : children) {
            acc += r.areaBudget();
            if (acc >= target) {
                return r;
            }
        }
        return children.get(0);
    }

    private static class TraversalState {
        Region region;
        long seed;

        void reset() {
            region = getRoot();
            seed = 0L;
        }
    }

    private static final class SiteBuffer {
        float[] x;
        float[] z;
        float[] weight;

        SiteBuffer(int capacity) {
            x = new float[capacity];
            z = new float[capacity];
            weight = new float[capacity];
        }

        void ensureCapacity(int size) {
            if (x.length >= size) return;
            int newCap = Math.max(size, x.length * 2);
            x = java.util.Arrays.copyOf(x, newCap);
            z = java.util.Arrays.copyOf(z, newCap);
            weight = java.util.Arrays.copyOf(weight, newCap);
        }
    }
}
