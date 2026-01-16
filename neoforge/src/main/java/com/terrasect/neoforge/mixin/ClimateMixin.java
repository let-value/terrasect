package com.terrasect.neoforge.mixin;

import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.handler.ClimateHandler;
import com.terrasect.common.mixin.VanillaSamplerAccessor;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Climate.Sampler.class)
public class ClimateMixin implements VanillaSamplerAccessor {

  @Unique private static final ThreadLocal<Boolean> terrasect$wantVanilla =
      ThreadLocal.withInitial(() -> false);

  @Override
  @Unique public Climate.TargetPoint terrasect$sampleVanilla(int x, int y, int z) {
    terrasect$wantVanilla.set(true);
    try {
      return ((Climate.Sampler) (Object) this).sample(x, y, z);
    } finally {
      terrasect$wantVanilla.set(false);
    }
  }

  @Inject(method = "sample", at = @At("RETURN"), cancellable = true)
  private void terrasect$modifyClimate(
      int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
    if (terrasect$wantVanilla.get()) return;

    var self = (Climate.Sampler) (Object) this;
    var context = MinecraftContext.get(self);
    assert context != null : "MinecraftContext not found in Climate.Sampler";

    ClimateHandler.modifyTargetPoint(context, x, y, z, cir.getReturnValue());
  }
}
