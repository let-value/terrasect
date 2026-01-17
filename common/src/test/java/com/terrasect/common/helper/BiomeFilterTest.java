package com.terrasect.common.helper;

import static org.junit.jupiter.api.Assertions.*;

import com.terrasect.common.definition.SelectionRules;
import com.terrasect.common.helpers.BiomeFilter;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class BiomeFilterTest {

  @Test
  public void nullRulesReturnsNoRules() {
    BiomeFilter.FilterResult result =
        BiomeFilter.checkBiome(null, "minecraft:plains", Collections.emptySet());
    assertEquals(BiomeFilter.FilterResult.NO_RULES, result);
  }

  @Test
  public void emptyRulesReturnsNoRules() {
    SelectionRules rules = SelectionRules.empty();
    BiomeFilter.FilterResult result =
        BiomeFilter.checkBiome(rules, "minecraft:plains", Collections.emptySet());
    assertEquals(BiomeFilter.FilterResult.NO_RULES, result);
  }

  @Test
  public void blockedByNameReturnsBlocked() {
    var rules = SelectionRules.builder().blockNames("minecraft:desert").build();

    BiomeFilter.FilterResult result =
        BiomeFilter.checkBiome(rules, "minecraft:desert", Collections.emptySet());
    assertEquals(BiomeFilter.FilterResult.BLOCKED, result);
  }

  @Test
  public void notBlockedByNameWithOtherBiome() {
    var rules = SelectionRules.builder().blockNames("minecraft:desert").build();

    assertTrue(BiomeFilter.isAllowed(rules, "minecraft:plains", Collections.emptySet()));
  }

  @Test
  public void blockedByModReturnsBlocked() {
    var rules = SelectionRules.builder().blockMods("terralith").build();

    BiomeFilter.FilterResult result =
        BiomeFilter.checkBiome(rules, "terralith:volcanic_crater", Collections.emptySet());
    assertEquals(BiomeFilter.FilterResult.BLOCKED, result);
  }

  @Test
  public void blockedByTagReturnsBlocked() {
    var rules = SelectionRules.builder().blockTags("#minecraft:is_ocean").build();

    var tags = Set.of("#minecraft:is_ocean", "#minecraft:is_overworld");

    BiomeFilter.FilterResult result = BiomeFilter.checkBiome(rules, "minecraft:ocean", tags);
    assertEquals(BiomeFilter.FilterResult.BLOCKED, result);
  }

  @Test
  public void tagMatchingWorksWithAndWithoutHashPrefix() {
    var rulesWithHash = SelectionRules.builder().blockTags("#minecraft:is_ocean").build();

    var rulesWithoutHash = SelectionRules.builder().blockTags("minecraft:is_ocean").build();

    var tagsWithHash = Set.of("#minecraft:is_ocean");
    var tagsWithoutHash = Set.of("minecraft:is_ocean");

    assertTrue(!BiomeFilter.isAllowed(rulesWithHash, "minecraft:ocean", tagsWithHash));
    assertTrue(!BiomeFilter.isAllowed(rulesWithHash, "minecraft:ocean", tagsWithoutHash));
    assertTrue(!BiomeFilter.isAllowed(rulesWithoutHash, "minecraft:ocean", tagsWithHash));
    assertTrue(!BiomeFilter.isAllowed(rulesWithoutHash, "minecraft:ocean", tagsWithoutHash));
  }

  @Test
  public void allowedByNameReturnsAllowed() {
    var rules = SelectionRules.builder().allowNames("minecraft:plains", "minecraft:forest").build();

    BiomeFilter.FilterResult result =
        BiomeFilter.checkBiome(rules, "minecraft:plains", Collections.emptySet());
    assertEquals(BiomeFilter.FilterResult.ALLOWED, result);
  }

  @Test
  public void allowedByModReturnsAllowed() {
    var rules = SelectionRules.builder().allowMods("minecraft").build();

    BiomeFilter.FilterResult result =
        BiomeFilter.checkBiome(rules, "minecraft:swamp", Collections.emptySet());
    assertEquals(BiomeFilter.FilterResult.ALLOWED, result);
  }

  @Test
  public void allowedByTagReturnsAllowed() {
    var rules = SelectionRules.builder().allowTags("#minecraft:is_forest").build();

    var tags = Set.of("#minecraft:is_forest", "#minecraft:is_overworld");

    BiomeFilter.FilterResult result = BiomeFilter.checkBiome(rules, "minecraft:forest", tags);
    assertEquals(BiomeFilter.FilterResult.ALLOWED, result);
  }

  @Test
  public void blockingTakesPriorityOverAllowing() {
    var rules =
        SelectionRules.builder().allowMods("minecraft").blockNames("minecraft:desert").build();

    BiomeFilter.FilterResult result =
        BiomeFilter.checkBiome(rules, "minecraft:desert", Collections.emptySet());
    assertEquals(BiomeFilter.FilterResult.BLOCKED, result);

    BiomeFilter.FilterResult forestResult =
        BiomeFilter.checkBiome(rules, "minecraft:forest", Collections.emptySet());
    assertEquals(BiomeFilter.FilterResult.ALLOWED, forestResult);
  }

  @Test
  public void blockOverridesAllowForSameName() {
    var rules =
        SelectionRules.builder()
            .allowNames("minecraft:desert")
            .blockNames("minecraft:desert")
            .build();

    assertEquals(
        BiomeFilter.FilterResult.BLOCKED,
        BiomeFilter.checkBiome(rules, "minecraft:desert", Collections.emptySet()));
  }

  @Test
  public void allowRulesBlockUnmatchedBiomes() {
    var rules = SelectionRules.builder().allowNames("minecraft:plains").build();

    BiomeFilter.FilterResult result =
        BiomeFilter.checkBiome(rules, "minecraft:desert", Collections.emptySet());
    assertEquals(BiomeFilter.FilterResult.BLOCKED, result);
  }

  @Test
  public void complexRulesWorkCorrectly() {

    var rules =
        SelectionRules.builder()
            .allowMods("minecraft")
            .blockTags("#minecraft:is_ocean")
            .allowNames("terralith:crystal_ocean")
            .build();

    assertTrue(BiomeFilter.isAllowed(rules, "minecraft:forest", Set.of("#minecraft:is_forest")));

    assertFalse(BiomeFilter.isAllowed(rules, "minecraft:ocean", Set.of("#minecraft:is_ocean")));

    assertTrue(
        BiomeFilter.isAllowed(rules, "terralith:crystal_ocean", Set.of("#minecraft:is_ocean")));

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
    var rules = SelectionRules.builder().allowMods("minecraft").build();

    BiomeFilter.FilterResult result =
        BiomeFilter.checkBiome(rules, "plains", Collections.emptySet());
    assertEquals(BiomeFilter.FilterResult.ALLOWED, result);
  }

  @Test
  public void isAllowedConvenienceMethod() {
    var rules = SelectionRules.builder().blockNames("minecraft:desert").build();

    assertFalse(BiomeFilter.isAllowed(rules, "minecraft:desert", Collections.emptySet()));
    assertTrue(BiomeFilter.isAllowed(rules, "minecraft:plains", Collections.emptySet()));
  }
}
