package com.terrasect.neoforge.mixin;

import com.terrasect.common.generation.MinecraftContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.RandomSequences;
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
 * NeoForge mixin for ServerLevel (MC 1.21.1 version).
 * 
 * <p>This version includes the ChunkProgressListener parameter that was present
 * in MC 1.21.1 but removed in MC 1.21.11.
 * 
 * <p>Registers the narrative generation context when a level is initialized.
 */
@Mixin(ServerLevel.class)
public class LevelMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(MinecraftServer minecraftServer, Executor executor, 
            LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData, 
            ResourceKey<Level> resourceKey, LevelStem levelStem,
            ChunkProgressListener chunkProgressListener,  // Added in 1.21.1, removed in 1.21.11
            boolean bl, long seed, List<CustomSpawner> list, boolean bl2, 
            @Nullable RandomSequences randomSequences,
            CallbackInfo ci) {
        
        ServerLevel level = (ServerLevel) (Object) this;
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();

        // Only process dimensions with MultiNoiseBiomeSource and NoiseBasedChunkGenerator
        if (biomeSource instanceof MultiNoiseBiomeSource multiNoise
                && generator instanceof NoiseBasedChunkGenerator noiseGen) {
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters = 
                ((MultiNoiseBiomeSourceAccessor) multiNoise).getParameters();
            
            // Get the sampler from the chunk source's random state
            Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
            
            MinecraftContext.create(resourceKey, seed, sampler, parameters);
        }
        // Note: TheEndBiomeSource and other biome sources don't use climate sampling
        // and don't need context registration - they have fixed biome placement
    }
}
