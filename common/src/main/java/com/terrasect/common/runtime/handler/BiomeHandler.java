package com.terrasect.common.runtime.handler;

import com.terrasect.common.runtime.BiomeFilter;
import com.terrasect.common.api.Region;
import com.terrasect.common.api.Context;
import com.terrasect.common.devtools.MixinSampler;
import com.terrasect.common.devtools.Profiler;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.lookup.BiomeLookup;
import com.terrasect.common.runtime.World;
import com.terrasect.common.generation.definition.SelectionRules;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

public final class BiomeHandler {
    
    private BiomeHandler() {}
    
    public static Holder<Biome> selectBiome(
            MinecraftContext context,
            int quartX, int quartZ,
            Climate.TargetPoint targetPoint) {
        
        Region region = World.getRegion(context, quartX, quartZ);
        SelectionRules rules = getRules(region);        
        Climate.ParameterList<Holder<Biome>> parameterList = context.getFilteredParameterList(rules);
        boolean wasFiltered = rules != null && (rules.hasAllowRules() || rules.hasBlockRules());
        Holder<Biome> result = parameterList.findValue(targetPoint);

        return result;
    }

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
    
    public static <K, P> boolean isBiomeAllowed(BiomeLookup<K, P> lookup, K biomeKey, SelectionRules rules) {
        return lookup.isAllowed(biomeKey, rules);
    }
}
