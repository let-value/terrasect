package com.terrasect.common.definition;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record StructureRules(SelectionRules selection, Set<String> requiredStructures) {
  public StructureRules {
    if (selection == null) selection = SelectionRules.empty();
    if (requiredStructures == null) requiredStructures = Collections.emptySet();
  }

  public boolean hasFilters() {
    return selection.hasRules();
  }

  public static StructureRules empty() {
    return builder().buildRules();
  }

  public StructureRules resolveWithParent(StructureRules parent) {
    if (parent == null) return this;

    var mergedSelection = selection.resolveWithParent(parent.selection);
    var mergedRequired = new LinkedHashSet<String>(parent.requiredStructures);
    mergedRequired.addAll(requiredStructures);
    mergedRequired.removeAll(mergedSelection.blockedNames());

    return new StructureRules(mergedSelection, Collections.unmodifiableSet(mergedRequired));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends SelectionRules.BuilderBase<Builder> {
    private final Set<String> requiredStructures = new LinkedHashSet<>();

    @Override
    protected Builder self() {
      return this;
    }

    public Builder requireStructures(String... structures) {
      Collections.addAll(requiredStructures, structures);
      return this;
    }

    public Builder selection(SelectionRules selection) {
      copyFrom(selection);
      return this;
    }

    public StructureRules buildRules() {
      return new StructureRules(
          super.build(), Collections.unmodifiableSet(new LinkedHashSet<>(requiredStructures)));
    }
  }
}
