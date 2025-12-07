package com.terrasect.mixin;

import com.terrasect.config.TerrasectConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
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

/**
 * Mixin to replace the BiomeSource with our single biome source
 */
@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkGeneratorMixin.class);
    
    @Shadow
    @Final
    @Mutable
    private BiomeSource biomeSource;
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(BiomeSource biomeSource, CallbackInfo ci) {
        // Replace the biome source with our wrapper that returns only one biome
        BiomeSource originalSource = this.biomeSource;
        this.biomeSource = buildSingleBiomeSource(originalSource);
        LOGGER.info("Terrasect: Replaced biome source with fixed single-biome source (target: {})", 
                TerrasectConfig.getTargetBiomeId());
    }

    private static BiomeSource buildSingleBiomeSource(BiomeSource wrapped) {
        Holder<Biome> targetBiome = resolveTargetBiome(wrapped);

        if (targetBiome == null) {
            LOGGER.error("Terrasect: Failed to initialize target biome, keeping original biome source");
            return wrapped;
        }

        LOGGER.info("Terrasect: Using biome {} for world generation", targetBiome);
        return new FixedBiomeSource(targetBiome);
    }

    private static Holder<Biome> resolveTargetBiome(BiomeSource wrapped) {
        ResourceKey<Biome> targetKey = TerrasectConfig.getTargetBiome();

        // Collect all possible biomes once for efficiency
        java.util.List<Holder<Biome>> possibleBiomes = java.util.List.copyOf(wrapped.possibleBiomes());

        if (possibleBiomes.isEmpty()) {
            LOGGER.error("Terrasect: Original biome source did not report any biomes");
            return null;
        }

        // Try to find the configured biome, fallback to Plains, then to any available biome
        return possibleBiomes.stream()
            .filter(holder -> holder.is(targetKey))
            .findFirst()
            .or(() -> {
                LOGGER.warn("Terrasect: Could not find configured biome {}, falling back to Plains",
                        TerrasectConfig.getTargetBiomeId());
                return possibleBiomes.stream()
                    .filter(holder -> holder.is(Biomes.PLAINS))
                    .findFirst();
            })
            .or(() -> {
                LOGGER.warn("Terrasect: Could not find Plains biome, using first available biome");
                return possibleBiomes.stream().findFirst();
            })
            .orElse(null);
    }
}
