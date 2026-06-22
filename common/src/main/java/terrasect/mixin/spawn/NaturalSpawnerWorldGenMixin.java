package terrasect.mixin.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.compat.SpawnCompat;
import terrasect.handler.MobHandler;

@Mixin(net.minecraft.world.level.NaturalSpawner.class)
public class NaturalSpawnerWorldGenMixin {

  @Inject(
      method =
          "canSpawnMobAt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/world/level/biome/MobSpawnSettings$SpawnerData;Lnet/minecraft/core/BlockPos;)Z",
      at = @At("HEAD"),
      cancellable = true)
  private static void terrasect$filterWorldGenMobSpawn(
      ServerLevel serverLevel,
      StructureManager structureManager,
      ChunkGenerator chunkGenerator,
      net.minecraft.world.entity.MobCategory mobCategory,
      MobSpawnSettings.SpawnerData spawnerData,
      BlockPos blockPos,
      CallbackInfoReturnable<Boolean> cir) {
    var chunkAccess = serverLevel.getChunk(blockPos);
    if (!MobHandler.allowSpawn(
        chunkAccess, blockPos.getX(), blockPos.getZ(), SpawnCompat.INSTANCE.getType(spawnerData))) {
      cir.setReturnValue(false);
    }
  }
}
