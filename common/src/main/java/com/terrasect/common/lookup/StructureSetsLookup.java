package com.terrasect.common.lookup;

import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.StructureRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public final class StructureSetsLookup {

  public static final class FilteredSets {

    private final List<Holder<StructureSet>> sets;

    private final IdentityHashMap<StructureSet, List<StructureSet.StructureSelectionEntry>>
        entriesBySet;

    private FilteredSets(
        List<Holder<StructureSet>> sets,
        IdentityHashMap<StructureSet, List<StructureSet.StructureSelectionEntry>> entriesBySet) {
      this.sets = sets;
      this.entriesBySet = entriesBySet;
    }

    public List<Holder<StructureSet>> sets() {
      return sets;
    }

    public List<StructureSet.StructureSelectionEntry> getEntries(StructureSet set) {
      var filtered = entriesBySet.get(set);
      return filtered != null ? filtered : set.structures();
    }
  }

  private final FilteredSets defaultSets;

  private final Map<StructureRules, FilteredSets> filteredByRules;

  private StructureSetsLookup(
      FilteredSets defaultSets, Map<StructureRules, FilteredSets> filteredByRules) {
    this.defaultSets = defaultSets;
    this.filteredByRules = filteredByRules;
  }

  public FilteredSets getSets(StructureRules rules) {
    if (rules == null) {
      return defaultSets;
    }
    return filteredByRules.getOrDefault(rules, defaultSets);
  }

  public static StructureSetsLookup build(
      List<Holder<StructureSet>> possibleSets, Region root, RegistryAccess registryAccess) {
    StructureLookup structureLookup = StructureLookup.build(registryAccess);
    return build(possibleSets, root, structureLookup);
  }

  public static StructureSetsLookup build(
      List<Holder<StructureSet>> possibleSets, Region root, StructureLookup structureLookup) {

    FilteredSets defaultSets = buildDefault(possibleSets);

    if (root == null) {
      return new StructureSetsLookup(defaultSets, Collections.emptyMap());
    }

    var filtered = new IdentityHashMap<StructureRules, FilteredSets>();

    collectAndFilter(root, possibleSets, structureLookup, filtered);

    return new StructureSetsLookup(defaultSets, filtered);
  }

  private static FilteredSets buildDefault(List<Holder<StructureSet>> possibleSets) {

    return new FilteredSets(possibleSets, new IdentityHashMap<>());
  }

  private static void collectAndFilter(
      Region region,
      List<Holder<StructureSet>> possibleSets,
      StructureLookup structureLookup,
      Map<StructureRules, FilteredSets> filtered) {

    var rules = region.definition().structures();

    if (rules != null && !filtered.containsKey(rules) && hasActiveRules(rules)) {
      FilteredSets filteredSets = filterSets(possibleSets, rules, structureLookup);
      filtered.put(rules, filteredSets);
    }

    for (Region child : region.children()) {
      collectAndFilter(child, possibleSets, structureLookup, filtered);
    }
  }

  private static boolean hasActiveRules(StructureRules rules) {
    if (rules == null) return false;
    var selection = rules.selection();
    return (selection != null && (selection.hasAllowRules() || selection.hasBlockRules()))
        || !rules.requiredStructures().isEmpty();
  }

  private static FilteredSets filterSets(
      List<Holder<StructureSet>> sets, StructureRules rules, StructureLookup lookup) {

    var filteredSetList = new ArrayList<Holder<StructureSet>>(sets.size());
    var entriesBySet =
        new IdentityHashMap<StructureSet, List<StructureSet.StructureSelectionEntry>>();

    for (Holder<StructureSet> setHolder : sets) {
      var set = setHolder.value();
      List<StructureSet.StructureSelectionEntry> original = set.structures();

      var filteredEntries = filterEntries(original, rules, lookup);

      if (!filteredEntries.isEmpty()) {
        filteredSetList.add(setHolder);

        if (filteredEntries.size() < original.size()) {
          entriesBySet.put(set, filteredEntries);
        }
      }
    }

    return new FilteredSets(filteredSetList, entriesBySet);
  }

  private static List<StructureSet.StructureSelectionEntry> filterEntries(
      List<StructureSet.StructureSelectionEntry> entries,
      StructureRules rules,
      StructureLookup lookup) {

    List<StructureSet.StructureSelectionEntry> filtered = null;

    for (var i = 0; i < entries.size(); i++) {
      var entry = entries.get(i);
      var structure = entry.structure().value();

      if (lookup.isAllowed(structure, rules)) {
        if (filtered != null) {
          filtered.add(entry);
        }
      } else {

        if (filtered == null) {
          filtered = new ArrayList<>(entries.size());
          for (var j = 0; j < i; j++) {
            filtered.add(entries.get(j));
          }
        }
      }
    }

    return filtered != null ? filtered : entries;
  }
}
