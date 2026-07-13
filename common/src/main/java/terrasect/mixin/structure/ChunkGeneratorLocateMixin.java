package terrasect.mixin.structure;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import terrasect.handler.StructureHandler;

@Mixin(targets = "net.minecraft.world.level.chunk.ChunkGenerator")
public class ChunkGeneratorLocateMixin {

  private static final String GET_STRUCTURE_GENERATING_AT =
      "Lnet/minecraft/world/level/chunk/ChunkGenerator;getStructureGeneratingAt(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/StructureManager;ZLnet/minecraft/world/level/levelgen/structure/placement/StructurePlacement;Lnet/minecraft/world/level/ChunkPos;)Lcom/mojang/datafixers/util/Pair;";

  @WrapOperation(
      method =
          "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/level/levelgen/structure/placement/ConcentricRingsStructurePlacement;)Lcom/mojang/datafixers/util/Pair;",
      at = @At(value = "INVOKE", target = GET_STRUCTURE_GENERATING_AT))
  private static Pair<BlockPos, Holder<Structure>> terrasect$filterConcentricLocate(
      Set<Holder<Structure>> set,
      LevelReader levelReader,
      StructureManager structureManager,
      boolean bl,
      StructurePlacement structurePlacement,
      ChunkPos chunkPos,
      Operation<Pair<BlockPos, Holder<Structure>>> original) {
    var resolved =
        StructureHandler.resolveLocateSet(
            set, levelReader, chunkPos.getMinBlockX() >> 4, chunkPos.getMinBlockZ() >> 4);
    if (resolved == null) return null;
    return original.call(resolved, levelReader, structureManager, bl, structurePlacement, chunkPos);
  }

  @WrapOperation(
      method =
          "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/StructureManager;IIIZJLnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;)Lcom/mojang/datafixers/util/Pair;",
      at = @At(value = "INVOKE", target = GET_STRUCTURE_GENERATING_AT))
  private static Pair<BlockPos, Holder<Structure>> terrasect$filterRandomSpreadLocate(
      Set<Holder<Structure>> set,
      LevelReader levelReader,
      StructureManager structureManager,
      boolean bl,
      StructurePlacement structurePlacement,
      ChunkPos chunkPos,
      Operation<Pair<BlockPos, Holder<Structure>>> original) {
    var resolved =
        StructureHandler.resolveLocateSet(
            set, levelReader, chunkPos.getMinBlockX() >> 4, chunkPos.getMinBlockZ() >> 4);
    if (resolved == null) return null;
    return original.call(resolved, levelReader, structureManager, bl, structurePlacement, chunkPos);
  }
}
