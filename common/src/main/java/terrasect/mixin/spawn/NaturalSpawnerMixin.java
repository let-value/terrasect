package terrasect.mixin.spawn;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.handler.MobHandler;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {

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
    ChunkAccess chunkAccess = serverLevel.getChunk(blockPos);
    if (!MobHandler.allowSpawn(
        chunkAccess, blockPos.getX(), blockPos.getZ(), spawnerData.type())) {
      cir.setReturnValue(false);
    }
  }

  @WrapOperation(
      method =
          "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;test(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/ChunkAccess;)Z"))
  private static boolean terrasect$filterMobSpawn(
      NaturalSpawner.SpawnPredicate predicate,
      EntityType<?> entityType,
      BlockPos blockPos,
      ChunkAccess chunkAccess,
      Operation<Boolean> original) {
    if (!MobHandler.allowSpawn(chunkAccess, blockPos.getX(), blockPos.getZ(), entityType)) {
      return false;
    }
    return original.call(predicate, entityType, blockPos, chunkAccess);
  }
}
