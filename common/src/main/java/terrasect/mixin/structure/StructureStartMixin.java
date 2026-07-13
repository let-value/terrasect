package terrasect.mixin.structure;

import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import terrasect.compat.ResourceKeyCompat;
import terrasect.handler.StructureHandler;

@Mixin(StructureStart.class)
public abstract class StructureStartMixin {
  @Shadow
  public abstract Structure getStructure();

  @Shadow private ChunkPos chunkPos;

  @Inject(method = "placeInChunk", at = @At("TAIL"))
  private void terrasect$recordGeneratedStructure(
      WorldGenLevel world,
      StructureManager structureManager,
      ChunkGenerator chunkGenerator,
      RandomSource random,
      BoundingBox boundingBox,
      ChunkPos chunkPos,
      CallbackInfo ci) {
    Structure structure = getStructure();
    // spotless:off
    //? if >=1.21.11 {
    String structureId =
        structureManager
            .registryAccess()
            .lookupOrThrow(Registries.STRUCTURE)
            .getResourceKey(structure)
            .map(ResourceKeyCompat.INSTANCE::getKeyId)
            .orElse(null);
    //?} else {
    /*String structureId =
        structureManager
            .registryAccess()
            .lookupOrThrow(Registries.STRUCTURE)
            .listElements()
            .filter(h -> h.value() == structure)
            .findFirst()
            .map(h -> ResourceKeyCompat.INSTANCE.getKeyId(h.key()))
            .orElse(null);*/
    //?}
    // spotless:on
    if (structureId == null) {
      return;
    }
    StructureHandler.recordGeneratedStructure(
        structureId,
        "chunk=" + (chunkPos.getMinBlockX() >> 4) + "," + (chunkPos.getMinBlockZ() >> 4),
        "origin_chunk="
            + (this.chunkPos.getMinBlockX() >> 4)
            + ","
            + (this.chunkPos.getMinBlockZ() >> 4));
  }
}
