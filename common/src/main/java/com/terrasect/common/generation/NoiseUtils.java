package com.terrasect.common.generation;

public class NoiseUtils {

    // Simple value noise for warping
    public static float valueNoise(int x, int z, long seed, int salt, int scale) {
        int X = MathUtils.floorDiv(x, scale);
        int Z = MathUtils.floorDiv(z, scale);
        
        // Relative position in grid cell
        float u = (x - X * scale) / (float) scale;
        float v = (z - Z * scale) / (float) scale;
        
        // Smoothstep
        u = u * u * (3 - 2 * u);
        v = v * v * (3 - 2 * v);
        
        float a = MathUtils.randomFloat(seed, X, Z, salt);
        float b = MathUtils.randomFloat(seed, X + 1, Z, salt);
        float c = MathUtils.randomFloat(seed, X, Z + 1, salt);
        float d = MathUtils.randomFloat(seed, X + 1, Z + 1, salt);
        
        return MathUtils.lerp(v, MathUtils.lerp(u, a, b), MathUtils.lerp(u, c, d));
    }

    // Warp noise 1 (X displacement)
    public static float warpNoise1(int x, int z, long seed) {
        // Octave 1: Large scale for broad distortion
        float n1 = valueNoise(x, z, seed, 1001, 400);
        // Octave 2: Smaller scale detail
        float n2 = valueNoise(x, z, seed, 1002, 200) * 0.5f;
        return n1 + n2;
    }

    // Warp noise 2 (Z displacement)
    public static float warpNoise2(int x, int z, long seed) {
        float n1 = valueNoise(x, z, seed, 2001, 400);
        float n2 = valueNoise(x, z, seed, 2002, 200) * 0.5f;
        return n1 + n2;
    }

    // Continuous mask: high near rivers
    public static float riverMask(int x, int z, long seed) {
        // Ridged noise makes good rivers (inverted ridges)
        // Scale 512 to match region size roughly
        
        float n = valueNoise(x, z, seed, 3001, 512);
        n += valueNoise(x, z, seed, 3002, 256) * 0.5f;
        n /= 1.5f; // Normalize roughly to 0..1
        
        // River is at 0.5, so distance from 0.5
        float dist = Math.abs(n - 0.5f);
        // Invert: 1.0 at center, 0.0 far away
        return 1.0f - MathUtils.clamp01(dist * 4.0f); // Sharp falloff
    }

    // Continuous mask: high on ridges / mountains
    public static float ridgeMask(int x, int z, long seed) {
        // Ridged noise: 1 - abs(noise)
        float n = valueNoise(x, z, seed, 4001, 600);
        n += valueNoise(x, z, seed, 4002, 300) * 0.5f;
        n /= 1.5f;
        
        // Map -1..1 (approx) to 0..1
        // Actually valueNoise is 0..1 approx (randomFloat is 0..1)
        // Let's center it
        float centered = n * 2.0f - 1.0f;
        return 1.0f - Math.abs(centered);
    }
}
