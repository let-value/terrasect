package terrasect.mixin.structure;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import terrasect.extender.ChunkAccessExtender;
import terrasect.handler.StructureHandler;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorStructureMixin {
  private static final ThreadLocal<TerrasectStructureArgs> TERRASECT_STRUCTURE_ARGS =
      new ThreadLocal<>();

  @Inject(method = "createStructures", at = @At("HEAD"))
  private void terrasect$beginCreateStructures(
      RegistryAccess registryAccess,
      ChunkGeneratorStructureState state,
      StructureManager structureManager,
      ChunkAccess chunkAccess,
      StructureTemplateManager structureTemplateManager,
      ResourceKey<Level> dimensionKey,
      CallbackInfo ci) {
    TERRASECT_STRUCTURE_ARGS.set(new TerrasectStructureArgs(chunkAccess, dimensionKey));
  }

  @Inject(method = "createStructures", at = @At("RETURN"))
  private void terrasect$endCreateStructures(
      RegistryAccess registryAccess,
      ChunkGeneratorStructureState state,
      StructureManager structureManager,
      ChunkAccess chunkAccess,
      StructureTemplateManager structureTemplateManager,
      ResourceKey<Level> dimensionKey,
      CallbackInfo ci) {
    TERRASECT_STRUCTURE_ARGS.remove();
  }

  @ModifyExpressionValue(
      method = "createStructures",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;possibleStructureSets()Ljava/util/List;"))
  private List<Holder<StructureSet>> terrasect$filterStructureSets(
      List<Holder<StructureSet>> sets) {
    var args = TERRASECT_STRUCTURE_ARGS.get();
    if (args == null) return sets;
    var chunkPos = args.chunkAccess().getPos();
    var chunkCtx = ((ChunkAccessExtender) args.chunkAccess()).terrasect$getContext();
    var filtered =
        StructureHandler.getFilteredSets(chunkCtx, args.dimensionKey(), chunkPos.x, chunkPos.z);
    return filtered != null ? filtered : sets;
  }

  private record TerrasectStructureArgs(ChunkAccess chunkAccess, ResourceKey<Level> dimensionKey) {}
}
