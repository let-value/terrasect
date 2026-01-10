package com.terrasect.common.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

/**
 * Mixin-backed accessor interface for {@code MultiNoiseBiomeSource}.
 *
 * <p>Implemented via loader mixins to expose the parameters field without
 * referencing loader-specific mixin infrastructure from common code.
 */
public interface MultiNoiseBiomeSourceAccessor {
    /**
     * Access the parameters field.
     * Left: Direct parameter list
     * Right: Holder to a parameter list preset (e.g., OVERWORLD, NETHER)
     */
    Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> terrasect$getParameters();
}
