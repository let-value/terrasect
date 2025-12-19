package com.terrasect.common.generation;

import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.generation.strategy.LayoutStrategies;

/**
 * Encapsulates the math for traversing the region hierarchy. Separating this from
 * {@link World} makes it easier to reason about the generation math independently
 * from the global static state.
 */
final class NarrativeSpace {

    // Warp configuration - simple 3-layer approach:
    // 1. Base warp: organic region shapes (like vanilla biomes)
    // 2. Feature warp: rivers/ridges push boundaries toward them
    // 3. Detail jitter: fine edge noise for natural borders
    //
    // Key insight: warp must be SMOOTH - high frequency noise causes fragmentation.
    // The "jagged Minecraft look" comes from block-level terrain, not region boundaries.
    
    private static final int WARP_SCALE = 400;           // Base noise scale (blocks) - larger = smoother
    private static final float WARP_AMPLITUDE = 80.0f;   // Base displacement (blocks)
    private static final float FEATURE_STRENGTH = 32.0f; // River/ridge pull strength
    private static final int DETAIL_SCALE = 64;          // Edge detail scale - not too small!
    private static final float DETAIL_AMPLITUDE = 8.0f;  // Edge detail (blocks)
    private static final float SPAWN_SAFE_RADIUS = 256.0f; // Reduced warp near spawn

    Region getRegionAtDepth(Region root, int x, int z, Strategy context, int targetDepth) {
        return (Region) traverse(root, x, z, context, targetDepth, false);
    }

    long getRegionSeedAtDepth(Region root, int x, int z, Strategy context, int targetDepth) {
        return (Long) traverse(root, x, z, context, targetDepth, true);
    }

    private Object traverse(Region root, int x, int z, Strategy context, int targetDepth, boolean returnSeed) {
        // WARPED TRAVERSAL: Apply warp ONCE at the start, then use warped coords consistently.
        // This creates organic region boundaries while maintaining proper parent-child containment.
        // The key insight: warp the INPUT coordinates, then traverse with those warped coords.
        // Children stay within parent bounds because we use the SAME warped coords throughout.
        
        // Apply warp to input coordinates
        long packedWarp = getWarpedPoint(x, z, context.getSeed(), context);
        float wx = Float.intBitsToFloat((int) (packedWarp >> 32));
        float wz = Float.intBitsToFloat((int) packedWarp);

        Region currentRegion = root;
        long currentSeed = context.getSeed();
        float cx = 0;
        float cz = 0;
        float radius = (float) root.areaBudget();

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
    private long getWarpedPoint(int x, int z, long seed, Strategy context) {
        // Spawn protection: reduce warp near origin for predictable starting area
        float dist = (float) Math.sqrt(x * x + z * z);
        float damp = Math.min(1.0f, dist / SPAWN_SAFE_RADIUS);
        
        // Layer 1: Base warp - organic region shapes
        float baseX = (NoiseUtils.valueNoise(x, z, seed, 1001, WARP_SCALE) - 0.5f) * 2.0f;
        float baseZ = (NoiseUtils.valueNoise(x, z, seed, 1002, WARP_SCALE) - 0.5f) * 2.0f;
        
        // Layer 2: Feature influence - rivers/ridges attract boundaries
        float river = context.getRiverInfluence(x, z);
        float ridge = context.getRidgeInfluence(x, z);
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

        return ((long) Float.floatToRawIntBits(wx) << 32) | (Float.floatToRawIntBits(wz) & 0xFFFFFFFFL);
    }
}
