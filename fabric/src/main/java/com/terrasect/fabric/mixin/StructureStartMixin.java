package com.terrasect.fabric.mixin;

import com.terrasect.common.definition.StructureRules;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.generation.TraversalResult;
import com.terrasect.common.generation.World;
import com.terrasect.common.mixin.StructureLookupAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureStart.class)
public class StructureStartMixin {

    @Shadow @Final private Structure structure;

    @Inject(method = "placeInChunk", at = @At("HEAD"), cancellable = true)
    private void onPlaceInChunk(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BoundingBox boundingBox,
            ChunkPos chunkPos,
            CallbackInfo ci) {
        if (!(chunkGenerator instanceof StructureLookupAccess lookupAccess)) {
            return;
        }

        var lookup = lookupAccess.terrasect$getStructureLookup();

        var context = MinecraftContext.get(level.getLevel().dimension());
        if (context == null) {
            return;
        }

        TraversalResult traversal = World.traverse(context, chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        if (traversal == null || traversal.region == null) {
            return;
        }

        StructureRules rules = traversal.region.definition().structures();
        if (rules == null) {
            return;
        }

        if (!lookup.isAllowed(structure, rules)) {
            ci.cancel();
        }
    }
}
