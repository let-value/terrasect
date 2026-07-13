package terrasect.mixin.structure;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import terrasect.extender.ChunkAccessExtender;
import terrasect.handler.StructureHandler;

@Mixin(net.minecraft.world.level.chunk.ChunkGenerator.class)
public class ChunkGeneratorStructureMixin {

  private static List<Holder<StructureSet>> terrasect$filter(
      List<Holder<StructureSet>> sets, ChunkAccess chunkAccess, ResourceKey<Level> dimensionKey) {
    var chunkPos = chunkAccess.getPos();
    var chunkCtx = ((ChunkAccessExtender) chunkAccess).terrasect$getContext();
    var filtered =
        StructureHandler.getFilteredSets(
            chunkCtx, dimensionKey, chunkPos.getMinBlockX() >> 4, chunkPos.getMinBlockZ() >> 4);
    return filtered != null ? filtered : sets;
  }

  // createStructures gained a trailing ResourceKey<Level> parameter in 1.21.11; 1.21.1 has no
  // dimension key available at this call site at all. @Local(argsOnly=true) would fail to
  // resolve on 1.21.1 since no argument of that type exists, so the capture is gated and 1.21.1
  // falls back to StructureHandler's chunkContext-only path (see its dimensionKey javadoc).
  // spotless:off
  //? if >=1.21.11 {
  @WrapOperation(
      method = "createStructures",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;possibleStructureSets()Ljava/util/List;"))
  private List<Holder<StructureSet>> terrasect$filterStructureSets(
      ChunkGeneratorStructureState state,
      Operation<List<Holder<StructureSet>>> original,
      @Local(argsOnly = true) ChunkAccess chunkAccess,
      @Local(argsOnly = true) ResourceKey<Level> dimensionKey) {
    return terrasect$filter(original.call(state), chunkAccess, dimensionKey);
  }
  //?} else {
  /*@WrapOperation(
      method = "createStructures",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;possibleStructureSets()Ljava/util/List;"))
  private List<Holder<StructureSet>> terrasect$filterStructureSets(
      ChunkGeneratorStructureState state,
      Operation<List<Holder<StructureSet>>> original,
      @Local(argsOnly = true) ChunkAccess chunkAccess) {
    return terrasect$filter(original.call(state), chunkAccess, null);
  }
  *///?}
  // spotless:on
}
