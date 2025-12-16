package com.terrasect.common.generation;

public class RegionField {

    public static final int REPEAT_PERIOD_POCKETS = 5;
    private static final float INV_U16 = 1.0f / 65535.0f;
    private static final EdgeStatistics EDGE_STATS = EdgeStatistics.vanillaOverworld();

    /**
     * Computes the region ID and edge distance for a given world coordinate.
     * Returns a packed long: (regionId << 32) | (floatToRawIntBits(edge))
     */
    public static long getRegionData(int x, int z, long worldSeed, int cellSize, float warpAmp, int pocketSize) {
        int px = MathUtils.floorDiv(x, pocketSize);
        int pz = MathUtils.floorDiv(z, pocketSize);

        int rpx = MathUtils.mod(px, REPEAT_PERIOD_POCKETS);
        int rpz = MathUtils.mod(pz, REPEAT_PERIOD_POCKETS);

        long pocketSeed = MathUtils.hash64(worldSeed, rpx, rpz, 0x12345L);

        int warpScale = cellSize;
        float warp1 = NoiseUtils.warpNoise1(x, z, pocketSeed, warpScale);
        float warp2 = NoiseUtils.warpNoise2(x, z, pocketSeed, warpScale);

        float coarseScale = EDGE_STATS.coarseAverageRunBlocks() * 0.5f;
        float coarseAmplitude = EDGE_STATS.coarseTransitionDensity() * cellSize * 2.5f;

        float macroX = (NoiseUtils.valueNoise(x, z, worldSeed, 9101, (int) coarseScale) - 0.5f) * coarseAmplitude;
        float macroZ = (NoiseUtils.valueNoise(z, x, worldSeed, 9102, (int) coarseScale) - 0.5f) * coarseAmplitude;

        float microX = (NoiseUtils.valueNoise(x, z, worldSeed, 9103, 16) - 0.5f) * EDGE_STATS.fineHorizontalJitter() * 0.5f;
        float microZ = (NoiseUtils.valueNoise(z, x, worldSeed, 9104, 16) - 0.5f) * EDGE_STATS.fineVerticalJitter();

        int xw = (int) (x + warpAmp * (warp1 * 2.0f - 1.0f) + macroX + microX);
        int zw = (int) (z + warpAmp * (warp2 * 2.0f - 1.0f) + macroZ + microZ);

        int gx = MathUtils.floorDiv(xw, cellSize);
        int gz = MathUtils.floorDiv(zw, cellSize);

        float bestD = Float.MAX_VALUE;
        float secondBestD = Float.MAX_VALUE;
        int bestId = 0;

        for (int ix = gx - 1; ix <= gx + 1; ix++) {
            for (int iz = gz - 1; iz <= gz + 1; iz++) {
                long baseHash = MathUtils.hash64(worldSeed, ix, iz, pocketSeed);

                float jx = ((baseHash >>> 48) & 0xFFFF) * INV_U16 * cellSize;
                float jz = ((baseHash >>> 32) & 0xFFFF) * INV_U16 * cellSize;

                float sx = ix * (float) cellSize + jx;
                float sz = iz * (float) cellSize + jz;

                float dx = xw - sx;
                float dz = zw - sz;
                float d = dx * dx + dz * dz;

                if (d < bestD) {
                    secondBestD = bestD;
                    bestD = d;
                    bestId = (int) (MathUtils.hash64(pocketSeed ^ worldSeed, ix, iz, 0x9E3779B97F4A7C15L) >>> 32);
                } else if (d < secondBestD) {
                    secondBestD = d;
                }
            }
        }

        float rawEdge = (float) (Math.sqrt(secondBestD) - Math.sqrt(bestD));

        float fineDensity = (NoiseUtils.valueNoise(x, z, worldSeed, 9105, 32) - 0.5f) * EDGE_STATS.fineTransitionDensity() * 2.5f;
        float coarseDensity = (NoiseUtils.valueNoise(z, x, worldSeed, 9106, (int) coarseScale) - 0.5f)
            * EDGE_STATS.coarseTransitionDensity();

        float edge = Math.max(0.0f, rawEdge * (1.0f + fineDensity + coarseDensity));

        return ((long) bestId << 32) | (Float.floatToRawIntBits(edge) & 0xFFFFFFFFL);
    }

    public static int unpackRegionId(long packed) {
        return (int) (packed >>> 32);
    }

    public static float unpackEdge(long packed) {
        return Float.intBitsToFloat((int) (packed & 0xFFFFFFFFL));
    }

    /**
     * Returns the global grid coordinates of the closest Voronoi site.
     * Returns (ix << 32) | (iz & 0xFFFFFFFFL)
     */
    public static long getClosestGridCell(int x, int z, long worldSeed, int cellSize, float warpAmp, int pocketSize) {
        // 1. Compute pocket coordinates
        int px = MathUtils.floorDiv(x, pocketSize);
        int pz = MathUtils.floorDiv(z, pocketSize);

        // 2. Wrap for repetition
        int rpx = MathUtils.mod(px, REPEAT_PERIOD_POCKETS);
        int rpz = MathUtils.mod(pz, REPEAT_PERIOD_POCKETS);

        // 3. Compute pocket seed
        long pocketSeed = MathUtils.hash64(worldSeed, rpx, rpz, 0x12345L);

        // 4. Domain warp
        int warpScale = cellSize; 
        float warp1 = NoiseUtils.warpNoise1(x, z, pocketSeed, warpScale);
        float warp2 = NoiseUtils.warpNoise2(x, z, pocketSeed, warpScale);
        
        int xw = (int) (x + warpAmp * (warp1 * 2.0f - 1.0f));
        int zw = (int) (z + warpAmp * (warp2 * 2.0f - 1.0f));

        // 5. Grid cell
        int gx = MathUtils.floorDiv(xw, cellSize);
        int gz = MathUtils.floorDiv(zw, cellSize);

        float bestD = Float.MAX_VALUE;
        int bestIx = 0;
        int bestIz = 0;

        // 6. Check neighbors
        for (int ix = gx - 1; ix <= gx + 1; ix++) {
            for (int iz = gz - 1; iz <= gz + 1; iz++) {
                long h = MathUtils.hash64(worldSeed, ix, iz, pocketSeed);
                
                float jx = (MathUtils.hash64(h, ix, iz, 1) & 0xFFFF) / 65536.0f * cellSize;
                float jz = (MathUtils.hash64(h, ix, iz, 2) & 0xFFFF) / 65536.0f * cellSize;
                
                float sx = ix * cellSize + jx;
                float sz = iz * cellSize + jz;

                float dx = xw - sx;
                float dz = zw - sz;
                float d = dx * dx + dz * dz;

                if (d < bestD) {
                    bestD = d;
                    bestIx = ix;
                    bestIz = iz;
                }
            }
        }

        return ((long) bestIx << 32) | (bestIz & 0xFFFFFFFFL);
    }

    public static float getSiteX(int ix, int iz, long worldSeed, int cellSize, int pocketSize, int x, int z) {
        int px = MathUtils.floorDiv(x, pocketSize);
        int pz = MathUtils.floorDiv(z, pocketSize);
        int rpx = MathUtils.mod(px, REPEAT_PERIOD_POCKETS);
        int rpz = MathUtils.mod(pz, REPEAT_PERIOD_POCKETS);
        long pocketSeed = MathUtils.hash64(worldSeed, rpx, rpz, 0x12345L);
        
        long h = MathUtils.hash64(worldSeed, ix, iz, pocketSeed);
        float jx = (MathUtils.hash64(h, ix, iz, 1) & 0xFFFF) / 65536.0f * cellSize;
        return ix * cellSize + jx;
    }

    public static float getSiteZ(int ix, int iz, long worldSeed, int cellSize, int pocketSize, int x, int z) {
        int px = MathUtils.floorDiv(x, pocketSize);
        int pz = MathUtils.floorDiv(z, pocketSize);
        int rpx = MathUtils.mod(px, REPEAT_PERIOD_POCKETS);
        int rpz = MathUtils.mod(pz, REPEAT_PERIOD_POCKETS);
        long pocketSeed = MathUtils.hash64(worldSeed, rpx, rpz, 0x12345L);
        
        long h = MathUtils.hash64(worldSeed, ix, iz, pocketSeed);
        float jz = (MathUtils.hash64(h, ix, iz, 2) & 0xFFFF) / 65536.0f * cellSize;
        return iz * cellSize + jz;
    }

    /**
     * Returns the axial coordinates (q, r) of the hex cell containing (x, z).
     * Returns (q << 32) | (r & 0xFFFFFFFFL)
     */
    public static long getHexCell(float x, float z, float size) {
        // Pointy-topped hex conversion
        float q = (float) (Math.sqrt(3)/3 * x - 1.0/3 * z) / size;
        float r = (float) (2.0/3 * z) / size;
        return hexRound(q, r);
    }

    private static long hexRound(float q, float r) {
        float s = -q - r;
        int rq = Math.round(q);
        int rr = Math.round(r);
        int rs = Math.round(s);

        float q_diff = Math.abs(rq - q);
        float r_diff = Math.abs(rr - r);
        float s_diff = Math.abs(rs - s);

        if (q_diff > r_diff && q_diff > s_diff) {
            rq = -rr - rs;
        } else if (r_diff > s_diff) {
            rr = -rq - rs;
        }
        
        return ((long) rq << 32) | (rr & 0xFFFFFFFFL);
    }
}
