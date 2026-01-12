package com.terrasect.common.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RegionDefinitionTest {

    @Test public void inheritsAndBlocksParentDefinitions() {
        var registry = new RegionRegistry();
        registry.region("ROOT")
                .climate(climate -> climate.temperature(0.9f).humidity(0.7f))
                .biomes(biomes -> biomes.allowMods("minecraft").allowTags("#overworld"))
                .structures(structures -> structures.allowMods("minecraft").requireStructures("minecraft:village"))
                .mobs(mobs -> mobs.allowTags("#passive"))
                .child("CHILD", child -> child.biomes(
                        biomes -> biomes.blockTags("#overworld").allowNames("custom:glowing_grove"))
                        .structures(structures ->
                                structures.blockNames("minecraft:village").requireStructures("custom:scripted_camp"))
                        .mobs(mobs -> mobs.blockMods("minecraft").allowNames("custom:wisp")));

        var root = registry.build("ROOT");

        var child = root.children().getFirst();
        var resolved = child.definition();

        assertEquals(0.9f, resolved.climate().temperature().min());
        assertEquals(0.9f, resolved.climate().temperature().max());
        assertEquals(0.7f, resolved.climate().humidity().min());
        assertEquals(0.7f, resolved.climate().humidity().max());

        assertTrue(resolved.biomes().allowedTags().isEmpty());
        assertTrue(resolved.biomes().allowedNames().contains("custom:glowing_grove"));

        assertTrue(resolved.structures().requiredStructures().contains("custom:scripted_camp"));
        assertTrue(
                resolved.structures().requiredStructures().stream().noneMatch(id -> id.contains("minecraft:village")));

        assertTrue(resolved.mobs().blockedMods().contains("minecraft"));
        assertTrue(resolved.mobs().allowedNames().contains("custom:wisp"));
    }
}
