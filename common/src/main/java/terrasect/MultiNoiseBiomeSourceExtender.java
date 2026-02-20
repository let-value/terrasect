package terrasect;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

public interface MultiNoiseBiomeSourceExtender {

  Either<
          Climate.ParameterList<Holder<Biome>>,
          Holder<MultiNoiseBiomeSourceParameterList>>
      terrasect$getParameters();
}
