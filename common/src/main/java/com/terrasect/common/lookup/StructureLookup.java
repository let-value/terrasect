package com.terrasect.common.lookup;

import com.terrasect.common.compat.ResourceKeyCompat;
import com.terrasect.common.compat.StructureCompat;
import com.terrasect.common.definition.SelectionRules;
import com.terrasect.common.definition.StructureRules;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class StructureLookup {

  public record Entry(String id, Set<String> tags) {}

  private static final StructureMask NO_RULES = new StructureMask(null);

  private final StructureEntry[] entries;
  private final IdentityHashMap<Structure, Integer> indexByStructure;
  private final IdentityHashMap<StructureRules, StructureMask> masks;

  private StructureLookup(
      StructureEntry[] entries,
      IdentityHashMap<Structure, Integer> indexByStructure,
      IdentityHashMap<StructureRules, StructureMask> masks) {
    this.entries = entries;
    this.indexByStructure = indexByStructure;
    this.masks = masks;
  }

  public Entry get(Structure structure) {
    var index = indexByStructure.get(structure);
    if (index == null) {
      return null;
    }
    return entries[index].entry;
  }

  public boolean isAllowed(Structure structure, StructureRules rules) {
    var mask = getMask(rules);
    if (mask == NO_RULES) {
      return true;
    }

    var index = indexByStructure.get(structure);
    if (index == null) {
      return true;
    }

    return mask.allowed[index];
  }

  public static StructureLookup build(RegistryAccess registryAccess) {
    var registry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
    return build(registry);
  }

  public static StructureLookup build(Registry<Structure> registry) {
    var entries = buildEntries(registry);
    var indexByStructure = buildIndex(entries);
    var masks = new IdentityHashMap<StructureRules, StructureMask>();
    return new StructureLookup(entries, indexByStructure, masks);
  }

  public static StructureLookup build(HolderLookup.RegistryLookup<Structure> registryLookup) {
    var entries = buildEntries(registryLookup);
    var indexByStructure = buildIndex(entries);
    var masks = new IdentityHashMap<StructureRules, StructureMask>();
    return new StructureLookup(entries, indexByStructure, masks);
  }

  private static StructureEntry[] buildEntries(Registry<Structure> registry) {
    var size = registry.size();
    if (size == 0) {
      return new StructureEntry[0];
    }

    StructureEntry[] entries = new StructureEntry[size];
    var index = 0;
    for (var entry : registry.entrySet()) {
      var key = entry.getKey();
      var structure = entry.getValue();
      var holder = registry.wrapAsHolder(structure);
      Set<String> tags = holder != null ? buildTags(holder) : Collections.emptySet();
      String id = ResourceKeyCompat.getKeyId(key);

      entries[index] = new StructureEntry(structure, new Entry(id, tags));
      index++;
    }

    if (index == entries.length) {
      return entries;
    }

    StructureEntry[] trimmed = new StructureEntry[index];
    System.arraycopy(entries, 0, trimmed, 0, index);
    return trimmed;
  }

  private static StructureEntry[] buildEntries(
      HolderLookup.RegistryLookup<Structure> registryLookup) {
    var holders = registryLookup.listElements().toList();
    var size = holders.size();
    if (size == 0) {
      return new StructureEntry[0];
    }

    StructureEntry[] entries = new StructureEntry[size];
    for (var i = 0; i < size; i++) {
      var holder = holders.get(i);
      var id = holder.unwrapKey().map(ResourceKeyCompat::getKeyId).orElse("unknown");
      entries[i] = new StructureEntry(holder.value(), new Entry(id, buildTags(holder)));
    }

    return entries;
  }

  private static IdentityHashMap<Structure, Integer> buildIndex(StructureEntry[] entries) {
    var indexByStructure = new IdentityHashMap<Structure, Integer>(entries.length);
    for (var i = 0; i < entries.length; i++) {
      indexByStructure.put(entries[i].structure, i);
    }
    return indexByStructure;
  }

  private static StructureMask buildMask(StructureEntry[] entries, StructureRules rules) {
    if (rules == null || entries.length == 0 || !rules.hasFilters()) {
      return NO_RULES;
    }

    boolean[] allowed = new boolean[entries.length];
    var selection = rules.selection();
    var required = rules.requiredStructures();

    for (var i = 0; i < entries.length; i++) {
      var entry = entries[i].entry;
      if (!required.isEmpty() && required.contains(entry.id())) {
        allowed[i] = true;
        continue;
      }

      allowed[i] = isAllowed(selection, entry);
    }

    return new StructureMask(allowed);
  }

  private static Set<String> buildTags(Holder<Structure> structure) {
    var tags = new HashSet<String>();
    try {
      StructureCompat.getTags(structure).forEach(tag -> tags.add("#" + tag.location().toString()));
    } catch (IllegalStateException ignored) {

    }
    return tags;
  }

  private StructureMask getMask(StructureRules rules) {
    if (rules == null) {
      return NO_RULES;
    }

    var mask = masks.get(rules);
    if (mask == null) {
      mask = buildMask(entries, rules);
      masks.put(rules, mask);
    }
    return mask;
  }

  private static boolean isAllowed(SelectionRules selection, Entry entry) {
    if (selection == null || (!selection.hasAllowRules() && !selection.hasBlockRules())) {
      return true;
    }

    var id = entry.id();
    var tags = entry.tags();

    if (selection.isNameAllowed(id)) {
      return true;
    }
    if (selection.isNameBlocked(id)) {
      return false;
    }

    if (selection.hasBlockedTag(tags)) {
      return false;
    }

    var namespace = extractNamespace(id);
    if (selection.isModBlocked(namespace)) {
      return false;
    }

    if (!selection.hasAllowRules()) {
      return true;
    }

    if (selection.hasAllowedTag(tags)) {
      return true;
    }
    return selection.isModAllowed(namespace);
  }

  private static String extractNamespace(String resourceId) {
    if (resourceId == null || resourceId.isEmpty()) return "minecraft";
    var colonIndex = resourceId.indexOf(':');
    if (colonIndex > 0) {
      return resourceId.substring(0, colonIndex);
    }
    return "minecraft";
  }

  private static final class StructureEntry {
    private final Structure structure;
    private final Entry entry;

    private StructureEntry(Structure structure, Entry entry) {
      this.structure = structure;
      this.entry = entry;
    }
  }

  private static final class StructureMask {
    private final boolean[] allowed;

    private StructureMask(boolean[] allowed) {
      this.allowed = allowed;
    }
  }
}
