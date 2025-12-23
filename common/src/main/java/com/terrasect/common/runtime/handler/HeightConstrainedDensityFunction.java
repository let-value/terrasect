package com.terrasect.common.runtime.handler;

import com.terrasect.common.devtools.Profiler;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps finalDensity() to constrain terrain height by returning air above maxHeight.
 * 
 * <p>Uses pre-computed {@link TerrainHeightLookup} for O(1) height lookups,
 * avoiding repeated region traversal during terrain generation.
 */
public record HeightConstrainedDensityFunction(
    DensityFunction wrapped,
    @Nullable TerrainHeightLookup lookup
) implements DensityFunction {
    
    private static final double AIR_DENSITY = -1.0;
    
    @Override
    public double compute(FunctionContext ctx) {
        long t0 = Profiler.begin();
        double density = wrapped.compute(ctx);
        
        // Fast path: no constraints in this chunk or density already air
        if (lookup == null || density <= 0) {
            Profiler.end(Profiler.TERRAIN_DENSITY_COMPUTE, t0);
            return density;
        }
        
        int maxHeight = lookup.getMaxHeight(ctx.blockX(), ctx.blockZ());
        Profiler.end(Profiler.TERRAIN_DENSITY_COMPUTE, t0);
        
        // Check if this position is above the height constraint
        return (maxHeight != TerrainHeightLookup.NO_CONSTRAINT && ctx.blockY() > maxHeight) 
            ? AIR_DENSITY : density;
    }
    
    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        long t0 = Profiler.begin();
        wrapped.fillArray(array, contextProvider);
        
        // Fast path: no constraints in this chunk
        if (lookup == null) {
            Profiler.end(Profiler.TERRAIN_FILL_ARRAY, t0);
            return;
        }
        
        for (int i = 0; i < array.length; i++) {
            if (array[i] <= 0) continue;
            
            FunctionContext ctx = contextProvider.forIndex(i);
            int maxHeight = lookup.getMaxHeight(ctx.blockX(), ctx.blockZ());
            
            if (maxHeight != TerrainHeightLookup.NO_CONSTRAINT && ctx.blockY() > maxHeight) {
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
        return new HeightConstrainedDensityFunction(wrapped.mapAll(visitor), lookup);
    }
    
    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() { return wrapped.codec(); }
}
