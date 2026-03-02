package terrasect.mixin.noise;

import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.extender.ChunkAccessExtender;
import terrasect.handler.NoiseHandler;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {

  @Inject(method = "iterateNoiseColumn", at = @At("HEAD"))
  private void terrasect$setPendingChunkForHeightProbe(
      LevelHeightAccessor levelHeightAccessor,
      RandomState randomState,
      int i,
      int j,
      @Nullable MutableObject<NoiseColumn> mutableObject,
      @Nullable Predicate<BlockState> predicate,
      CallbackInfoReturnable<OptionalInt> cir) {
    if (levelHeightAccessor instanceof ChunkAccess chunk) {
      NoiseHandler.pendingChunk.set((ChunkAccessExtender) chunk);
    }
  }

  @Inject(method = "iterateNoiseColumn", at = @At("RETURN"))
  private void terrasect$clearPendingChunkForHeightProbe(
      LevelHeightAccessor levelHeightAccessor,
      RandomState randomState,
      int i,
      int j,
      @Nullable MutableObject<NoiseColumn> mutableObject,
      @Nullable Predicate<BlockState> predicate,
      CallbackInfoReturnable<OptionalInt> cir) {
    if (levelHeightAccessor instanceof ChunkAccess) {
      NoiseHandler.pendingChunk.remove();
    }
  }
}
