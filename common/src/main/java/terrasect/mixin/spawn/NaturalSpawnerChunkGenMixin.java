package terrasect.mixin.spawn;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import terrasect.handler.MobHandler;

// Chunk-generation population (CREATURE-category mobs: overworld animals, nether striders) never
// goes through canSpawnMobAt or the runtime SpawnPredicate, so it needs its own filter. The
// position check runs before the entity is created, making it the cheapest cancellation point.
@Mixin(NaturalSpawner.class)
public class NaturalSpawnerChunkGenMixin {
  // The position check moved from a NaturalSpawner-private helper taking SpawnPlacements$Type
  // (1.20.1) to SpawnPlacements.isSpawnPositionOk keyed by EntityType (1.21.1+). Owner and
  // descriptor both differ, so only the matching wrap may be compiled per version.
  // spotless:off
  //? if >=1.21.1 {
  @WrapOperation(
      method =
          "spawnMobsForChunkGeneration(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/util/RandomSource;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/SpawnPlacements;isSpawnPositionOk(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
  private static boolean terrasect$filterChunkGenSpawn(
      EntityType<?> entityType,
      LevelReader levelReader,
      BlockPos blockPos,
      Operation<Boolean> original) {
    var chunkAccess = levelReader.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
    if (!MobHandler.allowSpawn(chunkAccess, blockPos.getX(), blockPos.getZ(), entityType)) {
      return false;
    }
    return original.call(entityType, levelReader, blockPos);
  }
  //?} else {
  /*@WrapOperation(
      method =
          "spawnMobsForChunkGeneration(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/util/RandomSource;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/NaturalSpawner;isSpawnPositionOk(Lnet/minecraft/world/entity/SpawnPlacements$Type;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/EntityType;)Z"))
  private static boolean terrasect$filterChunkGenSpawn(
      net.minecraft.world.entity.SpawnPlacements.Type placementType,
      LevelReader levelReader,
      BlockPos blockPos,
      EntityType<?> entityType,
      Operation<Boolean> original) {
    var chunkAccess = levelReader.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
    if (!MobHandler.allowSpawn(chunkAccess, blockPos.getX(), blockPos.getZ(), entityType)) {
      return false;
    }
    return original.call(placementType, levelReader, blockPos, entityType);
  }
  *///?}
  // spotless:on
}
