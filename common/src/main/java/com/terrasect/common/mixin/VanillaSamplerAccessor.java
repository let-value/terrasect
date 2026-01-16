package com.terrasect.common.mixin;

import net.minecraft.world.level.biome.Climate;

public interface VanillaSamplerAccessor {
  Climate.TargetPoint terrasect$sampleVanilla(int x, int y, int z);
}
