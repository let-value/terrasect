package terrasect.mixin.noise;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import terrasect.compat.ResourceKeyCompat;
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
              this.terrasect$key = ResourceKeyCompat.INSTANCE.getKeyPath(key);
            });
  }

  // Vanilla rebuilds this HolderHolder when a tree is remapped (e.g. NoiseChunk's per-chunk
  // mapAll),
  // producing a keyless `Holder.direct` copy. Stamp our captured key onto that rebuilt holder so a
  // constrained function keeps its identity everywhere it is referenced — not only as a top-level
  // NoiseRouter field but also nested inside finalDensity's spline coordinates. That is what lets a
  // continents/erosion/depth pin compose into the terrain instead of only shifting biome climate.
  @ModifyReturnValue(
      method = {"mapAll", "mapChildren"},
      at = @At("RETURN"),
      require = 0)
  private DensityFunction terrasect$propagateKey(DensityFunction rebuilt) {
    if (this.terrasect$key != null && rebuilt instanceof DensityFunctionHolderExtender ext) {
      ext.terrasect$setKey(this.terrasect$key);
    }
    return rebuilt;
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
