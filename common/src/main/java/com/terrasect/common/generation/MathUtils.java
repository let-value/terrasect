package com.terrasect.common.generation;

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
}
