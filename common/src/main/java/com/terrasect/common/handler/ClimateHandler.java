package com.terrasect.common.handler;

import com.terrasect.common.Context;
import com.terrasect.common.definition.ClimateSettings;
import com.terrasect.common.generation.TraversalResult;
import com.terrasect.common.generation.World;
import com.terrasect.common.helpers.ClimateModifier;
import net.minecraft.world.level.biome.Climate;

public final class ClimateHandler {
    private ClimateHandler() {}

    public static Climate.TargetPoint modifyTargetPoint(
            Context context, int x, int y, int z, Climate.TargetPoint original) {

        if (context == null || original == null) {
            return original;
        }

        int blockX = x << 2;
        int blockZ = z << 2;

        TraversalResult traversal = World.traverse(context, blockX, blockZ);
        if (traversal == null || traversal.region == null) {
            return original;
        }

        ClimateSettings climate = traversal.region.definition().climate();
        if (climate == null) {
            return original;
        }

        if (climate.temperature() == null
                && climate.humidity() == null
                && climate.continentalness() == null
                && climate.erosion() == null
                && climate.depth() == null
                && climate.weirdness() == null) {
            return original;
        }

        float edgeFactor = 1.0f - traversal.edgeDistance;
        return ClimateModifier.modify(climate, original, edgeFactor);
    }
}
