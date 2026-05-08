## Task
**Date:** 20260508
**Submitted By:** Hermes Orchestrator
**Status:** COMPLETED
**Kanban item:** `t_81cdfce7` — Explore project and establish development context and best practices

### Request
Manual kickoff for the first Terrasect Kanban item: **Explore project and establish development context and best practices**.

The human/orchestrator separation of concerns is:

- Hermes Kanban board `terrasect` tracks high-level work between Alexander and Hermes.
- `docs/TODO.md` and `docs/goals/` are the execution mechanism for delegated external agents/providers.
- This goal file is the source of truth for delegated work and must contain all durable communication.
- The delegating orchestrator should only need to tell a worker: read this file and write your results back into it.

Important orchestration convention:

- We are operating as orchestrators, not direct implementers.
- Future workers should also understand they may be orchestrators: they should manage/decompose/delegate tasks where appropriate rather than silently doing unrelated implementation work themselves.
- Kanban workers must understand the same separation: Kanban is Alexander ↔ Hermes coordination; goal files are Hermes ↔ delegated-agent execution records.

### Workspace
Repository root:

```text
/home/alex/terrasect
```

### Context
Terrasect is a Minecraft mod/project. The project-specific guide is:

```text
/home/alex/terrasect/AGENTS.md
```

The durable project knowledge base is:

```text
/home/alex/terrasect/docs/KNOWLEDGE_BASE.md
```

Existing docs to consult:

```text
/home/alex/terrasect/docs/PROJECT_MAP.md
/home/alex/terrasect/docs/TODO.md
/home/alex/terrasect/docs/goals/README.md
/home/alex/terrasect/docs/goals/*.md
```

Known project structure from `AGENTS.md`:

- `common`: shared code usable across mod loaders.
- `fabric`: Fabric loader code, including mixins, client game tests, and Fabric-specific behavior.
- `neoforge`: NeoForge loader code, including mixins and NeoForge-specific behavior.
- `versions`: dynamically created versioned projects inheriting from root projects to override behavior for older Minecraft versions.

Known Terrasect mission:

> Narrative world partitioning that enables predictable journeys across an infinite Minecraft world via a region registry describing regions and their relationships.

Hard constraints to preserve:

- Explicit, readable, low-magic code.
- Respect Minecraft loader boundaries.
- Avoid allocations in hot paths.
- Avoid streams/lambdas/boxing/temporary collections in worldgen/render/tick-sensitive paths.
- Be skeptical of caches unless lifecycle and invalidation are clear.
- Centralize serialization/network/registry/init concerns.

### Task
Explore the repository and establish development context and best practices for future Terrasect work.

This is an exploration/documentation/orchestration task, not a feature implementation task. Do not make code changes unless a tiny documentation correction is clearly necessary and directly supports this goal.

Please inspect the project enough to answer:

1. What are the major modules, source sets, generated/versioned areas, and their responsibilities?
2. What build system and Gradle tasks matter for normal development?
3. What test types appear to exist, especially Java/Kotlin unit tests, Fabric/NeoForge tests, game tests, and GUI/client game tests?
4. What are the most important code paths and packages to understand first?
5. What project conventions and best practices should future agents follow?
6. What should future orchestrator workers delegate vs do directly?
7. What open questions or risks should become follow-up Kanban items or future goal files?

### Acceptance criteria
A complete response must be written under `## Response` in this file and must include:

- **Completed by:** provider/agent name, including model/provider if available.
- **Date:** current date/time if available.
- **Exploration scope:** files/directories/commands inspected.
- **Repository map:** concise but grounded overview of modules and responsibilities.
- **Development environment notes:** build/test tooling, required Java/Kotlin/Gradle facts discovered from the repo.
- **Testing map:** known commands/tasks/test source sets and any unknowns.
- **Best practices for future agents:** concrete rules grounded in the repo and `AGENTS.md`.
- **Orchestrator guidance:** how future Kanban/external-agent workers should manage/decompose/delegate rather than over-implement.
- **Recommended next steps:** concrete follow-up items suitable for Kanban or goal files.
- **Verification:** what was run/read and any limitations.

If durable project knowledge is discovered that belongs in `docs/KNOWLEDGE_BASE.md`, update that file as well and mention the change in this goal file.

### Provider chain
The orchestrator will attempt providers in this order:

1. Claude Code
2. Codex
3. Copilot
4. Ollama/local fallback

If you are a later provider in the chain, read any `## Handoff` blocks in this file before starting.

---
**Sub-agent instruction:** This file contains everything you need. Read it in full. Perform the work. Write your complete response under `## Response` below before finishing. Do not summarize back to the orchestrator — write the full output so it is preserved for future sessions. Update the `Status` field to `COMPLETED` when done. If broader protocol understanding is needed, read `docs/KNOWLEDGE_BASE.md`.

## Execution log

- 20260508 19:56 — Goal file created by Hermes Orchestrator.
- 20260508 20:10 — Completed by Claude Code (claude-sonnet-4-6). Response written to goal file. KNOWLEDGE_BASE.md Section 7 added.

## Response

**Completed by:** Claude Code (claude-sonnet-4-6)
**Date:** 2026-05-08

---

### Exploration scope

Files and directories inspected:

- Root: `build.gradle`, `settings.gradle`, `gradle.properties`, `README.md`, `AGENTS.md`, `CLAUDE.md`
- Gradle helpers: `gradle/loaders/fabric.gradle`, `gradle/versioned.gradle`, `gradle/strip-comments.gradle`
- Module build files: `common/build.gradle`, `fabric/build.gradle`, `neoforge/build.gradle`
- All versioned projects under `versions/1.20.1/`, `versions/1.21.1/`, `versions/1.21.11/` — build files, gradle.properties, source overrides
- Mod metadata: `fabric/src/main/resources/fabric.mod.json`, `neoforge/src/main/resources/META-INF/neoforge.mods.toml`, both `terrasect.mixins.json`, `terrasect.accesswidener`
- All source-set Java file inventories for `common/src/main`, `common/src/test`, `fabric/src/main`, `fabric/src/client`, `neoforge/src/main`
- Docs: `docs/KNOWLEDGE_BASE.md`, `docs/PROJECT_MAP.md`, `docs/TODO.md`, `docs/goals/README.md`, all goal files in `docs/goals/`

No code changes were made. Exploration was read-only.

---

### 1. Major modules, source sets, generated/versioned areas, and responsibilities

#### Module summary

| Module | Source sets | Java files | Responsibility |
|--------|------------|------------|----------------|
| `common` | `main`, `test` | 52 main + 23 test | All shared logic: region registry, generation strategies, worldgen pipeline, lookup tables, handlers, mixins (accessor interfaces only), test framework |
| `fabric` | `main`, `client`, (test via Fabric infra) | 18 | Fabric mod entry point, 15 Fabric-specific mixin implementations, client game test integration |
| `neoforge` | `main` | 16 | NeoForge mod entry point, 15 NeoForge-specific mixin implementations |
| `fabric-1_20_1` | inherited from `:fabric` + overrides | ~0 new | Fabric 1.20.1 (Java 17) — 4 mixins active, 1 compat override |
| `fabric-1_21_1` | inherited + client overrides | ~4 new | Fabric 1.21.1 (Java 21) — full mixin set, client split-env |
| `fabric-1_21_11` | inherited + minimal | ~0 new | Fabric 1.21.11 (Java 21) — `RegionDebugEntry` excluded due to API change |
| `neoforge-1_21_1` | inherited + mixin overrides | ~2 new | NeoForge 1.21.1 (Java 21) — LevelMixin + TerrainHeightMixin override |
| `neoforge-1_21_11` | inherited | ~0 new | NeoForge 1.21.11 (Java 21) |

#### Common subpackages

| Package | Responsibility |
|---------|---------------|
| `definition` | Region data model: `RegionRegistry`, `RegionDefinition`, `Region`, `GenerationStrategy`, `NoiseConstraints`, `HeightConstraints`, `ClimateSettings`, `SelectionRules`, `StructureRules`, `NoiseTransform`, `EdgeStatistics` |
| `generation` | World gen pipeline: `World` (dimension registration), `Layout`, `MinecraftContext`, `TraversalIterator`, `TraversalResult` |
| `handler` | Per-system integration handlers: `BiomeHandler`, `ClimateHandler`, `LevelHandler`, `NoiseHandler`, `StructureHandler` |
| `helpers` | Worldgen utilities: `ClimateModifier`, `MinecraftEdgeSampler`, `RegionSampler` |
| `lookup` | Performance-critical lookup tables: `BiomeLookup`, `CompiledNoiseRegistry`, `NoiseChunkLookup`, `StructureLookup`, `StructureSetsLookup`, `TerrainHeightLookup` |
| `strategy` | Layout algorithm implementations: `HexStrategy`, `VoronoiStrategy`, `SubdivisionStrategy`, `TemplateStrategy`, `LayoutStrategies` (factory), `QueryResult` |
| `mixin` | Accessor interfaces (read-only): `ClimateTargetPointAccessor`, `MultiNoiseBiomeSourceAccessor`, `NoiseChunkAccessor`, `ShiftNoiseAccess`, `VanillaSamplerAccessor` |
| `compat` | Cross-version API shims: `BiomeCompat`, `ResourceKeyCompat`, `StructureCompat` |
| `util` | Math/utility helpers: `MathUtils`, `NoiseUtils`, `Packer`, `MutablePointContext` |
| `gui` | Client debug UI: `RegionDebugEntry` (F3 debug screen) |
| `testing` | Test framework: `SnapshotTests`, `SnapshotUpdateExtension`, `SnapshotHashes`, `SnapshotOutputPaths`, `SnapshotHtmlReports` |

#### Versioning model

The `versions/` directory contains version-specific overrides. The `gradle/versioned.gradle` helpers (`inheritFromBaseWithSourcesJar`, `inheritFabricSplitFromBase`, `inheritNeoforgeMainFromBaseAndCommonWithSourcesJar`) copy base sources into versioned builds and apply a filter list so only overrides are replaced. This means:

- Base projects (`common`, `fabric`, `neoforge`) target the bleeding-edge snapshot (currently `26.1-snapshot-2`, Java 25).
- Version-specific overrides live under `versions/{ver}/{loader}/src/` and contain only files that differ.
- Mixin configs can differ per-version: 1.20.1 activates only 4 mixins; later versions use all 15.
- `versions/1.21.11/common/build.gradle` explicitly excludes `RegionDebugEntry.java` due to an API incompatibility introduced in that MC version.

#### Gradle module naming

Settings dynamically includes `{loader}-{version_id}` projects (dots → underscores): `fabric-1_20_1`, `fabric-1_21_1`, `fabric-1_21_11`, `neoforge-1_21_1`, `neoforge-1_21_11`.

---

### 2. Build system and Gradle tasks that matter

#### Gradle version and toolchain

- **Gradle:** 9.2.1 (wrapper)
- **Root java_version:** 25 (snapshot, targets bleeding-edge MC)
- **Versioned overrides:** 17 for 1.20.1, 21 for 1.21.1+
- **Kotlin:** not used; pure Java project
- `org.gradle.parallel=false`, `org.gradle.caching=true`

#### Key plugins

| Plugin | Purpose |
|--------|---------|
| `net.neoforged.moddev` | NeoForge/NeoForm mod development (common uses vanilla-mode) |
| `net.fabricmc.fabric-loom` | Fabric mod development, source remapping, split env source sets |
| `com.diffplug.spotless` | Code formatting (Google Java Format 1.33.0) for Java, Gradle, MD, JSON, YAML |
| `org.openrewrite.rewrite` | Automated refactoring (`UseVar` recipe — migrate to `var` inference) |

#### Key tasks

| Task | Module | When to use |
|------|--------|-------------|
| `./gradlew build` | all | Compile + test all modules |
| `./gradlew :common:test` | common | Run common unit + integration tests |
| `./gradlew :common:test -PupdateSnapshots` | common | Regenerate snapshot files |
| `./gradlew spotlessApply` | all | Apply Google Java Format |
| `./gradlew spotlessCheck` | all | CI format check |
| `./gradlew :fabric:runClient` | fabric | Launch Fabric dev client |
| `./gradlew :fabric:runServer` | fabric | Launch Fabric dev server |
| `./gradlew :neoforge:runClient` | neoforge | Launch NeoForge dev client |
| `./gradlew :neoforge:runServer` | neoforge | Launch NeoForge dev server |
| `./gradlew :fabric:unpackMinecraft` | fabric | Extract mapped Minecraft sources to `minecraft/` |
| `./gradlew stripComments` | all | Remove non-`@keep` comments from source |
| `./gradlew :fabric-1_20_1:build` | versioned | Build a specific versioned artifact |

#### Notes for dev environment

- `gradle.properties` has `org.gradle.java.home=C:\\Program Files\\Java\\jdk-25` — this is a Windows path that won't work on Linux. Relies on `auto-detect` and `auto-download` as fallback. On Linux, ensure a JDK 25 is available or use the auto-download feature.
- The snapshot Minecraft version (`26.1-snapshot-2`) means builds may fail without the snapshot artifact in cache. Use `unpackMinecraft` only after confirming the snapshot is available.

---

### 3. Test types

#### Test categories

| Test type | Location | Framework | Activation |
|-----------|----------|-----------|------------|
| Unit tests (definition model) | `common/src/test/java/.../definition/` | JUnit 5 | `./gradlew :common:test` |
| Strategy + generation unit tests | `common/src/test/java/.../generation/` | JUnit 5 + snapshot-tests | `./gradlew :common:test` |
| Snapshot tests | `common/src/test/resources/.../*_snapshots/` | `de.skuzzle.test:snapshot-tests-junit5:1.11.0` | `./gradlew :common:test`; update with `-PupdateSnapshots` |
| Integration tests (Minecraft noise) | `common/src/test/java/.../integration/` | JUnit 5 + snapshot-tests | `./gradlew :common:test` (requires Minecraft via NeoForm) |
| Fabric client game tests | `fabric/src/client/java/.../ClientGameTestIntegration.java` | Fabric API game test infra | `./gradlew :fabric:runClient` (in-game) |
| NeoForge game tests | Not present (no test source set) | — | Not configured |

#### Snapshot test mechanics

- Snapshots live as static files under `src/test/resources/com/terrasect/common/.../[TestClass]_snapshots/`.
- `SnapshotUpdateExtension` reads the `-PupdateSnapshots` Gradle property via system property `forceUpdateSnapshots`.
- HTML reports are rendered via Mustache templates in `src/test/resources/templates/` — the templates cover gallery, noise-router, and ocean-constraints visualizations.
- Snapshot hashes in `SnapshotHashes.java` allow lightweight diffing without re-rendering.

#### Known unknowns for tests

- NeoForge module has no test source set. Whether NeoForge game tests (e.g., using `@GameTest`) are planned or intentionally omitted is not documented.
- Fabric client game test integration exists (`ClientGameTestIntegration.java`) but the test class content was not read — unclear how extensive the coverage is.
- No visible performance benchmarks (JMH or similar) despite the hot-path performance constraint being a hard requirement.

---

### 4. Most important code paths and packages to understand first

Priority reading order for a new contributor:

1. **`common/definition/RegionRegistry.java`** — entry point for the region data model; understand how regions are declared and what the registry holds.
2. **`common/definition/RegionDefinition.java`, `Region.java`** — the core data structures.
3. **`common/strategy/`** — `LayoutStrategies`, `VoronoiStrategy`, `HexStrategy`, `SubdivisionStrategy` — how partitioning strategies work.
4. **`common/generation/World.java`** and `Layout.java` — how the registry feeds into dimension registration and layout computation.
5. **`common/handler/`** — `BiomeHandler`, `ClimateHandler`, `NoiseHandler`, `StructureHandler` — the integration seam between Terrasect logic and Minecraft's worldgen APIs.
6. **`common/lookup/`** — `BiomeLookup`, `CompiledNoiseRegistry`, `NoiseChunkLookup` — performance-critical hot paths; understand before touching.
7. **`fabric/mixin/` or `neoforge/mixin/`** — one loader's mixin set to see how Terrasect injects into the MC pipeline. `LevelMixin`, `ChunkGeneratorMixin`, and `MultiNoiseBiomeSourceMixin` are the most central.
8. **`common/compat/`** — `BiomeCompat`, `ResourceKeyCompat`, `StructureCompat` — cross-version shims; important before touching versioned areas.

The `TestRegions.java` at the root of `common` is a useful orientation point — it instantiates test regions and reveals the intended developer-facing API.

---

### 5. Project conventions and best practices for future agents

These are grounded in the repository itself (AGENTS.md / CLAUDE.md) plus findings from this exploration:

#### Code style

- **Google Java Format** via Spotless. Run `./gradlew spotlessApply` before committing. Never bypass; CI enforces it.
- `UseVar` (OpenRewrite) is active — the project auto-migrates to `var` inference. Do not fight it.
- `stripComments` task exists. Comments written in source must be justifiable; pure what/how comments will be stripped.

#### Loader boundary discipline (hard constraint)

- `common` must have zero loader-specific imports. All MC API usage in `common` goes through accessor interfaces in `common/mixin/` (which are just `@Accessor`/`@Invoker` mixin interfaces, not implementations).
- Mixin *implementations* live in `fabric/mixin/` and `neoforge/mixin/` only.
- Client-only code in Fabric lives in the `client` source set; never leak to `main` or `common`.

#### Hot path rules (hard constraint)

Before touching any code in `common/lookup/`, `common/handler/`, `common/helpers/`, or any mixin body:
- Zero new allocations (no `new`, no stream, no lambda, no autoboxing, no varargs in loops).
- No unbounded iteration.
- No new caches unless lifecycle and invalidation cost are fully justified in a comment marked `@keep`.
- Prefer precomputed tables and index-based lookups.

#### Versioned areas

- Place version-specific overrides in `versions/{ver}/{loader}/src/` only — never in base modules.
- If a base class needs a conditional block for a version, prefer a compat shim in `common/compat/` instead.
- When a new MC version is added: copy the nearest version directory as a starting point; override only differing files; update `versions/{ver}/gradle.properties`.
- The 1.21.11 precedent of excluding `RegionDebugEntry.java` via build config (not a runtime guard) is the preferred model for API-incompatible classes.

#### Mixin discipline

- Mixins are minimal. Each mixin targets one specific injection point.
- `@Shadow`, `@Accessor`, `@Invoker` in `common/mixin/` (interfaces only). Implementation injection (`@Inject`, `@Overwrite`, `@Redirect`) only in loader-specific packages.
- Access widening via `terrasect.accesswidener` for Fabric; equivalent mechanism for NeoForge. Centralize; do not widen more than needed.
- Mixin list in `.json` configs must stay in sync with actual mixin class existence. Version-specific configs (e.g., 1.20.1's 4-mixin list) are maintained separately.

#### Testing conventions

- Snapshot tests are authoritative for strategy output. If a strategy change is intentional, update snapshots explicitly with `-PupdateSnapshots`.
- Integration tests (`integration/` package) require Minecraft via NeoForm — they are slow. Do not add more without need.
- The custom `testing/` package infrastructure (`SnapshotTests`, `SnapshotHtmlReports`) is reusable — use it, don't duplicate.
- Fixed seed `42424242L` is the canonical test seed for deterministic generation tests.

#### Serialization/networking (when relevant)

- NBT/codec logic must be centralized; no scattered `getInt("key")` calls across the codebase.
- Define defaults explicitly in codecs/serializers and plan for migration.
- If packets are added: validate and clamp on the receiving side; keep payloads small.

#### Change scope

- Do not make tiny isolated edits. Scan surrounding code for consistency before committing.
- Propose refactors before implementing them — wait for explicit approval.
- One intent per commit/PR.

---

### 6. What future orchestrator workers should delegate vs. do directly

#### Do directly (orchestrator executes without delegation)

- Creating and populating goal files.
- Updating `Status` field in goal files.
- Appending `## Handoff` blocks or execution log entries.
- Deleting completed items from `docs/TODO.md`.
- Routing the provider chain (Claude Code → Codex → Copilot → Ollama).
- Verifying goal file completion after a sub-agent returns.
- Escalating `FAILED` goals to Alexander.

#### Delegate (do not implement directly)

- Any code authoring, refactoring, or analysis.
- Any file reads beyond the goal file and KNOWLEDGE_BASE.md.
- Build/test execution and result interpretation.
- Architectural design decisions or technical trade-off analysis.
- Documentation writing (outside of goal file protocol scaffolding).

#### Decomposition guidance

- If a Kanban item requires multiple independent code changes, split into multiple goal files. Each goal file should have a single, verifiable intent.
- If a task requires research before implementation, create a research goal file first; the implementation goal file references the research response.
- If a task touches both loaders (Fabric + NeoForge), verify it is truly symmetric — often it is and can be delegated as one task, but be explicit in the goal file about the expectation that both loaders must be updated.
- If a task touches the hot path (`lookup/`, `handler/`, mixin bodies), flag this in the goal file's context so the sub-agent applies the hot path checklist.

#### Worker orientation reminder

Sub-agents reading a goal file may themselves be orchestrators for complex tasks. The goal file should make it explicit whether the assigned agent is expected to decompose further or to implement directly. Default: implement directly unless the task is explicitly marked "orchestrate and delegate."

---

### 7. Open questions and recommended next steps

These items should become Kanban cards or follow-up goal files:

#### High priority

1. **`docs/PROJECT_MAP.md` is outdated and partially speculative.** The current map was generated without full code inspection and contains assumptions about SDF usage and the worldgen pipeline that may not match the actual implementation. A dedicated goal file should assign a sub-agent to read the actual source files and rewrite the map with verified facts. This is already tracked in `docs/TODO.md`.

2. **No performance benchmarks.** The hot-path allocation-free constraint is a hard requirement, but there are no JMH benchmarks or allocation-tracking tests. Given that `lookup/` and `handler/` packages are the most likely sources of performance regressions, a benchmark suite is a risk-mitigation priority.

3. **NeoForge has no test source set.** The NeoForge module has no unit or game tests configured. All tests are in `common` or Fabric's client game tests. Whether NeoForge-specific behavior is tested (e.g., loader lifecycle, mixin injection correctness) is unknown.

4. **Windows JDK path in `gradle.properties`.** `org.gradle.java.home=C:\\Program Files\\Java\\jdk-25` will not work in CI or on Linux. This should be removed from the committed `gradle.properties` and handled via environment or user-local settings.

#### Medium priority

5. **Versioning model documentation.** The `versions/` inheritance mechanism (`gradle/versioned.gradle`) is not documented. New contributors will struggle to add a new MC version without guidance. A short how-to in `docs/` or AGENTS.md would save significant time.

6. **Fabric client game test coverage audit.** `ClientGameTestIntegration.java` exists but content was not verified in this exploration. What is actually tested in-game is unknown — this should be documented.

7. **`RegionDebugEntry` exclusion in 1.21.11 is load-bearing.** The root-project version of this class uses an API removed or changed in 1.21.11. This is a ticking technical debt item — the compat story for future MC versions that also lack the old API needs to be planned.

8. **`versions/` only covers up to 1.21.11.** If MC 1.22+ or further snapshots introduce breaking changes, the versioning model will need to accommodate them. This is not urgent but should be monitored as snapshot versions progress.

#### Low priority / informational

9. **`stripComments` task usage is unclear.** It exists but there are no documented conventions about when it is run or whether it is part of CI. If it is expected to run on commit, it should be part of the Spotless or build lifecycle.

10. **OpenRewrite `UseVar` recipe is active.** This auto-migrates local variables to `var`. Sub-agents writing Java code should be aware that their explicit type declarations may be rewritten on next build. This is fine but worth knowing to avoid confusion when code looks different after a Gradle run.

---

### Development environment notes (summary)

- **Java:** JDK 25 for root/bleeding-edge; JDK 21 for 1.21.x versioned builds; JDK 17 for 1.20.1. Gradle auto-downloads if not found locally.
- **Gradle:** 9.2.1 via wrapper. Use `./gradlew`, not system Gradle.
- **MC snapshot:** Root targets `26.1-snapshot-2`. Must be available in local Gradle/Loom cache.
- **Parallel builds disabled.** `org.gradle.parallel=false` — builds run sequentially. This is intentional (likely due to versioned project cross-dependencies).
- **Caching enabled.** `org.gradle.caching=true` — build cache is on; clean runs will be slow.

---

### Testing map (summary)

| Command | What runs |
|---------|----------|
| `./gradlew :common:test` | All unit + snapshot + integration tests |
| `./gradlew :common:test -PupdateSnapshots` | Same + regenerate snapshots |
| `./gradlew build` | All modules compile + common tests |
| `./gradlew :fabric:runClient` | Launches game + Fabric client game tests |
| No equivalent for NeoForge | — |

---

### Verification

- All findings are based on file reads; no code was executed and no Gradle tasks were run.
- Source file inventories were obtained via directory traversal, not compilation — if generated sources exist at runtime, they would not appear in this report.
- The content of individual Java source files (beyond their names and package placement) was not read in full for most files. Deep logic within specific handlers, strategies, and mixins was not analyzed.
- Mixin JSON configs were read; mixin class implementations were not audited for correctness.

---

### KNOWLEDGE_BASE.md update

A new **Section 7 — Terrasect Development Context** has been added to `docs/KNOWLEDGE_BASE.md` capturing the durable technical facts discovered here: module layout, build tasks, test commands, Java version matrix, versioning model, and hot-path rules. These facts belong in the knowledge base because they are stable across sessions and will be needed by future agents without requiring re-exploration.

The **Execution log** entry for this goal: `20260508 20:10 — Completed by Claude Code (claude-sonnet-4-6). Response written to goal file. KNOWLEDGE_BASE.md Section 7 added.`
