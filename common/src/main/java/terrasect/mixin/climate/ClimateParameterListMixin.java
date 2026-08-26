package terrasect.mixin.climate;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import terrasect.extender.ClimateParameterListExtender;
import terrasect.generation.ChunkContext;
import terrasect.generation.DimensionContext;
import terrasect.handler.BiomeHandler;

@Mixin(value = Climate.ParameterList.class, priority = 1100)
public class ClimateParameterListMixin implements ClimateParameterListExtender {
  @Unique
  private static final boolean terrasect$POSITIONAL_LOOKUP = terrasect$findPositionalLookup();

  @Unique
  private static final ThreadLocal<DimensionContext> terrasect$dimensionContext =
      new ThreadLocal<>();

  @Unique
  private static final ThreadLocal<ChunkContext> terrasect$chunkContext = new ThreadLocal<>();

  @Unique
  private static boolean terrasect$findPositionalLookup() {
    try {
      Climate.ParameterList.class.getMethod(
          "findValuePositional", Climate.TargetPoint.class, int.class, int.class, int.class);
      return true;
    } catch (NoSuchMethodException exception) {
      return false;
    }
  }

  @Override
  public boolean terrasect$hasPositionalLookup() {
    return terrasect$POSITIONAL_LOOKUP;
  }

  @Override
  public void terrasect$setQueryContext(DimensionContext context, ChunkContext chunkContext) {
    terrasect$dimensionContext.set(context);
    terrasect$chunkContext.set(chunkContext);
  }

  @Override
  public void terrasect$clearQueryContext() {
    terrasect$dimensionContext.remove();
    terrasect$chunkContext.remove();
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
      return BiomeHandler.selectBiome(
          context, terrasect$chunkContext.get(), quartX, quartZ, targetPoint, biome);
    } finally {
      terrasect$clearQueryContext();
    }
  }
}
