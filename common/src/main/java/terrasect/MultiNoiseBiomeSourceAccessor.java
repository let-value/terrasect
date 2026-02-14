package terrasect;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.jetbrains.annotations.NotNull;

public interface MultiNoiseBiomeSourceAccessor {

  Either<
          Climate.ParameterList<@NotNull Holder<@NotNull Biome>>,
          Holder<@NotNull MultiNoiseBiomeSourceParameterList>>
      terrasect$getParameters();
}
