package com.terrasect.fabric.mixin;

import com.terrasect.common.handler.LevelHandler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Mixin for ServerLevel that registers the generation context.
 * 
 * <p>Injects at RETURN after the level is fully constructed.
 * If biome/climate lookups happen during construction (e.g., spawn finding),
 * they gracefully fall back to vanilla behavior. All logic is in {@link LevelHandler}.
 */
@Mixin(ServerLevel.class)
public class LevelMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(MinecraftServer server, Executor executor, 
            LevelStorageSource.LevelStorageAccess storage, ServerLevelData levelData, 
            ResourceKey<Level> dimension, LevelStem levelStem, boolean bl, long seed, 
            List<CustomSpawner> spawners, boolean bl2, @Nullable RandomSequences randomSequences, 
            CallbackInfo ci) {
        
        ServerLevel level = (ServerLevel) (Object) this;
        var generator = level.getChunkSource().getGenerator();
        var biomeSource = generator.getBiomeSource();
        
        if (biomeSource instanceof MultiNoiseBiomeSource multiNoise 
                && generator instanceof NoiseBasedChunkGenerator) {
            
            var parameters = ((MultiNoiseBiomeSourceAccessor) multiNoise).getParameters();
            var sampler = level.getChunkSource().randomState().sampler();
            
            LevelHandler.registerContext(dimension, seed, sampler, parameters);
        }
    }
}
