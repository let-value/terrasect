package terrasect.mixin;

import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.VanillaSamplerAccessor;
import terrasect.generation.Context;

@Mixin(Climate.Sampler.class)
public class ClimateSamplerMixin implements VanillaSamplerAccessor {

  @Unique
  private static final ThreadLocal<Boolean> terrasect$wantVanilla =
      ThreadLocal.withInitial(() -> false);

  @Override
  @Unique
  public Climate.TargetPoint terrasect$sampleVanilla(int x, int y, int z) {
    terrasect$wantVanilla.set(true);
    try {
      return ((Climate.Sampler) (Object) this).sample(x, y, z);
    } finally {
      terrasect$wantVanilla.set(false);
    }
  }

  @Inject(method = "sample", at = @At("RETURN"))
  private void terrasect$modifyClimate(
      int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
    if (terrasect$wantVanilla.get()) return;

    var self = (Climate.Sampler) (Object) this;

    var context = Context.Companion.get(self);

    // ClimateHandler.modifyTargetPoint(context, x, y, z, cir.getReturnValue());
  }
}
