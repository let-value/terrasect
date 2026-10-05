# Local runtime verification

Verified 2026-10-05T07:00:52+00:00 on macOS arm64 in PR #76.

All **18 clean/compatibility pack runs** across all nine supported loader/version lanes passed,
with **33 executed test cases**: 23 client cases and ten native server smoke cases.
Server fallback covers five lanes whose client API/Connector bridge is unavailable.

## Commands and checks

```sh
./gradlew spotlessCheck test :fabric:1.20.1:build :fabric:1.21.1:build :neoforge:1.21.1:build :fabric:1.21.11:build :neoforge:1.21.11:build :fabric:26.1.x:build :neoforge:26.1.x:build :fabric:26.2.x:build :neoforge:26.2.x:build --continue --no-daemon --max-workers=3
./gradlew -p buildSrc test
./gradlew minecraftTest --continue --no-daemon --max-workers=3
./gradlew :e2e:1.20.1:runGameTest :e2e:1.21.1:runGameTest --continue
```

Formatting, all nine production builds, 1,150 common unit tests across five versions, six
collector/process checks and both restored legacy CI server GameTests passed. The process probes
cover fatal hangs and immediate launcher exit, including native servers launched via JVM argfiles.
A combined build/runtime invocation exhausted the existing 2 GB Gradle heap; final verification
ran the build and runtime commands separately with bounded concurrency.

A byte-only ZIP comment change was previously verified to invalidate pack preparation and change
the staged jar/report SHA. The original production output was restored before runtime verification.
Production jars contain no `terrasect.gametest` classes; tests are separate installable mods.

## Scenario results

Each scenario maps to `build/minecraft-test/results/<id>.json` and
`build/minecraft-test/logs/<id>.log`. Results embed the installed inventory, SHA-256, requested
coordinates, mode, expected/executed cases and exit status. Logs preserve launcher commands.

| Scenario ID | Minecraft | Loader pin | Java | Mode | Result / cases |
|---|---|---|---|---|---|
| `fabric-1.20.1-build` | 1.20.1 | 0.16.14 | 17 | server | passed / 1 |
| `fabric-1.20.1-compat` | 1.20.1 | 0.16.14 | 17 | server | passed / 1 |
| `fabric-1.21.1-build` | 1.21.1 | 0.18.4 | 21 | server | passed / 1 |
| `fabric-1.21.1-compat` | 1.21.1 | 0.18.4 | 21 | server | passed / 1 |
| `fabric-1.21.11-build` | 1.21.11 | 0.18.4 | 21 | client | passed / 2 |
| `fabric-1.21.11-compat` | 1.21.11 | 0.18.4 | 21 | client | passed / 3 |
| `fabric-26.1.x-build` | 26.1.2 | 0.19.3 | 25 | client | passed / 2 |
| `fabric-26.1.x-compat` | 26.1.2 | 0.19.3 | 25 | client | passed / 3 |
| `fabric-26.2.x-build` | 26.2 | 0.19.3 | 25 | client | passed / 2 |
| `fabric-26.2.x-compat` | 26.2 | 0.19.3 | 25 | client | passed / 7 |
| `neoforge-1.21.1-build` | 1.21.1 | 21.1.234 | 21 | server | passed / 1 |
| `neoforge-1.21.1-compat` | 1.21.1 | 21.1.234 | 21 | server | passed / 1 |
| `neoforge-1.21.11-build` | 1.21.11 | 21.11.36-beta | 21 | server | passed / 1 |
| `neoforge-1.21.11-compat` | 1.21.11 | 21.11.36-beta | 21 | server | passed / 1 |
| `neoforge-26.1.x-build` | 26.1.2 | 26.1.2.112 | 25 | client | passed / 2 |
| `neoforge-26.1.x-compat` | 26.1.2 | 26.1.2.112 | 25 | client | passed / 2 |
| `neoforge-26.2.x-build` | 26.2 | 26.2.0.6-beta | 25 | server | passed / 1 |
| `neoforge-26.2.x-compat` | 26.2 | 26.2.0.6-beta | 25 | server | passed / 1 |

## Executed client tests

- `fabric-1.21.11-build`: SmokeGameTest, LootConstraintBlockAllGameTest.
- `fabric-1.21.11-compat`: CompatSmokeGameTest, ModdedBiomeConstraintGameTest, CreatePonderCompatGameTest.
- `fabric-26.1.x-build`: SmokeGameTest, LootConstraintBlockAllGameTest.
- `fabric-26.1.x-compat`: CompatSmokeGameTest, ModdedBiomeConstraintGameTest, CreatePonderCompatGameTest.
- `fabric-26.2.x-build`: SmokeGameTest, LootConstraintBlockAllGameTest.
- `fabric-26.2.x-compat`: CompatSmokeGameTest, ModdedBiomeConstraintGameTest, CreatePonderCompatGameTest, BiomesOPlentyCompatGameTest, TerraBlenderCompatGameTest, DistantHorizonsCompatGameTest, C2MECompatGameTest.
- `neoforge-26.1.x-build`: SmokeGameTest, LootConstraintBlockAllGameTest.
- `neoforge-26.1.x-compat`: CompatSmokeGameTest, ModdedBiomeConstraintGameTest.

All server-mode packs run `ServerSmokeGameTest`: every constraint lookup is active, `/ts locate`
and `/ts query` work, an actual forced village start exists, and biome sampling admits only desert.
The test stops the server after assertions; startup or `Done` alone does not pass.

## Exact production and test artifacts

Native NeoForge client packs use the native production jar plus the Fabric test mod through
Sinytra; native server packs use a separate NeoForge server test mod without Connector.

| Installed jar | SHA-256 |
|---|---|
| `terrasect-compat-tests-0.2.3+1.21.11-gametest.jar` | `5e68bc6c741b0a747e5be722a3aaebc3ed33cdc81f8951e0eb7d5e7d0bc8dc62` |
| `terrasect-compat-tests-0.2.3+26.1.2-gametest.jar` | `2747634ea20503f7d9d6309a0bec0e9a57497cf4b201deb53bde6049c61ce8f8` |
| `terrasect-compat-tests-0.2.3+26.2-gametest.jar` | `00d0d8c24276de210fe99dd9ee76e94ad281ac77d219f761d96eac3db5c79b99` |
| `terrasect-fabric-0.2.3+1.20.1.jar` | `0ea98d0f72a6ddbee73112d9f914a73c271dc9aedfefd8b738e48fb04b1379ea` |
| `terrasect-fabric-0.2.3+1.21.1.jar` | `2679421e95288a77bb74531ea38f002d088484ec90cc6afa2daa319c5dedae85` |
| `terrasect-fabric-0.2.3+1.21.11.jar` | `409c5e08a27bb4a4b6287bf3ef2c6dbf49a8509b4b136ec7c01267e5d1ed8abb` |
| `terrasect-fabric-0.2.3+26.1.2.jar` | `38aaba66c60b7ba437ef941fb9f3ad5a3a53e6fc635332cfd0a10ef9accffaed` |
| `terrasect-fabric-0.2.3+26.2.jar` | `c3b902375211ddb082e7dbf4e4c11f28b84a1f486b6003311ea7a1b1b174047f` |
| `terrasect-neoforge-0.2.3+1.21.1.jar` | `ac5466cd32c6b212b1edd731f8c2fe9186afcbd55c5664d23f79f187e7838969` |
| `terrasect-neoforge-0.2.3+1.21.11.jar` | `98aa2eceb9b8ecb33cc4acf4d3196d4077c7ee6ed45abc31c6e1dd87de4a6ebb` |
| `terrasect-neoforge-0.2.3+26.1.2.jar` | `e3cb89c2c3f166d329b2fa87df79457d8ca8fc02bda9685ace87f3b73327e33f` |
| `terrasect-neoforge-0.2.3+26.2.jar` | `4ee1f901f51fe6a1a7ccb5e7ef2fe67fc2c7eb5792b985b0e73d392ef9534c3c` |
| `terrasect-server-tests-fabric-0.2.3+1.20.1-gametest.jar` | `fbc31f1261a10e41d61d696cc68b298fdb505fbe792dcded2831c164f1295c8a` |
| `terrasect-server-tests-fabric-0.2.3+1.21.1-gametest.jar` | `03c154ea7be2badf0b2388664ec11aa43ea0cbb5d4bfd78ffcf1388fe389a1c2` |
| `terrasect-server-tests-neoforge-0.2.3+1.21.1-gametest.jar` | `de00bc2b96395700415865faedb396e55c405f285d1109b08ef6901b86395ecc` |
| `terrasect-server-tests-neoforge-0.2.3+1.21.11-gametest.jar` | `1caf28e8445e18538ca1db5918bd3aad017d3ce838f7cfe665b8362f44c5d761` |
| `terrasect-server-tests-neoforge-0.2.3+26.2-gametest.jar` | `da74e7903ff38242828e2558e8783616f511c81b9a0a92f5f84a9a63f57d2ca5` |
| `terrasect-tests-0.2.3+1.21.11-gametest.jar` | `b4bc2f1f92b8138e85ce201f096a7998a3dc6bcbd1abd312b78e43e889a1af95` |
| `terrasect-tests-0.2.3+26.1.2-gametest.jar` | `6725c6769de1262198fedd080f520329b1b49b88f50e2797f60f8e388b677826` |
| `terrasect-tests-0.2.3+26.2-gametest.jar` | `e7a19f38b65f8e48dc1eb2fa6692c755d3cf6bc76e1156ea3aa08be380fc4478` |

## Third-party version pins

The complete installed filenames and dependency hashes are embedded in each result manifest.
These exact Modrinth version IDs were used; runtime library/bridge coordinates are recorded there too.

| Lane | Requested Modrinth versions |
|---|---|
| `fabric-1.20.1` | `glitchcore:25HLOiOl`, `biomes-o-plenty:eZaag2ca`, `terrablender:kmob8AJ4`, `distanthorizons:6UnEfsRQ` |
| `fabric-1.21.1` | `glitchcore:sux8kYHe`, `biomes-o-plenty:fhyqphwv`, `terrablender:XNtIBXyQ`, `distanthorizons:9w34y8ai` |
| `fabric-1.21.11` | `glitchcore:CO7NeLTt`, `biomes-o-plenty:JJKbM72H`, `terrablender:chxo508B`, `distanthorizons:oNqCUHFk`, `create-fly:fn0H9rSj` |
| `fabric-26.1.x` | `glitchcore:WNtSATXw`, `biomes-o-plenty:8MVwdPgG`, `terrablender:NIXt6lha`, `distanthorizons:c0SAW45X`, `create-fly:wV5IQLKg` |
| `fabric-26.2.x` | `glitchcore:SDUCBYRU`, `biomes-o-plenty:MNZPk6V0`, `terrablender:KQix25Qc`, `distanthorizons:gBf0SaV1`, `create-fly:phlsMPgT`, `c2me-fabric:XQMx5J57` |
| `neoforge-1.21.1` | `glitchcore:S2TfWrZR`, `biomes-o-plenty:BtZKRp69`, `terrablender:6e8GCrLb`, `distanthorizons:9w34y8ai` |
| `neoforge-1.21.11` | `glitchcore:6dbbrOrO`, `biomes-o-plenty:cCnnnC72`, `terrablender:xjrV1Uvq`, `distanthorizons:oNqCUHFk` |
| `neoforge-26.1.x` | `glitchcore:mYUbCfgT`, `biomes-o-plenty:mqkuMsI9`, `terrablender:cWLGFrxw`, `distanthorizons:QHxoccsN` |
| `neoforge-26.2.x` | `glitchcore:POAebwFo`, `biomes-o-plenty:Kpuzuokl`, `terrablender:TzDe72Nr`, `distanthorizons:gBf0SaV1` |

## CI and coverage limits

Automatic PR/main CI runs builds, unit/style checks and five lightweight Fabric smoke jobs.
The heavy production/modpack matrix is available only through the manual **Modpack runtime tests**
workflow (`runtime-tests.yml`), with build/compat/both selection. This is a local execution record,
not a completed GitHub Actions run of that new workflow.

Client packs use pinned HeadlessMC 2.10.0, LWJGL stubs and dummy assets; DH and NeoForge early-window
rendering are disabled. Generation, LOD database creation and Ponder screen state are tested.
GPU rendering, screenshot correctness, unavailable client APIs and arbitrary mod combinations
are outside this smoke coverage. See [SUPPORT.md](SUPPORT.md) for the mode of every lane.
No merge, tag, publication or release was performed.
