package com.terrasect.common.runtime;

import com.terrasect.common.api.Region;
import com.terrasect.common.api.Context;
import com.terrasect.common.util.Packer;
import com.terrasect.common.util.NoiseUtils;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.runtime.strategy.LayoutStrategies;

final class Layout {

    // Warp configuration - simple 3-layer approach:
    // 1. Base warp: organic region shapes (like vanilla biomes)
    // 2. Feature warp: rivers/ridges push boundaries toward them
    // 3. Detail jitter: fine edge noise for natural borders
    //
    // Key insight: warp must be SMOOTH - high frequency noise causes fragmentation.
    // The "jagged Minecraft look" comes from block-level terrain, not region boundaries.
    
    // IMPORTANT: Keep warp VERY smooth to avoid leopard-pattern fragmentation.
    // Region boundaries should be kilometers apart, not meters.
    private static final int WARP_SCALE = 2000;          // Base noise scale (blocks) - MUCH larger for smooth transitions
    private static final float WARP_AMPLITUDE = 200.0f;  // Base displacement (blocks) - scaled with region size
    private static final float FEATURE_STRENGTH = 50.0f; // River/ridge pull strength
    private static final int DETAIL_SCALE = 500;         // Edge detail scale - keep large!
    private static final float DETAIL_AMPLITUDE = 20.0f; // Edge detail (blocks) - subtle
    private static final float SPAWN_SAFE_RADIUS = 512.0f; // Reduced warp near spawn

    Region getRegionAtDepth(Region root, int x, int z, Context context, int targetDepth) {
        return getRegionAtDepth(root, x, z, context, targetDepth, 0, 0);
    }
    
    Region getRegionAtDepth(Region root, int x, int z, Context context, int targetDepth, 
                            float gridOffsetX, float gridOffsetZ) {
        return (Region) traverse(root, x, z, context, targetDepth, false, gridOffsetX, gridOffsetZ);
    }

    long getRegionSeedAtDepth(Region root, int x, int z, Context context, int targetDepth) {
        return getRegionSeedAtDepth(root, x, z, context, targetDepth, 0, 0);
    }
    
    long getRegionSeedAtDepth(Region root, int x, int z, Context context, int targetDepth,
                              float gridOffsetX, float gridOffsetZ) {
        return (Long) traverse(root, x, z, context, targetDepth, true, gridOffsetX, gridOffsetZ);
    }

    private Object traverse(Region root, int x, int z, Context context, int targetDepth, boolean returnSeed,
                            float gridOffsetX, float gridOffsetZ) {
        // WARPED TRAVERSAL: Apply offset first, then warp, then traverse.
        // This creates organic region boundaries while maintaining proper parent-child containment.
        // Offset is applied BEFORE warp so that the anchored region's input coords become (0,0).
        
        // Apply grid offset first (shifts input coords so anchored region is at origin)
        int offsetX = x + (int) gridOffsetX;
        int offsetZ = z + (int) gridOffsetZ;
        
        // Then apply warp to the offset coordinates
        long packedWarp = getWarpedPoint(offsetX, offsetZ, context.getSeed(), context);
        float wx = Packer.unpackPairSecond(packedWarp);
        float wz = Packer.unpackPairFirst(packedWarp);

        Region currentRegion = root;
        long currentSeed = context.getSeed();
        float cx = 0;
        float cz = 0;
        // Budget represents area, so radius = sqrt(budget)
        float radius = (float) Math.sqrt(root.areaBudget());

        int currentDepth = 0;
        while (currentRegion.hasChildren() && currentDepth < targetDepth) {
            GenerationStrategyType type = currentRegion.definition().generationStrategy();

            // Use WARPED coordinates relative to parent center
            // Same warped coords used at all depths ensures children stay in parent bounds
            float dx = wx - cx;
            float dz = wz - cz;

            // All strategies use unified interface
            int regionIndex = LayoutStrategies.query(currentRegion, currentSeed, dx, dz, radius);
            
            currentRegion = currentRegion.children().get(regionIndex);
            currentSeed = LayoutStrategies.getSeed(type, currentSeed, regionIndex, currentRegion);
            cx += LayoutStrategies.getLastCenterX() * radius;
            cz += LayoutStrategies.getLastCenterZ() * radius;
            radius *= LayoutStrategies.getLastRadius();

            currentDepth++;
        }

        return returnSeed ? currentSeed : currentRegion;
    }

    /**
     * Warps a world coordinate to create organic region boundaries.
     * 
     * Three layers of displacement:
     * 1. Base warp - smooth noise for natural-looking region shapes
     * 2. Feature warp - rivers and ridges pull boundaries toward them
     * 3. Detail jitter - fine noise for Minecraft-style jagged edges
     */
    private long getWarpedPoint(int x, int z, long seed, Context context) {
        // Spawn protection: reduce warp near origin for predictable starting area
        float dist = (float) Math.sqrt(x * x + z * z);
        float damp = Math.min(1.0f, dist / SPAWN_SAFE_RADIUS);
        
        // Layer 1: Base warp - organic region shapes
        float baseX = (NoiseUtils.valueNoise(x, z, seed, 1001, WARP_SCALE) - 0.5f) * 2.0f;
        float baseZ = (NoiseUtils.valueNoise(x, z, seed, 1002, WARP_SCALE) - 0.5f) * 2.0f;
        
        // Layer 2: Feature influence - rivers/ridges attract boundaries
        long influence = context.getInfluence(x, z);
        float river = Packer.unpackPairFirst(influence);
        float ridge = Packer.unpackPairSecond(influence);
        float featureStrength = (river + ridge) * FEATURE_STRENGTH;
        
        // Push toward features using gradient approximation (noise derivatives)
        float featureAngle = NoiseUtils.valueNoise(x, z, seed, 2001, WARP_SCALE / 2) * 6.283f;
        float featureX = (float) Math.cos(featureAngle) * featureStrength;
        float featureZ = (float) Math.sin(featureAngle) * featureStrength;
        
        // Layer 3: Detail jitter - fine edge noise for natural borders
        float detailX = (NoiseUtils.valueNoise(x, z, seed, 3001, DETAIL_SCALE) - 0.5f) * 2.0f;
        float detailZ = (NoiseUtils.valueNoise(x, z, seed, 3002, DETAIL_SCALE) - 0.5f) * 2.0f;
        
        // Combine all layers
        float wx = x + (baseX * WARP_AMPLITUDE + featureX) * damp + detailX * DETAIL_AMPLITUDE;
        float wz = z + (baseZ * WARP_AMPLITUDE + featureZ) * damp + detailZ * DETAIL_AMPLITUDE;

        return Packer.packPair(wz, wx);
    }
}
