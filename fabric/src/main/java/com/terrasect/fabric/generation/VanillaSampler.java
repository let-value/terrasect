package com.terrasect.fabric.generation;

import net.minecraft.world.level.biome.Climate;

public interface VanillaSampler {
    Climate.TargetPoint terrasect$sampleVanilla(int x, int y, int z);
}
