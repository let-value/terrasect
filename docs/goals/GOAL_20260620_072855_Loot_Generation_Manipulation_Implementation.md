## Task
**Date:** 20260620
**Submitted By:** Hermes orchestrator
**Status:** COMPLETED

### Request
Implement loot generation manipulation in the dedicated `feature/loot-generation-manipulation` worktree, using both prior investigation runs as source of truth.

### Context
- Project: Terrasect Minecraft mod
- Repo root: /home/alex/terrasect/.worktrees/loot-generation-manipulation
- Base branch: reborn
- Branch/worktree: feature/loot-generation-manipulation
- Existing PR: #57
- Investigation run 1 established:
  - Minecraft loot generation flows through `LootContextParams.ORIGIN`
  - `RandomizableContainer.unpackLootTable(...)` is where container position enters loot context
  - Fabric runtime hook: `LootTableEvents.MODIFY_DROPS`
  - NeoForge runtime hook: `IGlobalLootModifier`
  - Fallback: mixin on `LootTable.getRandomItemsRaw`
- Investigation run 2 established:
  - Runtime hook can stay allocation-free per drop
  - One per-event region lookup is unavoidable and acceptable
  - Use a prebuilt item identity/tag index at world-load time
  - Modded items should travel through the same registry/tag path as vanilla items
  - Consider strict/default-allow semantics for region loot rules if needed
- This implementation should follow those conclusions rather than re-litigating them
- Keep durable progress, blockers, and verification in this file

### Acceptance criteria
- Add the common loot-region model and compiled lookup path
- Wire the runtime loot filter through Fabric and NeoForge hooks
- Preserve low-allocation hot-path behavior
- Preserve mod interoperability for vanilla and modded items
- Run targeted verification, then update this file with real results and final status
- Refresh PR #57 body if the implementation scope changes materially

### Run metadata
- Provider: Claude Code (delegated)
- Session id: TBD
- Retry count: 0
- Retry after: TBD
- Blocked reason: TBD

---
**Sub-agent instruction:** This file contains the implementation brief. Read it and both prior loot investigation goal files in full before coding:
- `docs/goals/GOAL_20260620_065520_Loot_Generation_Manipulation_Investigation.md`
- `docs/goals/GOAL_20260620_071605_Loot_Generation_Manipulation_Hot_Path_and_Interop_Followup.md`

Implement the feature in this worktree, keep stdout terse, update this goal file with progress, blockers, and verification, and leave the worktree in a reviewable state. If broader repo protocol is needed, consult `docs/KNOWLEDGE_BASE.md`.

## Response
**Completed by:** Claude Code (claude-sonnet-4-6)
**Date:** 20260620

### Files created / modified

| File | Change |
|---|---|
| `common/.../definition/Region.kt` | Added `loot: SelectionConstraints? = null` field |
| `common/.../lookup/CompiledLootLookup.kt` | **New** — pre-computes `IdentityHashMap<Region, IdentityHashMap<Item, Boolean>>` from item registry at world-load time; `allowDrop(region, item)` is two identity-map lookups |
| `common/.../generation/DimensionContext.kt` | Added `lootLookup: CompiledLootLookup?` built alongside `structureLookup` in constructor |
| `common/.../handler/LootHandler.kt` | **New** `object`; `filterDrops(context, drops)` resolves origin → chunk-grid region lookup (fallback to traversal) → `drops.removeIf { !lookup.allowDrop(region, it.item) }` |
| `common/.../instrumentation/TerrasectMetrics.kt` | Added `LOOT` scope to `TerrasectInstrScope`, `LOOT_APPLIED` to `TerrasectMetricEvent`, `loot` to `TerrasectInstr` |
| `fabric/.../TerrasectFabric.kt` | Registered `LootTableEvents.MODIFY_DROPS` → `LootHandler.filterDrops` |
| `neoforge/.../RegionLootModifier.kt` | **New** — extends `LootModifier(emptyArray())`; `CODEC = MapCodec.unit(::RegionLootModifier)`; delegates to `LootHandler.filterDrops` |
| `neoforge/.../TerrasectNeoForge.kt` | Added `DeferredRegister<MapCodec<out IGlobalLootModifier>>`, registered `"region_loot_filter"` codec on mod event bus |
| `neoforge/src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json` | **New** — lists `terrasect:region_loot_filter` |
| `neoforge/src/main/resources/data/terrasect/loot_modifiers/region_loot_filter.json` | **New** — `"type": "terrasect:region_loot_filter"`, `"conditions": []` |

### Design decisions

- **Pre-computed decisions** (like `CompiledMobLookup`): index is `IdentityHashMap<Region, IdentityHashMap<Item, Boolean>>` computed at world-load. Hot path is two identity-map lookups — zero allocation per drop beyond the predicate lambda captured by `removeIf`.
- **Single region lookup per event invocation**: chunk-grid path first (array index into `PalettedGrid`), traversal fallback if chunk context is unavailable.
- **`MapCodec.unit(::RegionLootModifier)`** for NeoForge: no-field codec since the modifier is stateless. JSON only needs `"type"` field.
- **Fabric cast**: `drops as MutableList` required since Fabric's Java API types `drops` as `List<ItemStack>` but passes a mutable `ArrayList`.

### Verification

```
./gradlew :common:compileKotlin   → BUILD SUCCESSFUL
./gradlew :fabric:compileKotlin   → BUILD SUCCESSFUL
./gradlew :neoforge:compileKotlin → BUILD SUCCESSFUL
./gradlew :common:build           → BUILD SUCCESSFUL (all unit tests pass)
./gradlew spotlessCheck           → BUILD SUCCESSFUL (no format violations)
```

### Blockers: none

### Next steps

- Add `loot` field to `RegionDefinition` deserialization path if region configs are loaded from JSON/data-packs (not in scope of this PR).
- Add a gametest exercising `LootHandler.filterDrops` (parallel to `StructureConstraintGameTest`).
- PR #57 scope unchanged — implementation matches original plan.
