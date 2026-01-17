package com.terrasect.common.definition;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public record SelectionRules(
    Set<String> allowedMods,
    Set<String> allowedTags,
    Set<String> allowedNames,
    Set<String> blockedMods,
    Set<String> blockedTags,
    Set<String> blockedNames,
    boolean hasAllowRules,
    boolean hasBlockRules) {

  public enum Match {
    ALLOWED,
    BLOCKED,
    NO_RULES
  }

  public SelectionRules {
    if (allowedMods == null) allowedMods = Collections.emptySet();
    if (allowedTags == null) allowedTags = Collections.emptySet();
    if (allowedNames == null) allowedNames = Collections.emptySet();
    if (blockedMods == null) blockedMods = Collections.emptySet();
    if (blockedTags == null) blockedTags = Collections.emptySet();
    if (blockedNames == null) blockedNames = Collections.emptySet();
  }

  public boolean hasRules() {
    return hasAllowRules || hasBlockRules;
  }

  public Match evaluate(String resourceId, Set<String> tags) {
    if (!hasRules()) {
      return Match.NO_RULES;
    }

    var namespace = extractNamespace(resourceId);

    if (isNameBlocked(resourceId) || hasBlockedTag(tags) || isModBlocked(namespace)) {
      return Match.BLOCKED;
    }

    if (!hasAllowRules) {
      return Match.NO_RULES;
    }

    if (isNameAllowed(resourceId) || hasAllowedTag(tags) || isModAllowed(namespace)) {
      return Match.ALLOWED;
    }

    return Match.BLOCKED;
  }

  private static String extractNamespace(String resourceId) {
    if (resourceId == null || resourceId.isEmpty()) return "minecraft";
    var colonIndex = resourceId.indexOf(':');
    if (colonIndex > 0) {
      return resourceId.substring(0, colonIndex);
    }
    return "minecraft";
  }

  public boolean isNameAllowed(String biomeId) {
    return allowedNames.contains(biomeId);
  }

  public boolean isNameBlocked(String biomeId) {
    return blockedNames.contains(biomeId);
  }

  public boolean isModAllowed(String modNamespace) {
    return allowedMods.contains(modNamespace);
  }

  public boolean isModBlocked(String modNamespace) {
    return blockedMods.contains(modNamespace);
  }

  public boolean hasAllowedTag(Set<String> biomeTags) {
    if (biomeTags == null || biomeTags.isEmpty() || allowedTags.isEmpty()) return false;
    for (String tag : biomeTags) {
      if (allowedTags.contains(tag)) return true;
    }
    return false;
  }

  public boolean hasBlockedTag(Set<String> biomeTags) {
    if (biomeTags == null || biomeTags.isEmpty() || blockedTags.isEmpty()) return false;
    for (String tag : biomeTags) {
      if (blockedTags.contains(tag)) return true;
    }
    return false;
  }

  public static SelectionRules empty() {
    return builder().build();
  }

  public SelectionRules resolveWithParent(SelectionRules parent) {
    if (parent == null) return this;

    var mergedAllowedMods = merge(parent.allowedMods, allowedMods);
    var mergedAllowedTags = merge(parent.allowedTags, allowedTags);
    var mergedAllowedNames = merge(parent.allowedNames, allowedNames);

    var mergedBlockedMods = merge(parent.blockedMods, blockedMods);
    var mergedBlockedTags = merge(parent.blockedTags, blockedTags);
    var mergedBlockedNames = merge(parent.blockedNames, blockedNames);

    mergedAllowedMods.removeAll(mergedBlockedMods);
    mergedAllowedTags.removeAll(mergedBlockedTags);
    mergedAllowedNames.removeAll(mergedBlockedNames);

    var hasAllow =
        !mergedAllowedMods.isEmpty()
            || !mergedAllowedTags.isEmpty()
            || !mergedAllowedNames.isEmpty();
    var hasBlock =
        !mergedBlockedMods.isEmpty()
            || !mergedBlockedTags.isEmpty()
            || !mergedBlockedNames.isEmpty();

    return new SelectionRules(
        Collections.unmodifiableSet(mergedAllowedMods),
        Collections.unmodifiableSet(mergedAllowedTags),
        Collections.unmodifiableSet(mergedAllowedNames),
        Collections.unmodifiableSet(mergedBlockedMods),
        Collections.unmodifiableSet(mergedBlockedTags),
        Collections.unmodifiableSet(mergedBlockedNames),
        hasAllow,
        hasBlock);
  }

  private Set<String> merge(Set<String> parent, Set<String> child) {
    var merged = new HashSet<String>(Objects.requireNonNullElse(parent, Collections.emptySet()));
    merged.addAll(Objects.requireNonNullElse(child, Collections.emptySet()));
    return merged;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends BuilderBase<Builder> {
    @Override
    protected Builder self() {
      return this;
    }
  }

  public abstract static class BuilderBase<T extends BuilderBase<T>> {
    private final Set<String> allowedMods = new HashSet<>();
    private final Set<String> allowedTags = new HashSet<>();
    private final Set<String> allowedNames = new HashSet<>();
    private final Set<String> blockedMods = new HashSet<>();
    private final Set<String> blockedTags = new HashSet<>();
    private final Set<String> blockedNames = new HashSet<>();

    protected abstract T self();

    public T allowMods(String... mods) {
      Collections.addAll(allowedMods, mods);
      return self();
    }

    public T allowTags(String... tags) {
      for (String tag : tags) {

        allowedTags.add(tag);
        if (tag.startsWith("#")) {
          allowedTags.add(tag.substring(1));
        } else {
          allowedTags.add("#" + tag);
        }
      }
      return self();
    }

    public T allowNames(String... names) {
      Collections.addAll(allowedNames, names);
      return self();
    }

    public T blockMods(String... mods) {
      Collections.addAll(blockedMods, mods);
      return self();
    }

    public T blockTags(String... tags) {
      for (String tag : tags) {

        blockedTags.add(tag);
        if (tag.startsWith("#")) {
          blockedTags.add(tag.substring(1));
        } else {
          blockedTags.add("#" + tag);
        }
      }
      return self();
    }

    public T blockNames(String... names) {
      Collections.addAll(blockedNames, names);
      return self();
    }

    public SelectionRules build() {
      var hasAllow = !allowedMods.isEmpty() || !allowedTags.isEmpty() || !allowedNames.isEmpty();
      var hasBlock = !blockedMods.isEmpty() || !blockedTags.isEmpty() || !blockedNames.isEmpty();

      return new SelectionRules(
          Collections.unmodifiableSet(new HashSet<>(allowedMods)),
          Collections.unmodifiableSet(new HashSet<>(allowedTags)),
          Collections.unmodifiableSet(new HashSet<>(allowedNames)),
          Collections.unmodifiableSet(new HashSet<>(blockedMods)),
          Collections.unmodifiableSet(new HashSet<>(blockedTags)),
          Collections.unmodifiableSet(new HashSet<>(blockedNames)),
          hasAllow,
          hasBlock);
    }

    public T copyFrom(SelectionRules rules) {
      if (rules == null) return self();
      allowedMods.addAll(rules.allowedMods());
      allowedTags.addAll(rules.allowedTags());
      allowedNames.addAll(rules.allowedNames());
      blockedMods.addAll(rules.blockedMods());
      blockedTags.addAll(rules.blockedTags());
      blockedNames.addAll(rules.blockedNames());
      return self();
    }
  }
}
