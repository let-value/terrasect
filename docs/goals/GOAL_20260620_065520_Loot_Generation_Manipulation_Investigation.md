## Task
**Date:** 20260620
**Submitted By:** Hermes orchestrator
**Status:** COMPLETED

### Request
Just like we have a pr for mob constraints origin/feature/mob-spawn-constraints-implementation we need to create a separate worktree and pr for loot generation manipulation, the Minecraft has a loot table and its probably position based so our goal is to use that position and influence the generated loot based on the region constraints, delegate to claude code to start a new work tree, unpack Minecraft and begin investigating loot generation code in Minecraft

### Context
- Project: Terrasect Minecraft mod
- Repo root: /home/alex/terrasect/.worktrees/loot-generation-manipulation
- Base branch: reborn
- New worktree branch: feature/loot-generation-manipulation
- Existing related PR: #56, feature/mob-spawn-constraints-implementation, for the mob constraints runtime slice
- The goal of this task is investigation and setup, not implementation yet
- We want a separate workspace/worktree and a separate PR for loot generation manipulation
- The investigation should begin by unpacking Minecraft and locating the relevant loot generation code paths
- Likely areas of interest: loot table selection, world/position-sensitive loot generation, region-constraint integration points, and any data available at generation time that can be used to bias loot
- Keep durable progress, findings, blockers, and next steps in this goal file

### Acceptance criteria
- A dedicated worktree exists for the loot-generation task
- Claude Code has been tasked against that worktree to begin investigation
- Minecraft has been unpacked or the exact unpack command/result has been recorded
- The goal file contains a concise investigation summary with the relevant classes/files and the next likely implementation path
- A PR exists or has been prepared for the new branch/worktree
- The goal file status is updated to COMPLETED or BLOCKED with a clear blocker

### Run metadata
- Provider: Claude Code (delegated)
- Session id: TBD
- Retry count: 0
- Retry after: TBD
- Blocked reason: TBD

---
**Sub-agent instruction:** This file contains everything you need. Read it in full. Perform the work. Write your complete response under `## Response` below before finishing. Update the `Status` field to `COMPLETED` when done. If broader protocol understanding is needed, read `docs/KNOWLEDGE_BASE.md`.

## Response
**Completed by:** Claude Code (claude-sonnet-4-6)
**Date:** 20260620

### Unpack result
`./gradlew :fabric:genSources && ./gradlew :fabric:unpackMinecraft` — sources extracted to
`minecraft/` (repo root of this worktree).

---

### Loot generation pipeline overview

All classes live under `minecraft/net/minecraft/world/level/storage/loot/`.

| Class | Role |
|---|---|
| `LootParams` | Carrier: `ServerLevel` + `ContextMap` (typed params) + `luck`. Built via `LootParams.Builder`. |
| `LootContext` | Runtime wrapper: `LootParams` + `RandomSource` + registry access. Passed to every condition/function. |
| `LootTable` | Entry points: `fill(Container, LootParams, seed)`, `getRandomItems(LootParams)`, `getRandomItemsRaw(LootContext, Consumer)`. |
| `LootPool` | Holds entries + conditions + functions; `addRandomItems(consumer, context)` drives per-pool generation. |
| `LootContextParams.ORIGIN` | `ContextKey<Vec3>` — **the position key** present in CHEST, BLOCK, ENTITY, FISHING, ARCHAEOLOGY, VAULT, GIFT, and most other param sets. |
| `LootContextParamSets.CHEST` | Requires `ORIGIN` + optional `THIS_ENTITY`. Used by all container loot. |
| `RandomizableContainer` | Interface on chest block entities. `unpackLootTable(player)` (line 83) constructs `LootParams.Builder` with `ORIGIN = Vec3.atCenterOf(blockPos)` then calls `lootTable.fill()`. This is where the physical block position enters the pipeline. |

#### How position flows into chest loot
`RandomizableContainer.unpackLootTable(player)` at line 83:
```java
LootParams.Builder builder = new LootParams.Builder((ServerLevel)level)
    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockPos));
lootTable.fill(this, builder.create(LootContextParamSets.CHEST), getLootTableSeed());
```
Entity kill loot and block break loot use the same `ORIGIN` key with the entity/block position.

#### Existing position-aware predicate (proof-of-concept)
`predicates/LocationCheck.java` reads `ORIGIN`, adds an offset, then passes the adjusted coordinates to `LocationPredicate.matches(level, x, y, z)`. This confirms that position-based gating is already a first-class pattern in the existing loot condition system.

---

### Hook entrypoints

#### Fabric — `LootTableEvents.MODIFY_DROPS` (preferred)
Source: `net.fabricmc.fabric.api.loot.v3.LootTableEvents` (fabric-loot-api-v3 2.0.20).
```java
LootTableEvents.MODIFY_DROPS.register((entry, context, drops) -> {
    Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
    ServerLevel level = context.getLevel();
    // manipulate drops list in-place
});
```
Called after the table finishes generating. Receives the live `LootContext` — no Mixin needed. This is the cleanest runtime hook.

#### NeoForge — `IGlobalLootModifier` (equivalent)
Source: `net.neoforged.neoforge.common.loot.IGlobalLootModifier`.
```java
ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext context)
```
Same `LootContext` access. Registered via JSON datapack (`neoforge:loot_modifiers/global_loot_modifiers`).

#### Fallback (both loaders) — Mixin on `LootTable.getRandomItemsRaw`
`@WrapOperation` or `@Inject(at=HEAD, cancellable)` on `getRandomItemsRaw(LootContext, Consumer<ItemStack>)`. Only needed if the loader-level events prove insufficient (e.g., inline/anonymous loot tables).

---

### Terrasect region lookup at loot time

Mob spawning gets the region from `ChunkContext` attached to `ChunkAccess`. Loot events don't expose `ChunkAccess`, but they do expose `LootContext.getLevel()` (a `ServerLevel`). The equivalent lookup:

```kotlin
fun filterDrops(level: ServerLevel, origin: Vec3, drops: MutableList<ItemStack>) {
    val dimensionId = ResourceKeyCompat.getKeyId(level.dimension())
    val ctx = DimensionContext.get(dimensionId) ?: return
    val lootLookup = ctx.lootLookup ?: return
    val region = ctx.traverser.traverse(origin.x.toInt(), origin.z.toInt(), null).region ?: return
    drops.removeIf { !lootLookup.allowDrop(region, it) }
}
```

No `ChunkAccess` required; `Traverser.traverse` with a `null` cache falls back to a direct computation (same fallback already used in `ChunkContext.getDistance`). Loot events are infrequent enough that traversal cost is negligible.

---

### Gap: `Region` has no loot field yet

`common/src/main/kotlin/terrasect/definition/Region.kt` currently has `mobs: SelectionConstraints?` but no loot equivalent. The `SelectionConstraints` type (already used for `mobs` and `biomes`) is the right reuse — it handles allow/deny lists with tag support.

---

### Implementation plan (ordered)

| Step | File | Change |
|---|---|---|
| 1 | `common/.../definition/Region.kt` | Add `loot: SelectionConstraints? = null` |
| 2 | `common/.../lookup/CompiledLootLookup.kt` | New — parallel to `CompiledMobLookup`; maps `Region → Map<Item, Boolean>` |
| 3 | `common/.../generation/DimensionContext.kt` | Add `lootLookup: CompiledLootLookup?` field, build in init |
| 4 | `common/.../handler/LootHandler.kt` | New Kotlin `object`; `filterDrops(level, origin, drops)` |
| 5 | `fabric/.../TerrasectFabric.kt` | Register `LootTableEvents.MODIFY_DROPS` → `LootHandler.filterDrops` |
| 6 | `neoforge/.../TerrasectNeoForge.kt` | Register `IGlobalLootModifier` → `LootHandler.filterDrops` |
| 7 | Instrumentation | Add `LOOT_APPLIED` metric in `TerrasectMetricEvent`, wire counters in `LootHandler` |

The pattern mirrors the mob spawn constraints implementation exactly. Steps 1–4 are pure `common/` work; steps 5–6 are thin loader-specific wiring. The loader-specific hooks (`MODIFY_DROPS` / `IGlobalLootModifier`) give runtime position access without Mixins, so the whole thing should be implementable without touching Minecraft internals.
