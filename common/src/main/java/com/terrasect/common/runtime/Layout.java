package com.terrasect.common.runtime;

import com.terrasect.common.api.Region;
import com.terrasect.common.api.Context;
import com.terrasect.common.util.Packer;
import com.terrasect.common.util.NoiseUtils;
import com.terrasect.common.runtime.strategy.LayoutStrategies;
import com.terrasect.common.runtime.strategy.QueryResult;

final class Layout {

    // Thread-local result to avoid allocations
    private static final ThreadLocal<TraversalResult> RESULT = ThreadLocal.withInitial(TraversalResult::new);

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
        return traverse(root, x, z, context, targetDepth, gridOffsetX, gridOffsetZ).region;
    }

    long getRegionSeedAtDepth(Region root, int x, int z, Context context, int targetDepth) {
        return getRegionSeedAtDepth(root, x, z, context, targetDepth, 0, 0);
    }
    
    long getRegionSeedAtDepth(Region root, int x, int z, Context context, int targetDepth,
                              float gridOffsetX, float gridOffsetZ) {
        return traverse(root, x, z, context, targetDepth, gridOffsetX, gridOffsetZ).seed;
    }
    
    /**
     * Full traversal returning region, seed, and edge distance.
     * Returns thread-local result - caller must use values before next call.
     */
    TraversalResult getTraversalResult(Region root, int x, int z, Context context, int targetDepth,
                                        float gridOffsetX, float gridOffsetZ) {
        return traverse(root, x, z, context, targetDepth, gridOffsetX, gridOffsetZ);
    }

    private TraversalResult traverse(Region root, int x, int z, Context context, int targetDepth,
                            float gridOffsetX, float gridOffsetZ) {
        TraversalResult out = RESULT.get();
        out.edgeDistance = 1.0f;  // Start at center, take minimum during traversal
        float minBlockDistToEdge = Float.MAX_VALUE;  // Track actual block distance for edgeInfluence
        
        long currentSeed = context.getSeed();
        // WARPED TRAVERSAL: Apply offset first, then warp, then traverse.
        // This creates organic region boundaries while maintaining proper parent-child containment.
        // Offset is applied BEFORE warp so that the anchored region's input coords become (0,0).
        
        // Apply grid offset first (shifts input coords so anchored region is at origin)
        int offsetX = x + (int) gridOffsetX;
        int offsetZ = z + (int) gridOffsetZ;
        
        // Then apply warp to the offset coordinates
        long packedWarp = getWarpedPoint(offsetX, offsetZ, currentSeed, context);
        float wx = Packer.unpackPairSecond(packedWarp);
        float wz = Packer.unpackPairFirst(packedWarp);

        Region currentRegion = root;
        float cx = 0;
        float cz = 0;
        // Use pre-baked radius from Region
        float radius = root.radius();

        int currentDepth = 0;
        while (currentRegion.hasChildren() && currentDepth < targetDepth) {
            // Use WARPED coordinates relative to parent center
            // Same warped coords used at all depths ensures children stay in parent bounds
            float dx = wx - cx;
            float dz = wz - cz;

            // Single query returns all needed values
            QueryResult result = LayoutStrategies.query(currentRegion, currentSeed, dx, dz, radius);
            
            currentRegion = currentRegion.children().get(result.childIndex);
            currentSeed = result.childSeed;
            cx += result.centerX * radius;
            cz += result.centerZ * radius;
            radius *= result.radius;
            
            // Track minimum edge distance across all hierarchy levels
            out.edgeDistance = Math.min(out.edgeDistance, result.edgeDistance);
            
            // Compute actual block distance to edge for this level
            // edgeDistance is normalized (0=edge, 1=center), radius is in blocks
            float blockDist = result.edgeDistance * radius;
            minBlockDistToEdge = Math.min(minBlockDistToEdge, blockDist);

            currentDepth++;
        }

        out.region = currentRegion;
        out.seed = currentSeed;
        
        // Compute edgeInfluence: 0 when >8 blocks from edge, ramps to 1 at edge
        // Formula: 1 - clamp(blockDist / 8, 0, 1)
        if (minBlockDistToEdge == Float.MAX_VALUE) {
            out.edgeInfluence = 0.0f;  // No traversal happened, assume interior
        } else {
            float normalized = minBlockDistToEdge / 8.0f;
            out.edgeInfluence = 1.0f - Math.min(normalized, 1.0f);
        }
        
        return out;
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
