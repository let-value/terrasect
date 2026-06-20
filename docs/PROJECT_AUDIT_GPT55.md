# Terrasect Project Audit (GPT-5.5)

## Executive summary
Terrasect still has a clear center of gravity: the older SDF utilities are the cleanest part of the codebase. Files like `common/src/main/kotlin/terrasect/sdf/area.kt`, `hex.kt`, `surround.kt`, and `subdivision.kt` are small, direct, and explicit. They are easy to audit because each one does one thing with very little indirection.

The merged codebase around them is much noisier. After the recent mob-spawn and loot-generation work, `common` now carries several parallel subsystems with similar structure but different domains: region definition, traversal, compiled lookups, handlers, metrics, and loader interop. The main quality problem is not missing functionality; it is duplication, statefulness, and boundary blur. The code still works like a mod, but it reads like several large features stacked on top of one another instead of one coherent architecture.

## Top pain points

### 1) Region definition/building is too stateful and implicit
`RegionDefinition` and `RegionBuilder` rely on a lot of lazy mutable state and parent/child mutation during build. That makes the API feel convenient, but it also makes it hard to see what the final tree will contain without mentally executing the builder.

There is also at least one obvious footgun: `adjacentTo(vararg regionNames)` calls `adjacentTo.plus(regionNames)` without storing the result, so the method currently has no effect.

### 2) The new loot/mob/structure layers are structurally duplicated
`CompiledMobLookup`, `CompiledLootLookup`, and `CompiledStructureLookup` all follow the same shape: collect constrained regions, index registry entries, precompute per-region decisions, and serve a hot-path query. The domain types differ, but the orchestration is almost identical. This is classic merge residue: similar systems were added independently, and the code never got collapsed back into a shared kernel.

### 3) Common is becoming a “god module”
The `common` module now holds not just shared logic, but hot-path handlers, metrics, mixins, extenders, lookup compilation, and domain builders. That is convenient for wiring, but it weakens architectural boundaries. Loader modules are thin, yet a lot of cross-loader behavior is encoded indirectly through Java mixins and Kotlin helper objects, which makes the real flow harder to follow.

### 4) Instrumentation is oversized relative to the rest of the codebase
`common/src/main/kotlin/terrasect/instrumentation/Instr.kt` is the largest source file in `common` at 433 lines, far larger than the next biggest source file. It provides a powerful generic metrics facade, but the overload-heavy API obscures the actual application logic.

That is a maintainability smell: the observability layer has become a subsystem rather than a helper.

### 5) Tests are skewed toward the older geometry/generation core
Common tests are strong for SDF, strategies, and traversal. The newer merged pathways are less directly protected: there is no obvious unit-test coverage for loot filtering, and mob/structure behavior is mostly exercised through Fabric gametests. That leaves the most recently added surfaces more dependent on integration behavior than on fast, local tests.

## Examples and evidence

- **The cleanest code is still the SDF layer:**
  - `common/src/main/kotlin/terrasect/sdf/subdivision.kt`
  - `common/src/main/kotlin/terrasect/sdf/area.kt`
  - `common/src/main/kotlin/terrasect/sdf/surround.kt`
  - `common/src/main/kotlin/terrasect/sdf/hex.kt`

  These files are minimal, single-purpose, and explicit about their data flow.

- **Region building has grown too clever:**
  - `common/src/main/kotlin/terrasect/definition/RegionDefinition.kt`
  - `common/src/main/kotlin/terrasect/definition/RegionRegistry.kt`

  The build process mixes copy-on-build behavior, parent inheritance, side effects, and recursive tree construction.

- **Lookup code repeats the same pipeline three times:**
  - `common/src/main/kotlin/terrasect/lookup/CompiledStructureLookup.kt`
  - `common/src/main/kotlin/terrasect/lookup/CompiledLootLookup.kt`
  - `common/src/main/kotlin/terrasect/lookup/CompiledMobLookup.kt`
  - `common/src/main/kotlin/terrasect/handler/StructureHandler.kt`
  - `common/src/main/kotlin/terrasect/handler/LootHandler.kt`
  - `common/src/main/kotlin/terrasect/handler/MobHandler.kt`

  These files repeat the same indexing/filtering pattern with different registries and constraints.

- **Recent merge activity correlates with the complexity:**
  The current branch already includes back-to-back large merges for mob spawn constraints and loot generation manipulation. The current shape of the code reflects that history: it is feature-rich, but it has not yet been re-compacted into a smaller set of primitives.

- **Largest file in `common`:**
  - `common/src/main/kotlin/terrasect/instrumentation/Instr.kt` at 433 lines

## Recommended improvements

### 1) Extract a shared region-constraint compiler
Create one generic compile-and-cache path for registry-based selection, then parameterize it for mobs, loot, and structures. The current three lookups are similar enough that the duplication is now costing clarity.

### 2) Make region definition more explicit and more immutable
Prefer a smaller builder surface with explicit merge/override methods over lazy mutable fields. The goal should be: one place where defaults are applied, one place where inheritance happens, and one place where the final tree is produced.

Also fix the obvious no-op in `adjacentTo(vararg)` while the API is still evolving.

### 3) Separate core worldgen logic from adapter plumbing
Keep the traversal/SDF/strategy layer narrow and explicit, and push loader or mixin concerns into a dedicated adapter boundary. The goal is to make the shared model read like the SDF code, not like a web of event hooks.

### 4) Shrink and simplify instrumentation
Split `Instr.kt` into smaller pieces or reduce the overload surface. The metrics backend should support the game logic, not dominate the source file landscape.

### 5) Add focused tests around the newest systems
Add fast unit tests for loot filtering, mob gating, and structure selection/caching behavior. Keep gametests for end-to-end validation, but back them with smaller tests that catch regressions without launching the game.

## Priority-ranked action list

1. **Unify the three region lookup compilers** (`mob`, `loot`, `structure`) behind one shared abstraction.
2. **Refactor `RegionDefinition` / `RegionBuilder` into a simpler, more immutable API** and fix the `adjacentTo` no-op.
3. **Add direct tests for loot, mob, and structure paths** so the newest merge surfaces have fast coverage.
4. **Split or compress `Instr.kt`** so instrumentation stops being the largest conceptual subsystem in `common`.
5. **Keep the SDF files as the style target** and push the rest of the codebase toward that directness.

## Cross-review alignment note
This revision intentionally converges on the same diagnosis as the Claude Opus review: the main problems are mutable region modeling, duplicated compiled-selection logic, and a `common` module that is carrying too much orchestration. Strategy/state handling and loader parity are still worth attention, but they are secondary to the shared core issues above.

Overall verdict: Terrasect still has a strong core, but the post-merge feature layer now needs consolidation. The fastest path to a cleaner repository is not more abstraction; it is fewer parallel codepaths, fewer mutable builder tricks, and more of the directness already present in the SDF utilities.