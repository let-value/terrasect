package com.terrasect.fabric.mixin;

import com.terrasect.fabric.generation.MinecraftContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
 * Mixin for ServerLevel that registers the narrative generation context
 * BEFORE spawn finding occurs during world initialization.
 * 
 * This injects at HEAD of the constructor because spawn finding happens
 * DURING the constructor and needs the context to be registered first.
 * Note: HEAD injection must be static since 'this' is not available before super().
 */
@Mixin(ServerLevel.class)
public class LevelMixin {

    @Inject(method = "<init>", at = @At("HEAD"))
    private static void onInitHead(MinecraftServer minecraftServer, Executor executor, LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData, ResourceKey<Level> resourceKey, LevelStem levelStem, boolean bl, long seed, List<CustomSpawner> list, boolean bl2, @Nullable RandomSequences randomSequences, CallbackInfo ci) {
        ChunkGenerator generator = levelStem.generator();
        BiomeSource biomeSource = generator.getBiomeSource();

        // Only process overworld with MultiNoiseBiomeSource
        if (resourceKey == Level.OVERWORLD && biomeSource instanceof MultiNoiseBiomeSource multiNoise) {
            if (generator instanceof NoiseBasedChunkGenerator) {
                // Get noise settings from registry (use OVERWORLD preset for overworld)
                NoiseGeneratorSettings settings;
                try {
                    settings = minecraftServer.registryAccess()
                        .lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.OVERWORLD)
                        .value();
                } catch (Exception e) {
                    // Fallback to dummy settings if registry lookup fails
                    settings = NoiseGeneratorSettings.dummy();
                }
                
                // Get noise registry from server
                HolderGetter<NormalNoise.NoiseParameters> noiseParams = 
                    minecraftServer.registryAccess().lookupOrThrow(Registries.NOISE);
                
                // Create RandomState early (before spawn finding)
                RandomState randomState = RandomState.create(settings, noiseParams, seed);
                Climate.Sampler sampler = randomState.sampler();
                
                // Get biome parameters
                Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters = 
                    ((MultiNoiseBiomeSourceAccessor) multiNoise).getParameters();
                
                // Register early - before spawn finding!
                MinecraftContext context = MinecraftContext.create(resourceKey, seed, sampler, parameters);
                MinecraftContext.register(resourceKey, context);
            }
        }
    }
}
