package com.terrasect.common.generation;

import com.terrasect.common.api.Region;
import com.terrasect.common.api.RegionRegistry;
import com.terrasect.common.generation.definition.RegionDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegionDefinitionTest {

    @Test
    public void inheritsAndBlocksParentDefinitions() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
            .climate(climate -> climate.temperature(0.9f).humidity(0.7f))
            .biomes(biomes -> biomes.allowMods("minecraft").allowTags("#overworld"))
            .structures(structures -> structures
                .allowMods("minecraft")
                .requireStructures("minecraft:village"))
            .mobs(mobs -> mobs.allowTags("#passive"))
            .child("CHILD", child -> child
                .biomes(biomes -> biomes.blockTags("#overworld").allowNames("custom:glowing_grove"))
                .structures(structures -> structures
                    .blockNames("minecraft:village")
                    .requireStructures("custom:scripted_camp"))
                .mobs(mobs -> mobs.blockMods("minecraft").allowNames("custom:wisp")));

        Region root = registry.build("ROOT");

        Region child = root.children().getFirst();
        RegionDefinition resolved = child.definition();

        assertEquals(0.9f, resolved.climate().temperature());
        assertEquals(0.7f, resolved.climate().humidity());

        // Inherited overworld tag was blocked, so only the custom biome remains.
        assertTrue(resolved.biomes().allowedTags().isEmpty());
        assertTrue(resolved.biomes().allowedNames().contains("custom:glowing_grove"));

        // Required structure from parent is removed because of explicit block, and custom requirement remains.
        assertTrue(resolved.structures().requiredStructures().contains("custom:scripted_camp"));
        assertTrue(resolved.structures().requiredStructures().stream().noneMatch(id -> id.contains("minecraft:village")));

        // Hostile/punchy mobs can be blocked at the mod level while allowing specific story mobs.
        assertTrue(resolved.mobs().blockedMods().contains("minecraft"));
        assertTrue(resolved.mobs().allowedNames().contains("custom:wisp"));
    }
}
