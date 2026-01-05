package com.terrasect.common.compat;

import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Implemented via mixin on vanilla shift density functions to expose their backing {@link DensityFunction.NoiseHolder}
 * without referencing package-private Minecraft types.
 */
public interface ShiftNoiseAccess {
    DensityFunction.NoiseHolder terrasect$getOffsetNoise();
}

