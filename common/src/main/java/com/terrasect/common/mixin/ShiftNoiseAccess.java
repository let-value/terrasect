package com.terrasect.common.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface ShiftNoiseAccess {
    DensityFunction.NoiseHolder terrasect$getOffsetNoise();
}
