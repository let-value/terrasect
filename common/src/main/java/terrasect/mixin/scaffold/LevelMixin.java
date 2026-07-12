package terrasect.mixin.scaffold;

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
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import terrasect.extender.MultiNoiseBiomeSourceExtender;
import terrasect.extender.PresetIdHolder;
import terrasect.generation.DimensionContext;

@Mixin(ServerLevel.class)
public class LevelMixin {
  @Inject(
      method =
          "<init>(Lnet/minecraft/server/MinecraftServer;Ljava/util/concurrent/Executor;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/world/level/storage/ServerLevelData;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/dimension/LevelStem;ZJLjava/util/List;ZLnet/minecraft/world/RandomSequences;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerChunkCache;getGeneratorState()Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;",
              ordinal = 0,
              shift = At.Shift.BEFORE),
      require = 0)
  private void terrasect$registerContextWithRandomSequences(
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
    terrasect$registerContext(server, levelData, dimension, seed);
  }

  private void terrasect$registerContext(
      MinecraftServer server, ServerLevelData levelData, ResourceKey<Level> dimension, long seed) {
    var level = (ServerLevel) (Object) this;
    var chunkSource = level.getChunkSource();
    var generator = chunkSource.getGenerator();
    var biomeSource = generator.getBiomeSource();
    var sampler = chunkSource.randomState().sampler();
    var structureSets = chunkSource.getGeneratorState().possibleStructureSets();
    var registry = server.registryAccess();
    var presetId =
        levelData instanceof PresetIdHolder extender ? extender.terrasect$getPresetId() : null;

    var climateParameters =
        biomeSource instanceof MultiNoiseBiomeSource multiNoise
            ? ((MultiNoiseBiomeSourceExtender) multiNoise).terrasect$getParameters()
            : null;
    var climateList =
        climateParameters != null
            ? climateParameters.map(list -> list, holder -> holder.value().parameters())
            : null;

    DimensionContext.register(
        presetId, dimension, structureSets, registry, seed, sampler, climateList);
  }
}
