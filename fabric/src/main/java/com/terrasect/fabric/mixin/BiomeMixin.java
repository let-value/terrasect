package com.terrasect.fabric.mixin;

import com.terrasect.fabric.generation.FabricNarrGenContext;
import com.terrasect.common.generation.Config;
import com.terrasect.common.generation.EdgeSpice;
import com.terrasect.common.generation.Region;
import com.terrasect.common.generation.RegionField;
import com.terrasect.common.generation.Strategy;
import com.terrasect.common.generation.World;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultiNoiseBiomeSource.class)
public class BiomeMixin {

    @Shadow @Final private Either<Climate.ParameterList<Holder<Biome>>, Holder<Biome>> parameters;

    @Redirect(method = "getNoiseBiome", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Climate$Sampler;sample(III)Lnet/minecraft/world/level/biome/Climate$TargetPoint;"))
    private Climate.TargetPoint redirectSample(Climate.Sampler instance, int x, int y, int z) {
        Climate.TargetPoint original = instance.sample(x, y, z);
        
        Strategy context = FabricNarrGenContext.get(instance);
        if (context == null) {
            return original;
        }
        
        long seed = context.getSeed();

        int blockX = x << 2;
        int blockZ = z << 2;
        Region region = World.getRegion(blockX, blockZ, context);
        long regionData = RegionField.getRegionData(blockX, blockZ, seed, Config.REGION_CELL_SIZE, Config.REGION_WARP_AMPLITUDE,
            Config.REGION_POCKET_SIZE);
        float edgeDistance = RegionField.unpackEdge(regionData);

        // Remix the climate target point using the vanilla-inspired edge statistics. This keeps
        // region borders lively without calling the sampler at runtime.
        return EdgeSpice.apply(original, seed ^ region.name().hashCode(), blockX, blockZ, edgeDistance);
    }
}
