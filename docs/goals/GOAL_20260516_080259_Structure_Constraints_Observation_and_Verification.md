# Goal: Structure Constraints Observation and Verification

Status: DONE
Created: 2026-05-16 08:02:59
Branch: structures-constraints
Worktree: /home/alex/terrasect
PR: https://github.com/let-value/terrasect/pull/51

## Objective
Verify whether the current structure-constraints injections are sufficient for Minecraft's real structure-generation pipeline.

## Request
Tell Claude Code to:
1. read `docs/MINECRAFT_STRUCTURES.md`, which we created as the source-inspected reference for vanilla Minecraft structure placement,
2. inspect the current Terrasect structure injections and Kotlin lookup code,
3. determine whether the current hook point is enough for Minecraft's actual structure-generation logic,
4. explain any gaps caused by Minecraft's more complicated structure-generation flow,
5. avoid making changes unless the verification shows a clear missing hook or correctness issue,
6. keep the result concise and grounded in the document plus code inspection.

## Context
The implementation work already added a structure-constraint path, including:
- a thin Fabric Java mixin wrapping `ChunkGenerator.createStructures()`
- Kotlin helpers for filtering `StructureSet` lists by region constraints
- a `StructureHandler` entry point and compiled lookup cache

The concern now is not basic implementation, but whether the surface we hooked is too narrow. Minecraft structure generation has additional logic beyond the top-level `possibleStructureSets()` list, and the current injection may not fully cover that complexity.

Relevant files to inspect:
- `docs/MINECRAFT_STRUCTURES.md`
- `common/src/main/java/terrasect/mixin/structure/ChunkGeneratorStructureMixin.java`
- `common/src/main/kotlin/terrasect/handler/StructureHandler.kt`
- `common/src/main/kotlin/terrasect/lookup/CompiledStructureLookup.kt`
- `common/src/main/kotlin/terrasect/generation/DimensionContext.kt`
- `common/src/main/kotlin/terrasect/definition/SelectionConstraints.kt`

## Acceptance criteria
A correct response should:
- state whether the existing injection is sufficient or insufficient,
- cite the specific Minecraft structure-generation steps that matter,
- identify any missing hook points or correctness risks,
- distinguish between a conceptual verification result and a code change request,
- update this goal file with the verification outcome.

## Update requirements
While working, Claude Code should update this file with:
- CLAIMED / IN_PROGRESS / completion status
- what it found in the Minecraft document
- whether the current injections are enough
- any gaps or follow-up action
- final verification summary

## Verification outcome

**Status: DONE — no code changes made**

### Verdict: the existing hook is sufficient for the primary generation path

The `@WrapOperation` on `ChunkGeneratorStructureState.possibleStructureSets()` inside
`ChunkGenerator.createStructures()` is the correct and sufficient interception point for
preventing structures from spawning in constrained regions. Every downstream step —
`placement.isStructureChunk()`, `tryGenerateStructure()`, biome validity, and weighted
multi-structure selection — is gated on iterating the list returned by that call. If a set
is absent from the returned list, no placement check or generation attempt happens for it.

### How the pipeline is covered

The per-chunk flow (from `MINECRAFT_STRUCTURES.md §10`) is:

```
createStructures → iterates state.possibleStructureSets()    ← HOOK HERE (WrapOperation)
  └─ for each set → placement.isStructureChunk()
       └─ isPlacementChunk()
       └─ applyAdditionalChunkRestrictions()   (frequency)
       └─ applyInteractionsWithOtherStructures() (exclusion zone)
  └─ tryGenerateStructure → structure.generate → isValidBiome
```

The hook sits above all three sub-checks. Removing a set from the list eliminates it
before any spatial, frequency, exclusion, or biome test fires. No additional hook is
needed in the happy path.

The multi-structure case (§4, §10): when a set has multiple weighted candidates and some
are filtered out, `CompiledStructureLookup.computeFilteredSets` creates a new `StructureSet`
with only the remaining entries preserving individual weights, and the game's weighted
selection loop works correctly on that narrowed list.

The `ConcentricRings` (Stronghold) case (§7): pre-computed ring positions live in
`ChunkGeneratorStructureState` and are not affected by the per-chunk filter. The filter
simply causes the Stronghold set to be absent from the returned list for constrained chunks,
so `isPlacementChunk` is never reached and no Stronghold start is created. Correct.

### Two edge cases — minor, no action required

**1. ExclusionZone over-suppression (deprecated path)**

`applyInteractionsWithOtherStructures()` calls `state.hasStructureChunkInRange(otherSet, …)`
with a `Holder<StructureSet>` taken directly from the exclusion zone config, bypassing the
filtered list entirely (§5, §9). If Terrasect filters out set B for a region, the exclusion
zone of set A (which excludes B) will still see B as a potential placement source, and may
over-suppress A in areas where B is absent due to constraints. In vanilla this only affects
pillager outposts/villages mutual exclusion. Noted, but: (a) the ExclusionZone mechanism is
`@Deprecated`; (b) the scenario requires filtering one of the two sets while wanting the
other; (c) over-suppression is safer than under-suppression. No hook warranted.

**2. `/locate` returns unfiltered results (UX gap, not generation correctness)**

`/locate` goes through `ChunkGeneratorStructureState.findNearestMapStructure()`, which
reads the stored `possibleStructureSets` list directly — not the per-chunk filtered list.
It will report positions for structures that would be suppressed by region constraints at
actual generation time. This is a UX inconsistency, not a generation bug; the structure
still won't actually generate. No action required at this time.

### Conclusion

No additional hook points are needed for Minecraft's real structure-generation flow.
The two gaps above are edge cases that do not compromise the core constraint guarantee.

## Completion criteria
Mark this goal DONE only when:
- the document has been read,
- the current injections have been assessed against Minecraft's real structure-generation flow,
- the file records a clear verdict and reasoning,
- no unnecessary code changes were made unless needed to prove a gap.
