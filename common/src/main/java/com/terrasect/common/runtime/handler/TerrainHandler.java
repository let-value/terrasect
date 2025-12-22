package com.terrasect.common.runtime.handler;

import com.terrasect.common.api.Context;
import com.terrasect.common.devtools.Profiler;
import com.terrasect.common.runtime.World;

public final class TerrainHandler {

    private TerrainHandler() {
        // Static utility class
    }

    public static Integer getMaxHeight(Context context, int blockX, int blockZ) {
        long t0 = Profiler.begin();

        if (context == null) {
            Profiler.end(Profiler.TERRAIN_HEIGHT_CHECK, t0);
            return null;
        }

        var region = World.getRegion(context, blockX, blockZ);
        if (region == null) {
            Profiler.end(Profiler.TERRAIN_HEIGHT_CHECK, t0);
            return null;
        }

        var climate = region.definition().climate();
        if (climate == null || !climate.hasHeightConstraints()) {
            Profiler.end(Profiler.TERRAIN_HEIGHT_CHECK, t0);
            return null;
        }

        Profiler.end(Profiler.TERRAIN_HEIGHT_CHECK, t0);
        return climate.maxHeight();
    }
}
