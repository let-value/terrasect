## Task
**Date:** 20260620
**Submitted By:** Hermes orchestrator
**Status:** COMPLETED

### Request
Run Claude Code again in the dedicated loot-generation-manipulation worktree and extend the investigation with these questions:

- The mod loader hook path is probably less flexible, so can we bind region/chunk context at allocation time and avoid allocations in the hot-path loot hooks (`LootTableEvents.MODIFY_DROPS` / `IGlobalLootModifier`)?
- How should we handle interoperability with other mods that add their own items so we can still enforce region-based loot rules on modded drops?

### Context
- Project: Terrasect Minecraft mod
- Repo root: /home/alex/terrasect/.worktrees/loot-generation-manipulation
- Base branch: reborn
- Branch/worktree: feature/loot-generation-manipulation
- Existing PR: #57
- Previous investigation already established:
  - `LootContextParams.ORIGIN` carries position into loot generation
  - Fabric hook: `LootTableEvents.MODIFY_DROPS`
  - NeoForge hook: `IGlobalLootModifier`
  - Fallback: mixin on `LootTable.getRandomItemsRaw`
  - Region lookup can be derived from `ServerLevel` + origin without `ChunkAccess`
- The new investigation should focus on allocation-free runtime design and cross-mod loot enforcement
- Keep durable findings, blockers, and next steps in this file

### Acceptance criteria
- Claude Code runs in the dedicated worktree and records the follow-up investigation
- The response identifies whether the current hook path can be made allocation-free or where unavoidable allocations remain
- The response explains the best way to preserve interoperability with other mods' loot items while still enforcing region rules
- The response records concrete next implementation steps, including any loader-specific or common-code changes
- The goal file is updated to COMPLETED or BLOCKED with a clear reason

### Run metadata
- Provider: Claude Code (delegated)
- Session id: TBD
- Retry count: 0
- Retry after: TBD
- Blocked reason: TBD

---
**Sub-agent instruction:** This file contains everything you need. Read it in full. Perform the work. Write your complete response under `## Response` below before finishing. Update `Status` to `COMPLETED` when done. If broader protocol understanding is needed, read `docs/KNOWLEDGE_BASE.md`.

## Response
**Completed by:** Claude Code (claude-sonnet-4-6)
**Date:** 20260620

---

### Investigation scope

Sources examined:
- `minecraft/net/minecraft/world/level/storage/loot/LootContext.java` — hook entry point
- `minecraft/net/minecraft/world/level/storage/loot/LootParams.java` — context carrier
- `minecraft/net/minecraft/util/context/ContextMap.java` — param map impl
- `minecraft/net/minecraft/core/Holder.java` — item registry holder (tags, key)
- `minecraft/net/minecraft/resources/Identifier.java` — namespace field
- `minecraft/net/minecraft/world/level/Level.java` — chunk access
- `fabric-loot-api-v3-2.0.20-sources.jar` → `LootTableEvents.java` — Fabric hook signature
- `neoforge-21.11.36-beta-sources.jar` → `IGlobalLootModifier.java` — NeoForge hook signature
- `common/…/generation/Traverser.kt` — ThreadLocal singleton design
- `common/…/generation/ChunkContext.kt` + `ChunkAccessMixin.java` — pre-computed region grid
- `common/…/definition/SelectionConstraints.kt` — existing evaluation logic
- `common/…/lookup/CompiledStructureLookup.kt` — reference for CompiledLootLookup design

---

### Q1: Allocation-free hot path

**Short answer:** The filter hot path CAN be made fully allocation-free per-drop. One lightweight cost per event invocation (a chunk-map lookup + array index) is unavoidable and replaces what would otherwise be a full SDF traversal.

#### What ContextMap.getOptional allocates (nothing)

`ContextMap.getOptional()` is a plain `IdentityHashMap.get(contextKey)` — no allocation. `context.getLevel()` is a field-chain through `params.level` — no allocation. `getOptionalParameter(ORIGIN)` returns the already-allocated `Vec3` stored in the map — no additional allocation.

#### Where allocations appear in the naive design

| Call | Allocates? | Notes |
|---|---|---|
| `context.getOptionalParameter(LootContextParams.ORIGIN)` | No | IdentityHashMap.get |
| `context.getLevel()` | No | field access chain |
| `stack.getItem()` | No | stored field |
| `item.builtInRegistryHolder()` | No | stored field on Item |
| `holder.key().identifier()` | No | stored fields |
| `identifier.getNamespace()` | No | stored String field |
| `identifier.toString()` | **Yes** | constructs `"namespace:path"` String per call |
| `holder.tags().stream()` | **Yes** | `Holder.Reference.tags()` calls `boundTags().stream()` — allocates a Stream per item per call |
| `Optional<ResourceKey>` from `unwrapKey()` | **Yes** | wraps key in Optional |
| `Traverser.traverse()` | No | ThreadLocal singleton `TraversalStep`, fully reused |
| `level.getChunk(cx, cz)` (loaded chunk) | No | chunk-map lookup (Long2ObjectLinkedOpenHashMap), no allocation |
| `(chunk as ChunkAccessExtender).terrasect$getContext()` | No | mixin field access |
| `chunkContext.getRegion(blockX, blockZ)` | No | `PalettedGrid` array index |

#### Design that eliminates all per-drop allocations

Parallel `CompiledStructureLookup` with a pre-built item identity index:

```kotlin
// Built once at DimensionContext.register() time — all mod items registered before world load
class CompiledLootLookup private constructor(
    private val index: IdentityHashMap<Item, LootItemEntry>,
) {
    fun allowDrop(constraints: SelectionConstraints, stack: ItemStack): Boolean {
        val item = stack.item
        val entry = index[item]  // IdentityHashMap.get — no allocation
        val id = entry?.id       // pre-computed String — no allocation
        val tags = entry?.tags   // pre-built Set<String> — no allocation
        return constraints.evaluate(id, tags)
    }

    companion object {
        fun build(root: Region, registry: RegistryAccess.Frozen): CompiledLootLookup? {
            if (!anyRegionHasLoot(root)) return null
            val itemRegistry = registry.lookupOrThrow(Registries.ITEM)
            val index = IdentityHashMap<Item, LootItemEntry>()
            itemRegistry.listElements().forEach { holder ->
                val item = holder.value()
                val id = holder.key().identifier().toString()       // allocated once
                val tags = holder.tags()
                    .map { it.identifier().toString() }             // allocated once
                    .toHashSet()
                index[item] = LootItemEntry(id, tags)
            }
            return CompiledLootLookup(index)
        }
    }
}

private data class LootItemEntry(val id: String?, val tags: Set<String>)
```

At event time:
```
1× ContextMap.get(ORIGIN)         → no alloc
1× params.level field access      → no alloc
1× level.getChunk(cx, cz)         → chunk-map lookup, no alloc (chunk already loaded)
1× mixin field read (ChunkContext) → no alloc
1× PalettedGrid array index        → no alloc
N× IdentityHashMap.get(item)       → no alloc per drop
N× Set.contains() in evaluate()    → no alloc per drop
```

#### Binding region/chunk context "at allocation time"

"Binding at allocation time" in the strongest sense — storing the region alongside the `LootContext` when it is constructed — requires a Mixin on `LootContext.Builder.create()` or `LootTable.getRandomItemsRaw`, because neither the Fabric `MODIFY_DROPS` event nor `IGlobalLootModifier` fires during context construction.

However, in practice this distinction does not matter: both hooks deliver the full drops list in one call, so the region lookup happens **once per event invocation** regardless. The chunk-based path (above) is a single array-index lookup into a pre-built grid — effectively free. There is no "per-drop" context resolution to eliminate.

The chunk-grid approach _is_ binding at chunk-load time: the region was traversed for every block in the chunk when it loaded, and the result is stored in `ChunkContext.regions` (a `PalettedGrid<Region>`). The loot event reads a single cell from that grid. This is the closest we can get to pre-binding without intercepting `LootContext` construction via Mixin.

#### Unavoidable costs after the above design

- **One chunk-map lookup** per loot event invocation. For an already-loaded chunk this is a `Long2ObjectLinkedOpenHashMap.get()` — fast but not free. There is no way to eliminate this without storing the region in the `LootContext` via Mixin.
- **Fallback traversal** when the chunk lookup returns null (e.g., for loot fired during worldgen before chunks fully load): `Traverser.traverse()` is ThreadLocal and allocation-free, but costs CPU for SDF computation.
- **Instrumentation lambdas** in `TerrasectInstr.count(...)`: the tag-value lambdas capture strings that may involve short-lived objects. These should remain conditional on the metrics backend being active.

---

### Q2: Cross-mod interoperability

**Short answer:** `SelectionConstraints` already covers all cases needed for modded-item enforcement. The pre-built item index captures every registered modded item's tags and namespace at world load. No special handling for "modded vs vanilla" is needed — the namespace mechanism handles it.

#### How SelectionConstraints handles modded items

`SelectionConstraints.evaluate(resourceId, tags)` evaluation order:

1. `blockedNames.contains(resourceId)` → exact item block, e.g. `"botania:overgrowth_seed"`
2. `allowedNames.contains(resourceId)` → exact item allow
3. `blockedTags.any(tags::contains)` → tag-based block, e.g. `"c:tools"`
4. `allowedTags.any(tags::contains)` → tag-based allow
5. `blockedMods.contains(namespace)` → namespace block, e.g. `"botania"`
6. `allowedMods.contains(namespace)` → namespace allow
7. **default: `return true`** (pass-through / interop-safe)

The namespace is extracted from `resourceId` by `extractNamespace()` — a simple `indexOf(':')` + `take()` on a pre-computed String. No allocation at hot-path time because the `resourceId` string was pre-built in the index.

#### Tag pre-resolution covers all modded items

The `CompiledLootLookup` index is built from `registry.lookupOrThrow(Registries.ITEM)`, which at world load time includes all items from all loaded mods. Tags are resolved from the live server registry (the same registry used for data-pack tag resolution), so:
- Items from mod A tagged with `c:tools` by mod B's tag file are correctly captured
- Data-pack-overridden tags are reflected correctly
- No special-casing of modded vs vanilla items needed

#### Common-tag interop patterns (recommended)

```kotlin
// Block loot category across all mods
loot = SelectionConstraints.builder()
    .blockTags("c:tools", "c:weapons")
    .build()

// Allow only food drops from any mod
loot = SelectionConstraints.builder()
    .allowTags("c:foods")
    .blockMods("*")   // see gap note below
    .build()

// Suppress a specific mod's drops entirely
loot = SelectionConstraints.builder()
    .blockMods("create")
    .build()
```

`c:tools`, `c:weapons`, `c:foods`, etc. are the Fabric/NeoForge common-tag conventions maintained by the modding community. The pre-built index stores them as plain strings so `SelectionConstraints.evaluate()` does `Set.contains("c:tools")` — a single hash lookup.

#### Interop gap: deny-by-default mode

`SelectionConstraints.evaluate()` is **default-allow** (step 7 above). A region specifying `allowedMods = {"minecraft"}` would still pass modded items through, because the modded item hits neither the block lists nor the allow lists and falls to `return true`.

This gap should be addressed in the `CompiledLootLookup` layer by supporting a `strictMode` option at the `SelectionConstraints` level (or a `defaultAllow: Boolean` field). Region authors who want strict enforcement can set `defaultAllow = false`; the general interop-safe default remains `true`. This is a design decision for the implementation PR rather than a blocker.

**Current behavior is correct for the common case**: block specific things, allow everything else. Most region loot rules will be additive restrictions (block certain categories) rather than allow-lists.

#### Fallback for items not in pre-built index

All items are registered before world load in 1.21, so the index is complete. The edge case (dynamic items, creative-mode items not in the item registry) falls back to:
```kotlin
val ns = stack.item.builtInRegistryHolder().key().identifier().namespace  // no allocation
constraints.evaluate("$ns:unknown", emptySet())  // one String allocation only for the fallback path
```
This gracefully applies namespace-level rules with no tag matching.

---

### Revised implementation plan

The previous investigation's plan (steps 1–7) is correct. This follow-up adds precision on two steps:

| Step | File | Change |
|---|---|---|
| 1 | `common/.../definition/Region.kt` | Add `loot: SelectionConstraints? = null` |
| 2 | `common/.../lookup/CompiledLootLookup.kt` | **New** — `IdentityHashMap<Item, LootItemEntry>` built from `registry.lookupOrThrow(Registries.ITEM)` at world load; `allowDrop(constraints, stack): Boolean`; null-return from `build()` if no region has loot constraints |
| 3 | `common/.../generation/DimensionContext.kt` | Add `lootLookup: CompiledLootLookup?` built in `register()` alongside `structureLookup` |
| 4 | `common/.../handler/LootHandler.kt` | **New** `object`; `filterDrops(context: LootContext, drops: MutableList<ItemStack>)`: resolve origin → chunk-grid region lookup (fallback to traversal) → `drops.removeIf { !lootLookup.allowDrop(constraints, it) }` |
| 5 | `fabric/.../TerrasectFabric.kt` | `LootTableEvents.MODIFY_DROPS.register { _, context, drops -> LootHandler.filterDrops(context, drops) }` |
| 6 | `neoforge/.../TerrasectNeoForge.kt` | `IGlobalLootModifier.apply()` implementation delegating to `LootHandler.filterDrops`; register via `GlobalLootModifiers` event |
| 7 | `common/.../instrumentation/TerrasectMetrics.kt` | Add `LOOT_APPLIED` and `LOOT_FILTERED` event counters in `TerrasectMetricEvent` / `TerrasectInstr` |

**`DimensionContext.register()` signature extension**: pass `registry: RegistryAccess.Frozen` already present — no new parameters needed. `CompiledLootLookup.build(root, registry)` mirrors `CompiledStructureLookup.build(allSets, root, registry)`.

**LootHandler region lookup** (allocation-free path):
```kotlin
fun filterDrops(context: LootContext, drops: MutableList<ItemStack>) {
    val origin = context.getOptionalParameter(LootContextParams.ORIGIN) ?: return
    val level = context.level
    val dimId = ResourceKeyCompat.getKeyId(level.dimension())
    val ctx = DimensionContext.get(dimId) ?: return
    val lookup = ctx.lootLookup ?: return
    val blockX = origin.x.toInt()
    val blockZ = origin.z.toInt()
    val chunk = level.getChunk(blockX shr 4, blockZ shr 4)
    val region = (chunk as? ChunkAccessExtender)?.terrasect$getContext()?.getRegion(blockX, blockZ)
        ?: ctx.traverser.traverse(blockX, blockZ).region
    val constraints = region.loot ?: return
    drops.removeIf { !lookup.allowDrop(constraints, it) }
}
```

**NeoForge note**: `IGlobalLootModifier` is registered via JSON datapack (`neoforge:loot_modifiers/global_loot_modifiers`) and requires a `MapCodec`. The implementation class should extend `LootModifier` (which handles `LootItemCondition[]` boilerplate) and provide a `CODEC` registered in `NeoForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS` on the NeoForge mod bus. This is loader-specific wiring and belongs in `neoforge/` only.

---

### Blockers: none
