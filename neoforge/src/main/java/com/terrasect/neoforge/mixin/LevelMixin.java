package com.terrasect.neoforge.mixin;

import com.terrasect.common.handler.LevelHandler;
import com.terrasect.common.mixin.MultiNoiseBiomeSourceAccessor;
import java.util.List;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class LevelMixin {

  @Inject(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerChunkCache;getGeneratorState()Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;",
              ordinal = 0,
              shift = At.Shift.BEFORE))
  private void terrasect$registerContext(
      MinecraftServer server,
      Executor executor,
      LevelStorageSource.LevelStorageAccess storage,
      ServerLevelData levelData,
      ResourceKey<Level> dimension,
      LevelStem levelStem,
      boolean bl,
      long seed,
      List<CustomSpawner> spawners,
      boolean bl2,
      @Nullable RandomSequences randomSequences,
      CallbackInfo ci) {

    var level = (ServerLevel) (Object) this;
    var chunkSource = level.getChunkSource();
    var generator = chunkSource.getGenerator();
    var biomeSource = generator.getBiomeSource();

    if (biomeSource instanceof MultiNoiseBiomeSource multiNoise
        && generator instanceof NoiseBasedChunkGenerator) {
      var parameters = ((MultiNoiseBiomeSourceAccessor) multiNoise).terrasect$getParameters();
      var sampler = chunkSource.randomState().sampler();
      var possibleSets = chunkSource.getGeneratorState().possibleStructureSets();

      LevelHandler.registerContext(
          dimension, seed, sampler, parameters, possibleSets, server.registryAccess());
    }
  }
}
