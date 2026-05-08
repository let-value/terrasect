# Terrasect Project Map

**Codebase:** `reborn` branch  
**Last verified:** 2026-05-08  
**Purpose:** Architectural reference for contributors and agents working on Terrasect.

---

## 1. Repository Layout

```
terrasect/
├── common/                        # All shared logic (see §2)
│   └── src/
│       ├── main/
│       │   ├── java/terrasect/    # Java: mixins, extender interfaces
│       │   └── kotlin/terrasect/  # Kotlin: everything else
│       └── test/
│           └── kotlin/terrasect/  # All tests (Kotlin)
├── fabric/                        # Fabric loader integration (see §3)
│   └── src/
│       ├── main/kotlin/terrasect/
│       ├── client/kotlin/terrasect/
│       └── gametest/kotlin/terrasect/
├── neoforge/                      # NeoForge loader integration (see §3)
│   └── src/main/kotlin/terrasect/
├── compat/c2me/                   # Git submodule — C2ME-fabric compat
├── build.gradle                   # Root build (Spotless, shared subproject config)
├── settings.gradle                # Module declarations: common, fabric, neoforge
├── gradle.properties              # All version pins
└── AGENTS.md / CLAUDE.md / GEMINI.md  # Agent instructions (CLAUDE.md → AGENTS.md symlink)
```

---

## 2. Common Module — Package Overview

All substantive logic lives in `common/`. The module is split between Java (mixins and extender interfaces) and Kotlin (everything else).

### Java packages (`common/src/main/java/terrasect/`)

| Package | Contents |
|---------|---------|
| `extender/` | Cross-cast interfaces used by mixins to expose data: `ChunkAccessExtender`, `ClimateSamplerExtender`, `ClimateTargetPointExtender`, `DensityFunctionHolderExtender`, `MultiNoiseBiomeSourceExtender`, `NoiseChunkExtender`, `PresetIdHolder` |
| `mixin/climate/` | 4 climate mixins: `ClimateClimateSamplerMixin`, `ClimateTargetPointMixin`, `MultiNoiseBiomeSourceMixin`, `NoiseChunkClimateSamplerMixin` |
| `mixin/noise/` | 3 noise mixins: `DensityFunctionHolderMixin`, `NoiseBasedChunkGeneratorMixin`, `NoiseChunkFunctionsMixin` |
| `mixin/preset/` | 6 preset/world-init mixins: `DedicatedServerPropertiesMixin`, `DedicatedServerPropertiesWorldDimensionDataMixin`, `DerivedLevelDataMixin`, `MainMixin`, `PrimaryLevelDataMixin`, `WorldDimensionsMixin` |
| `mixin/scaffold/` | 3 structural mixins: `ChunkAccessMixin`, `LevelMixin`, `NoiseChunkMixin` |
| `client/` | 1 client mixin: `CreateWorldScreenMixin` |

### Kotlin packages (`common/src/main/kotlin/terrasect/`)

| Package | Contents |
|---------|---------|
| `cache/` | `RegionsCache` (Caffeine-backed, striped-key, two-level), `PalettedGrid<T>` |
| `compat/` | `ResourceKeyCompat` — loader-agnostic dimension key helper |
| `definition/` | Region data model: `Region`, `RegionDefinition` (DSL builder), `RegionRegistry`, `ClimateConstraints`, `HeightConstraints`, `NoiseConstraints`, `SelectionConstraints`, `Strategy`, `PresetRegistry` |
| `generation/` | Pipeline: `Address`, `ChunkContext`, `DimensionContext`, `Locator` + `LocateStep`, `Traverser` + `TraversalStep` |
| `gui/` | `RegionDebugEntry` — debug overlay entry |
| `handler/` | `ClimateHandler`, `NoiseHandler` — hot-path Minecraft integration |
| `helpers/` | `ChunkDensityFunction`, `NoiseTransform` |
| `lookup/` | `CompiledNoiseRegistry` — pre-compiled noise constraint map for a region tree |
| `presets/` | `ClimateDebug.kt` (`CLIMATE_DEBUG` preset definition), `Index.kt` (`Presets` enum) |
| `sdf/` | SDF library: `area`, `bounds`, `compose`, `consts`, `hex`, `polygon`, `sites`, `subdivision`, `surround`, `voronoi` |
| `strategies/` | `HexStrategy`, `SubdivisionStrategy`, `SurroundStrategy`, `VoronoiStrategy` |
| `utils/` | `Packer` |
| Root | `Terrasect.kt` (init singleton + shared cache), `Constants.kt` |

---

## 3. Loader Modules

The loader modules are intentionally minimal. All game logic is in `common/`. The loaders only provide:
- Mod entry point (calling `Terrasect.init()`)
- Loader-specific lifecycle hooks
- Game test integration (Fabric only)

### Fabric (`fabric/src/`)

| File | Purpose |
|------|---------|
| `main/kotlin/terrasect/TerrasectFabric.kt` | `ModInitializer` entry point |
| `client/kotlin/terrasect/TerrasectFabricClient.kt` | `ClientModInitializer` entry point |
| `gametest/kotlin/terrasect/WorldDigestGameTest.kt` | Game test: world digest snapshot |
| `gametest/kotlin/terrasect/TerrasectFabricClientGameTest.kt` | Game test: client-side |
| `gametest/kotlin/terrasect/GameTestFilter.kt` | Test filter utility |

### NeoForge (`neoforge/src/`)

| File | Purpose |
|------|---------|
| `main/kotlin/terrasect/TerrasectNeoForge.kt` | `@Mod` entry point with event bus setup |

---

## 4. Generation Architecture

### Region Tree

A `Region` is an immutable tree node. The root region for a dimension is resolved from the active `RegionRegistry` (via `PresetRegistry`). Each `Region` can have:
- A `Strategy` (how its space is partitioned into child regions)
- Constraint objects (`ClimateConstraints`, `HeightConstraints`, `NoiseConstraints`, `SelectionConstraints`) that the handlers apply when modifying Minecraft's worldgen pipeline

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

`ChunkContext` runs this traversal for every block in a chunk (plus padding) at chunk-load time, caching results in a `PalettedGrid<Region>` and a `FloatArray` of distances. Handlers read from this pre-computed grid on the hot path.

### Dimension Lifecycle

1. On world load, `DimensionContext.register(...)` is called (via the preset/scaffold mixins).
2. It resolves the active `RegionRegistry` from `PresetRegistry`, builds the region tree, and creates a `DimensionContext` stored in a `ConcurrentHashMap` keyed by dimension ID.
3. `ClimateHandler` and `NoiseHandler` retrieve `DimensionContext` by dimension ID when processing noise/climate calls.

### SDF Usage

Signed Distance Fields compute the distance of a world coordinate from a region boundary. The `SdfCompose` accumulator collects SDFs as the tree traversal descends, producing a composited distance value stored per-block in `ChunkContext.distances`.

---

## 5. Key Invariants

1. **Allocation-free hot path.** `ChunkContext`, `Traverser`/`TraversalStep`, `Locator`/`LocateStep`, and `RegionsCache` are designed to avoid per-call allocations. `TraversalStep` and `LocateStep` are `ThreadLocal` singletons. Do not introduce `Stream` usage or boxing in these paths.
2. **Deterministic generation.** All strategies are deterministic given the same seed. Tests use a canonical seed (`42424242L`). Snapshot tests verify strategy output.
3. **Mixins stay in `common/`.** All 16+1 mixin implementations are loader-agnostic and live in `common/`. Loader modules do not contain mixin code.
4. **Preset + region tree is immutable at runtime.** `Region` objects are constructed once on world load and treated as value objects. The `RegionsCache` keys are derived from the immutable address path, not from mutable state.

---

## 6. Testing

All tests are in `common/src/test/kotlin/`. Run with `./gradlew :common:test`. Snapshot tests use `de.skuzzle.test:snapshot-tests-junit5`.

To update snapshots: `./gradlew :common:test -PupdateSnapshots`

Test coverage areas:
- SDF geometry (`sdf/` — bounds, distance, hex, polygon, compose, sites)
- Strategies (`strategies/` — all four strategy implementations)
- Generation pipeline (`generation/` — Address, Locator, Traverser)
- Region definition (`definition/RegionDefinitionTest`)
- Noise transform helpers (`helpers/NoiseTransformSnapshotTest`)
- Compiled noise registry (`lookup/CompiledNoiseConstraintsTest`)
- Snapshot framework itself (`testing/SnapshotLibraryTest`)

---

## 7. Build System Notes

- Root `build.gradle` applies Spotless and shared subproject config (Java/Kotlin toolchain, snapshot flag handling).
- `settings.gradle` includes `common`, `fabric`, `neoforge` only.
- `gradle.properties` contains all version pins. No Windows-specific paths are present.
- Spotless `enforceCheck = false` in root — CI explicitly calls `spotlessCheck`. Always run `./gradlew spotlessApply` before committing.
