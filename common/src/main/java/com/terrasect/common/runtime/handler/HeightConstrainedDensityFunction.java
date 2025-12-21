package com.terrasect.common.runtime.handler;

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
        double density = wrapped.compute(ctx);
        if (density <= 0 || context == null) return density;
        
        Integer maxHeight = TerrainHandler.getMaxHeight(context, ctx.blockX(), ctx.blockZ());
        return (maxHeight != null && ctx.blockY() > maxHeight) ? AIR_DENSITY : density;
    }
    
    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        wrapped.fillArray(array, contextProvider);
        if (context == null) return;
        
        for (int i = 0; i < array.length; i++) {
            if (array[i] <= 0) continue;
            FunctionContext ctx = contextProvider.forIndex(i);
            Integer maxHeight = TerrainHandler.getMaxHeight(context, ctx.blockX(), ctx.blockZ());
            if (maxHeight != null && ctx.blockY() > maxHeight) {
                array[i] = AIR_DENSITY;
            }
        }
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
