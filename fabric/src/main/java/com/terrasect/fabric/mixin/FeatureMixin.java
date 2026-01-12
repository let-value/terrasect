package com.terrasect.fabric.mixin;

import com.terrasect.common.definition.Region;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.generation.TraversalResult;
import com.terrasect.common.generation.World;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
public class FeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void onPlace(
            WorldGenLevel level,
            ChunkGenerator generator,
            RandomSource random,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        var context = MinecraftContext.get(level.getLevel().dimension());

        if (context == null) {
            return;
        }

        TraversalResult traversal = World.traverse(context, pos.getX(), pos.getZ());
        @SuppressWarnings("unused")
        Region region = traversal != null ? traversal.region : null;
    }
}
