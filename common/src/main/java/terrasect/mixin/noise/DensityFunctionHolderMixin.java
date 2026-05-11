package terrasect.mixin.noise;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.extender.DensityFunctionHolderExtender;
import terrasect.handler.NoiseHandler;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$HolderHolder")
public class DensityFunctionHolderMixin implements DensityFunctionHolderExtender {

  @Unique
  private static final Logger TERRASECT_LOGGER =
      LoggerFactory.getLogger("Terrasect/DensityFunctionHolderMixin");

  @Unique private static int terrasect$keyCaptureLogCount;
  @Unique private static int terrasect$missingChunkLogCount;

  @Unique private String terrasect$key;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void terrasect$captureKey(Holder<DensityFunction> function, CallbackInfo ci) {
    function
        .unwrapKey()
        .ifPresent(
            key -> {
              this.terrasect$key = key.identifier().getPath();
              if (terrasect$keyCaptureLogCount < 24) {
                terrasect$keyCaptureLogCount++;
                TERRASECT_LOGGER.info(
                    "[NC-HolderKey] captured density holder key={}", this.terrasect$key);
              }
            });
  }

  @Inject(method = "compute", at = @At("RETURN"), cancellable = true)
  private void terrasect$modifyKeyedValue(
      DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
    if (this.terrasect$key == null) {
      return;
    }
    var chunk = NoiseHandler.currentChunk.get();
    if (chunk == null) {
      if (terrasect$missingChunkLogCount < 24) {
        terrasect$missingChunkLogCount++;
        TERRASECT_LOGGER.info(
            "[NC-HolderKey] skipped keyed value key={} because chunk context is missing",
            this.terrasect$key);
      }
      return;
    }
    var value =
        NoiseHandler.modifyDensityValue(
            this.terrasect$key,
            cir.getReturnValueD(),
            context.blockX(),
            context.blockY(),
            context.blockZ(),
            chunk);
    if (value != null) {
      cir.setReturnValue(value);
    }
  }

  @Inject(method = "fillArray", at = @At("RETURN"))
  private void terrasect$modifyKeyedArray(
      double[] values, DensityFunction.ContextProvider contextProvider, CallbackInfo ci) {
    if (this.terrasect$key == null) {
      return;
    }
    var chunk = NoiseHandler.currentChunk.get();
    if (chunk == null) {
      if (terrasect$missingChunkLogCount < 24) {
        terrasect$missingChunkLogCount++;
        TERRASECT_LOGGER.info(
            "[NC-HolderKey] skipped keyed value key={} because chunk context is missing",
            this.terrasect$key);
      }
      return;
    }
    for (int i = 0; i < values.length; i++) {
      var context = contextProvider.forIndex(i);
      var value =
          NoiseHandler.modifyDensityValue(
              this.terrasect$key,
              values[i],
              context.blockX(),
              context.blockY(),
              context.blockZ(),
              chunk);
      if (value != null) {
        values[i] = value;
      }
    }
  }

  @ModifyArg(
      method = "mapAll",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;apply(Lnet/minecraft/world/level/levelgen/DensityFunction;)Lnet/minecraft/world/level/levelgen/DensityFunction;"))
  private DensityFunction terrasect$propagateKey(DensityFunction newHolder) {
    if (this.terrasect$key != null && newHolder instanceof DensityFunctionHolderExtender ext) {
      ext.terrasect$setKey(this.terrasect$key);
    }
    return newHolder;
  }

  @Override
  public String terrasect$getKey() {
    return this.terrasect$key;
  }

  @Override
  public void terrasect$setKey(String key) {
    this.terrasect$key = key;
  }
}
