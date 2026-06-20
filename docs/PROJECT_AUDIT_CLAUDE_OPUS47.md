# Terrasect Project Audit — Claude Opus 4.7

## Executive summary

Terrasect still has a clear center of gravity: the older SDF utilities are the cleanest part of the codebase. Files like `common/src/main/kotlin/terrasect/sdf/area.kt`, `hex.kt`, `surround.kt`, and `subdivision.kt` are small, direct, and explicit. They are easy to audit because each one does one thing with very little indirection.

The merged codebase around them is much noisier. After the recent mob-spawn and loot-generation work, the common module now carries several parallel subsystems with similar structure but different domains: region definition, traversal, compiled lookups, handlers, metrics, and loader interop. The main quality problem is not missing functionality; it is duplication, statefulness, and boundary blur. The clearest shared diagnosis is now the same across the major feature paths: the three compiled lookup pipelines, the mutable region builder, and the stateful strategy/runtime layer all need consolidation. The code still works like a mod, but it reads like several large features stacked on top of one another instead of one coherent architecture.

## Top pain points

1. **Region definition/building is too stateful and implicit.**  
   `RegionDefinition` and `RegionBuilder` rely on a lot of lazy mutable state and parent/child mutation during build. That makes the API feel convenient, but it also makes it hard to see what the final tree will contain without mentally executing the builder.

   There is also at least one obvious footgun: `adjacentTo(vararg regionNames)` calls `adjacentTo.plus(regionNames)` without storing the result, so the method currently has no effect.

2. **The new loot/mob/structure layers are structurally duplicated.**  
   `CompiledMobLookup`, `CompiledLootLookup`, and `CompiledStructureLookup` all follow the same shape: collect constrained regions, index registry entries, precompute per-region decisions, and serve a hot-path query. The domain types differ, but the orchestration is almost identical. This is classic merge residue: similar systems were added independently, and the code never got collapsed back into a shared kernel.

3. **Common is becoming a “god module.”**  
   The `common` module now holds not just shared logic, but hot-path handlers, metrics, mixins, extenders, lookup compilation, and domain builders. That is convenient for wiring, but it weakens architectural boundaries. Loader modules are thin, yet a lot of cross-loader behavior is encoded indirectly through Java mixins and Kotlin helper objects, which makes the real flow harder to follow.

4. **Instrumentation is oversized relative to the rest of the codebase.**  
   `common/src/main/kotlin/terrasect/instrumentation/Instr.kt` is the largest source file in `common` at 433 lines, far larger than the next biggest source file. It provides a powerful generic metrics facade, but the overload-heavy API obscures the actual application logic.

   That is a maintainability smell: the observability layer has become a subsystem rather than a helper.

5. **Tests are skewed toward the older geometry/generation core.**  
   Common tests are strong for SDF, strategies, and traversal. The newer merged pathways are less directly protected: there is no obvious unit-test coverage for loot filtering, and mob/structure behavior is mostly exercised through Fabric gametests. That leaves the most recently added surfaces more dependent on integration behavior than on fast, local tests.

## Examples and evidence

- **The cleanest code is still the SDF layer.**  
  `common/src/main/kotlin/terrasect/sdf/subdivision.kt` is 14 lines, `area.kt` is 27 lines, `surround.kt` is 55 lines, and `hex.kt` is 76 lines. These files are minimal, single-purpose, and explicit about their data flow.

- **Region building has grown too clever.**  
  `common/src/main/kotlin/terrasect/definition/RegionDefinition.kt` is 153 lines and mixes mutable state, lazy builders, parent inheritance, and child registration. `RegionRegistry.kt` adds a shared `visiting` set for cycle control, but the early-return path on cycle detection does not clear the active marker before returning, which is another sign that the builder/registry model is too stateful.

- **Lookup code repeats the same pipeline three times.**  
  `CompiledMobLookup.kt`, `CompiledLootLookup.kt`, and `CompiledStructureLookup.kt` all build registry indexes with `IdentityHashMap`, gather constrained regions recursively, and precompute per-region decisions. The code is readable in isolation, but the repeated structure makes it harder to evolve the domain rules consistently.

- **The hot-path handlers are more contingent than they look.**  
  `LootHandler.kt`, `MobHandler.kt`, and `StructureHandler.kt` each have to juggle chunk context, dimension lookup, fallbacks, region traversal, and metrics counting. They are small individually, but the repetition across them suggests the architecture is not yet expressing the shared pattern cleanly.

- **The strategy layer is more stateful than the SDF layer.**  
  `HexStrategy.kt`, `VoronoiStrategy.kt`, `SubdivisionStrategy.kt`, and `SurroundStrategy.kt` rely on scratch objects and encoded traversal state. They are optimized, but the control flow is less direct than the SDF utilities they compose.

- **The codebase has a heavy interop/mixin layer.**  
  There are 57 Kotlin source files and 32 Java source files in `common`, with 22 Java mixins and 9 extenders. Files like `DensityFunctionHolderMixin.java` and `ChunkGeneratorStructureMixin.java` show the amount of low-level plumbing required to bridge Minecraft internals back into the Kotlin domain model.

- **Recent merge activity correlates with the complexity.**  
  The current branch already includes back-to-back large merges for mob spawn constraints and loot generation manipulation. The current shape of the code reflects that history: it is feature-rich, but it has not yet been re-compacted into a smaller set of primitives.

## Recommended improvements

1. **Extract a shared compiled-selection pattern.**  
   Mob, loot, and structure lookups should share one generic "compile registry entries, cache decisions per region, evaluate by selection constraints" core. Each domain can then supply only the type-specific extraction and evaluation hooks.

2. **Make region definition more explicit and more immutable.**  
   Prefer a smaller builder surface with explicit merge/override methods over lazy mutable fields. The goal should be: one place where defaults are applied, one place where inheritance happens, and one place where the final tree is produced.

3. **Push the strategy layer toward small typed value objects.**  
   The strategy implementations would read more like the SDF utilities if traversal state were represented as small explicit data objects instead of a mix of `ThreadLocal`, `ByteBuffer`, and ad hoc mutation.

4. **Shrink and simplify instrumentation.**  
   Split `Instr.kt` into smaller pieces or reduce the overload surface. The metrics backend should support the game logic, not dominate the source file landscape.

5. **Close the loader verification gap.**  
   Add NeoForge parity tests or document why they are intentionally absent. For a multiloader project, symmetric verification is a real quality multiplier.

## Priority-ranked action list

1. **Unify the three region lookup compilers** (`mob`, `loot`, `structure`) behind one shared abstraction.
2. **Refactor `RegionDefinition` / `RegionBuilder` into a simpler, more immutable API** and fix the `adjacentTo` no-op.
3. **Add direct tests for loot, mob, and structure paths** so the newest merge surfaces have fast coverage.
4. **Split or compress `Instr.kt`** so instrumentation stops being the largest conceptual subsystem in `common`.
5. **Keep the SDF files as the style target** and push the rest of the codebase toward that directness.

## Cross-review alignment note

This revision intentionally converges on the same diagnosis as the GPT-5.5 audit: the main problems are duplicated compiled-selection logic, mutable region modeling, a `common` module that carries too much orchestration, and an observability layer that is too large for its role. Strategy/state handling and loader parity are still worth attention, but they are secondary to the shared core issues above.
