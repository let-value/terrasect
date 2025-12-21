package com.terrasect.neoforge.mixin;

import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.generation.VanillaSampler;
import com.terrasect.common.runtime.handler.ClimateHandler;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Climate.Sampler.class)
public class ClimateMixin implements VanillaSampler {
    
    /**
     * Thread-local flag to indicate we want vanilla (unmodified) values.
     * When true, the mixin exits early without modifications.
     */
    @Unique
    private static final ThreadLocal<Boolean> terrasect$wantVanilla = ThreadLocal.withInitial(() -> false);
    
    /**
     * Get vanilla climate sample without our modifications.
     * This is called via the VanillaSampler interface.
     */
    @Override
    @Unique
    public Climate.TargetPoint terrasect$sampleVanilla(int x, int y, int z) {
        terrasect$wantVanilla.set(true);
        try {
            return ((Climate.Sampler)(Object)this).sample(x, y, z);
        } finally {
            terrasect$wantVanilla.set(false);
        }
    }
    
    /**
     * Inject at the end of sample() to modify the climate values.
     */
    @Inject(
        method = "sample",
        at = @At("RETURN"),
        cancellable = true
    )
    private void terrasect$modifyClimate(int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
        // Exit early if vanilla values requested (prevents recursion)
        if (terrasect$wantVanilla.get()) {
            return;
        }
        
        // Get context from this sampler instance
        Climate.Sampler self = (Climate.Sampler)(Object)this;
        MinecraftContext context = MinecraftContext.get(self);
        if (context == null) {
            return;
        }
        
        Climate.TargetPoint original = cir.getReturnValue();
        
        ClimateHandler.ClimateResult result = ClimateHandler.modifyClimate(
            context, x, y, z,
            original.temperature(),
            original.humidity()
        );
        
        // If modified, replace the return value
        if (result.modified()) {
            cir.setReturnValue(new Climate.TargetPoint(
                result.temperature(),
                result.humidity(),
                original.continentalness(),
                original.erosion(),
                original.depth(),
                original.weirdness()
            ));
        }
    }
}
