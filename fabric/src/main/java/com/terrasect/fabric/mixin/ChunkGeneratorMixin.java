package com.terrasect.fabric.mixin;

import com.terrasect.common.lookup.StructureLookup;
import com.terrasect.common.mixin.StructureLookupAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin implements StructureLookupAccess {

    @Unique private StructureLookup terrasect$structureLookup;

    @Inject(method = "<init>(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/biome/BiomeSource;)V", at = @At("TAIL"), require = 0)
    private void onInitWithRegistry(RegistryAccess registryAccess, BiomeSource biomeSource, CallbackInfo ci) {
        if (terrasect$structureLookup == null) {
            terrasect$structureLookup = StructureLookup.build(registryAccess);
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/biome/BiomeSource;)V", at = @At("TAIL"), require = 0)
    private void onInit(BiomeSource biomeSource, CallbackInfo ci) {
        if (terrasect$structureLookup == null) {
            terrasect$structureLookup = StructureLookup.build(BuiltInRegistries.STRUCTURE);
        }
    }

    @Override
    public StructureLookup terrasect$getStructureLookup() {
        return terrasect$structureLookup;
    }

    @Override
    public void terrasect$setStructureLookup(StructureLookup lookup) {
        this.terrasect$structureLookup = lookup;
    }
}
