package com.terrasect.neoforge.mixin;

import com.terrasect.common.handler.LevelHandler;
import com.terrasect.common.mixin.MultiNoiseBiomeSourceAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.RandomSequences;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * NeoForge mixin for ServerLevel that registers the generation context.
 * 
 * <p>Injects before the first {@code ensureStructuresGenerated()} call during construction,
 * so other worldgen mixins can safely rely on the context being available.
 * All logic is in {@link LevelHandler}.
 */
@Mixin(ServerLevel.class)
public class LevelMixin {

    @Inject(method = "<init>", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerChunkCache;getGeneratorState()Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;",
            ordinal = 0,
            shift = At.Shift.BEFORE
    ))
    private void onInit(MinecraftServer server, Executor executor, 
            LevelStorageSource.LevelStorageAccess storage, ServerLevelData levelData, 
            ResourceKey<Level> dimension, LevelStem levelStem, 
            boolean bl, long seed, List<CustomSpawner> spawners, boolean bl2, 
            @Nullable RandomSequences randomSequences,
            CallbackInfo ci) {
        
        ServerLevel level = (ServerLevel) (Object) this;
        var generator = level.getChunkSource().getGenerator();
        var biomeSource = generator.getBiomeSource();

        if (biomeSource instanceof MultiNoiseBiomeSource multiNoise
                && generator instanceof NoiseBasedChunkGenerator) {
            
            var parameters = ((MultiNoiseBiomeSourceAccessor) multiNoise).terrasect$getParameters();
            var sampler = level.getChunkSource().randomState().sampler();
            
            LevelHandler.registerContext(dimension, seed, sampler, parameters);
        }
    }
}
