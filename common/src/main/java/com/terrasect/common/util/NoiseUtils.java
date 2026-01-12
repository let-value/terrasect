package com.terrasect.common.util;

public class NoiseUtils {

    public static float valueNoise(int x, int z, long seed, int salt, int scale) {
        var X = MathUtils.floorDiv(x, scale);
        var Z = MathUtils.floorDiv(z, scale);

        var u = (x - X * scale) / (float) scale;
        var v = (z - Z * scale) / (float) scale;

        u = u * u * (3 - 2 * u);
        v = v * v * (3 - 2 * v);

        var a = MathUtils.randomFloat(seed, X, Z, salt);
        var b = MathUtils.randomFloat(seed, X + 1, Z, salt);
        var c = MathUtils.randomFloat(seed, X, Z + 1, salt);
        var d = MathUtils.randomFloat(seed, X + 1, Z + 1, salt);

        return MathUtils.lerp(v, MathUtils.lerp(u, a, b), MathUtils.lerp(u, c, d));
    }

    public static float riverMask(int x, int z, long seed) {
        var n = valueNoise(x, z, seed, 3001, 512);
        n += valueNoise(x, z, seed, 3002, 256) * 0.5f;
        n /= 1.5f;

        var dist = Math.abs(n - 0.5f);

        return 1.0f - MathUtils.clamp01(dist * 4.0f);
    }

    public static float ridgeMask(int x, int z, long seed) {
        var n = valueNoise(x, z, seed, 4001, 600);
        n += valueNoise(x, z, seed, 4002, 300) * 0.5f;
        n /= 1.5f;

        var centered = n * 2.0f - 1.0f;
        return 1.0f - Math.abs(centered);
    }
}
