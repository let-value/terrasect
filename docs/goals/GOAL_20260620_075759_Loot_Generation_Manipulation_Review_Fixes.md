## Task
**Date:** 20260620
**Submitted By:** Hermes orchestrator
**Status:** COMPLETED

### Request
Address the independent review feedback on PR #57 for loot generation manipulation.

### Review feedback to fix
1. **Critical:** loot constraints are not wired into region construction.
   - `Region.kt` has `loot`, but `RegionDefinition` / `RegionRegistry.buildTree()` still only propagate `mobs`.
   - This likely makes the loot filter inert because runtime region trees keep `loot == null`.
2. **Medium:** there is no end-to-end verification for the loot path.
   - Add the smallest targeted test or other concrete verification that proves the region field is propagated and the loot path activates.
3. Keep the hot-path design allocation-free and preserve mod interoperability.
4. Keep the implementation aligned with the earlier spawn-constraints PR: common logic in shared code, thin loader wiring, explicit verification.

### Context
- Project: Terrasect Minecraft mod
- Repo root: /home/alex/terrasect/.worktrees/loot-generation-manipulation
- Base branch: reborn
- Branch/worktree: feature/loot-generation-manipulation
- Existing PR: #57
- Current implementation already added loot handler / compiled lookup / loader hooks
- The likely missing piece is region-definition propagation of the new loot field
- Update this file with concrete progress, blockers, and verification

### Acceptance criteria
- Root cause of the inert loot path is fixed
- Loot region data is propagated through region construction/deserialization as needed
- A targeted verification step proves the fix
- PR #57 is updated if the diff or scope changes
- This goal file ends in COMPLETED or BLOCKED with a clear reason

### Run metadata
- Provider: Claude Code (delegated)
- Session id: TBD
- Retry count: 0
- Retry after: TBD
- Blocked reason: TBD

---
**Sub-agent instruction:** Read this file and the current implementation goal plus the two loot investigation files. Fix the review issues, verify the result, and keep stdout terse. Update this file with your response before finishing.

## Response
**Completed by:** Claude Code (claude-sonnet-4-6)
**Date:** 20260620

### Root cause

`Region.kt` had the `loot: SelectionConstraints? = null` field, but `RegionDefinition`/`RegionBuilder` had no counterpart — so the field could never be populated from a builder/DSL/`buildTree()` call. `RegionRegistry.buildTree()` also did not pass `loot` through to the `Region(...)` constructor, making the field permanently `null` at runtime regardless of any configuration.

### Files changed

| File | Change |
|---|---|
| `common/.../definition/RegionDefinition.kt` | Added `loot: SelectionConstraints?` to `RegionDefinition`; added `lootLazyBuilder`/`lootBuilder`, `loot { }` DSL method, `inheritParent` handling, and `build()` output in `RegionBuilder` |
| `common/.../definition/RegionRegistry.kt` | Added `loot = definition.loot` to `Region(...)` construction in `buildTree()` |
| `common/.../definition/RegionDefinitionTest.kt` | New test `loot constraints are propagated through buildTree` — configures loot via DSL, calls `buildTree()`, asserts non-null `loot` with expected `allowedNames`/`blockedTags` |

### Verification

```
./gradlew :common:compileKotlin      → BUILD SUCCESSFUL
./gradlew :fabric:compileKotlin      → BUILD SUCCESSFUL
./gradlew :neoforge:compileKotlin    → BUILD SUCCESSFUL
./gradlew :common:test               → BUILD SUCCESSFUL (all tests pass, including new loot propagation test)
./gradlew spotlessCheck              → BUILD SUCCESSFUL (no format violations)
```

### Hot-path and interop preserved

- `LootHandler.filterDrops`, `CompiledLootLookup`, and `DimensionContext.lootLookup` were not modified — the allocation-free identity-map hot path is unchanged.
- `SelectionConstraints` evaluation (namespace/tag/name matching) is unchanged, so modded items continue to be handled through the same registry-indexed path.

### Blockers: none
