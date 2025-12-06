package com.terrasect.worldgen;

import com.mojang.serialization.MapCodec;
import com.terrasect.Terrasect;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Biome modifier that changes all biomes to a single biome
 */
public record SingleBiomeModifier(Holder<Biome> targetBiome) implements BiomeModifier {
    
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Terrasect.MOD_ID);
    
    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<SingleBiomeModifier>> SINGLE_BIOME_CODEC =
            BIOME_MODIFIER_SERIALIZERS.register("single_biome", () -> 
                MapCodec.unit(new SingleBiomeModifier(null)));
    
    public static void register(IEventBus modEventBus) {
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);
    }
    
    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        // This modifier doesn't modify biomes in-place; instead, we use a custom biome source
        // The actual worldgen modification happens through the dimension type JSON
    }
    
    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return SINGLE_BIOME_CODEC.get();
    }
}
