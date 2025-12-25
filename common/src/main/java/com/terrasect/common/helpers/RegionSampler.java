package com.terrasect.common.helpers;

import com.terrasect.common.Context;
import com.terrasect.common.definition.Region;
import com.terrasect.common.generation.TraversalResult;
import com.terrasect.common.generation.World;

import java.util.HashMap;
import java.util.Map;

public class RegionSampler {

    public static Map<String, Integer> sample(int x, int z, int width, int height, int step, int depth, Context context) {
        Map<String, Integer> counts = new HashMap<>();
        
        for (int iz = 0; iz < height; iz += step) {
            for (int ix = 0; ix < width; ix += step) {
                int wx = x + ix;
                int wz = z + iz;
                
                Region region = getRegionAtDepth(context, wx, wz, depth);
                String name = region.name();
                counts.put(name, counts.getOrDefault(name, 0) + 1);
            }
        }
        return counts;
    }

    private static Region getRegionAtDepth(Context context, int x, int z, int targetDepth) {
        TraversalResult traversal = World.traverse(context, x, z, targetDepth);
        return traversal != null ? traversal.region : null;
    }
}
