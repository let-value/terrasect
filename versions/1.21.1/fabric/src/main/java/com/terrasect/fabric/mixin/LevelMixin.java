package com.terrasect.fabric.mixin;

import com.terrasect.fabric.generation.MinecraftContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import com.mojang.datafixers.util.Either;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Fabric mixin for ServerLevel (MC 1.21.1 version).
 * 
 * <p>This version includes the ChunkProgressListener parameter that was present
 * in MC 1.21.1 but removed in MC 1.21.11.
 * 
 * <p>Registers the narrative generation context when a level is initialized.
 */
@Mixin(ServerLevel.class)
public class LevelMixin {

    @Inject(method = "<init>", at = @At("HEAD"))
    private static void onInitHead(MinecraftServer minecraftServer, Executor executor, 
            LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData, 
            ResourceKey<Level> resourceKey, LevelStem levelStem,
            ChunkProgressListener chunkProgressListener,  // Present in 1.21.1, removed in 1.21.11
            boolean bl, long seed, List<CustomSpawner> list, boolean bl2, 
            @Nullable RandomSequences randomSequences, 
            CallbackInfo ci) {
        
        var generator = levelStem.generator();
        var biomeSource = generator.getBiomeSource();

        
        if (biomeSource instanceof MultiNoiseBiomeSource multiNoise 
                && generator instanceof NoiseBasedChunkGenerator) {
            
            
            var settings = getNoiseSettings(minecraftServer, resourceKey);
            
            
            var noiseParams = minecraftServer.registryAccess().lookupOrThrow(Registries.NOISE);
            
            
            var randomState = RandomState.create(settings, noiseParams, seed);
            var sampler = randomState.sampler();
            
            
            var parameters = ((MultiNoiseBiomeSourceAccessor) multiNoise).getParameters();
            
            
            MinecraftContext.create(resourceKey, seed, sampler, parameters);
        }
        
        
    }
    
    /**
     * Get the appropriate noise settings for a dimension.
     * 
     * @param server The Minecraft server
     * @param dimension The dimension resource key
     * @return NoiseGeneratorSettings for this dimension
     */
    private static NoiseGeneratorSettings getNoiseSettings(MinecraftServer server, ResourceKey<Level> dimension) {
        try {
            var noiseSettingsRegistry = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
            
            
            if (dimension == Level.OVERWORLD) {
                return noiseSettingsRegistry.getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
            } else if (dimension == Level.NETHER) {
                return noiseSettingsRegistry.getOrThrow(NoiseGeneratorSettings.NETHER).value();
            } else if (dimension == Level.END) {
                return noiseSettingsRegistry.getOrThrow(NoiseGeneratorSettings.END).value();
            }
            
            
            
            return noiseSettingsRegistry.getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        } catch (Exception e) {
            
            return NoiseGeneratorSettings.dummy();
        }
    }
}
