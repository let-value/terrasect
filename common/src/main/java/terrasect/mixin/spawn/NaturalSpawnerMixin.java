package terrasect.mixin.spawn;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import terrasect.handler.MobHandler;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {

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
    if (!MobHandler.allowSpawn(chunkAccess, blockPos.getX(), blockPos.getZ(), entityType))
      return false;
    return original.call(predicate, entityType, blockPos, chunkAccess);
  }
}
