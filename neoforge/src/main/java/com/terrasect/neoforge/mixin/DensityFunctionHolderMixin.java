package com.terrasect.neoforge.mixin;

import com.terrasect.common.handler.NoiseHandler;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$HolderHolder")
public class DensityFunctionHolderMixin {

  @Shadow @Final private Holder<DensityFunction> function;

  @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
  private void terrasect$applyDensityConstraints(
      DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
    var original = function.value().compute(context);
    if (!(function instanceof Holder.Reference<DensityFunction> ref)) {
      cir.setReturnValue(original);
      return;
    }

    var adjusted = NoiseHandler.sampleDensityFunction(ref.key(), original, context);
    cir.setReturnValue(adjusted);
  }
}
