package com.terrasect.common.handler;

import com.terrasect.common.Context;
import com.terrasect.common.generation.World;
import com.terrasect.common.helpers.ClimateModifier;
import net.minecraft.world.level.biome.Climate;

public final class ClimateHandler {
    private ClimateHandler() {
    }

    public static Climate.TargetPoint modifyTargetPoint(
            Context context, int x, int y, int z, Climate.TargetPoint original) {

        if (context == null || original == null) {
            return original;
        }

        var blockX = x << 2;
        var blockZ = z << 2;

        var traversal = World.traverse(context, blockX, blockZ);
        if (traversal == null || traversal.region == null) {
            return original;
        }

        var climate = traversal.region.definition().climate();
        if (climate == null || !climate.hasRanges()) {
            return original;
        }

        var edgeFactor = 1.0f - traversal.edgeDistance;
        return ClimateModifier.modify(climate, original, edgeFactor);
    }
}
