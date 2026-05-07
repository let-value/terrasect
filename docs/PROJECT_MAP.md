# 🗺️ Terrasect Project Map: Architectural Analysis

**Purpose:** To serve as a comprehensive architectural knowledge base draft covering the project structure, core methodologies, and domain logistics for Terrasect, which enables narrative world partitioning within a Minecraft-like environment.

## 1. Directory Structure/File Purpose

The project is divided into three core modules (`common`, `fabric`, `neoforge`) to ensure modularity and adherence to loader-specific requirements.

### 📁 `/home/alex/terrasect/common/` (Core Logic)
This module contains shared utility code, generation strategies, and resource definitions, ensuring core worldgen logic is reusable across both mod loaders.
*   **`src/test/...`**: Extensive test resources and snapshot files, indicating a focus on determinism and visual validation of various generation strategies (e.g., Voronoi, Ocean Constraints, Biome Visualization).
*   **`src/test/resources/templates/...`**: Mustache templates and resource layout files used for generating structured data or visualizations (e.g., `layout.mustache`, `sections/overview.mustache`).
*   **`src/test/java/...`**: Integration and unit tests (e.g., `StrategySnapshotTest.java`, `MinecraftNoiseRouterSnapshotTest.java`), validating the complex interaction of noise functions and generation stages.
*   **Core Logic Implication:** This directory houses the abstract worldgen machinery (strategies, noise functions, data structures) independent of the final Minecraft mod loader.

### 📁 `/home/alex/terrasect/fabric/` (Fabric Loader Specifics)
This module contains all code necessary to hook Terrasect into the Fabric mod loader environment (mixins, client initializers).
*   **Mixins (`*Mixin.java`):** Patches key Minecraft classes (e.g., `BiomeMixin`, `ChunkGeneratorMixin`, `LevelMixin`) to inject Terrasect's world generation logic at runtime, ensuring the mod hooks into the game's core generation pipeline.
*   **`TerrasectFabric.java`**: The entry point for the mod, responsible for triggering the common initialization logic upon mod load.
*   **`fabric.mod.json` / Mixin Configs:** Standard resource files defining mod metadata and the scope of code modification.

### 📁 `/home/alex/terrasect/neoforge/` (NeoForge Loader Specifics)
This module mirrors the `fabric` module, containing the platform-specific code required to integrate Terrasect into the NeoForge environment.
*   **Mixins (`*Mixin.java`):** Provides platform-specific patches for classes like `BiomeMixin`, `ChunkGeneratorMixin`, etc., ensuring compatibility with the NeoForge life cycle and APIs.
*   **`TerrasectNeoForge.java`**: The entry point for the mod, similar to the Fabric version, managing initialization.
*   **Config Files:** Contains specific NeoForge resource files (`.mods.toml`, `accesstransformer.cfg`) governing mod loading and access control.

## 2. Key Classes/Ownership

| Key Class/Mixin | Ownership | Function |
| :--- | :--- | :--- |
| `Terrasect.init()` | `common` | The central initialization method, coordinating component setup and ensuring the world generation pipeline is ready regardless of the platform. |
| `BiomeMixin` | `fabric`/`neoforge` | Intercepts Biome generation calls to allow Terrasect's logic (e.g., temperature, biomes) to override or augment inherent Minecraft behavior. |
| `ChunkGeneratorMixin` | `fabric`/`neoforge` | Hook into the core chunk generation pipeline, providing the execution point for complex region calculations before final block placement. |
| `NoiseMixin` / `ClimateMixin` | `fabric`/`neoforge` | Provides mechanisms to calculate large-scale environmental data (e.g., temperature gradients, resource density) using sophisticated noise functions. |
| `StrategySnapshotTest` | `common` | Utility class and test suite framework used to validate the determinism and output of various world generation "strategies" (e.g., Voronoi, nested subdivisions). |

## 3. Region Data Flow/Worldgen

Terrasect's world generation is conceptualized as a four-stage pipeline, transforming high-level definitions into low-level block properties.

1.  **Definition (Constraints & Strategies):** The process begins by loading high-level world constraints (e.g., "Ocean region must be here," "Temperature must vary across latitude"). This stage uses defined *strategies* (like Voronoi or hexagonal subdivision) referenced from the **Region Registry** to partition the space conceptually.
2.  **Macro Calculation (Noise & Traits):** Large-scale environmental properties are calculated. Noise functions (NoiseMixin) and custom trait evaluators (ClimateMixin) run across the entire region to define continuous values (e.g., altitude, humidity, temperature). The `common` module houses the mathematical core for this calculation.
3.  **Refinement (Optimization & Biome Assignment):** This stage combines the macro traits into discrete, localized world data. The generated values are fed into the **Biome/Structure registry** (via Platform Mixins), which uses the constraints to narrow down potential outcomes and establish adjacency rules.
4.  **Injection (Runtime Implementation):** The fully calculated, localized data packet (containing defined biomes, structure flags, and property overrides) is injected into the game's chunk generation pipeline (`ChunkGeneratorMixin`), ensuring that the modified data is used as the primary source of truth for block placement during runtime.

## 4. Assumptions/Invariants

1.  **Modularity Boundary:** The **`common`** module must remain strictly agnostic of the Minecraft loader (Fabric/NeoForge). All platform-specific code must reside in the respective loader module and be limited to mixins/hot-path overhauls.
2.  **Determinism:** World generation must be completely deterministic. The use of a fixed seed (`SEED = 42424242L`) in testing enforces that the same input *always* yields the same output structure.
3.  **Hot Path Allocation-Free:** All code segments running in high-frequency paths (per block, per chunk, or per tick) must execute without memory allocations (e.g., avoiding `java.util.Stream`, unnecessary Object creation, boxing) to ensure required performance targets and prevent stuttering.