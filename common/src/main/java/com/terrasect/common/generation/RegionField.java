package com.terrasect.common.generation;

public class RegionField {

    public static final int POCKET_SIZE_BLOCKS = 2048;
    public static final int REPEAT_PERIOD_POCKETS = 5;

    /**
     * Computes the region ID and edge distance for a given world coordinate.
     * Returns a packed long: (regionId << 32) | (floatToRawIntBits(edge))
     */
    public static long getRegionData(int x, int z, long worldSeed) {
        return getRegionData(x, z, worldSeed, 512, 200.0f);
    }

    public static long getRegionData(int x, int z, long worldSeed, int cellSize, float warpAmp) {
        // 1. Compute pocket coordinates
        int px = MathUtils.floorDiv(x, POCKET_SIZE_BLOCKS);
        int pz = MathUtils.floorDiv(z, POCKET_SIZE_BLOCKS);

        // 2. Wrap for repetition (handle negatives)
        int rpx = MathUtils.mod(px, REPEAT_PERIOD_POCKETS);
        int rpz = MathUtils.mod(pz, REPEAT_PERIOD_POCKETS);

        // 3. Compute pocket seed
        long pocketSeed = MathUtils.hash64(worldSeed, rpx, rpz, 0x12345L);

        // 4. Domain warp coordinates
        float warp1 = NoiseUtils.warpNoise1(x, z, pocketSeed);
        float warp2 = NoiseUtils.warpNoise2(x, z, pocketSeed);
        
        int xw = (int) (x + warpAmp * (warp1 * 2.0f - 1.0f)); // Map 0..1 to -1..1
        int zw = (int) (z + warpAmp * (warp2 * 2.0f - 1.0f));

        // 5. Compute grid cell for Worley
        int gx = MathUtils.floorDiv(xw, cellSize);
        int gz = MathUtils.floorDiv(zw, cellSize);

        float bestD = Float.MAX_VALUE;
        float secondBestD = Float.MAX_VALUE;
        int bestId = 0;

        // 6. Check 3x3 neighbors
        for (int ix = gx - 1; ix <= gx + 1; ix++) {
            for (int iz = gz - 1; iz <= gz + 1; iz++) {
                long h = MathUtils.hash64(worldSeed, ix, iz, pocketSeed);
                
                // Site position
                // Jitter from hash: 0..cellSize
                float jx = (MathUtils.hash64(h, ix, iz, 1) & 0xFFFF) / 65536.0f * cellSize;
                float jz = (MathUtils.hash64(h, ix, iz, 2) & 0xFFFF) / 65536.0f * cellSize;
                
                float sx = ix * cellSize + jx;
                float sz = iz * cellSize + jz;

                float dx = xw - sx;
                float dz = zw - sz;
                float d = dx * dx + dz * dz;

                if (d < bestD) {
                    secondBestD = bestD;
                    bestD = d;
                    // Generate a region ID based on the cell coordinates and pocket
                    bestId = (int) MathUtils.hash64(pocketSeed, ix, iz, 0xCAFE);
                } else if (d < secondBestD) {
                    secondBestD = d;
                }
            }
        }

        // 7. Compute edge distance (sqrt for linear distance)
        float distBest = (float) Math.sqrt(bestD);
        float distSecond = (float) Math.sqrt(secondBestD);
        float edge = distSecond - distBest;

        return ((long) bestId << 32) | (Float.floatToRawIntBits(edge) & 0xFFFFFFFFL);
    }

    public static int unpackRegionId(long packed) {
        return (int) (packed >>> 32);
    }

    public static float unpackEdge(long packed) {
        return Float.intBitsToFloat((int) (packed & 0xFFFFFFFFL));
    }
}
