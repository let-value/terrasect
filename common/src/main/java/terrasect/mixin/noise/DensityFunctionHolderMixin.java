package terrasect.mixin.noise;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import terrasect.extender.DensityFunctionHolderExtender;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$HolderHolder")
public class DensityFunctionHolderMixin implements DensityFunctionHolderExtender {

  @Unique private String terrasect$key;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void terrasect$captureKey(Holder<DensityFunction> function, CallbackInfo ci) {
    function
        .unwrapKey()
        .ifPresent(
            key -> {
              this.terrasect$key = key.identifier().getPath();
            });
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
