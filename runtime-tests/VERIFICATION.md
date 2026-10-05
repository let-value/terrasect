# Local runtime verification

Verified 2026-10-05T09:16:37+05:00 on macOS arm64 in PR #76.

Eight available client scenarios passed with **23 executed test cases**. Ten scenarios across five
lanes remain incomplete for the upstream capabilities listed in [SUPPORT.md](SUPPORT.md).
The full matrix exits nonzero for exactly those blockers; release is not ready.

## Commands and checks

```sh
./gradlew spotlessCheck test :fabric:1.20.1:build :fabric:1.21.1:build :neoforge:1.21.1:build :fabric:1.21.11:build :neoforge:1.21.11:build :fabric:26.1.x:build :neoforge:26.1.x:build :fabric:26.2.x:build :neoforge:26.2.x:build --continue
./gradlew -p buildSrc test
./gradlew spotlessCheck minecraftTest --continue
```

Formatting, all nine production builds, 1,150 common unit tests across five versions and four
collector/process checks passed. The process probes cover fatal hangs and immediate parent exit.
A byte-only ZIP comment change invalidated pack preparation and changed the staged jar/report SHA;
the original production output was restored before the final matrix.

## Scenario results

Each ID below maps to `build/minecraft-test/results/<id>.json` and
`build/minecraft-test/logs/<id>.log`. The log includes the exact launcher command; the result embeds
the complete installed pack inventory with SHA-256, requested coordinates, filter and execution list.
Per-lane reruns use `./gradlew :<loader>:<segment>:minecraftTestBuild` / `minecraftTestCompat`.

| Scenario ID | Minecraft | Loader | Java | Result / cases |
|---|---|---|---|---|
| `fabric-1.20.1-build` | 1.20.1 | fabric — | — | incomplete / 0 |
| `fabric-1.20.1-compat` | 1.20.1 | fabric — | — | incomplete / 0 |
| `fabric-1.21.1-build` | 1.21.1 | fabric — | — | incomplete / 0 |
| `fabric-1.21.1-compat` | 1.21.1 | fabric — | — | incomplete / 0 |
| `fabric-1.21.11-build` | 1.21.11 | fabric 0.18.4 | 21 | passed / 2 |
| `fabric-1.21.11-compat` | 1.21.11 | fabric 0.18.4 | 21 | passed / 3 |
| `fabric-26.1.x-build` | 26.1.2 | fabric 0.19.3 | 25 | passed / 2 |
| `fabric-26.1.x-compat` | 26.1.2 | fabric 0.19.3 | 25 | passed / 3 |
| `fabric-26.2.x-build` | 26.2 | fabric 0.19.3 | 25 | passed / 2 |
| `fabric-26.2.x-compat` | 26.2 | fabric 0.19.3 | 25 | passed / 7 |
| `neoforge-1.21.1-build` | 1.21.1 | neoforge — | — | incomplete / 0 |
| `neoforge-1.21.1-compat` | 1.21.1 | neoforge — | — | incomplete / 0 |
| `neoforge-1.21.11-build` | 1.21.11 | neoforge — | — | incomplete / 0 |
| `neoforge-1.21.11-compat` | 1.21.11 | neoforge — | — | incomplete / 0 |
| `neoforge-26.1.x-build` | 26.1.2 | neoforge 26.1.2.112 | 25 | passed / 2 |
| `neoforge-26.1.x-compat` | 26.1.2 | neoforge 26.1.2.112 | 25 | passed / 2 |
| `neoforge-26.2.x-build` | 26.2 | neoforge — | — | incomplete / 0 |
| `neoforge-26.2.x-compat` | 26.2 | neoforge — | — | incomplete / 0 |

## Executed tests

- `fabric-1.21.11-build`: SmokeGameTest, LootConstraintBlockAllGameTest.
- `fabric-1.21.11-compat`: CompatSmokeGameTest, ModdedBiomeConstraintGameTest, CreatePonderCompatGameTest.
- `fabric-26.1.x-build`: SmokeGameTest, LootConstraintBlockAllGameTest.
- `fabric-26.1.x-compat`: CompatSmokeGameTest, ModdedBiomeConstraintGameTest, CreatePonderCompatGameTest.
- `fabric-26.2.x-build`: SmokeGameTest, LootConstraintBlockAllGameTest.
- `fabric-26.2.x-compat`: CompatSmokeGameTest, ModdedBiomeConstraintGameTest, CreatePonderCompatGameTest, BiomesOPlentyCompatGameTest, TerraBlenderCompatGameTest, DistantHorizonsCompatGameTest, C2MECompatGameTest.
- `neoforge-26.1.x-build`: SmokeGameTest, LootConstraintBlockAllGameTest.
- `neoforge-26.1.x-compat`: CompatSmokeGameTest, ModdedBiomeConstraintGameTest.

## Exact production and test artifacts

Native NeoForge scenarios use the NeoForge production jar and the same Minecraft-version Fabric test jars.

| Installed jar | SHA-256 |
|---|---|
| `terrasect-compat-tests-0.2.3+1.21.11-gametest.jar` | `5e68bc6c741b0a747e5be722a3aaebc3ed33cdc81f8951e0eb7d5e7d0bc8dc62` |
| `terrasect-compat-tests-0.2.3+26.1.2-gametest.jar` | `2747634ea20503f7d9d6309a0bec0e9a57497cf4b201deb53bde6049c61ce8f8` |
| `terrasect-compat-tests-0.2.3+26.2-gametest.jar` | `00d0d8c24276de210fe99dd9ee76e94ad281ac77d219f761d96eac3db5c79b99` |
| `terrasect-fabric-0.2.3+1.21.11.jar` | `409c5e08a27bb4a4b6287bf3ef2c6dbf49a8509b4b136ec7c01267e5d1ed8abb` |
| `terrasect-fabric-0.2.3+26.1.2.jar` | `38aaba66c60b7ba437ef941fb9f3ad5a3a53e6fc635332cfd0a10ef9accffaed` |
| `terrasect-fabric-0.2.3+26.2.jar` | `c3b902375211ddb082e7dbf4e4c11f28b84a1f486b6003311ea7a1b1b174047f` |
| `terrasect-neoforge-0.2.3+26.1.2.jar` | `e3cb89c2c3f166d329b2fa87df79457d8ca8fc02bda9685ace87f3b73327e33f` |
| `terrasect-tests-0.2.3+1.21.11-gametest.jar` | `b4bc2f1f92b8138e85ce201f096a7998a3dc6bcbd1abd312b78e43e889a1af95` |
| `terrasect-tests-0.2.3+26.1.2-gametest.jar` | `6725c6769de1262198fedd080f520329b1b49b88f50e2797f60f8e388b677826` |
| `terrasect-tests-0.2.3+26.2-gametest.jar` | `e7a19f38b65f8e48dc1eb2fa6692c755d3cf6bc76e1156ea3aa08be380fc4478` |

## Runtime dependency pins

`fabric-1.21.11`:

- `net.fabricmc.fabric-api:fabric-api:0.141.1+1.21.11`
- `net.fabricmc:fabric-language-kotlin:1.13.8+kotlin.2.3.0`

`fabric-26.1.x`:

- `net.fabricmc.fabric-api:fabric-api:0.154.2+26.1.2`
- `net.fabricmc:fabric-language-kotlin:1.13.8+kotlin.2.3.0`

`fabric-26.2.x`:

- `net.fabricmc.fabric-api:fabric-api:0.152.2+26.2`
- `net.fabricmc:fabric-language-kotlin:1.13.8+kotlin.2.3.0`

`neoforge-26.1.x`:

- `thedarkcolour:kotlinforforge-neoforge:6.1.0`
- `thedarkcolour:kfflang-neoforge:6.1.0`
- `thedarkcolour:kfflib-neoforge:6.1.0`
- `thedarkcolour:kffmod-neoforge:6.1.0`
- `org.sinytra:connector:3.0.0-beta.6+26.1.2`
- `org.sinytra.forgified-fabric-api:forgified-fabric-api:0.155.3+26.1.2+3.5.7`
- `org.sinytra.launchpad:launchpad:1.9.2+26.1.2`
- `net.fabricmc:fabric-language-kotlin:1.13.8+kotlin.2.3.0`

## Third-party pins

Exact Modrinth version IDs resolved by Gradle, with installed filenames from the tested packs.

| Lane | Requested third-party coordinate | Installed jar |
|---|---|---|
| `fabric-1.21.11` | `maven.modrinth:glitchcore:CO7NeLTt` | `glitchcore-CO7NeLTt.jar` |
| `fabric-1.21.11` | `maven.modrinth:biomes-o-plenty:JJKbM72H` | `biomes-o-plenty-JJKbM72H.jar` |
| `fabric-1.21.11` | `maven.modrinth:terrablender:chxo508B` | `terrablender-chxo508B.jar` |
| `fabric-1.21.11` | `maven.modrinth:distanthorizons:oNqCUHFk` | `distanthorizons-oNqCUHFk.jar` |
| `fabric-1.21.11` | `maven.modrinth:create-fly:fn0H9rSj` | `create-fly-fn0H9rSj.jar` |
| `fabric-26.1.x` | `maven.modrinth:glitchcore:WNtSATXw` | `glitchcore-WNtSATXw.jar` |
| `fabric-26.1.x` | `maven.modrinth:biomes-o-plenty:8MVwdPgG` | `biomes-o-plenty-8MVwdPgG.jar` |
| `fabric-26.1.x` | `maven.modrinth:terrablender:NIXt6lha` | `terrablender-NIXt6lha.jar` |
| `fabric-26.1.x` | `maven.modrinth:distanthorizons:c0SAW45X` | `distanthorizons-c0SAW45X.jar` |
| `fabric-26.1.x` | `maven.modrinth:create-fly:wV5IQLKg` | `create-fly-wV5IQLKg.jar` |
| `fabric-26.2.x` | `maven.modrinth:glitchcore:SDUCBYRU` | `glitchcore-SDUCBYRU.jar` |
| `fabric-26.2.x` | `maven.modrinth:biomes-o-plenty:MNZPk6V0` | `biomes-o-plenty-MNZPk6V0.jar` |
| `fabric-26.2.x` | `maven.modrinth:terrablender:KQix25Qc` | `terrablender-KQix25Qc.jar` |
| `fabric-26.2.x` | `maven.modrinth:distanthorizons:gBf0SaV1` | `distanthorizons-gBf0SaV1.jar` |
| `fabric-26.2.x` | `maven.modrinth:create-fly:phlsMPgT` | `create-fly-phlsMPgT.jar` |
| `fabric-26.2.x` | `maven.modrinth:c2me-fabric:XQMx5J57` | `c2me-fabric-XQMx5J57.jar` |
| `neoforge-26.1.x` | `maven.modrinth:glitchcore:mYUbCfgT` | `glitchcore-mYUbCfgT.jar` |
| `neoforge-26.1.x` | `maven.modrinth:biomes-o-plenty:mqkuMsI9` | `biomes-o-plenty-mqkuMsI9.jar` |
| `neoforge-26.1.x` | `maven.modrinth:terrablender:cWLGFrxw` | `terrablender-cWLGFrxw.jar` |
| `neoforge-26.1.x` | `maven.modrinth:distanthorizons:QHxoccsN` | `distanthorizons-QHxoccsN.jar` |

## Limits

Clients use HeadlessMC 2.10.0, LWJGL stubs and dummy assets; DH rendering and NeoForge early-window
rendering are disabled. World generation, LOD database creation and Ponder screen state are tested;
GPU rendering and screenshot correctness are not. CI has been updated to repeat these tasks and
upload evidence, but this record establishes local execution, not a completed GitHub Actions run.
No merge, tag, publication or release was performed.
