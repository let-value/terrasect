package com.terrasect.common.runtime.handler;

import com.terrasect.common.devtools.Profiler;
import com.terrasect.common.generation.MinecraftContext;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Wraps preliminarySurfaceLevel() to clamp surface level to maxHeight.
 * Ensures aquifer fills water correctly above constrained terrain.
 * Self-contained - receives MinecraftContext at construction time from NoiseChunkMixin.
 */
public record HeightConstrainedSurfaceLevel(
    DensityFunction wrapped,
    MinecraftContext context
) implements DensityFunction {
    
    @Override
    public double compute(FunctionContext ctx) {
        long t0 = Profiler.begin();
        double surface = wrapped.compute(ctx);
        if (context == null) {
            Profiler.end(Profiler.TERRAIN_DENSITY_COMPUTE, t0);
            return surface;
        }
        
        Integer maxHeight = TerrainHandler.getMaxHeight(context, ctx.blockX(), ctx.blockZ());
        Profiler.end(Profiler.TERRAIN_DENSITY_COMPUTE, t0);
        return (maxHeight != null && surface > maxHeight) ? maxHeight : surface;
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
            FunctionContext ctx = contextProvider.forIndex(i);
            Integer maxHeight = TerrainHandler.getMaxHeight(context, ctx.blockX(), ctx.blockZ());
            if (maxHeight != null && array[i] > maxHeight) {
                array[i] = maxHeight;
            }
        }
        Profiler.end(Profiler.TERRAIN_FILL_ARRAY, t0);
    }
    
    @Override
    public double minValue() { return wrapped.minValue(); }
    
    @Override
    public double maxValue() { return wrapped.maxValue(); }
    
    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return new HeightConstrainedSurfaceLevel(wrapped.mapAll(visitor), context);
    }
    
    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() { return wrapped.codec(); }
}
