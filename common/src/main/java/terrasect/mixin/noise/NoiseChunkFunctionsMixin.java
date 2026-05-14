package terrasect.mixin.noise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.extender.DensityFunctionHolderExtender;
import terrasect.extender.NoiseChunkExtender;
import terrasect.generation.ChunkContext;
import terrasect.handler.NoiseHandler;

@Mixin(NoiseChunk.class)
public class NoiseChunkFunctionsMixin {
  @Inject(method = "wrap", at = @At("HEAD"), cancellable = true)
  private void terrasect$keepKeyedHolders(
      DensityFunction densityFunction, CallbackInfoReturnable<DensityFunction> cir) {
    if (densityFunction instanceof DensityFunctions.HolderHolder) {
      cir.setReturnValue(densityFunction);
    }
  }

  @Inject(method = "wrapNew", at = @At("HEAD"), cancellable = true)
  private void terrasect$wrapKeyedHolderAtCreation(
      DensityFunction densityFunction, CallbackInfoReturnable<DensityFunction> cir) {
    if (!(densityFunction instanceof DensityFunctionHolderExtender holder)) {
      return;
    }
    var key = holder.terrasect$getKey();
    var chunk = ((NoiseChunkExtender) this).terrasect$getChunk();
    ChunkContext context = chunk == null ? null : chunk.terrasect$getContext();
    cir.setReturnValue(NoiseHandler.wrapDensity(densityFunction, key, context));
  }

  @WrapOperation(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
  private NoiseRouter terrasect$attachChunkToDensityFunctions(
      NoiseRouter router, DensityFunction.Visitor visitor, Operation<NoiseRouter> original) {
    var result = original.call(router, visitor);
    var chunk = ((NoiseChunkExtender) this).terrasect$getChunk();
    ChunkContext context = chunk == null ? null : chunk.terrasect$getContext();
    return NoiseHandler.wrapNoiseRouter(result, context);
  }
}
