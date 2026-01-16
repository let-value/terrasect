package com.terrasect.common.util;

public class MathUtils {

  public static int floorDiv(int a, int b) {
    var r = a / b;

    if ((a ^ b) < 0 && (r * b != a)) {
      r--;
    }
    return r;
  }

  public static int mod(int a, int b) {
    var r = a % b;
    if (r < 0) {
      r += b;
    }
    return r;
  }

  public static long hash64(long seed, int x, int z, long salt) {
    var h = seed + x * 31337L + z * 0x5F3759DFL + salt;
    h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
    h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
    h = h ^ (h >>> 31);
    return h;
  }

  public static long hash64(long seed, int x, int z, int salt) {
    return hash64(seed, x, z, (long) salt);
  }

  public static float randomFloat(long seed, int x, int z, int salt) {
    var h = hash64(seed, x, z, salt);

    return (h >>> 40) * 5.9604645E-8F;
  }

  public static float clamp01(float v) {
    return v < 0 ? 0 : (v > 1 ? 1 : v);
  }

  public static float lerp(float t, float a, float b) {
    return a + t * (b - a);
  }

  public static long getHexCell(float x, float z, float size) {

    var q = (float) (Math.sqrt(3) / 3 * x - 1.0 / 3 * z) / size;
    var r = (float) (2.0 / 3 * z) / size;
    return hexRound(q, r);
  }

  private static long hexRound(float q, float r) {
    var s = -q - r;
    var rq = Math.round(q);
    var rr = Math.round(r);
    var rs = Math.round(s);

    var q_diff = Math.abs(rq - q);
    var r_diff = Math.abs(rr - r);
    var s_diff = Math.abs(rs - s);

    if (q_diff > r_diff && q_diff > s_diff) {
      rq = -rr - rs;
    } else if (r_diff > s_diff) {
      rr = -rq - rs;
    }

    return ((long) rq << 32) | (rr & 0xFFFFFFFFL);
  }
}
