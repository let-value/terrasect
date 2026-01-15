package com.terrasect.fabric.mixin;

import com.terrasect.common.definition.StructureRules;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.generation.TraversalResult;
import com.terrasect.common.generation.World;
import com.terrasect.common.lookup.StructureLookup;
import com.terrasect.common.mixin.StructureLookupAccess;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin implements StructureLookupAccess {

    @Unique private StructureLookup terrasect$structureLookup;
    @Unique private static final ThreadLocal<StructureRules> TERRASECT_RULES = new ThreadLocal<>();

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
        TERRASECT_RULES.set(terrasect$sampleRules(resourceKey, chunkAccess));
    }

    @Inject(
            method = "createStructures(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/resources/ResourceKey;)V",
            at = @At("RETURN"))
    private void onCreateStructuresReturn(CallbackInfo ci) {
        TERRASECT_RULES.remove();
    }

    @Redirect(
            method = "createStructures(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/resources/ResourceKey;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/StructureSet;structures()Ljava/util/List;"))
    private List<StructureSet.StructureSelectionEntry> terrasect$filterStructureSet(StructureSet set) {
        StructureRules rules = TERRASECT_RULES.get();
        if (rules == null) {
            return set.structures();
        }

        StructureLookup lookup = terrasect$structureLookup;
        if (lookup == null) {
            return set.structures();
        }

        List<StructureSet.StructureSelectionEntry> list = set.structures();
        int size = list.size();
        if (size == 0) {
            return list;
        }

        int allowedCount = 0;
        for (int i = 0; i < size; i++) {
            StructureSet.StructureSelectionEntry entry = list.get(i);
            if (lookup.isAllowed(entry.structure().value(), rules)) {
                allowedCount++;
            }
        }

        if (allowedCount == size) {
            return list;
        }
        if (allowedCount == 0) {
            return Collections.emptyList();
        }

        var filtered = new ArrayList<StructureSet.StructureSelectionEntry>(allowedCount);
        for (int i = 0; i < size; i++) {
            StructureSet.StructureSelectionEntry entry = list.get(i);
            if (lookup.isAllowed(entry.structure().value(), rules)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    @Unique
    private static StructureRules terrasect$sampleRules(ResourceKey<Level> dimension, ChunkAccess chunkAccess) {
        MinecraftContext context = MinecraftContext.get(dimension);
        if (context == null) {
            return null;
        }

        ChunkPos chunkPos = chunkAccess.getPos();
        int blockX = chunkPos.getMinBlockX() + 8;
        int blockZ = chunkPos.getMinBlockZ() + 8;
        TraversalResult traversal = World.traverse(context, blockX, blockZ);
        if (traversal == null || traversal.region == null) {
            return null;
        }

        StructureRules rules = traversal.region.definition().structures();
        if (rules == null) {
            return null;
        }

        var selection = rules.selection();
        boolean hasRules =
                (selection != null && (selection.hasAllowRules() || selection.hasBlockRules()))
                        || !rules.requiredStructures().isEmpty();
        return hasRules ? rules : null;
    }

    @Override
    public StructureLookup terrasect$getStructureLookup() {
        return terrasect$structureLookup;
    }
}
