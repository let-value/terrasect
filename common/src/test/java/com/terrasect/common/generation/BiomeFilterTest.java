package com.terrasect.common.generation;

import com.terrasect.common.generation.definition.SelectionRules;
import com.terrasect.common.runtime.BiomeFilter;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BiomeFilter which checks biomes against SelectionRules.
 */
public class BiomeFilterTest {

    @Test
    public void nullRulesReturnsNoRules() {
        BiomeFilter.FilterResult result = BiomeFilter.checkBiome(
            null, "minecraft:plains", Collections.emptySet()
        );
        assertEquals(BiomeFilter.FilterResult.NO_RULES, result);
    }

    @Test
    public void emptyRulesReturnsNoRules() {
        SelectionRules rules = SelectionRules.empty();
        BiomeFilter.FilterResult result = BiomeFilter.checkBiome(
            rules, "minecraft:plains", Collections.emptySet()
        );
        assertEquals(BiomeFilter.FilterResult.NO_RULES, result);
    }

    @Test
    public void blockedByNameReturnsBlocked() {
        SelectionRules rules = SelectionRules.builder()
            .blockNames("minecraft:desert")
            .build();
        
        BiomeFilter.FilterResult result = BiomeFilter.checkBiome(
            rules, "minecraft:desert", Collections.emptySet()
        );
        assertEquals(BiomeFilter.FilterResult.BLOCKED, result);
    }

    @Test
    public void notBlockedByNameWithOtherBiome() {
        SelectionRules rules = SelectionRules.builder()
            .blockNames("minecraft:desert")
            .build();
        
        // Different biome should not be blocked (no allow rules = permissive)
        assertTrue(BiomeFilter.isAllowed(rules, "minecraft:plains", Collections.emptySet()));
    }

    @Test
    public void blockedByModReturnsBlocked() {
        SelectionRules rules = SelectionRules.builder()
            .blockMods("terralith")
            .build();
        
        BiomeFilter.FilterResult result = BiomeFilter.checkBiome(
            rules, "terralith:volcanic_crater", Collections.emptySet()
        );
        assertEquals(BiomeFilter.FilterResult.BLOCKED, result);
    }

    @Test
    public void blockedByTagReturnsBlocked() {
        SelectionRules rules = SelectionRules.builder()
            .blockTags("#minecraft:is_ocean")
            .build();
        
        Set<String> tags = Set.of("#minecraft:is_ocean", "#minecraft:is_overworld");
        
        BiomeFilter.FilterResult result = BiomeFilter.checkBiome(
            rules, "minecraft:ocean", tags
        );
        assertEquals(BiomeFilter.FilterResult.BLOCKED, result);
    }

    @Test
    public void tagMatchingWorksWithAndWithoutHashPrefix() {
        SelectionRules rulesWithHash = SelectionRules.builder()
            .blockTags("#minecraft:is_ocean")
            .build();
        
        SelectionRules rulesWithoutHash = SelectionRules.builder()
            .blockTags("minecraft:is_ocean")
            .build();
        
        Set<String> tagsWithHash = Set.of("#minecraft:is_ocean");
        Set<String> tagsWithoutHash = Set.of("minecraft:is_ocean");
        
        // All combinations should work
        assertTrue(!BiomeFilter.isAllowed(rulesWithHash, "minecraft:ocean", tagsWithHash));
        assertTrue(!BiomeFilter.isAllowed(rulesWithHash, "minecraft:ocean", tagsWithoutHash));
        assertTrue(!BiomeFilter.isAllowed(rulesWithoutHash, "minecraft:ocean", tagsWithHash));
        assertTrue(!BiomeFilter.isAllowed(rulesWithoutHash, "minecraft:ocean", tagsWithoutHash));
    }

    @Test
    public void allowedByNameReturnsAllowed() {
        SelectionRules rules = SelectionRules.builder()
            .allowNames("minecraft:plains", "minecraft:forest")
            .build();
        
        BiomeFilter.FilterResult result = BiomeFilter.checkBiome(
            rules, "minecraft:plains", Collections.emptySet()
        );
        assertEquals(BiomeFilter.FilterResult.ALLOWED, result);
    }

    @Test
    public void allowedByModReturnsAllowed() {
        SelectionRules rules = SelectionRules.builder()
            .allowMods("minecraft")
            .build();
        
        BiomeFilter.FilterResult result = BiomeFilter.checkBiome(
            rules, "minecraft:swamp", Collections.emptySet()
        );
        assertEquals(BiomeFilter.FilterResult.ALLOWED, result);
    }

    @Test
    public void allowedByTagReturnsAllowed() {
        SelectionRules rules = SelectionRules.builder()
            .allowTags("#minecraft:is_forest")
            .build();
        
        Set<String> tags = Set.of("#minecraft:is_forest", "#minecraft:is_overworld");
        
        BiomeFilter.FilterResult result = BiomeFilter.checkBiome(
            rules, "minecraft:forest", tags
        );
        assertEquals(BiomeFilter.FilterResult.ALLOWED, result);
    }

    @Test
    public void blockingTakesPriorityOverAllowing() {
        SelectionRules rules = SelectionRules.builder()
            .allowMods("minecraft")
            .blockNames("minecraft:desert")
            .build();
        
        // Desert is blocked even though minecraft mod is allowed
        BiomeFilter.FilterResult result = BiomeFilter.checkBiome(
            rules, "minecraft:desert", Collections.emptySet()
        );
        assertEquals(BiomeFilter.FilterResult.BLOCKED, result);
        
        // Other minecraft biomes are still allowed
        BiomeFilter.FilterResult forestResult = BiomeFilter.checkBiome(
            rules, "minecraft:forest", Collections.emptySet()
        );
        assertEquals(BiomeFilter.FilterResult.ALLOWED, forestResult);
    }

    @Test
    public void allowRulesBlockUnmatchedBiomes() {
        SelectionRules rules = SelectionRules.builder()
            .allowNames("minecraft:plains")
            .build();
        
        // Biome not in allow list should be blocked
        BiomeFilter.FilterResult result = BiomeFilter.checkBiome(
            rules, "minecraft:desert", Collections.emptySet()
        );
        assertEquals(BiomeFilter.FilterResult.BLOCKED, result);
    }

    @Test
    public void complexRulesWorkCorrectly() {
        // Allow all minecraft biomes, but block oceans, and specifically allow a modded ocean
        SelectionRules rules = SelectionRules.builder()
            .allowMods("minecraft")
            .blockTags("#minecraft:is_ocean")
            .allowNames("terralith:crystal_ocean")
            .build();
        
        // Regular minecraft biome - allowed
        assertTrue(BiomeFilter.isAllowed(rules, "minecraft:forest", Set.of("#minecraft:is_forest")));
        
        // Minecraft ocean - blocked
        assertFalse(BiomeFilter.isAllowed(rules, "minecraft:ocean", Set.of("#minecraft:is_ocean")));
        
        // Specific modded ocean - allowed
        assertTrue(BiomeFilter.isAllowed(rules, "terralith:crystal_ocean", Set.of("#minecraft:is_ocean")));
        
        // Other modded biome - blocked (not in allowed mods)
        assertFalse(BiomeFilter.isAllowed(rules, "terralith:volcanic_crater", Collections.emptySet()));
    }

    @Test
    public void hasRulesReturnsFalseForEmpty() {
        assertFalse(BiomeFilter.hasRules(null));
        assertFalse(BiomeFilter.hasRules(SelectionRules.empty()));
    }

    @Test
    public void hasRulesReturnsTrueWithAnyRule() {
        assertTrue(BiomeFilter.hasRules(SelectionRules.builder().allowMods("minecraft").build()));
        assertTrue(BiomeFilter.hasRules(SelectionRules.builder().allowTags("#test").build()));
        assertTrue(BiomeFilter.hasRules(SelectionRules.builder().allowNames("minecraft:test").build()));
        assertTrue(BiomeFilter.hasRules(SelectionRules.builder().blockMods("test").build()));
        assertTrue(BiomeFilter.hasRules(SelectionRules.builder().blockTags("#test").build()));
        assertTrue(BiomeFilter.hasRules(SelectionRules.builder().blockNames("test:test").build()));
    }

    @Test
    public void defaultNamespaceIsMinecraft() {
        SelectionRules rules = SelectionRules.builder()
            .allowMods("minecraft")
            .build();
        
        // Biome without namespace should default to minecraft
        BiomeFilter.FilterResult result = BiomeFilter.checkBiome(
            rules, "plains", Collections.emptySet()
        );
        assertEquals(BiomeFilter.FilterResult.ALLOWED, result);
    }

    @Test
    public void isAllowedConvenienceMethod() {
        SelectionRules rules = SelectionRules.builder()
            .blockNames("minecraft:desert")
            .build();
        
        assertFalse(BiomeFilter.isAllowed(rules, "minecraft:desert", Collections.emptySet()));
        assertTrue(BiomeFilter.isAllowed(rules, "minecraft:plains", Collections.emptySet()));
    }
}
