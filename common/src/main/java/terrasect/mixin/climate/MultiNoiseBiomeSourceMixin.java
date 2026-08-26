package terrasect.mixin.climate;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import terrasect.extender.ChunkAccessExtender;
import terrasect.extender.ClimateParameterListExtender;
import terrasect.extender.ClimateSamplerExtender;
import terrasect.extender.MultiNoiseBiomeSourceExtender;
import terrasect.extender.NoiseChunkExtender;
import terrasect.generation.ChunkContext;
import terrasect.generation.DimensionContext;
import terrasect.handler.BiomeHandler;

@Mixin(value = MultiNoiseBiomeSource.class, priority = 0)
public abstract class MultiNoiseBiomeSourceMixin implements MultiNoiseBiomeSourceExtender {
  @Unique private DimensionContext terrasect$dimensionContext;
  @Unique private Climate.ParameterList<Holder<Biome>> terrasect$parameterList;
  @Unique private boolean terrasect$positionalLookup;

  @Accessor("parameters")
  @Override
  public abstract Either<
          Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>>
      terrasect$getParameters();

  @Invoker("parameters")
  protected abstract Climate.ParameterList<Holder<Biome>> terrasect$getParameterList();

  @Override
  public DimensionContext terrasect$getDimensionContext() {
    return this.terrasect$dimensionContext;
  }

  @Override
  public void terrasect$setDimensionContext(DimensionContext context) {
    this.terrasect$dimensionContext = context;
    if (context == null) {
      this.terrasect$positionalLookup = false;
      if (this.terrasect$parameterList != null) {
        ((ClimateParameterListExtender) (Object) this.terrasect$parameterList)
            .terrasect$clearQueryContext();
      }
      return;
    }
    this.terrasect$parameterList = this.terrasect$getParameterList();
    this.terrasect$positionalLookup =
        ((ClimateParameterListExtender) (Object) this.terrasect$parameterList)
            .terrasect$hasPositionalLookup();
  }

  @ModifyVariable(
      method =
          "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
      at = @At("HEAD"),
      argsOnly = true,
      require = 0)
  private Climate.Sampler terrasect$beginBiomeQuery(Climate.Sampler sampler) {
    if (this.terrasect$positionalLookup) {
      ((ClimateParameterListExtender) (Object) this.terrasect$parameterList)
          .terrasect$setQueryContext(
              this.terrasect$dimensionContext, terrasect$getChunkContext(sampler));
    }
    return sampler;
  }

  @Redirect(
      method =
          "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/biome/MultiNoiseBiomeSource;getNoiseBiome(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Lnet/minecraft/core/Holder;"))
  private Holder<Biome> terrasect$selectBiome(
      MultiNoiseBiomeSource self,
      Climate.TargetPoint targetPoint,
      int quartX,
      int quartY,
      int quartZ,
      Climate.Sampler sampler) {
    var context = ((MultiNoiseBiomeSourceExtender) self).terrasect$getDimensionContext();
    if (context == null) {
      return self.getNoiseBiome(targetPoint);
    }
    var base = self.getNoiseBiome(targetPoint);
    if (this.terrasect$positionalLookup) {
      ((ClimateParameterListExtender) (Object) this.terrasect$parameterList)
          .terrasect$clearQueryContext();
      return base;
    }
    return BiomeHandler.selectBiome(
        context, terrasect$getChunkContext(sampler), quartX, quartZ, targetPoint, base);
  }

  @Unique
  private ChunkContext terrasect$getChunkContext(Climate.Sampler sampler) {
    var samplerExtender = (ClimateSamplerExtender) (Object) sampler;
    NoiseChunkExtender noiseChunk = samplerExtender.terrasect$getNoiseChunk();
    if (noiseChunk == null) {
      return null;
    }
    ChunkAccessExtender chunk = noiseChunk.terrasect$getChunk();
    return chunk == null ? null : chunk.terrasect$getContext();
  }
}
