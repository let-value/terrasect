package com.terrasect.neoforge.mixin;

import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.handler.BiomeHandler;
import com.terrasect.common.mixin.MultiNoiseBiomeSourceAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * NeoForge mixin for MultiNoiseBiomeSource that applies region-based biome filtering.
 * 
 * <p>This is a thin wrapper - all logic is in {@link BiomeHandler#selectBiome}.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class BiomeMixin {

    @Redirect(
        method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/MultiNoiseBiomeSource;getNoiseBiome(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Lnet/minecraft/core/Holder;"
        )
    )
    private Holder<Biome> terrasect$filterBiome(
            MultiNoiseBiomeSource self,
            Climate.TargetPoint targetPoint,
            int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        
        MinecraftContext context = MinecraftContext.get(sampler);
        if (context == null) {
            return ((MultiNoiseBiomeSourceAccessor) self).terrasect$getParameters().map(
                list -> list,
                holder -> holder.value().parameters()
            ).findValue(targetPoint);
        }
        
        return BiomeHandler.selectBiome(context, quartX, quartZ, targetPoint);
    }
}
