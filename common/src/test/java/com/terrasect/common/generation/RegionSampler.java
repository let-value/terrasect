package com.terrasect.common.generation;

import com.terrasect.common.api.Region;
import com.terrasect.common.api.Context;
import com.terrasect.common.runtime.World;

import java.util.HashMap;
import java.util.Map;

public class RegionSampler {

    public static Map<String, Integer> sample(int x, int z, int width, int height, int step, int depth, Context strategy) {
        Map<String, Integer> counts = new HashMap<>();
        
        for (int iz = 0; iz < height; iz += step) {
            for (int ix = 0; ix < width; ix += step) {
                int wx = x + ix;
                int wz = z + iz;
                
                Region region = getRegionAtDepth(wx, wz, strategy, depth);
                String name = region.name();
                counts.put(name, counts.getOrDefault(name, 0) + 1);
            }
        }
        return counts;
    }

    private static Region getRegionAtDepth(int x, int z, Context context, int targetDepth) {
        return World.getRegionAtDepth(World.OVERWORLD, x, z, context, targetDepth);
    }
}
