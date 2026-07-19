# Terrasect Project Map

**Codebase:** `version-0.2.0` branch (Stonecutter-driven multiversion source)
**Last verified:** 2026-07-19
**Purpose:** Architectural reference for contributors and agents working on Terrasect.

---

## 1. Repository Layout

```
terrasect/
├── common/                        # All shared logic (see §2) — vcs source of truth
│   └── src/
│       ├── main/
│       │   ├── java/terrasect/    # Java: mixins, extender interfaces
│       │   └── kotlin/terrasect/  # Kotlin: everything else
│       └── test/
│           └── kotlin/terrasect/  # All unit/snapshot tests (Kotlin)
├── fabric/                        # Fabric loader integration (see §3)
├── neoforge/                      # NeoForge loader integration (see §3)
├── compat/c2me/                   # Git submodule — C2ME-fabric compat
├── e2e/                           # Fabric client gametest tree, separate Stonecutter matrix (see §6)
├── e2e-compat/                    # Third-party mod compatibility gametests, kept out of e2e (see §6)
├── versions/                      # Stonecutter-generated per-version projects — git-ignored, do not edit directly
├── settings.gradle.kts            # Stonecutter version matrix + module declarations
├── stonecutter.properties.toml    # Per-version dependency coordinates
├── stonecutter.gradle.kts         # Active dev version selector + Spotless config (no separate root build.gradle.kts)
├── gradle.properties              # Gradle daemon/build flags (not version pins — see stonecutter.properties.toml)
└── AGENTS.md / CLAUDE.md / GEMINI.md  # Agent instructions (CLAUDE.md → AGENTS.md symlink)
```

`common/`, `fabric/`, `neoforge/` hold the real, git-tracked source for the active
dev version (`26.2`). Stonecutter preprocesses this source into the per-version
projects under `versions/` at build/sync time — that directory is generated and
git-ignored, never edited directly. Note `common`/`fabric`/`neoforge` are not
themselves buildable Gradle projects — every buildable project is version-qualified
(`:<version>-<loader>`, e.g. `:26.2.x-fabric`). See [`docs/MULTIVERSION.md`](MULTIVERSION.md)
for the full version-matrix, compat-shim, and Stonecutter-gating story.

---

## 2. Common Module — Package Overview

All substantive logic lives in `common/`. The module is split between Java (mixins and extender interfaces) and Kotlin (everything else).

### Java packages (`common/src/main/java/terrasect/`)

| Package | Contents |
|---------|---------|
| `extender/` | Cross-cast interfaces used by mixins to expose data: `ChunkAccessExtender`, `ChunkGeneratorStructureStateExtender`, `ClimateSamplerExtender`, `ClimateTargetPointExtender`, `DensityFunctionHolderExtender`, `MultiNoiseBiomeSourceExtender`, `NoiseChunkExtender`, `PresetIdHolder`, `RandomSpreadStructurePlacementExtender`, `StructurePlacementExtender` |
| `mixin/climate/` | `ClimateClimateSamplerMixin`, `ClimateTargetPointMixin`, `MultiNoiseBiomeSourceMixin`, `NoiseChunkClimateSamplerMixin` |
| `mixin/noise/` | `DensityFunctionHolderMixin`, `NoiseChunkFunctionsMixin` |
| `mixin/preset/` | `DedicatedServerPropertiesMixin`, `DedicatedServerPropertiesWorldDimensionDataMixin`, `DerivedLevelDataMixin`, `MainMixin`, `PrimaryLevelDataMixin`, `WorldDimensionsMixin` |
| `mixin/scaffold/` | `ChunkAccessMixin`, `ChunkGeneratorStructureStateMixin`, `LevelMixin`, `NoiseChunkMixin` |
| `mixin/spawn/` | `NaturalSpawnerChunkGenMixin`, `NaturalSpawnerRuntimeMixin`, `NaturalSpawnerWorldGenMixin` |
| `mixin/structure/` | `ChunkGeneratorForcedMixin`, `ChunkGeneratorLocateMixin`, `ChunkGeneratorStructureMixin`, `ChunkStatusTasksStructureMixin`, `JigsawStructureAccessor`, `RandomSpreadStructurePlacementMixin`, `StructurePlacementMixin`, `StructureStartMixin` |
| `mixin/loot/` | `LootTableMixin` |
| `mixin/command/` | `CommandsMixin` |
| `client/` | `CreateWorldScreenMixin`, `DebugScreenEntriesInvoker` |
| `helpers/` | `ChunkDensityFunction` |

### Kotlin packages (`common/src/main/kotlin/terrasect/`)

| Package | Contents |
|---------|---------|
| `cache/` | `RegionsCache` (Caffeine-backed, striped-key, two-level), `PalettedGrid<T>` |
| `compat/` | Minecraft-**version** shims across the `>=1.21.11` fault line: `ResourceKeyCompat`, `SpawnCompat`, `LootContextCompat`, `NoiseRouterCompat`, `StructureMetadataCompat` — see [`docs/MULTIVERSION.md`](MULTIVERSION.md) |
| `definition/` | Region data model: `Region`, `RegionDefinition` (DSL builder), `RegionRegistry`, `Archetype`, `ClimateConstraints`, `HeightConstraints`, `NoiseConstraints`, `SelectionConstraints`, `StructureConstraints`, `Strategy`, `PresetRegistry` |
| `config/` | Strict TOML schema/parser (`TerrasectToml`, `TerrasectTomlWriter`, `TomlTable`), default config generation (`DefaultConfigFiles`), and runtime config application (`TerrasectConfig`, `TerrasectConfigManager`) |
| `generation/` | Pipeline: `Address`, `ChunkContext`, `DimensionContext`, `ForcedPlan`, `Locator`, `Selector`, `Traverser` |
| `gui/` | `RegionDebugEntry` — debug overlay entry |
| `handler/` | Hot-path Minecraft integration: `ClimateHandler`, `NoiseHandler`, `LootHandler`, `MobHandler`, `StructureHandler`, `CommandHandler`, `NoiseLogger` |
| `helpers/` | `ChunkDensityFunction` (Java), `NoiseTransform` |
| `instrumentation/` | Disabled-by-default scoped metrics API: `Instr`, `Counter`, `Timer`, `MetricId`, `Metrics`, `MetricsBackend`, `TerrasectMetrics` |
| `lookup/` | Pre-compiled per-region decision tables, one per domain (kept as parallel, independent implementations — see `AGENTS.md`'s standing architecture decisions): `CompiledNoiseConstraints`, `CompiledMobLookup`, `CompiledLootLookup`, `CompiledStructureLookup`, `CompiledForcedStructures` |
| `presets/` | `ClimateDebug.kt` (`CLIMATE_DEBUG` preset definition), `Index.kt` (`Presets` enum) |
| `sdf/` | SDF library: `area`, `archipelago`, `bounds`, `compose`, `consts`, `decoration`, `hex`, `noise`, `polygon`, `scatter`, `sites`, `subdivision`, `surround`, `voronoi` |
| `strategies/` | `ArchipelagoStrategy`, `HexStrategy`, `SubdivisionStrategy`, `SurroundStrategy`, `VoronoiStrategy` |
| `utils/` | `Packer` |
| Root | `Terrasect.kt` (init singleton + shared cache), `Constants.kt` |

---

## 3. Loader Modules

The loader modules are intentionally minimal. All game logic is in `common/`. The loaders only provide:
- Mod entry point (calling `Terrasect.init(configRoot)`)
- Loader-specific lifecycle hooks

### Fabric (`fabric/src/`)

| File | Purpose |
|------|---------|
| `main/kotlin/terrasect/TerrasectFabric.kt` | `ModInitializer` entry point |
| `client/kotlin/terrasect/TerrasectFabricClient.kt` | `ClientModInitializer` entry point |

Fabric client gametests now live in the separate `e2e`/`e2e-compat` Stonecutter
trees (see §6), not under `fabric/src`.

### NeoForge (`neoforge/src/`)

| File | Purpose |
|------|---------|
| `main/kotlin/terrasect/TerrasectNeoForge.kt` | `@Mod` entry point with event bus setup |

---

## 4. Generation Architecture

### Region Tree

A `Region` is an immutable tree node. The root region for a dimension is resolved from the active `RegionRegistry` (via `PresetRegistry`). Each `Region` can have:
- A `Strategy` (how its space is partitioned into child regions)
- Constraint objects (`ClimateConstraints`, `HeightConstraints`, `NoiseConstraints`, `SelectionConstraints`, `StructureConstraints`) that the handlers apply when modifying Minecraft's worldgen pipeline

### World-Coordinate Resolution

```
(blockX, blockZ)
       │
       ▼
   Traverser.traverse(x, z)
       │  recursively calls Strategy.traverse(step) at each level
       │  each strategy uses SDF computations + RegionsCache
       ▼
   TraversalStep { region: Region, distance: Float, id: ByteBuffer }
```

`ChunkContext` runs this traversal for every block in a chunk (plus padding) at chunk-load time, caching results in a `PalettedGrid<Region>` and a `FloatArray` of distances. Handlers read from this pre-computed grid on the hot path. `Locator`/`ForcedPlan`/`Selector` provide the equivalent lookup path for structure placement/location queries.

### Dimension Lifecycle

1. On world load, `DimensionContext.register(...)` is called (via the preset/scaffold mixins).
2. It resolves the active `RegionRegistry` from `PresetRegistry`, builds the region tree, compiles the per-domain lookup tables (`lookup/Compiled*`), and creates a `DimensionContext` stored in a `ConcurrentHashMap` keyed by dimension ID.
3. `ClimateHandler`, `NoiseHandler`, `LootHandler`, `MobHandler`, and `StructureHandler` retrieve `DimensionContext` by dimension ID when processing their respective hot-path calls.

### SDF Usage

Signed Distance Fields compute the distance of a world coordinate from a region boundary. The `SdfCompose` accumulator collects SDFs as the tree traversal descends, producing a composited distance value stored per-block in `ChunkContext.distances`.

---

## 5. Key Invariants

1. **Allocation-free hot path.** `ChunkContext`, `Traverser`, `Locator`, `RegionsCache`, and the `lookup/Compiled*` tables are designed to avoid per-call allocations.
2. **Deterministic generation.** All strategies are deterministic given the same seed. Tests use a canonical seed (`42424242L`). Snapshot tests verify strategy output.
3. **Mixins stay in `common/`.** All mixin implementations are loader-agnostic and live in `common/`. Loader modules do not contain mixin code.
4. **Preset + region tree is immutable at runtime.** `Region` objects are constructed once on world load and treated as value objects. The `RegionsCache` keys are derived from the immutable address path, not from mutable state.
5. **No shared kernel across `lookup/Compiled*`.** `CompiledMobLookup`, `CompiledLootLookup`, and `CompiledStructureLookup` are structurally similar but stay independent per-domain implementations — see the standing architecture decisions in `AGENTS.md`/`CLAUDE.md`.

---

## 6. Testing

### Unit / snapshot tests

All unit and snapshot tests are in `common/src/test/kotlin/`. Run with `./gradlew :26.2.x-common:test`. Snapshot tests use `de.skuzzle.test:snapshot-tests-junit5`.

To update snapshots: `./gradlew :26.2.x-common:test -PupdateSnapshots`

Coverage areas: SDF geometry (`sdf/`), strategies (`strategies/`), generation pipeline (`generation/`), region/structure/selection definitions (`definition/`), config parsing (`config/`), handlers (`handler/`), compiled lookups (`lookup/`), instrumentation (`instrumentation/`), noise transform helpers (`helpers/`), and the snapshot framework itself (`testing/`).

### Client gametests (`e2e`, `e2e-compat`)

`e2e` is a separate Stonecutter tree of Fabric client gametests spanning a subset of the main version matrix. See [`docs/MULTIVERSION.md`](MULTIVERSION.md) for the full version list and gating rules. Two tiers:
- `e2e/src/gametest*` — portable smoke coverage (`SmokeGameTest`, `LootConstraintGameTest`) that runs on every e2e version and asserts the constraint pipeline is actually active, not just that generation succeeded.
- `e2e/src/gametest-latest` — heavy tests (terrain digests, structure/mob/archetype/dimension probes, screenshots) compiled only on the latest matrix version.

`e2e-compat` holds third-party mod compatibility gametests, kept in its own tree so the core suite never depends on a third-party mod jar being resolvable.

Run: `./gradlew :e2e:<version>:runClientGameTest` (optionally `-Ptest=<TestName>`).

---

## 7. Build System Notes

- `stonecutter.gradle.kts` sets the active dev version (`stonecutter active "..."`) and applies Spotless — there is no separate root `build.gradle.kts`.
- `settings.gradle.kts` declares the Stonecutter version matrix (`common`/`fabric`/`neoforge` per Minecraft version) plus the separate `e2e` and `e2e-compat` Stonecutter trees, and each tree's `vcsVersion` (which version's source is mirrored into the top-level directories for git/IDE).
- `stonecutter.properties.toml` holds per-version dependency coordinates and the per-version Java toolchain (`java = "..."`); `gradle.properties` only holds Gradle daemon/build flags, not version pins.
- CI explicitly calls `spotlessCheck`. Always run `./gradlew spotlessApply` before committing.
- Spotless rules: Java → `googleJavaFormat()`, Kotlin → `ktfmt().googleStyle()`, Kotlin Gradle → `ktfmt()`.
- Snapshot update flags accepted: `-PupdateSnapshots`, `-PupdateSnapshots=true`, `-PsnapshotUpdate`.

### Key version pins (active dev version, `26.2.x`, from `stonecutter.properties.toml`)

| Property | Value |
|----------|-------|
| Minecraft | 26.2 |
| Java | 25 |
| Kotlin | 2.3.0 |
| JVM target | 25 |
| Fabric Loader | 0.19.3 |
| NeoForge Loader | 26.2.0.6-beta |
| Kotlin for Forge | 6.3.0 |

See [`docs/MULTIVERSION.md`](MULTIVERSION.md) for the full multi-version matrix (`1.20.1`, `1.21.1`, `1.21.11`, `26.1`, `26.2`).

### Common's runtime dependencies must be embedded per loader

`common`'s third-party libraries (`caffeine`, `net.openhft:zero-allocation-hashing`,
`com.github.komputing:kbase58`, `com.electronwill.night-config`) are declared `implementation` in
`build.common.gradle.kts`. That only puts them on the **compile/dev** classpath of a consuming
loader module — it does **not** get them into a shipped mod jar. Each loader module must separately
embed every one of them, or players crash with `NoClassDefFoundError` the first time the missing
class is touched (see the `Caffeine` crash in issue #62 — NeoForge's per-mod module isolation means
nothing outside a mod's own jar/`jarJar` bundle is visible to it, unlike Fabric's shared classloader,
which can accidentally mask the same gap if some other installed mod happens to bundle the same
library):

- **Fabric** (`build.fabric.gradle.kts`): add each library via `embedded("group:artifact:version")`
  (the `io.github.gmazzo.dependencies.embedded` plugin's config) — for every version, not only the
  legacy (`1.20.1`) Loom path. `runtimeOnly` is not enough; it only affects the dev/test classpath.
- **NeoForge** (`build.neoforge.gradle.kts`): add each library via `jarJar("group:artifact:version")`
  (from `net.neoforged.moddev`'s built-in JarJar support). `implementation` is not enough for the
  same reason.
- `com.electronwill.night-config` doesn't need an explicit NeoForge declaration — it's already a
  transitive dependency of NeoForge's own loader (`fancymodloader`), so it's present on the module
  path for free. Verify any *new* common runtime dependency against the target platform's own POM
  before assuming this shortcut applies to it too.
- **Verify embedding, don't just verify compilation.** `./gradlew :<version>-<loader>:build`
  succeeding proves nothing about the shipped jar's contents — the missing classes only show up at
  runtime. After adding/changing a common runtime dependency, build the real production artifact
  (`:<version>-fabric:remapJar` pre-1.21.11, `:<version>-fabric:jar` at 1.21.11+, or
  `:<version>-neoforge:jar`) and `unzip -l` it to confirm the library's classes are actually inside.
  A `-dev` jar under `build/devlibs/` or a stale `build/libs/*.jar` is not the shipped artifact and
  will pass this check even when the fix is wrong (extraction tasks like
  `extractEmbeddedDependenciesClasses` cache silently across an added dependency; force with
  `--rerun-tasks` if in doubt).

### Key Gradle tasks

| Task | Purpose |
|------|---------|
| `./gradlew build` | Compile + test all modules across the version matrix |
| `./gradlew :26.2.x-common:test` | All unit + snapshot tests |
| `./gradlew :26.2.x-common:test -PupdateSnapshots` | Same, regenerating snapshot reference files |
| `./gradlew spotlessApply` | Apply Google Java Format + ktfmt |
| `./gradlew spotlessCheck` | Verify formatting (enforced by CI) |
| `./gradlew :<version>-fabric:runClient` / `runServer` | Launch Fabric dev game for a given version |
| `./gradlew :<version>-neoforge:runClient` / `runServer` | Launch NeoForge dev game for a given version |
| `./gradlew :e2e:<version>:runClientGameTest` | Run client gametests for a given version |

---

## 8. Code Style Guardrails

Terrasect favors direct, low-magic code. Apply these guardrails when editing source:

- Prefer no comments in code. Simplify the code instead, and remove obsolete comments when touching a file.
- Break APIs when needed to remove duplicate paths or compatibility wrappers; update call sites together.
- Keep implementations small, explicit, and coherent across modules.
- Avoid adding abstraction layers unless they clearly reduce duplication or clarify boundaries.
- Remove obsolete code when replacing functionality so the codebase stays singular and easy to follow.

See `AGENTS.md`/`CLAUDE.md` for the standing architecture decisions (settled by prior audit review) that should not be re-litigated without an explicit request.
