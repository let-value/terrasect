package com.terrasect.neoforge.mixin;

import com.terrasect.common.handler.StructureHandler;
import com.terrasect.common.lookup.StructureSetsLookup;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {

  @Unique private StructureSetsLookup.FilteredSets terrasect$filteredSets;

  @Inject(method = "createStructures", at = @At("HEAD"))
  private void terrasect$initFilteredSets(
      RegistryAccess registryAccess,
      ChunkGeneratorStructureState state,
      StructureManager structureManager,
      ChunkAccess chunk,
      StructureTemplateManager templateManager,
      ResourceKey<Level> dimension,
      CallbackInfo ci) {
    terrasect$filteredSets = StructureHandler.getFilteredSets(dimension, chunk.getPos());
  }

  @Redirect(
      method = "createStructures",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;possibleStructureSets()Ljava/util/List;"))
  private List<Holder<StructureSet>> terrasect$filterPossibleSets(
      ChunkGeneratorStructureState state) {
    if (terrasect$filteredSets == null) {
      return state.possibleStructureSets();
    }
    return terrasect$filteredSets.sets();
  }
}
