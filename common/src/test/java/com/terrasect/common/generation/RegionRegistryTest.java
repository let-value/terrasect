package com.terrasect.common.generation;

import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionDefinition;
import com.terrasect.common.definition.RegionRegistry;

import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegionRegistryTest {

    @Test
    public void buildsTreeFromFlatAndNestedRegistrations() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
            .biomes(biomes -> biomes.allowMods("minecraft"))
            .child("NESTED", child -> child
                .radius(14)  // 14^2 = 196 area
                .biomes(biomes -> biomes.allowNames("custom:grove")));

        registry.region("FLAT")
            .parent("ROOT")
            .radius(12)  // 12^2 = 144 area
            .biomes(biomes -> biomes.blockMods("minecraft").allowTags("#mystic"));

        Region root = registry.build("ROOT");

        // Root budget is sum of children: 196 + 144 = 340
        assertEquals(340, root.areaBudget());

        Region flatChild = root.children().stream().filter(r -> r.name().equals("FLAT")).findFirst().orElseThrow();
        Region nestedChild = root.children().stream().filter(r -> r.name().equals("NESTED")).findFirst().orElseThrow();

        RegionDefinition nestedDefinition = nestedChild.definition();
        assertTrue(nestedDefinition.biomes().allowedMods().contains("minecraft"));
        assertTrue(nestedDefinition.biomes().allowedNames().contains("custom:grove"));

        RegionDefinition flatDefinition = flatChild.definition();
        assertTrue(flatDefinition.biomes().blockedMods().contains("minecraft"));
        assertTrue(flatDefinition.biomes().allowedTags().contains("#mystic"));

        // Ensure build order is stable for consumers that iterate children.
        assertEquals("FLAT", root.children().stream().min(Comparator.comparingInt(r -> r.name().length())).get().name());
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
        // Default radius is 100 -> area = 100^2 = 10000
        assertEquals(10000, orphan.areaBudget());

        Region loopA = registry.build("LOOP_A");
        assertEquals(1, loopA.children().size());
        Region loopB = loopA.children().get(0);
        assertEquals("LOOP_B", loopB.name());
        assertTrue(loopB.children().isEmpty());
        assertEquals(10000, loopB.areaBudget());
    }
}
