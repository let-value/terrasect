# Packaged client test support

Required release matrix, verified on 2026-10-05. Minecraft version truth comes from
`settings.gradle.kts`; loader/API/mod pins come from `stonecutter.properties.toml`.
`26.1.x` means Minecraft **26.1.2** in every production/test tree.

| Lane | Loader pin | Client API / bridge | Runtime capability |
|---|---|---|---|
| Fabric 1.20.1 | 0.16.14 | Fabric API 0.92.6+1.20.1 | INCOMPLETE: no client GameTest module |
| Fabric 1.21.1 | 0.18.4 | Fabric API 0.116.12+1.21.1 | INCOMPLETE: no client GameTest module |
| Fabric 1.21.11 | 0.18.4 | Fabric API 0.141.1+1.21.11 | Available |
| Fabric 26.1.x | 0.19.3 | Fabric API 0.154.2+26.1.2 | Available |
| Fabric 26.2.x | 0.19.3 | Fabric API 0.152.2+26.2 | Available |
| NeoForge 1.21.1 | 21.1.234 | Connector exists, Fabric client GameTest API absent | INCOMPLETE |
| NeoForge 1.21.11 | 21.11.36-beta | No Connector 1.21.11 artifact | INCOMPLETE |
| NeoForge 26.1.x | 26.1.2.112 | Connector 3.0.0-beta.6+26.1.2; FFAPI 0.155.3+26.1.2+3.5.7; Launchpad 1.9.2+26.1.2 | Available |
| NeoForge 26.2.x | 26.2.0.6-beta | No Connector or FFAPI 26.2 artifact | INCOMPLETE |

Upstream evidence: Fabric API POMs for [1.20.1](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.92.6+1.20.1/fabric-api-0.92.6+1.20.1.pom)
and [1.21.1](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.12+1.21.1/fabric-api-0.116.12+1.21.1.pom)
do not declare `fabric-client-gametest-api-v1`.
[Connector metadata](https://maven.sinytra.org/org/sinytra/connector/maven-metadata.xml) and
[FFAPI metadata](https://maven.sinytra.org/org/sinytra/forgified-fabric-api/forgified-fabric-api/maven-metadata.xml)
have no bridge for the blocked modern lanes. The pinned FFAPI requires NeoForge at least 26.1.2.112.

Every incomplete lane has both Gradle scenario tasks, writes an `incomplete` result with zero
executions and its precise blocker, then fails. `./gradlew minecraftTest --continue` exercises the
complete matrix; it must fail until these five capabilities exist. Release remains blocked.

## Assertions and third-party packs

Every available clean pack runs `SmokeGameTest` and `LootConstraintBlockAllGameTest`.
All compatibility packs contain GlitchCore, Biomes O' Plenty, TerraBlender, and Distant Horizons;
Fabric also contains Create Fly. Fabric 26.2 additionally contains C2ME.
Exact Modrinth version ids are pinned by `deps.compat_*`; each result embeds installed filenames,
mod ids, SHA-256 and requested coordinates, including runtime dependencies.

All available compatibility lanes run `CompatSmokeGameTest` and `ModdedBiomeConstraintGameTest`
(allow/block BOP biomes). Fabric lanes also run `CreatePonderCompatGameTest`.
Fabric 26.2 adds `BiomesOPlentyCompatGameTest`, `TerraBlenderCompatGameTest`,
`DistantHorizonsCompatGameTest` (LOD database) and `C2MECompatGameTest` (concurrent generation).
Thus clean/compat test counts are 2/3 for Fabric 1.21.11 and 26.1.2, 2/7 for Fabric 26.2,
and 2/2 for NeoForge 26.1.2. These bounded scenarios do not cover every possible mod combination.

NeoForge installs **native NeoForge Terrasect**, not its Fabric production jar. Only the separate
Fabric test mod and required Fabric runtime travel through Connector. Test classes use the
`terrasect.gametest` package to avoid a split package with the native mod under NeoForge's module loader.

HeadlessMC 2.10.0 and Ferium 4.7.1 are checksum pinned. Each client launch uses the version's Java
toolchain, fresh worlds, LWJGL stubs and dummy assets; DH rendering is disabled. Assertions establish
client lifecycle, generation and screen-state behavior, not GPU rendering or screenshot accuracy.

See [RELEASING.md](../docs/RELEASING.md) for commands, CI and evidence paths, and
[VERIFICATION.md](VERIFICATION.md) for the latest local execution record.
