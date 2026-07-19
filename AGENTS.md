# Terrasect: Minecraft Mod (Multiloader)

## Project Overview
Terrasect is a multiloader Minecraft mod, written in **Kotlin 2.3.0**, targeting Minecraft `1.20.1`,
`1.21.1`, `1.21.11`, `26.1`, and `26.2` via Stonecutter. **`26.2` is the primary / active development
version.** It follows a standard multi-module architecture to support both **Fabric** and **NeoForge**
loaders from a shared codebase. See [`docs/MULTIVERSION.md`](docs/MULTIVERSION.md) for the full matrix.

- **Primary Language:** Kotlin (with Java 25 on the active version; older targets use Java 17/21 — see `stonecutter.properties.toml`)
- **Target Platform:** Minecraft `26.2` (active); see the full version matrix above
- **Architecture:** Shared common logic with loader-specific implementation modules.

## Architecture & Modules
- **`common/`**: Contains the core logic shared across all loaders.
  - `terrasect.Terrasect`: Main initialization class.
  - `terrasect.definition`: Region-based registry and generation strategies (e.g., Hex, Voronoi).
  - `terrasect.sdf`: Likely contains Signed Distance Field logic for world generation.
- **`fabric/`**: Fabric-specific implementation and entrypoints.
  - `terrasect.TerrasectFabric`: Implements `ModInitializer`.
- **`neoforge/`**: NeoForge-specific implementation and entrypoints.
  - `terrasect.TerrasectNeoForge`: Main `@Mod` class, handling NeoForge event buses.
- **`compat/c2me/`**: A Git submodule pointing to [C2ME-fabric](https://github.com/RelativityMC/C2ME-fabric.git), used for performance enhancements.
- **`versions/`**: Stonecutter-generated per-version projects (git-ignored) — do not edit directly.
- **`e2e/`, `e2e-compat/`**: Fabric client gametest trees (separate Stonecutter matrices).
- **`docs/`**: Project docs and operational context (for example `docs/PROJECT_MAP.md`, `docs/AGENT_WORKFLOW.md`, and `docs/TODO.md`) that can speed up onboarding and planning.

## Building and Running
The project uses **Gradle** as its build system. `common`/`fabric`/`neoforge` are not directly
buildable — every buildable project is version-qualified (`:<version>-<loader>`).

- **Windows shell (PowerShell/cmd):** Use `./gradlew.bat` equivalents for commands below (for example `./gradlew.bat build`).
- **Build all versions/loaders:** `./gradlew build`
- **Build one version/loader:** `./gradlew :26.2.x-fabric:build` or `./gradlew :26.2.x-neoforge:build`
- **Run Fabric Client:** `./gradlew :26.2.x-fabric:runClient`
- **Run NeoForge Client:** `./gradlew :26.2.x-neoforge:runClient`
- **Run Fabric Server:** `./gradlew :26.2.x-fabric:runServer`
- **Run NeoForge Server:** `./gradlew :26.2.x-neoforge:runServer`

## Development Conventions
- **Code Style:** Enforced by **Spotless**.
  - **Kotlin:** `ktfmt` with Google style.
  - **Java:** `google-java-format`.
  - **Format Check:** `./gradlew spotlessCheck`
  - **Apply Formatting:** `./gradlew spotlessApply`
- **Style guardrails:** Keep code direct and comment-free. Prefer simplifying code, removing obsolete pathways, and updating callers instead of preserving compatibility wrappers or indirect layers.
- **Claude-backed task workspace:** When a task is delegated to Claude Code, create and keep a dedicated workspace before starting. A branch by itself is not enough. Reuse the same workspace and keep the associated PR open across repeated visits to the same topic until Alexander explicitly says the task is done.
- **Testing:** The project supports snapshot-based testing.
  - **Update Snapshots:** Use the `-PupdateSnapshots=true` flag during testing to update reference snapshots.
- **Mod IDs and Constants:** Centralized in `terrasect.Constants` within the `common` module.

## Architecture decisions (standing, do not re-litigate)
Settled by post-merge audit review; treat as authoritative unless the user explicitly revisits them.
- **No shared compiled-selection kernel across mob/loot/structure.** `CompiledMobLookup`, `CompiledLootLookup`, and `CompiledStructureLookup` look structurally similar (region → registry-entry decision table) but must stay parallel, independent implementations. A shared abstraction would force cohesion between three domains that don't actually share rules and would couple their evolution. Tighten each in place instead.
- **`RegionDefinition`/`RegionBuilder` mutability is intentional**, not a smell — no immutability rework.
- **`common` is deliberately the "god module"** for this project — it's a mod, not loader harnesses. Shared logic, handlers, mixins, and builders belong there.

## Key Versions (active version, `26.2.x`, from `stonecutter.properties.toml`)
- **Kotlin:** 2.3.0
- **Java:** 25
- **Minecraft:** 26.2
- **Fabric Loader:** 0.19.3
- **NeoForge Loader:** 26.2.0.6-beta
- **Kotlin for Forge:** 6.3.0

See [`docs/MULTIVERSION.md`](docs/MULTIVERSION.md) for pins on every other supported version.
