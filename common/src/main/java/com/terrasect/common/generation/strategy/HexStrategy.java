package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;
import com.terrasect.common.generation.RegionField;
import com.terrasect.common.generation.definition.StrategySettings;

import java.util.List;

/**
 * Infinite hex grid tiling strategy.
 * 
 * Unlike other strategies that partition a bounded parent region, HEX tiles
 * infinitely across the world. Each hex cell picks a child region weighted
 * by budget, with the origin hex (0,0) always getting the first child.
 * 
 * Supports optional "ring" regions - buffer zones between hex cells that
 * resolve to a separate region (like wilderness between story hexes).
 * 
 * Best for root level to create repeating narrative "stories" across the world.
 */
public final class HexStrategy {

    // Ring is ~15% of hex radius (the gap between inscribed circle and hex edge)
    private static final float RING_FRACTION = 0.15f;

    private HexStrategy() {}

    /**
     * Query which child region contains the point (dx, dz).
     * 
     * @param seed Parent seed
     * @param children Child regions  
     * @param dx X offset from parent center
     * @param dz Z offset from parent center
     * @param radius Hex cell size
     * @param settings Strategy settings (may be HexSettings with ring region)
     * @param out Output array: [childIndex, centerX, centerZ, radiusScale, hexQ, hexR, isRing]
     */
    public static void query(long seed, List<Region> children, float dx, float dz,
                              float radius, StrategySettings settings, float[] out) {
        // Get hex cell from world position
        long packedHex = RegionField.getHexCell(dx, dz, radius);
        int q = (int) (packedHex >> 32);
        int r = (int) packedHex;
        
        // Store hex coords for seed calculation
        out[4] = q;
        out[5] = r;
        out[6] = 0; // Not a ring by default
        
        // Calculate hex center
        float hexCenterX = (float) Math.sqrt(3) * q + (float) Math.sqrt(3) / 2.0f * r;
        float hexCenterZ = 3.0f / 2.0f * r;
        
        // Check if in ring zone (if ring region is configured)
        String ringRegionName = null;
        if (settings != null && settings.hex() != null) {
            ringRegionName = settings.hex().ringRegionName();
        }
        
        if (ringRegionName != null) {
            // Distance from hex center (normalized to radius)
            float localX = dx / radius - hexCenterX;
            float localZ = dz / radius - hexCenterZ;
            float distFromCenter = (float) Math.sqrt(localX * localX + localZ * localZ);
            
            // Inscribed circle radius is sqrt(3)/2 ≈ 0.866 of the outer radius
            float inscribedRadius = 0.866f;
            
            if (distFromCenter > inscribedRadius) {
                // We're in the ring zone - find the ring region
                int ringIndex = findChildByName(children, ringRegionName);
                if (ringIndex >= 0) {
                    out[0] = ringIndex;
                    out[1] = hexCenterX;
                    out[2] = hexCenterZ;
                    out[3] = 1.0f;
                    out[6] = 1; // Mark as ring region
                    return;
                }
            }
        }
        
        // Normal hex cell - pick child weighted by budget
        int hexDist = (Math.abs(q) + Math.abs(q + r) + Math.abs(r)) / 2;
        
        int childIndex;
        if (hexDist == 0) {
            // Origin hex always gets first child
            childIndex = 0;
        } else {
            // Other hexes pick weighted by budget (excluding ring region if present)
            childIndex = pickChildWeighted(children, ringRegionName, MathUtils.hash64(seed, q, r, 9999));
        }
        
        out[0] = childIndex;
        out[1] = hexCenterX;
        out[2] = hexCenterZ;
        out[3] = 1.0f; // Hex doesn't change radius scale
    }

    /**
     * Compute seed for a hex cell.
     */
    public static long getSeed(long parentSeed, int q, int r) {
        return MathUtils.hash64(parentSeed, q, r, 9999);
    }

    private static int findChildByName(List<Region> children, String name) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static int pickChildWeighted(List<Region> children, String excludeName, long seed) {
        float randomVal = (MathUtils.hash64(seed, 0, 0, 0) & 0xFFFF) / 65536.0f;
        float totalWeight = 0;
        for (Region region : children) {
            if (excludeName != null && region.name().equals(excludeName)) continue;
            totalWeight += region.areaBudget();
        }
        float target = randomVal * totalWeight;
        float currentW = 0;
        for (int i = 0; i < children.size(); i++) {
            Region region = children.get(i);
            if (excludeName != null && region.name().equals(excludeName)) continue;
            currentW += region.areaBudget();
            if (currentW >= target) return i;
        }
        return 0;
    }
}
