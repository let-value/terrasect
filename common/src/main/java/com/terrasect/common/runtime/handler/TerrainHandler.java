package com.terrasect.common.runtime.handler;

import com.terrasect.common.api.Context;
import com.terrasect.common.runtime.World;

public final class TerrainHandler {

    private TerrainHandler() {
        // Static utility class
    }

    public static Integer getMaxHeight(Context context, int blockX, int blockZ) {

        if (context == null) {
            return null;
        }

        var region = World.getRegion(context, blockX, blockZ);
        if (region == null) {
            return null;
        }

        var climate = region.definition().climate();
        if (climate == null || !climate.hasHeightConstraints()) {
            return null;
        }

        return climate.maxHeight();
    }
}
