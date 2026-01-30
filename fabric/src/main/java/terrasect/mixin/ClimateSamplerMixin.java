package terrasect.mixin;

import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.generation.Context;

@Mixin(Climate.Sampler.class)
public class ClimateSamplerMixin {

  @Unique
  private static final ThreadLocal<Boolean> terrasect$wantVanilla =
      ThreadLocal.withInitial(() -> false);

  @Inject(method = "sample", at = @At("RETURN"))
  private void terrasect$modifyClimate(
      int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {

    var self = (Climate.Sampler) (Object) this;

    var context = Context.Companion.get(self);

    // ClimateHandler.modifyTargetPoint(context, x, y, z, cir.getReturnValue());
  }
}
