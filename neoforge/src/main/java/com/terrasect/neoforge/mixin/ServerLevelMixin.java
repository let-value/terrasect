package com.terrasect.neoforge.mixin;

import com.terrasect.neoforge.generation.NeoForgeNarrGenContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.Climate;
import com.mojang.datafixers.util.Either;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * NeoForge mixin for ServerLevel that registers the narrative generation context
 * when a level is initialized.
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(MinecraftServer minecraftServer, Executor executor, LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData, ResourceKey<Level> resourceKey, LevelStem levelStem, ChunkProgressListener chunkProgressListener, boolean bl, long l, List<CustomSpawner> list, boolean bl2, RandomState randomState, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();

        if (biomeSource instanceof MultiNoiseBiomeSource multiNoise) {
             Either<Climate.ParameterList<Holder<Biome>>, Holder<Biome>> parameters = ((MultiNoiseBiomeSourceAccessor) multiNoise).getParameters();
             NeoForgeNarrGenContext context = new NeoForgeNarrGenContext(l, randomState.sampler(), parameters);
             NeoForgeNarrGenContext.register(resourceKey, context);
        }
    }
}
