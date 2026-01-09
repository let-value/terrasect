package com.terrasect.fabric.mixin;

import com.terrasect.common.mixin.ClimateTargetPointAccessor;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Climate.TargetPoint.class)
public interface ClimateTargetPointMixin extends ClimateTargetPointAccessor {
    @Accessor("temperature")
    @Mutable
    @Override
    void terrasect$setTemperature(long temperature);

    @Accessor("humidity")
    @Mutable
    @Override
    void terrasect$setHumidity(long humidity);

    @Accessor("continentalness")
    @Mutable
    @Override
    void terrasect$setContinentalness(long continentalness);

    @Accessor("erosion")
    @Mutable
    @Override
    void terrasect$setErosion(long erosion);

    @Accessor("depth")
    @Mutable
    @Override
    void terrasect$setDepth(long depth);

    @Accessor("weirdness")
    @Mutable
    @Override
    void terrasect$setWeirdness(long weirdness);
}

