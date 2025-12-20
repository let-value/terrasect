package com.terrasect.common.runtime.handler;

import com.terrasect.common.runtime.BiomeFilter;
import com.terrasect.common.api.Region;
import com.terrasect.common.api.Context;
import com.terrasect.common.devtools.MixinSampler;
import com.terrasect.common.lookup.BiomeLookup;
import com.terrasect.common.runtime.World;
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
     * Get the region for a location. Returns null if no context or region found.
     * 
     * <p>Callers can then access both rules and region name from the Region:
     * <pre>
     * Region region = BiomeHandler.getRegion(context, quartX, quartZ);
     * SelectionRules rules = region != null ? region.definition().biomes() : null;
     * String regionName = region != null ? region.name() : null;
     * </pre>
     * 
     * @param context The generation context (null returns null)
     * @param quartX Quart X coordinate (block >> 2)
     * @param quartZ Quart Z coordinate (block >> 2)
     * @return Region at this location, or null
     */
    public static Region getRegion(Context context, int quartX, int quartZ) {
        if (context == null) {
            return null;
        }
        int blockX = quartX << 2;
        int blockZ = quartZ << 2;
        return World.getRegion(context.getDimensionId(), blockX, blockZ, context);
    }
    
    /**
     * Get biome selection rules from a region, checking if filtering is needed.
     * Returns null if no filtering should be applied.
     * 
     * @param region The region (may be null)
     * @return SelectionRules if filtering needed, null otherwise
     */
    public static SelectionRules getRules(Region region) {
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
     * Record biome selection result for sampling.
     * Only records if MixinSampler is enabled.
     * 
     * @param quartX Quart X coordinate
     * @param quartZ Quart Z coordinate
     * @param biomeId The selected biome ID
     * @param region The region (may be null)
     * @param wasFiltered Whether filtering was applied
     */
    public static void recordResult(int quartX, int quartZ, String biomeId, 
                                     Region region, boolean wasFiltered) {
        if (MixinSampler.isEnabled()) {
            String regionName = region != null ? region.name() : null;
            MixinSampler.recordBiomeFilter(quartX, quartZ, biomeId, regionName, wasFiltered);
        }
    }
    
    /**
     * Check if a biome is allowed using a pre-built lookup for O(1) performance.
     * This is the preferred method for hot paths in world generation.
     * 
     * @param <K> The biome key type (typically Holder<Biome>)
     * @param <P> The parameter list type
     * @param lookup The pre-built biome metadata lookup
     * @param biomeKey The biome key to check
     * @param rules The selection rules to apply
     * @return true if the biome is allowed, false if blocked
     */
    public static <K, P> boolean isBiomeAllowed(BiomeLookup<K, P> lookup, K biomeKey, SelectionRules rules) {
        return lookup.isAllowed(biomeKey, rules);
    }
}
