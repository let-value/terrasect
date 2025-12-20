package com.terrasect.common.runtime.strategy;

import com.terrasect.common.util.MathUtils;
import com.terrasect.common.api.Region;
import com.terrasect.common.runtime.RegionField;
import com.terrasect.common.generation.definition.StrategySettings;

import java.util.List;

/**
 * Infinite hex grid tiling strategy.
 * 
 * Unlike other strategies that partition a bounded parent region, HEX tiles
 * infinitely across the world. Each hex cell picks a child region weighted
 * by budget, with the origin hex (0,0) always getting the first child.
 * 
 * Supports optional "ring" regions - buffer zones BETWEEN hex cells that
 * act as spacing/borders. The ring increases hex grid spacing rather than
 * reducing interior area, so children get full budget space.
 * 
 * Ring model:
 * - Interior radius = radius parameter (children use this full space)
 * - Ring width = derived from ring region's budget (e.g., 30 = 30% of interior)
 * - Hex grid spacing = interior + ring (hexes are farther apart)
 * - Points outside any hex's interior circle belong to the ring
 * 
 * Best for root level to create repeating narrative "stories" across the world.
 */
public final class HexStrategy {

    private HexStrategy() {}

    /**
     * Query which child region contains the point (dx, dz).
     * 
     * @param seed Parent seed
     * @param children Child regions  
     * @param dx X offset from parent center
     * @param dz Z offset from parent center
     * @param radius Interior hex cell size (children use this full space)
     * @param settings Strategy settings (may be HexSettings with ring region)
     * @param out Output array: [childIndex, centerX, centerZ, radiusScale, hexQ, hexR, isRing]
     *            centerX/centerZ are relative to parent center (normalized), not absolute hex coords
     */
    public static void query(long seed, List<Region> children, float dx, float dz,
                              float radius, StrategySettings settings, float[] out) {
        
        // Determine ring configuration
        String ringRegionName = null;
        float ringWidth = 0;  // As fraction of interior radius
        int ringIndex = -1;
        
        if (settings != null && settings.hex() != null) {
            ringRegionName = settings.hex().ringRegionName();
        }
        
        if (ringRegionName != null) {
            ringIndex = findChildByName(children, ringRegionName);
            if (ringIndex >= 0) {
                // Ring budget as percentage of interior (30 = 30% of interior radius)
                float ringBudget = children.get(ringIndex).areaBudget();
                ringWidth = ringBudget / 100.0f;
                ringWidth = Math.max(0.05f, Math.min(0.5f, ringWidth));
            }
        }
        
        // Effective hex size for grid spacing = interior + ring
        float gridRadius = radius * (1.0f + ringWidth);
        
        // Get hex cell using the larger grid spacing
        long packedHex = RegionField.getHexCell(dx, dz, gridRadius);
        int q = (int) (packedHex >> 32);
        int r = (int) packedHex;
        
        // Store hex coords for seed calculation
        out[4] = q;
        out[5] = r;
        out[6] = 0;
        
        // Calculate hex center in world space (using grid spacing)
        float hexCenterWorldX = ((float) Math.sqrt(3) * q + (float) Math.sqrt(3) / 2.0f * r) * gridRadius;
        float hexCenterWorldZ = (3.0f / 2.0f * r) * gridRadius;
        
        // Distance from hex center (in world units)
        float offsetX = dx - hexCenterWorldX;
        float offsetZ = dz - hexCenterWorldZ;
        float distFromCenter = (float) Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        
        // Check if in ring zone (outside interior circle but inside hex boundary)
        // Interior uses the original radius, ring fills the gap
        if (ringIndex >= 0 && distFromCenter > radius) {
            // In the ring zone - this is the space between hex interiors
            out[0] = ringIndex;
            // Normalize by radius so traverse computes correct world position
            out[1] = hexCenterWorldX / radius;
            out[2] = hexCenterWorldZ / radius;
            out[3] = ringWidth;  // Ring's effective radius
            out[6] = 1;
            return;
        }
        
        // Inside hex interior - pick child weighted by budget
        int hexDist = (Math.abs(q) + Math.abs(q + r) + Math.abs(r)) / 2;
        
        int childIndex;
        if (hexDist == 0) {
            childIndex = 0;  // Origin hex gets first child
        } else {
            childIndex = pickChildWeighted(children, ringRegionName, MathUtils.hash64(seed, q, r, 9999));
        }
        
        out[0] = childIndex;
        // Hex center normalized by radius (so traverse computes correct world position)
        out[1] = hexCenterWorldX / radius;
        out[2] = hexCenterWorldZ / radius;
        // Children fill the entire hex interior circle (radius).
        // The ring check already ensures we only get here for points within the interior.
        // Using 1.0 means child strategies work in the full interior space.
        out[3] = 1.0f;
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
