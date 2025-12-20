package com.terrasect.common.runtime.handler;

import com.terrasect.common.runtime.BiomeFilter;
import com.terrasect.common.api.Region;
import com.terrasect.common.api.Context;
import com.terrasect.common.lookup.BiomeLookup;
import com.terrasect.common.runtime.World;
import com.terrasect.common.devtools.MixinSampler;
import com.terrasect.common.generation.definition.SelectionRules;

/**
 * Shared biome filtering logic for platform mixins.
 * 
 * <p>This class contains all the common biome handling code that is shared
 * between Fabric and NeoForge BiomeMixin implementations.
 * 
 * <p>Supports dimension-aware region lookups. The context provides
 * the dimension ID, which is used to look up the appropriate root region.
 * 
 * <p>Design: The actual ParameterList filtering is done in platform mixins
 * since it requires Minecraft classes. This class provides allocation-free
 * rule lookups and O(1) biome checking.
 */
public final class BiomeHandler {
    
    private BiomeHandler() {
        // Static utility class
    }
    
    /**
     * Get biome selection rules for a location.
     * Returns null if no filtering should be applied.
     * 
     * <p>This method avoids allocation - callers just check for null.
     * 
     * @param context The generation context (null returns null)
     * @param quartX Quart X coordinate (block >> 2)
     * @param quartZ Quart Z coordinate (block >> 2)
     * @return SelectionRules if filtering needed, null otherwise
     */
    public static SelectionRules getRules(Context context, int quartX, int quartZ) {
        if (context == null) {
            return null;
        }
        
        int blockX = quartX << 2;
        int blockZ = quartZ << 2;
        
        Region region = getRegion(context, blockX, blockZ);
        if (region == null) {
            return null;
        }
        
        SelectionRules rules = region.definition().biomes();
        if (!BiomeFilter.hasRules(rules)) {
            return null;
        }
        
        return rules;
    }
    
    /**
     * Record biome selection result for debug/sampling.
     * Looks up region name internally to avoid passing it through mixin.
     * 
     * @param context The generation context
     * @param quartX Quart X coordinate
     * @param quartZ Quart Z coordinate
     * @param biomeId The selected biome ID
     * @param wasFiltered Whether filtering was applied
     */
    public static void recordResult(Context context, int quartX, int quartZ, 
                                     String biomeId, boolean wasFiltered) {
        String regionName = null;
        if (context != null) {
            Region region = getRegion(context, quartX << 2, quartZ << 2);
            if (region != null) {
                regionName = region.name();
            }
        }
        MixinSampler.recordBiomeFilter(quartX, quartZ, biomeId, regionName, wasFiltered);
    }
    
    /**
     * Get region at a block coordinate.
     */
    private static Region getRegion(Context context, int blockX, int blockZ) {
        return World.getRegion(context.getDimensionId(), blockX, blockZ, context);
    }
    
    /**
     * Check if a biome is allowed using a pre-built lookup for O(1) performance.
     * This is the preferred method for hot paths in world generation.
     * 
     * @param <K> The biome key type (typically Holder<Biome>)
     * @param lookup The pre-built biome metadata lookup
     * @param biomeKey The biome key to check
     * @param rules The selection rules to apply
     * @return true if the biome is allowed, false if blocked
     */
    public static <K> boolean isBiomeAllowed(BiomeLookup<K> lookup, K biomeKey, SelectionRules rules) {
        return lookup.isAllowed(biomeKey, rules);
    }
}
