package terrasect.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import terrasect.MultiNoiseBiomeSourceAccessor;

@Mixin(MultiNoiseBiomeSource.class)
public interface MultiNoiseBiomeSourceMixin extends MultiNoiseBiomeSourceAccessor {

    @Accessor("parameters")
    @Override
    Either<Climate.ParameterList<@NotNull Holder<@NotNull Biome>>, Holder<@NotNull MultiNoiseBiomeSourceParameterList>> terrasect$getParameters();
}
