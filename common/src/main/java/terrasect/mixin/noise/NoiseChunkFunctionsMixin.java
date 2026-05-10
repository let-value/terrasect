package terrasect.mixin.noise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import terrasect.extender.NoiseChunkExtender;
import terrasect.generation.ChunkContext;
import terrasect.handler.NoiseHandler;

@Mixin(NoiseChunk.class)
public class NoiseChunkFunctionsMixin {
  private static final Logger LOGGER =
      LoggerFactory.getLogger("Terrasect/NoiseChunkFunctionsMixin");

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

    var chunkExtender = ((NoiseChunkExtender) this).terrasect$getChunk();
    if (chunkExtender == null) {
      LOGGER.warn(
          "[NC-Mixin] NoiseChunkFunctionsMixin fired but pendingChunk is null — constraints skipped (iterateNoiseColumn path or missing set)");
      return result;
    }

    ChunkContext context = chunkExtender.terrasect$getContext();
    if (context == null) {
      LOGGER.warn(
          "[NC-Mixin] NoiseChunkFunctionsMixin fired but ChunkContext is null — constraints skipped");
      return result;
    }

    LOGGER.info(
        "[NC-Mixin] NoiseChunkFunctionsMixin wrapping router — constraints will be applied");
    return NoiseHandler.wrapNoiseRouter(result, context);
  }
}
