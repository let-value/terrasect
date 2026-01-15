package com.terrasect.fabric.mixin;

import com.terrasect.common.lookup.StructureLookup;
import com.terrasect.common.mixin.StructureLookupAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin implements StructureLookupAccess {

    @Unique private StructureLookup terrasect$structureLookup;

    @Inject(
            method = "createStructures(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/resources/ResourceKey;)V",
            at = @At("HEAD"))
    private void onCreateStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState chunkGeneratorStructureState,
            StructureManager structureManager,
            ChunkAccess chunkAccess,
            StructureTemplateManager structureTemplateManager,
            ResourceKey<Level> resourceKey,
            CallbackInfo ci) {
        if (terrasect$structureLookup == null) {
            terrasect$structureLookup = StructureLookup.build(registryAccess);
        }
    }

    @Override
    public StructureLookup terrasect$getStructureLookup() {
        return terrasect$structureLookup;
    }
}
