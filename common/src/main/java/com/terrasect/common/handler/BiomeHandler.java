package com.terrasect.common.handler;

import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.SelectionRules;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.generation.TraversalResult;
import com.terrasect.common.generation.World;
import com.terrasect.common.helpers.BiomeFilter;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

public final class BiomeHandler {

    private BiomeHandler() {}

    public static Holder<Biome> selectBiome(
            MinecraftContext context, int quartX, int quartZ, Climate.TargetPoint targetPoint) {

        // Convert quartile coordinates to block coordinates
        int blockX = quartX << 2;
        int blockZ = quartZ << 2;

        TraversalResult traversal = World.traverse(context, blockX, blockZ);
        Region region = traversal != null ? traversal.region : null;
        SelectionRules rules = getRules(region);
        var parameterList = context.biomeLookup.getFilteredParameterList(rules);
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
}
