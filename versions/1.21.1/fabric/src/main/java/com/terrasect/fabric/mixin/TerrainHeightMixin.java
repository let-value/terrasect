package com.terrasect.fabric.mixin;

import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.lookup.TerrainHeightLookup;
import com.terrasect.common.util.MutablePointContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunk.class)
public class TerrainHeightMixin {

  @Unique private TerrainHeightLookup terrasect$heightLookup;

  @Unique private Aquifer.FluidPicker terrasect$fluidPicker;

  @Inject(
      method = "<init>",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lnet/minecraft/world/level/levelgen/NoiseChunk;aquifer:Lnet/minecraft/world/level/levelgen/Aquifer;",
              opcode = Opcodes.PUTFIELD))
  private void initHeightConstraintsEarly(
      int cellCountXZ,
      RandomState randomState,
      int chunkMinX,
      int chunkMinZ,
      NoiseSettings noiseSettings,
      DensityFunctions.BeardifierOrMarker beardifier,
      NoiseGeneratorSettings generatorSettings,
      Aquifer.FluidPicker fluidPicker,
      Blender blender,
      CallbackInfo ci) {

    if (this.terrasect$heightLookup != null) return;

    this.terrasect$fluidPicker = fluidPicker;
    var context = MinecraftContext.get(randomState.sampler());

    var router = randomState.router();
    var depth = router.depth();

    var pointCtx = new MutablePointContext();
    terrasect$heightLookup =
        TerrainHeightLookup.build(
            context,
            chunkMinX,
            chunkMinZ,
            (x, z) -> {
              pointCtx.set(x, 64, z);
              var depthValue = depth.compute(pointCtx);
              return 64 + (int) (depthValue * 128);
            });
  }

  @Inject(method = "getInterpolatedState", at = @At("HEAD"), cancellable = true)
  private void constrainTerrainHeight(CallbackInfoReturnable<BlockState> cir) {
    if (terrasect$heightLookup == null) return;

    var self = (NoiseChunk) (Object) this;
    var blockX = self.blockX();
    var blockY = self.blockY();
    var blockZ = self.blockZ();

    var maxHeight = terrasect$heightLookup.getMaxHeight(blockX, blockZ);
    if (maxHeight != TerrainHeightLookup.NO_CONSTRAINT && blockY > maxHeight) {
      var fluidStatus = terrasect$fluidPicker.computeFluid(blockX, blockY, blockZ);
      cir.setReturnValue(fluidStatus.at(blockY));
    }
  }

  @Inject(method = "computePreliminarySurfaceLevel", at = @At("RETURN"), cancellable = true)
  private void clampPreliminarySurfaceLevel(long packedPos, CallbackInfoReturnable<Integer> cir) {
    if (terrasect$heightLookup == null) return;

    var x = (int) (packedPos & 0xFFFFFFFFL);
    var z = (int) (packedPos >>> 32);
    var maxHeight = terrasect$heightLookup.getMaxHeight(x, z);

    if (maxHeight != TerrainHeightLookup.NO_CONSTRAINT) {
      var original = cir.getReturnValue();
      if (original > maxHeight) {
        cir.setReturnValue(maxHeight);
      }
    }
  }
}
