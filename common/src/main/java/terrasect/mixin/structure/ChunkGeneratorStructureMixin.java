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

@Mixin(targets = "net.minecraft.world.level.chunk.ChunkGenerator")
public class ChunkGeneratorStructureMixin {
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
    var sets = original.call(state);
    var chunkPos = chunkAccess.getPos();
    var chunkCtx = ((ChunkAccessExtender) chunkAccess).terrasect$getContext();
    var filtered = StructureHandler.getFilteredSets(chunkCtx, dimensionKey, chunkPos.x, chunkPos.z);
    return filtered != null ? filtered : sets;
  }
}
