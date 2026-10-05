package terrasect.mixin.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "net.minecraft.world.level.chunk.status.WorldGenContext")
public interface WorldGenContextAccessor {
  @Invoker("level")
  ServerLevel terrasect$level();

  @Invoker("generator")
  ChunkGenerator terrasect$generator();

  @Invoker("structureManager")
  StructureTemplateManager terrasect$structureManager();
}
