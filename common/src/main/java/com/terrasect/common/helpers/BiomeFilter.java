package com.terrasect.common.helpers;

import com.terrasect.common.definition.SelectionRules;
import java.util.Set;

public final class BiomeFilter {

  public enum FilterResult {
    ALLOWED,

    BLOCKED,

    NO_RULES
  }

  public static FilterResult checkBiome(
      SelectionRules rules, String biomeId, Set<String> biomeTags) {
    if (rules == null) {
      return FilterResult.NO_RULES;
    }

    return switch (rules.evaluate(biomeId, biomeTags)) {
      case ALLOWED -> FilterResult.ALLOWED;
      case BLOCKED -> FilterResult.BLOCKED;
      case NO_RULES -> FilterResult.NO_RULES;
    };
  }

  public static boolean isAllowed(SelectionRules rules, String biomeId, Set<String> biomeTags) {
    FilterResult result = checkBiome(rules, biomeId, biomeTags);
    return result != FilterResult.BLOCKED;
  }

  public static boolean hasRules(SelectionRules rules) {
    return rules != null && rules.hasRules();
  }

  private BiomeFilter() {}
}
