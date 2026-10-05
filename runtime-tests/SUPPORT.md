# Packaged runtime test support

Required runtime matrix, verified on 2026-10-05. Minecraft version truth comes from
`settings.gradle.kts`; loader/API/mod pins come from `stonecutter.properties.toml`.
`26.1.x` means Minecraft **26.1.2** in every production/test tree. Production metadata and
publication labels require the exact tested Minecraft version, including on older targets.

| Lane | Loader pin | Client API / bridge | Runtime mode |
|---|---|---|---|
| Fabric 1.20.1 | 0.16.14 | Fabric API 0.92.6+1.20.1 | Server smoke: no client GameTest module |
| Fabric 1.21.1 | 0.18.4 | Fabric API 0.116.12+1.21.1 | Server smoke: no client GameTest module |
| Fabric 1.21.11 | 0.18.4 | Fabric API 0.141.1+1.21.11 | Client GameTests |
| Fabric 26.1.x | 0.19.3 | Fabric API 0.154.2+26.1.2 | Client GameTests |
| Fabric 26.2.x | 0.19.3 | Fabric API 0.152.2+26.2 | Client GameTests |
| NeoForge 1.21.1 | 21.1.234 | Connector exists, Fabric client GameTest API absent | Server smoke |
| NeoForge 1.21.11 | 21.11.36-beta | No Connector 1.21.11 artifact | Server smoke |
| NeoForge 26.1.x | 26.1.2.112 | Connector 3.0.0-beta.6+26.1.2; FFAPI 0.155.3+26.1.2+3.5.7; Launchpad 1.9.2+26.1.2 | Client GameTests |
| NeoForge 26.2.x | 26.2.0.6-beta | No Connector or FFAPI 26.2 artifact | Server smoke |

Upstream evidence: Fabric API POMs for [1.20.1](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.92.6+1.20.1/fabric-api-0.92.6+1.20.1.pom)
and [1.21.1](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.12+1.21.1/fabric-api-0.116.12+1.21.1.pom)
do not declare `fabric-client-gametest-api-v1`.
[Connector metadata](https://maven.sinytra.org/org/sinytra/connector/maven-metadata.xml) and
[FFAPI metadata](https://maven.sinytra.org/org/sinytra/forgified-fabric-api/forgified-fabric-api/maven-metadata.xml)
have no bridge for the modern server-fallback lanes. The pinned FFAPI requires NeoForge at least 26.1.2.112.

All nine lanes have clean and compatibility modpacks. Fabric 1.20.1/1.21.1 and NeoForge
1.21.1/1.21.11/26.2 use a separate native-loader server test mod. It runs inside a real dedicated
Minecraft server, forces the smoke preset before world creation, verifies every constraint lookup,
biome admission, `/ts` commands and an actual forced structure, then stops the server. Gradle
requires the assertion-completion marker and successful exit; startup or `Done` alone cannot pass.
These lanes establish server coverage, not unavailable client API/GUI coverage. No bridge is needed
for native NeoForge server tests.

PR CI retains its previous lightweight build/unit/style and five Fabric smoke jobs. The full
production-jar/modpack layer runs locally with `./gradlew minecraftTest --continue` or through the
manual **Modpack runtime tests** workflow (`runtime-tests.yml`), with build/compat/both selection.
Heavy third-party/client tests are not triggered on PRs or pushes to main.

## Assertions and third-party packs

Every client-mode clean pack runs `SmokeGameTest` and `LootConstraintBlockAllGameTest`.
All nine compatibility packs contain GlitchCore, Biomes O' Plenty, TerraBlender, and Distant Horizons;
Client-mode Fabric also contains Create Fly. Fabric 26.2 additionally contains C2ME. The configured C2ME pins for 1.21.11 and 26.1.2
are not installed by these packs and do not establish runtime coverage there.
Exact Modrinth version ids are pinned by `deps.compat_*`; each result embeds installed filenames,
mod ids, SHA-256 and requested coordinates, including runtime dependencies.

All client-mode compatibility lanes run `CompatSmokeGameTest` and `ModdedBiomeConstraintGameTest`
(allow/block BOP biomes). Fabric lanes also run `CreatePonderCompatGameTest`.
Fabric 26.2 adds `BiomesOPlentyCompatGameTest`, `TerraBlenderCompatGameTest`,
`DistantHorizonsCompatGameTest` (LOD database) and `C2MECompatGameTest` (concurrent generation).
Server-mode clean/compat packs each execute `ServerSmokeGameTest`. Client-mode clean/compat test counts are 2/3 for Fabric 1.21.11 and 26.1.2, 2/7 for Fabric 26.2,
and 2/2 for NeoForge 26.1.2. These bounded scenarios do not cover every possible mod combination.

NeoForge installs **native NeoForge Terrasect**, not its Fabric production jar. On client-mode lanes,
the separate Fabric test mod and required Fabric runtime travel through Connector. Test classes use the
`terrasect.gametest` package to avoid a split package with the native mod under NeoForge's module loader.

HeadlessMC 2.10.0 and Ferium 4.7.1 are checksum pinned. Each client launch uses the version's Java
toolchain, fresh worlds, LWJGL stubs and dummy assets; DH rendering is disabled. Assertions establish
client lifecycle, generation and screen-state behavior, not GPU rendering or screenshot accuracy.

See [RELEASING.md](../docs/RELEASING.md) for commands, CI and evidence paths, and
[VERIFICATION.md](VERIFICATION.md) for the latest local execution record.
