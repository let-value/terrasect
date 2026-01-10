package com.terrasect.common.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import org.junit.jupiter.api.Test;

public class RegionRegistryTest {

    @Test
    public void buildsTreeFromFlatAndNestedRegistrations() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
                .biomes(biomes -> biomes.allowMods("minecraft"))
                .child("NESTED", child -> child.radius(14).biomes(biomes -> biomes.allowNames("custom:grove")));

        registry.region("FLAT").parent("ROOT").radius(12).biomes(biomes -> biomes.blockMods("minecraft")
                .allowTags("#mystic"));

        Region root = registry.build("ROOT");

        assertEquals(340, root.areaBudget());

        Region flatChild = root.children().stream()
                .filter(r -> r.name().equals("FLAT"))
                .findFirst()
                .orElseThrow();
        Region nestedChild = root.children().stream()
                .filter(r -> r.name().equals("NESTED"))
                .findFirst()
                .orElseThrow();

        RegionDefinition nestedDefinition = nestedChild.definition();
        assertTrue(nestedDefinition.biomes().allowedMods().contains("minecraft"));
        assertTrue(nestedDefinition.biomes().allowedNames().contains("custom:grove"));

        RegionDefinition flatDefinition = flatChild.definition();
        assertTrue(flatDefinition.biomes().blockedMods().contains("minecraft"));
        assertTrue(flatDefinition.biomes().allowedTags().contains("#mystic"));

        assertEquals(
                "FLAT",
                root.children().stream()
                        .min(Comparator.comparingInt(r -> r.name().length()))
                        .get()
                        .name());
    }

    @Test
    public void toleratesInvalidParentLinksAndCycles() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("ORPHAN").parent("MISSING");
        registry.region("LOOP_A").parent("LOOP_B");
        registry.region("LOOP_B").parent("LOOP_A");

        Region orphan = registry.build("ORPHAN");
        assertEquals("ORPHAN", orphan.name());
        assertTrue(orphan.children().isEmpty());

        assertEquals(10000, orphan.areaBudget());

        Region loopA = registry.build("LOOP_A");
        assertEquals(1, loopA.children().size());
        Region loopB = loopA.children().get(0);
        assertEquals("LOOP_B", loopB.name());
        assertTrue(loopB.children().isEmpty());
        assertEquals(10000, loopB.areaBudget());
    }
}
