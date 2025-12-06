package com.terrasect.mixin;

import com.terrasect.Terrasect;
import com.terrasect.config.TerrasectConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkGeneratorMixin.class);
    
    @Shadow
    @Final
    @Mutable
    private Supplier<BiomeSource> biomeSource;
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(BiomeSource biomeSource, CallbackInfo ci) {
        // Replace the biome source with our wrapper that returns only one biome
        Supplier<BiomeSource> originalSource = this.biomeSource;
        this.biomeSource = () -> new SingleBiomeWrapper(originalSource.get());
        LOGGER.info("Terrasect: Replaced biome source with single biome wrapper (target: {})", 
                    TerrasectConfig.getTargetBiomeId());
    }
    
    /**
     * Wrapper BiomeSource that returns the configured biome for all positions
     */
    private static class SingleBiomeWrapper extends BiomeSource {
        private final BiomeSource wrapped;
        private Holder<Biome> targetBiome;
        private boolean initialized = false;
        
        public SingleBiomeWrapper(BiomeSource wrapped) {
            this.wrapped = wrapped;
        }
        
        @Override
        protected Stream<Holder<Biome>> collectPossibleBiomes() {
            return wrapped.collectPossibleBiomes();
        }
        
        /**
         * Initialize the target biome once on first call
         */
        private void initializeTargetBiome() {
            if (initialized) {
                return;
            }
            
            initialized = true;
            ResourceKey<Biome> targetKey = TerrasectConfig.getTargetBiome();
            
            // Collect all possible biomes once for efficiency
            java.util.List<Holder<Biome>> possibleBiomes = 
                wrapped.collectPossibleBiomes().toList();
            
            // Try to find the configured biome, fallback to Plains, then to any available biome
            targetBiome = possibleBiomes.stream()
                .filter(holder -> holder.is(targetKey))
                .findFirst()
                .or(() -> {
                    // Fallback to plains if configured biome not found
                    LOGGER.warn("Terrasect: Could not find configured biome {}, falling back to Plains", 
                               TerrasectConfig.getTargetBiomeId());
                    return possibleBiomes.stream()
                        .filter(holder -> holder.is(Biomes.PLAINS))
                        .findFirst();
                })
                .or(() -> {
                    // Last resort: use first available biome
                    LOGGER.warn("Terrasect: Could not find Plains biome, using first available biome");
                    return possibleBiomes.stream().findFirst();
                })
                .orElse(null);
            
            if (targetBiome != null) {
                LOGGER.info("Terrasect: Using biome {} for world generation", targetBiome);
            } else {
                LOGGER.error("Terrasect: Failed to initialize target biome!");
            }
        }
        
        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
            // Initialize on first call
            if (!initialized) {
                initializeTargetBiome();
            }
            
            // Return the target biome for all positions, or fallback to original behavior
            return targetBiome != null ? targetBiome : wrapped.getNoiseBiome(x, y, z, sampler);
        }
    }
}
