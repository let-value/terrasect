package terrasect.mixin.structure;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import terrasect.handler.StructureHandler;

@Mixin(StructureStart.class)
public abstract class StructureStartMixin {
  @Shadow public abstract Structure getStructure();

  @Shadow public abstract ChunkPos getChunkPos();

  @Inject(method = "placeInChunk", at = @At("TAIL"))
  private void terrasect$recordGeneratedStructure(
      StructureManager structureManager, CallbackInfo ci) {
    Structure structure = getStructure();
    String structureId =
        structureManager
            .registryAccess()
            .lookupOrThrow(Registries.STRUCTURE)
            .getResourceKey(structure)
            .map(key -> key.identifier().toString())
            .orElse(null);
    if (structureId == null) {
      return;
    }
    ChunkPos chunkPos = getChunkPos();
    StructureHandler.recordGeneratedStructure(
        structureId, "chunk=" + chunkPos.x + "," + chunkPos.z);
  }
}
