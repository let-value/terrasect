package com.terrasect.common.runtime.handler;

import com.terrasect.common.devtools.Profiler;
import com.terrasect.common.generation.MinecraftContext;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Wraps finalDensity() to constrain terrain height by returning air above maxHeight.
 * Self-contained - receives MinecraftContext at construction time from NoiseChunkMixin.
 */
public record HeightConstrainedDensityFunction(
    DensityFunction wrapped,
    MinecraftContext context
) implements DensityFunction {
    
    private static final double AIR_DENSITY = -1.0;
    
    @Override
    public double compute(FunctionContext ctx) {
        long t0 = Profiler.begin();
        double density = wrapped.compute(ctx);
        if (density <= 0 || context == null) {
            Profiler.end(Profiler.TERRAIN_DENSITY_COMPUTE, t0);
            return density;
        }
        
        Integer maxHeight = TerrainHandler.getMaxHeight(context, ctx.blockX(), ctx.blockZ());
        Profiler.end(Profiler.TERRAIN_DENSITY_COMPUTE, t0);
        return (maxHeight != null && ctx.blockY() > maxHeight) ? AIR_DENSITY : density;
    }
    
    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        long t0 = Profiler.begin();
        wrapped.fillArray(array, contextProvider);
        if (context == null) {
            Profiler.end(Profiler.TERRAIN_FILL_ARRAY, t0);
            return;
        }
        
        for (int i = 0; i < array.length; i++) {
            if (array[i] <= 0) continue;
            FunctionContext ctx = contextProvider.forIndex(i);
            Integer maxHeight = TerrainHandler.getMaxHeight(context, ctx.blockX(), ctx.blockZ());
            if (maxHeight != null && ctx.blockY() > maxHeight) {
                array[i] = AIR_DENSITY;
            }
        }
        Profiler.end(Profiler.TERRAIN_FILL_ARRAY, t0);
    }
    
    @Override
    public double minValue() { return Math.min(AIR_DENSITY, wrapped.minValue()); }
    
    @Override
    public double maxValue() { return wrapped.maxValue(); }
    
    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return new HeightConstrainedDensityFunction(wrapped.mapAll(visitor), context);
    }
    
    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() { return wrapped.codec(); }
}
