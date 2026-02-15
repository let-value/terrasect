package terrasect.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import terrasect.MultiNoiseBiomeSourceExtender;

@Mixin(MultiNoiseBiomeSource.class)
public interface MultiNoiseBiomeSourceMixin extends MultiNoiseBiomeSourceExtender {

  @Accessor("parameters")
  @Override
  Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>>
      terrasect$getParameters();
}
