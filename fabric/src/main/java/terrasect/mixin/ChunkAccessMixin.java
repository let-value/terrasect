package terrasect.mixin;

import java.util.function.Function;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.ChunkAccessExtender;
import terrasect.NoiseChunkExtender;

@Mixin(ChunkAccess.class)
public class ChunkAccessMixin implements ChunkAccessExtender {
  @Unique private Integer terrasect$worldgenCache;

  @Inject(method = "getOrCreateNoiseChunk", at = @At("RETURN"))
  private void terrasect$attachParentChunk(
      Function<ChunkAccess, NoiseChunk> factory, CallbackInfoReturnable<NoiseChunk> cir) {
    var noiseChunk = cir.getReturnValue();
    ((NoiseChunkExtender) noiseChunk).terrasect$setChunk(this);
  }

  @Override
  public ChunkAccess terrasect$getChunk() {
    return (ChunkAccess) (Object) this;
  }
}
