package com.terrasect.common.generation;

/**
 * Simple noise utilities for region warping and feature masks.
 * Uses value noise (interpolated random values) for smooth, controllable displacement.
 */
public class NoiseUtils {

    /**
     * Value noise: smooth random field in 2D.
     * Returns 0.0 to 1.0, smoothly interpolated between grid points.
     * 
     * @param x world X coordinate
     * @param z world Z coordinate  
     * @param seed world seed
     * @param salt additional salt for independent noise fields
     * @param scale grid cell size in blocks (larger = smoother)
     */
    public static float valueNoise(int x, int z, long seed, int salt, int scale) {
        int X = MathUtils.floorDiv(x, scale);
        int Z = MathUtils.floorDiv(z, scale);
        
        // Relative position in grid cell (0 to 1)
        float u = (x - X * scale) / (float) scale;
        float v = (z - Z * scale) / (float) scale;
        
        // Smoothstep interpolation (cubic Hermite)
        u = u * u * (3 - 2 * u);
        v = v * v * (3 - 2 * v);
        
        // Corner values
        float a = MathUtils.randomFloat(seed, X, Z, salt);
        float b = MathUtils.randomFloat(seed, X + 1, Z, salt);
        float c = MathUtils.randomFloat(seed, X, Z + 1, salt);
        float d = MathUtils.randomFloat(seed, X + 1, Z + 1, salt);
        
        // Bilinear interpolation
        return MathUtils.lerp(v, MathUtils.lerp(u, a, b), MathUtils.lerp(u, c, d));
    }

    /**
     * River proximity mask: high values near river centerlines.
     * Uses ridged noise inverted so ridges become river channels.
     */
    public static float riverMask(int x, int z, long seed) {
        float n = valueNoise(x, z, seed, 3001, 512);
        n += valueNoise(x, z, seed, 3002, 256) * 0.5f;
        n /= 1.5f; // Normalize to ~0..1
        
        // River is at 0.5, distance from centerline
        float dist = Math.abs(n - 0.5f);
        // Invert: 1.0 at center, 0.0 far away
        return 1.0f - MathUtils.clamp01(dist * 4.0f);
    }

    /**
     * Ridge/mountain mask: high values on ridge lines.
     * Uses ridged noise (1 - |noise|).
     */
    public static float ridgeMask(int x, int z, long seed) {
        float n = valueNoise(x, z, seed, 4001, 600);
        n += valueNoise(x, z, seed, 4002, 300) * 0.5f;
        n /= 1.5f;
        
        // Center around 0.5 and take absolute deviation
        float centered = n * 2.0f - 1.0f;
        return 1.0f - Math.abs(centered);
    }
}
