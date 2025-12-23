package com.terrasect.common.util;

public class MathUtils {
    /**
     * Computes floor(a / b).
     * Useful for grid coordinates.
     */
    public static int floorDiv(int a, int b) {
        int r = a / b;
        // if the signs are different and modulo not zero, adjust down
        if ((a ^ b) < 0 && (r * b != a)) {
            r--;
        }
        return r;
    }

    /**
     * Computes a mod b, handling negatives correctly to return [0, b).
     */
    public static int mod(int a, int b) {
        int r = a % b;
        if (r < 0) {
            r += b;
        }
        return r;
    }

    /**
     * MurmurHash3 mix64 variant or SplitMix64 step.
     * Deterministic, stateless.
     */
    public static long hash64(long seed, int x, int z, long salt) {
        long h = seed + x * 31337L + z * 0x5F3759DFL + salt;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return h;
    }
    
    public static long hash64(long seed, int x, int z, int salt) {
        return hash64(seed, x, z, (long) salt);
    }

    /**
     * Mixes coordinates to generate a pseudo-random float in [0, 1).
     */
    public static float randomFloat(long seed, int x, int z, int salt) {
        long h = hash64(seed, x, z, salt);
        // Use upper 24 bits for float mantissa
        return (h >>> 40) * 5.9604645E-8F; // 1 / 2^24
    }

    public static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
    
    public static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }
    
    /**
     * Returns the axial coordinates (q, r) of the hex cell containing (x, z).
     * Uses pointy-topped hexagon orientation.
     * 
     * @param x World X coordinate
     * @param z World Z coordinate
     * @param size Hex cell size (distance from center to corner)
     * @return Packed long: (q << 32) | (r & 0xFFFFFFFFL)
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
