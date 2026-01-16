package com.terrasect.common.helpers;

import com.terrasect.common.definition.SelectionRules;
import java.util.Set;

public final class StructureHandler {

  public enum FilterResult {
    ALLOWED,
    BLOCKED,
    NO_RULES
  }

  public static FilterResult checkStructure(
      SelectionRules rules, String structureId, Set<String> structureTags) {
    if (rules == null || (!rules.hasAllowRules() && !rules.hasBlockRules())) {
      return FilterResult.NO_RULES;
    }

    if (rules.isNameAllowed(structureId)) {
      return FilterResult.ALLOWED;
    }
    if (rules.isNameBlocked(structureId)) {
      return FilterResult.BLOCKED;
    }

    if (rules.hasBlockedTag(structureTags)) {
      return FilterResult.BLOCKED;
    }

    String modNamespace = extractNamespace(structureId);
    if (rules.isModBlocked(modNamespace)) {
      return FilterResult.BLOCKED;
    }

    if (!rules.hasAllowRules()) {
      return FilterResult.NO_RULES;
    }

    if (rules.hasAllowedTag(structureTags)) {
      return FilterResult.ALLOWED;
    }
    if (rules.isModAllowed(modNamespace)) {
      return FilterResult.ALLOWED;
    }

    return FilterResult.BLOCKED;
  }

  public static boolean isAllowed(
      SelectionRules rules, String structureId, Set<String> structureTags) {
    FilterResult result = checkStructure(rules, structureId, structureTags);
    return result != FilterResult.BLOCKED;
  }

  private static String extractNamespace(String resourceId) {
    if (resourceId == null || resourceId.isEmpty()) return "minecraft";
    var colonIndex = resourceId.indexOf(':');
    if (colonIndex > 0) {
      return resourceId.substring(0, colonIndex);
    }
    return "minecraft";
  }

  private StructureHandler() {}
}
