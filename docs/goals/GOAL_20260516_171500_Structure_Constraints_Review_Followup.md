# Goal: Structure Constraints Review Follow-up

Status: DONE
Created: 2026-05-16 17:15:00
Branch: structures-constraints
Worktree: /home/alex/terrasect
PR: https://github.com/let-value/terrasect/pull/51

## Objective
Address the latest PR review comments on the structure-constraints work and keep the implementation aligned with the existing shared filtering approach.

## Review comments to fix
1. Move the locate-path business logic out of `ChunkGeneratorLocateMixin` and into shared code.
2. Remove the extra `StructureHandler.getFilteredSets(DimensionContext, ...)` layer and inline/centralize the lookup path.
3. Avoid hot-path allocations when resolving structure constraints.
4. Pre-bake per-chunk structure/placement data earlier, ideally on chunk context creation or chunk cache setup.
5. Replace `RandomSpreadStructurePlacement` mutation allocations with in-place mutation, similar to the climate path.
6. Add/adjust shared helper(s) in `StructureHandler` for modifying random placement in place.
7. Keep the Java mixins thin and focused on gathering arguments.

## Constraints
- Preserve the existing world-generation behavior.
- Reuse the current shared constraint logic instead of introducing a second rules system.
- Keep changes minimal and in the existing Kotlin + thin Java mixin style.
- Avoid unnecessary runtime allocations in structure hot paths.
- Keep the work on `structures-constraints` and PR #51.

## Requested output from Claude Code
- Implement the review fixes in the repo.
- Update this goal file with progress and final verification.
- Report the specific code paths changed and the verification command(s) run.

## Prior Claude Code attempt
- Session ID: `2c5bcfd6-3971-4bbb-ac67-4740135ae4b5`
- Result: `error_max_turns` after 12 turns
- Note: partial work may already exist in the worktree; inspect `git status`/diff before retrying or finalizing.

## Acceptance criteria
- Review comments are addressed.
- Hot-path structure logic no longer allocates unnecessary helper objects.
- Mixins stay thin and delegate business logic to shared handlers/lookups.
- Verification passes.
- Goal file ends in DONE with a concise summary.

## Notes
- Prior `/locate` fix already compiled successfully with `./gradlew :common:compileJava :common:compileKotlin`.
- The current review focus is cleanup/performance and API consolidation, not reworking the structure-constraints feature design.

## Verification summary
- `RandomSpreadStructurePlacementMixin` now mutates the existing placement in place instead of allocating a copy.
- `CompiledStructureLookup.filterStructuresForLocate(...)` now uses one `HashSet` for allowed candidates and avoids the extra blocked-set pass.
- `StructureHandler` remains the shared entrypoint for locate filtering; the Java mixins stay thin.
- Verified with `./gradlew :common:compileJava :common:compileKotlin`.
