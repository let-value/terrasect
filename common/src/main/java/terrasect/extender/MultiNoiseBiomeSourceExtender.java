package terrasect.extender;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import terrasect.generation.DimensionContext;

public interface MultiNoiseBiomeSourceExtender {

  Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>>
      terrasect$getParameters();

  DimensionContext terrasect$getDimensionContext();

  void terrasect$setDimensionContext(DimensionContext context);
}
