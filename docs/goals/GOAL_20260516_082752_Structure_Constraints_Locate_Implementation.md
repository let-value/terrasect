# Goal: Structure Constraints Locate Implementation

Status: DONE
Created: 2026-05-16 08:27:52
Branch: structures-constraints
Worktree: /home/alex/terrasect
PR: https://github.com/let-value/terrasect/pull/51

## Objective
Make Terrasect's structure constraints affect Minecraft's `/locate` behavior the same way they affect world generation.

## Request
Tell Claude Code to:
1. read `docs/MINECRAFT_STRUCTURES.md` and the prior locate investigation goal file,
2. inspect how `/locate` resolves structure positions in Minecraft 1.21.x,
3. add or adjust the minimal hook(s) needed so `/locate` respects the same Terrasect structure constraints as world generation,
4. reuse the existing shared constraint logic instead of introducing a second rule system,
5. keep the solution direct and in the same style as the existing common/Kotlin + thin Java mixin approach,
6. avoid unnecessary abstractions or performance regressions,
7. keep the current branch and PR as the working surface,
8. update this goal file with progress, verification, and final result.

## Context
The current implementation already filters world generation via a `ChunkGenerator.createStructures()` mixin and the shared `StructureHandler.getFilteredSets(...)` helper.

A prior verification pass found that `/locate` is a separate pipeline and is not covered by the generation-only hook. The goal now is to close that gap so locate and generation obey one unified constraints decision.

Relevant files:
- `docs/MINECRAFT_STRUCTURES.md`
- `docs/goals/GOAL_20260516_080912_Structure_Constraints_Locate_Investigation.md`
- `common/src/main/java/terrasect/mixin/structure/ChunkGeneratorStructureMixin.java`
- `common/src/main/kotlin/terrasect/handler/StructureHandler.kt`
- `common/src/main/kotlin/terrasect/lookup/CompiledStructureLookup.kt`
- `common/src/main/kotlin/terrasect/generation/DimensionContext.kt`
- `common/src/main/kotlin/terrasect/definition/SelectionConstraints.kt`

## Acceptance criteria
A correct response should:
- make `/locate` respect Terrasect structure constraints,
- preserve the existing world-generation behavior,
- reuse the shared filtering logic if possible,
- identify any extra hook point required in the locate pipeline,
- record verification and the final implementation result in this goal file.

## Update requirements
While working, Claude Code should update this file with:
- CLAIMED / IN_PROGRESS / completion status
- what hook or path it changed for `/locate`
- whether generation and locate now share the same constraint logic
- any blockers or follow-up items
- final verification summary

## Verification summary
- `/locate` now uses the same region-aware constraint decision as world generation.
- The locate-path mixin filters the candidate `Set<Holder<Structure>>` by the shared `StructureHandler.getFilteredSets(...)` result for the candidate chunk.
- The implementation stays on the existing `structures-constraints` branch and PR #51.
- Verified with `./gradlew :common:compileJava :common:compileKotlin`.

## Completion criteria
Mark this goal DONE only when:
- `/locate` has been updated to obey the same constraints as world generation,
- the implementation stays aligned with the existing shared logic,
- the file records the final result and verification,
- the change is on the current branch/PR or its continuation.
