package terrasect.mixin.noise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import terrasect.extender.NoiseChunkExtender;
import terrasect.handler.NoiseHandler;

@Mixin(NoiseChunk.class)
public class NoiseChunkFunctionsMixin {
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

    var context = ((NoiseChunkExtender) this).terrasect$getChunk().terrasect$getContext();
    return NoiseHandler.wrapNoiseRouter(result, context);
  }
}
