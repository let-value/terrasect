package terrasect.mixin;

import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrasect.SamplerAccessor;
import terrasect.generation.Context;
import terrasect.handler.ClimateHandler;

@Mixin(Climate.Sampler.class)
public class ClimateSamplerAccessorMixin implements SamplerAccessor {
    @Unique
    private ChunkAccess terrasect$parentChunk;

    @Override
    public void terrasect$setChunkAccess(ChunkAccess chunkAccess) {
        this.terrasect$parentChunk = chunkAccess;
    }

    @Override
    public ChunkAccess terrasect$chunkAccess() {
        return this.terrasect$parentChunk;
    }

    @Inject(method = "sample", at = @At("RETURN"))
    private void terrasect$modifyClimate(
            int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
        var self = (Climate.Sampler) (Object) this;

        var context = Context.Companion.get(self);
        if (context == null) {
            return;
        }

        ClimateHandler.INSTANCE.modifyTargetPoint(context, x, z, cir.getReturnValue());
    }
}
