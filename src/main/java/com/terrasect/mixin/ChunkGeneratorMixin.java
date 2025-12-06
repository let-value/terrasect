package com.terrasect.mixin;

import com.terrasect.Terrasect;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Mixin to replace the BiomeSource with our single biome source
 */
@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    
    @Shadow
    @Final
    @Mutable
    private Supplier<BiomeSource> biomeSource;
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(BiomeSource biomeSource, CallbackInfo ci) {
        // Replace the biome source with our wrapper that returns only one biome
        Supplier<BiomeSource> originalSource = this.biomeSource;
        this.biomeSource = () -> new SingleBiomeWrapper(originalSource.get());
    }
    
    /**
     * Wrapper BiomeSource that returns plains biome for all positions
     */
    private static class SingleBiomeWrapper extends BiomeSource {
        private final BiomeSource wrapped;
        private Holder<Biome> targetBiome;
        
        public SingleBiomeWrapper(BiomeSource wrapped) {
            this.wrapped = wrapped;
        }
        
        @Override
        protected Stream<Holder<Biome>> collectPossibleBiomes() {
            return wrapped.collectPossibleBiomes();
        }
        
        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
            // Get target biome from wrapped source's first biome
            if (targetBiome == null) {
                targetBiome = wrapped.collectPossibleBiomes()
                    .filter(holder -> holder.is(Biomes.PLAINS))
                    .findFirst()
                    .orElse(wrapped.collectPossibleBiomes().findFirst().orElse(null));
            }
            // Return plains biome for all positions
            return targetBiome != null ? targetBiome : wrapped.getNoiseBiome(x, y, z, sampler);
        }
    }
}
