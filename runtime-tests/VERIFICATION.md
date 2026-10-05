# Local runtime verification

Verified 2026-10-05T09:50:50+00:00 on macOS arm64 in PR #76.

Release candidate **0.2.4** passed all **18 clean/compatibility pack runs** across all nine supported loader/version lanes,
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

## Upgrade isolation

DH 3.3.3 passed the three changed compatibility packs (11 cases) while C2ME stayed at alpha.0.13.
DH 26.2 requires Fabric Loader >=0.19.5; that lane was upgraded from 0.19.3 before testing.
C2ME alpha.0.56 then passed a separate Fabric 26.2 compatibility run (seven cases), including
concurrent generation and DH database creation. Both upgrades remained pinned for the final 0.2.4 matrix.
The final run below was performed after the version bump; all recorded production/test jars are 0.2.4.
Both C2ME versions emitted unsafe-terrain-read diagnostics for vanilla sculk worldgen; the seven
assertions and shutdown still passed. These smoke checks do not establish terrain correctness for
every upstream generation feature.

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
| `fabric-26.2.x-build` | 26.2 | 0.19.5 | 25 | client | passed / 2 |
| `fabric-26.2.x-compat` | 26.2 | 0.19.5 | 25 | client | passed / 7 |
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
| `terrasect-compat-tests-0.2.4+1.21.11-gametest.jar` | `5089f0871d3186349e03e5a7ed8aa86664bc62a364755bb56f22a46a9287cdb5` |
| `terrasect-compat-tests-0.2.4+26.1.2-gametest.jar` | `8f63326ab77ffdcae2f1b54c52a8d529c95b2d3e7e84d64d5925e0b36ebdef39` |
| `terrasect-compat-tests-0.2.4+26.2-gametest.jar` | `2deb6058a78e9ca2b8ca1f9b92c24ef6be9bd4bb3838085cd89c47fbad44bb36` |
| `terrasect-fabric-0.2.4+1.20.1.jar` | `a438865e73702e3bea2476ae37d30c989ddb2bb24b6984773c69f70553b385a9` |
| `terrasect-fabric-0.2.4+1.21.1.jar` | `2a8602ad9fb3103888e95bb12e5ec29e93c2137607f364b6e3a816a5733e1677` |
| `terrasect-fabric-0.2.4+1.21.11.jar` | `cf49c753ea526d9fc1d8eaa5568faf8ba831d5a360dd35c779083067e7bf979e` |
| `terrasect-fabric-0.2.4+26.1.2.jar` | `5168833d7fef487f9fd7d20eba0041655e54e48c1e31381ca8687a49860e4113` |
| `terrasect-fabric-0.2.4+26.2.jar` | `592be8981a9ae3ff053120c85bca7dd85cafc5a40c29680b23b8611f45ea3edd` |
| `terrasect-neoforge-0.2.4+1.21.1.jar` | `c0a929a525970d1e744b031958d178f5ca9ef6c2b2c353c8bb45cbfd17392750` |
| `terrasect-neoforge-0.2.4+1.21.11.jar` | `80ddf54e214757c519c96ba0631afb2fb66c19b75c687c92e7ab653942e01be9` |
| `terrasect-neoforge-0.2.4+26.1.2.jar` | `77c834eda78638a39f1492465b27ca136fc6ea762b9e7a225d573bbb54669b09` |
| `terrasect-neoforge-0.2.4+26.2.jar` | `03054855c9f510b7c635e6bc7a9b0243c68f286b679d1162c4907f19f026c4c6` |
| `terrasect-server-tests-fabric-0.2.4+1.20.1-gametest.jar` | `86a9b3593128c7f8ad4e694a0d0f0f2a30d2eed1cacb9ef9d92193c30e9705af` |
| `terrasect-server-tests-fabric-0.2.4+1.21.1-gametest.jar` | `ed2e7a975937630ac3d386c78a2c1f59e1f171e9f5e7a8dcce41a175b5ab0fa9` |
| `terrasect-server-tests-neoforge-0.2.4+1.21.1-gametest.jar` | `46a7e9cf797e39fb1317d3187befa6b4d76f1e69978ddae05b1263d8f1459613` |
| `terrasect-server-tests-neoforge-0.2.4+1.21.11-gametest.jar` | `8aca4c0a13b0e8d155b98b9a3aad5bff001fc9d077e35560fee7abfa2e1b55c1` |
| `terrasect-server-tests-neoforge-0.2.4+26.2-gametest.jar` | `eba138a5d7389f033a29c29c6a0e67c11f86f1bfcd5d0a98e44d9d34a05404e1` |
| `terrasect-tests-0.2.4+1.21.11-gametest.jar` | `cf439b9ab84cb3c2cbcae220fdcc85d26824ddf70b6add910e2b6f5a10ee394c` |
| `terrasect-tests-0.2.4+26.1.2-gametest.jar` | `c61a68f44fe30dc530540db50a7832fb6066cb0fd069d3dcdf3789034d1e7303` |
| `terrasect-tests-0.2.4+26.2-gametest.jar` | `2569461fb213bead8561c4648ca0089d5b3f4ba08510cc784befb1d2443ef726` |

## Third-party version pins

The complete installed filenames and dependency hashes are embedded in each result manifest.
These exact Modrinth version IDs were used; runtime library/bridge coordinates are recorded there too.

| Lane | Requested Modrinth versions |
|---|---|
| `fabric-1.20.1` | `glitchcore:25HLOiOl`, `biomes-o-plenty:eZaag2ca`, `terrablender:kmob8AJ4`, `distanthorizons:6UnEfsRQ` |
| `fabric-1.21.1` | `glitchcore:sux8kYHe`, `biomes-o-plenty:fhyqphwv`, `terrablender:XNtIBXyQ`, `distanthorizons:9w34y8ai` |
| `fabric-1.21.11` | `glitchcore:CO7NeLTt`, `biomes-o-plenty:JJKbM72H`, `terrablender:chxo508B`, `distanthorizons:oNqCUHFk`, `create-fly:fn0H9rSj` |
| `fabric-26.1.x` | `glitchcore:WNtSATXw`, `biomes-o-plenty:8MVwdPgG`, `terrablender:NIXt6lha`, `distanthorizons:QHxoccsN`, `create-fly:wV5IQLKg` |
| `fabric-26.2.x` | `glitchcore:SDUCBYRU`, `biomes-o-plenty:MNZPk6V0`, `terrablender:KQix25Qc`, `distanthorizons:aAow4a9f`, `create-fly:phlsMPgT`, `c2me-fabric:JRbWYiJa` |
| `neoforge-1.21.1` | `glitchcore:S2TfWrZR`, `biomes-o-plenty:BtZKRp69`, `terrablender:6e8GCrLb`, `distanthorizons:9w34y8ai` |
| `neoforge-1.21.11` | `glitchcore:6dbbrOrO`, `biomes-o-plenty:cCnnnC72`, `terrablender:xjrV1Uvq`, `distanthorizons:oNqCUHFk` |
| `neoforge-26.1.x` | `glitchcore:mYUbCfgT`, `biomes-o-plenty:mqkuMsI9`, `terrablender:cWLGFrxw`, `distanthorizons:QHxoccsN` |
| `neoforge-26.2.x` | `glitchcore:POAebwFo`, `biomes-o-plenty:Kpuzuokl`, `terrablender:TzDe72Nr`, `distanthorizons:aAow4a9f` |

## CI and coverage limits

Automatic PR/main CI runs builds, unit/style checks and five lightweight Fabric smoke jobs.
The heavy production/modpack matrix is available only through the manual **Modpack runtime tests**
workflow (`runtime-tests.yml`), with build/compat/both selection. This is a local execution record,
not a completed GitHub Actions run of that new workflow.

Client packs use pinned HeadlessMC 2.10.0, LWJGL stubs and dummy assets; DH and NeoForge early-window
rendering are disabled. Generation, LOD database creation and Ponder screen state are tested.
GPU rendering, screenshot correctness, unavailable client APIs and arbitrary mod combinations
are outside this smoke coverage. See [SUPPORT.md](SUPPORT.md) for the mode of every lane.
Release staging was dry-run with the actual workflow step: exactly nine final production jars,
whose SHA-256 matches both successful clean and compatibility runtime manifests. Publish labels
and generated Minecraft requirements match all five exact tested targets. The release workflow
does not yet enforce runtime/hash evidence automatically; compare release bytes before publishing.
No merge, tag, publication or release was performed.
