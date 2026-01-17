package com.terrasect.common.definition;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class SelectionRulesTest {

  @Test
  public void nullRulesReturnsNoRules() {
    var result = evaluate(null, "minecraft:plains", Collections.emptySet());
    assertEquals(SelectionRules.Match.NO_RULES, result);
  }

  @Test
  public void emptyRulesReturnsNoRules() {
    SelectionRules rules = SelectionRules.empty();
    var result = evaluate(rules, "minecraft:plains", Collections.emptySet());
    assertEquals(SelectionRules.Match.NO_RULES, result);
  }

  @Test
  public void blockedByNameReturnsBlocked() {
    var rules = SelectionRules.builder().blockNames("minecraft:desert").build();

    var result = evaluate(rules, "minecraft:desert", Collections.emptySet());
    assertEquals(SelectionRules.Match.BLOCKED, result);
  }

  @Test
  public void notBlockedByNameWithOtherBiome() {
    var rules = SelectionRules.builder().blockNames("minecraft:desert").build();

    assertTrue(isAllowed(rules, "minecraft:plains", Collections.emptySet()));
  }

  @Test
  public void blockedByModReturnsBlocked() {
    var rules = SelectionRules.builder().blockMods("terralith").build();

    var result = evaluate(rules, "terralith:volcanic_crater", Collections.emptySet());
    assertEquals(SelectionRules.Match.BLOCKED, result);
  }

  @Test
  public void blockedByTagReturnsBlocked() {
    var rules = SelectionRules.builder().blockTags("#minecraft:is_ocean").build();

    var tags = Set.of("#minecraft:is_ocean", "#minecraft:is_overworld");

    var result = evaluate(rules, "minecraft:ocean", tags);
    assertEquals(SelectionRules.Match.BLOCKED, result);
  }

  @Test
  public void tagMatchingWorksWithAndWithoutHashPrefix() {
    var rulesWithHash = SelectionRules.builder().blockTags("#minecraft:is_ocean").build();

    var rulesWithoutHash = SelectionRules.builder().blockTags("minecraft:is_ocean").build();

    var tagsWithHash = Set.of("#minecraft:is_ocean");
    var tagsWithoutHash = Set.of("minecraft:is_ocean");

    assertTrue(!isAllowed(rulesWithHash, "minecraft:ocean", tagsWithHash));
    assertTrue(!isAllowed(rulesWithHash, "minecraft:ocean", tagsWithoutHash));
    assertTrue(!isAllowed(rulesWithoutHash, "minecraft:ocean", tagsWithHash));
    assertTrue(!isAllowed(rulesWithoutHash, "minecraft:ocean", tagsWithoutHash));
  }

  @Test
  public void allowedByNameReturnsAllowed() {
    var rules = SelectionRules.builder().allowNames("minecraft:plains", "minecraft:forest").build();

    var result = evaluate(rules, "minecraft:plains", Collections.emptySet());
    assertEquals(SelectionRules.Match.ALLOWED, result);
  }

  @Test
  public void allowedByModReturnsAllowed() {
    var rules = SelectionRules.builder().allowMods("minecraft").build();

    var result = evaluate(rules, "minecraft:swamp", Collections.emptySet());
    assertEquals(SelectionRules.Match.ALLOWED, result);
  }

  @Test
  public void allowedByTagReturnsAllowed() {
    var rules = SelectionRules.builder().allowTags("#minecraft:is_forest").build();

    var tags = Set.of("#minecraft:is_forest", "#minecraft:is_overworld");

    var result = evaluate(rules, "minecraft:forest", tags);
    assertEquals(SelectionRules.Match.ALLOWED, result);
  }

  @Test
  public void blockingTakesPriorityOverAllowing() {
    var rules =
        SelectionRules.builder().allowMods("minecraft").blockNames("minecraft:desert").build();

    var result = evaluate(rules, "minecraft:desert", Collections.emptySet());
    assertEquals(SelectionRules.Match.BLOCKED, result);

    var forestResult = evaluate(rules, "minecraft:forest", Collections.emptySet());
    assertEquals(SelectionRules.Match.ALLOWED, forestResult);
  }

  @Test
  public void blockOverridesAllowForSameName() {
    var rules =
        SelectionRules.builder()
            .allowNames("minecraft:desert")
            .blockNames("minecraft:desert")
            .build();

    assertEquals(
        SelectionRules.Match.BLOCKED, evaluate(rules, "minecraft:desert", Collections.emptySet()));
  }

  @Test
  public void allowRulesBlockUnmatchedBiomes() {
    var rules = SelectionRules.builder().allowNames("minecraft:plains").build();

    var result = evaluate(rules, "minecraft:desert", Collections.emptySet());
    assertEquals(SelectionRules.Match.BLOCKED, result);
  }

  @Test
  public void complexRulesWorkCorrectly() {

    var rules =
        SelectionRules.builder()
            .allowMods("minecraft")
            .blockTags("#minecraft:is_ocean")
            .allowNames("terralith:crystal_ocean")
            .build();

    assertTrue(isAllowed(rules, "minecraft:forest", Set.of("#minecraft:is_forest")));

    assertFalse(isAllowed(rules, "minecraft:ocean", Set.of("#minecraft:is_ocean")));

    assertTrue(isAllowed(rules, "terralith:crystal_ocean", Set.of("#minecraft:is_ocean")));

    assertFalse(isAllowed(rules, "terralith:volcanic_crater", Collections.emptySet()));
  }

  @Test
  public void hasRulesReturnsFalseForEmpty() {
    assertFalse(hasRules(null));
    assertFalse(hasRules(SelectionRules.empty()));
  }

  @Test
  public void hasRulesReturnsTrueWithAnyRule() {
    assertTrue(hasRules(SelectionRules.builder().allowMods("minecraft").build()));
    assertTrue(hasRules(SelectionRules.builder().allowTags("#test").build()));
    assertTrue(hasRules(SelectionRules.builder().allowNames("minecraft:test").build()));
    assertTrue(hasRules(SelectionRules.builder().blockMods("test").build()));
    assertTrue(hasRules(SelectionRules.builder().blockTags("#test").build()));
    assertTrue(hasRules(SelectionRules.builder().blockNames("test:test").build()));
  }

  @Test
  public void defaultNamespaceIsMinecraft() {
    var rules = SelectionRules.builder().allowMods("minecraft").build();

    var result = evaluate(rules, "plains", Collections.emptySet());
    assertEquals(SelectionRules.Match.ALLOWED, result);
  }

  @Test
  public void isAllowedConvenienceMethod() {
    var rules = SelectionRules.builder().blockNames("minecraft:desert").build();

    assertFalse(isAllowed(rules, "minecraft:desert", Collections.emptySet()));
    assertTrue(isAllowed(rules, "minecraft:plains", Collections.emptySet()));
  }

  private SelectionRules.Match evaluate(SelectionRules rules, String id, Set<String> tags) {
    if (rules == null) {
      return SelectionRules.Match.NO_RULES;
    }
    return rules.evaluate(id, tags);
  }

  private boolean isAllowed(SelectionRules rules, String id, Set<String> tags) {
    return evaluate(rules, id, tags) != SelectionRules.Match.BLOCKED;
  }

  private boolean hasRules(SelectionRules rules) {
    return rules != null && rules.hasRules();
  }
}
