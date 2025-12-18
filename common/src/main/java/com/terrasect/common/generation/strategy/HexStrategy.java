package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;
import com.terrasect.common.generation.RegionField;

import java.util.List;

public final class HexStrategy {

    private HexStrategy() {}

    public static long getCell(float wx, float wz, float radius) {
        return RegionField.getHexCell(wx, wz, radius);
    }

    public static Region getRegion(List<Region> children, long seed, long packedHex) {
        int q = (int) (packedHex >> 32);
        int r = (int) packedHex;

        int hexDist = (Math.abs(q) + Math.abs(q + r) + Math.abs(r)) / 2;
        
        if (hexDist == 0) {
            return children.get(0);
        } else {
            long hexSeed = MathUtils.hash64(seed, q, r, 9999);
            return pickChildWeighted(children, hexSeed);
        }
    }

    public static long getSeed(long seed, long packedHex) {
        int q = (int) (packedHex >> 32);
        int r = (int) packedHex;
        
        int hexDist = (Math.abs(q) + Math.abs(q + r) + Math.abs(r)) / 2;
        
        if (hexDist == 0) {
            return MathUtils.hash64(seed, 0, 0, 9999);
        } else {
            return MathUtils.hash64(seed, q, r, 9999);
        }
    }

    public static float getNextCx(float cx, float radius, long packedHex) {
        int q = (int) (packedHex >> 32);
        int r = (int) packedHex;
        return cx + radius * ((float) Math.sqrt(3) * q + (float) Math.sqrt(3) / 2.0f * r);
    }

    public static float getNextCz(float cz, float radius, long packedHex) {
        int r = (int) packedHex;
        return cz + radius * (3.0f / 2.0f * r);
    }

    private static Region pickChildWeighted(List<Region> children, long seed) {
        float randomVal = (MathUtils.hash64(seed, 0, 0, 0) & 0xFFFF) / 65536.0f;

        float totalWeight = 0;
        for (Region r : children) {
            totalWeight += r.areaBudget();
        }

        float target = randomVal * totalWeight;
        float currentW = 0;
        for (Region r : children) {
            currentW += r.areaBudget();
            if (currentW >= target) return r;
        }
        return children.get(0);
    }
}
