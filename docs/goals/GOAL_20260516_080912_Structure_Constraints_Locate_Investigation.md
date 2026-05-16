# Goal: Structure Constraints Locate Investigation

Status: DONE
Created: 2026-05-16 08:09:12
Completed: 2026-05-16
Branch: structures-constraints
Worktree: /home/alex/terrasect
PR: https://github.com/let-value/terrasect/pull/51

## Objective
Investigate how Minecraft's `/locate` structure command works and determine whether Terrasect can use a single constraints solution that applies consistently to both world generation and locate results.

## Verdict

**The current solution is split.** World generation and `/locate` use entirely different internal call chains, and Terrasect's mixin only covers one of them.

---

## How `/locate` Works (Minecraft 1.21.x)

```
LocateCommand.locateStructure()
  └── ChunkGenerator.findNearestMapStructure(serverLevel, holderSet, blockPos, 100, false)
        └── for each Holder<Structure> in holderSet:
              chunkGeneratorStructureState.getPlacementsForStructure(holder)
                └── ensureStructuresGenerated() → generatePositions()
                      └── iterates this.possibleStructureSets()  ← full vanilla list
                            builds placementsForStructure: Map<Structure, List<StructurePlacement>>
        └── for each (StructurePlacement → structures) entry:
              getNearestGeneratedStructure(...)  ← expands outward in rings of chunks
                └── getStructureGeneratingAt(set, levelReader, structureManager, bl, placement, chunkPos)
                      └── structureManager.checkStructurePresence(chunkPos, structure, placement, bl)
```

`getPlacementsForStructure` reads from `placementsForStructure`, which is built **once** at dimension load during `generatePositions()`. That method calls `this.possibleStructureSets()` — the full vanilla biome-filtered set stored in the `ChunkGeneratorStructureState` field. The Terrasect mixin never intercepts this invocation.

---

## Why the Current Mixin Misses `/locate`

`ChunkGeneratorStructureMixin` uses:

```java
@WrapOperation(
    method = "createStructures",
    at = @At(value = "INVOKE", target = "...ChunkGeneratorStructureState;possibleStructureSets()..."))
```

This `@WrapOperation` fires **only** when `possibleStructureSets()` is called from within `ChunkGenerator.createStructures()`. The `/locate` path calls `possibleStructureSets()` from within `ChunkGeneratorStructureState.generatePositions()` — a completely different call site on a different class. The mixin never fires for it.

Result: `placementsForStructure` is always built from the full vanilla structure set. When `/locate` scans for the nearest structure, it consults this unfiltered map and ignores all Terrasect region constraints. A structure blocked by `SelectionConstraints` in a given region will still be reported by `/locate` if a vanilla-eligible chunk is nearby.

---

## What a Unified Solution Requires

The shared abstraction already exists: `StructureHandler.getFilteredSets(dimensionKey, chunkX, chunkZ)` returns the Terrasect-filtered set for a given chunk position. The missing piece is a second mixin that calls this during the `/locate` inner search.

### The hook point

`findNearestMapStructure` expands outward in rings of grid-aligned chunks (for `RandomSpread`) or iterates a pre-computed list (for `ConcentricRings`). At each candidate, it calls `getStructureGeneratingAt(set, levelReader, structureManager, bl, placement, chunkPos)`. This is where Terrasect constraints must be applied — after computing the candidate `chunkPos` but before accepting the result.

`getStructureGeneratingAt` is `private static` in `ChunkGenerator`, so it cannot be mixed into directly. The practical hook is a `@WrapOperation` on the two `getNearestGeneratedStructure` calls **inside** `findNearestMapStructure` (one for `ConcentricRings`, one for `RandomSpread`). The wrapper would:

1. Receive the candidate `chunkPos` (available from `StructurePlacement.getPotentialStructureChunk` for random spread, or from the pre-computed ring list for concentric rings).
2. Call `StructureHandler.getFilteredSets(dimensionKey, chunkPos.x, chunkPos.z)`.
3. If the filtered set excludes the target structure, skip this candidate (return null from the wrapped call).

`findNearestMapStructure` already has `ServerLevel serverLevel` in scope, which provides the dimension key — so no additional context threading is needed.

### Correctness note

The filter must be applied **per candidate chunk position**, not at the search origin. A structure blocked near the player may be allowed in a distant region. Returning `null` from the entire locate call when only the nearest candidate is blocked would give a wrong "not found" result.

---

## Gaps Identified

| Path | Mixin | Status |
|---|---|---|
| `ChunkGenerator.createStructures` (generation) | `ChunkGeneratorStructureMixin` | ✅ Covered |
| `ChunkGenerator.findNearestMapStructure` (locate) | none | ❌ Not covered |

No code was modified during this investigation.

---

## Follow-Up Action

Implement a second mixin — `ChunkGeneratorLocateMixin` (or add to the existing structure mixin file) — targeting `ChunkGenerator.findNearestMapStructure`. It should wrap the two `getNearestGeneratedStructure` calls and filter candidate positions using `StructureHandler.getFilteredSets`. Both paths would then share the same constraint evaluation logic with no new abstraction needed.

This is a separate implementation task. The existing PR (#51) should remain open until this mixin is also implemented and verified.
