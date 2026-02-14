package terrasect.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.NoiseChunkAccessor;

import java.util.function.Function;

@Mixin(ChunkAccess.class)
public class ChunkAccessMixin {
    @Inject(method = "getOrCreateNoiseChunk", at = @At("RETURN"))
    private void terrasect$attachParentChunk(
            Function<ChunkAccess, NoiseChunk> factory, CallbackInfoReturnable<NoiseChunk> cir) {
        var noiseChunk = cir.getReturnValue();
        ((NoiseChunkAccessor) noiseChunk).terrasect$setChunkAccess((ChunkAccess) (Object) this);
    }
}
