package terrasect.mixin.climate;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import terrasect.extender.ClimateParameterListExtender;
import terrasect.generation.DimensionContext;
import terrasect.handler.BiomeHandler;

@Mixin(value = Climate.ParameterList.class, priority = 1100)
public class ClimateParameterListMixin implements ClimateParameterListExtender {
  @Unique
  private static final ThreadLocal<DimensionContext> terrasect$dimensionContext =
      new ThreadLocal<>();

  @Override
  public void terrasect$setDimensionContext(DimensionContext context) {
    if (context == null) {
      terrasect$dimensionContext.remove();
    } else {
      terrasect$dimensionContext.set(context);
    }
  }

  @ModifyReturnValue(
      method =
          "findValuePositional(Lnet/minecraft/world/level/biome/Climate$TargetPoint;III)Ljava/lang/Object;",
      at = @At("RETURN"),
      require = 0,
      remap = false)
  private Object terrasect$filterPositionalBiome(
      Object base, Climate.TargetPoint targetPoint, int quartX, int quartY, int quartZ) {
    var context = terrasect$dimensionContext.get();
    if (context == null) {
      return base;
    }
    try {
      @SuppressWarnings("unchecked")
      var biome = (Holder<Biome>) base;
      return BiomeHandler.selectBiome(context, quartX, quartZ, targetPoint, biome);
    } finally {
      terrasect$dimensionContext.remove();
    }
  }
}
