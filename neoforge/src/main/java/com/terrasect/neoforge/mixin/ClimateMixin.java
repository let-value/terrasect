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

/**
 * Mixin for Climate.Sampler that applies region-based climate modifications.
 * 
 * <p>This is a thin wrapper - all logic is in {@link ClimateHandler#modifyTargetPoint}.
 * The only mixin-specific logic is the vanilla sampling bypass via ThreadLocal.
 */
@Mixin(Climate.Sampler.class)
public class ClimateMixin implements VanillaSampler {
    
    /** Thread-local flag to bypass modifications (prevents recursion). */
    @Unique
    private static final ThreadLocal<Boolean> terrasect$wantVanilla = ThreadLocal.withInitial(() -> false);
    
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
    
    @Inject(method = "sample", at = @At("RETURN"), cancellable = true)
    private void terrasect$modifyClimate(int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
        if (terrasect$wantVanilla.get()) return;
        
        Climate.Sampler self = (Climate.Sampler)(Object)this;
        MinecraftContext context = MinecraftContext.get(self);
        if (context == null) return;
        
        Climate.TargetPoint modified = ClimateHandler.modifyTargetPoint(context, x, y, z, cir.getReturnValue());
        if (modified != cir.getReturnValue()) {
            cir.setReturnValue(modified);
        }
    }
}
