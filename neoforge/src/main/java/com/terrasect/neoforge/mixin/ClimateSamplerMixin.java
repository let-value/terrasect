package com.terrasect.neoforge.mixin;

import com.terrasect.neoforge.generation.NeoForgeNarrGenContext;
import com.terrasect.neoforge.generation.SamplerBypass;
import com.terrasect.common.runtime.handler.ClimateHandler;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge mixin for Climate.Sampler that applies region-based climate modifications.
 * 
 * <p>Uses {@link SamplerBypass} to prevent infinite recursion. When we need
 * vanilla climate values (for river/ridge detection), external code sets the flag
 * via {@link SamplerBypass#setWantVanilla(boolean)} before calling sample().
 */
@Mixin(Climate.Sampler.class)
public class ClimateSamplerMixin {
    
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
        if (SamplerBypass.isWantVanilla()) {
            return;
        }
        
        // Get context from this sampler instance
        Climate.Sampler self = (Climate.Sampler)(Object)this;
        NeoForgeNarrGenContext context = NeoForgeNarrGenContext.get(self);
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
