package terrasect.mixin.structure;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.handler.StructureHandler;

@Pseudo
@Mixin(targets = "net.minecraft.world.level.chunk.status.ChunkStatusTasks")
public class ChunkStatusTasksStructureMixin {
  @Inject(method = "generateStructureStarts", at = @At("TAIL"), require = 0)
  private static void terrasect$placeForcedStructuresFromStatus(
      @Coerce Object worldGenContext,
      @Coerce Object step,
      @Coerce Object cache,
      ChunkAccess chunk,
      CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir)
      throws ReflectiveOperationException {
    Method levelMethod = worldGenContext.getClass().getMethod("level");
    Method generatorMethod = worldGenContext.getClass().getMethod("generator");
    Method templateManagerMethod = worldGenContext.getClass().getMethod("structureManager");
    ServerLevel level = (ServerLevel) levelMethod.invoke(worldGenContext);
    ChunkGenerator generator = (ChunkGenerator) generatorMethod.invoke(worldGenContext);
    StructureTemplateManager templateManager =
        (StructureTemplateManager) templateManagerMethod.invoke(worldGenContext);
    StructureHandler.placeForcedStructures(
        generator,
        level.registryAccess(),
        level.getChunkSource().getGeneratorState(),
        level.structureManager(),
        templateManager,
        chunk,
        level.dimension());
  }
}
