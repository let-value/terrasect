package terrasect.mixin.structure;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import terrasect.handler.StructureHandler;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorForcedMixin {
  // spotless:off
  //? if >=1.21.11 {
  @Inject(method = "createStructures", at = @At("TAIL"))
  private void terrasect$placeForcedStructures(
      RegistryAccess registryAccess,
      ChunkGeneratorStructureState state,
      StructureManager structureManager,
      ChunkAccess chunk,
      StructureTemplateManager templateManager,
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimensionKey,
      CallbackInfo ci) {
    StructureHandler.placeForcedStructures(
        (ChunkGenerator) (Object) this,
        registryAccess,
        state,
        structureManager,
        templateManager,
        chunk,
        dimensionKey);
  }
  //?} else {
  /*@Inject(method = "createStructures", at = @At("TAIL"))
  private void terrasect$placeForcedStructures(
      RegistryAccess registryAccess,
      ChunkGeneratorStructureState state,
      StructureManager structureManager,
      ChunkAccess chunk,
      StructureTemplateManager templateManager,
      CallbackInfo ci) {
    StructureHandler.placeForcedStructures(
        (ChunkGenerator) (Object) this,
        registryAccess,
        state,
        structureManager,
        templateManager,
        chunk,
        null);
  }
  *///?}
  // spotless:on
}
