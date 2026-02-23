package terrasect.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.NoiseChunkExtender;
import terrasect.RegionAwareDensityFunction;

@Mixin(NoiseChunk.class)
public class NoiseChunkWrapMixin {

  @Inject(method = "wrapNew", at = @At("RETURN"), cancellable = true)
  private void terrasect$wrapDensityFunction(
      DensityFunction densityFunction, CallbackInfoReturnable<DensityFunction> cir) {
    if (!(densityFunction instanceof DensityFunctions.HolderHolder holderHolder)) {
      return;
    }

    var keyOpt = holderHolder.function().unwrapKey();
    if (keyOpt.isEmpty()) {
      return;
    }

    var key = keyOpt.get();
    var result = cir.getReturnValue();
    cir.setReturnValue(
        new RegionAwareDensityFunction(result, key, (NoiseChunkExtender) (Object) this));
  }
}
