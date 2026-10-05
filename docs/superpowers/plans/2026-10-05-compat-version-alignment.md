# Compatibility Version Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make advertised Minecraft versions, modpack dependencies and published production jars agree with the versions verified in real Minecraft.

**Architecture:** Keep the existing five exact Minecraft targets and nine loader lanes. Correct release labels and overly broad Minecraft requirements, then update selected matching compatibility pins and rerun the existing packaged runtime tasks.

**Tech Stack:** Stonecutter, Gradle Kotlin DSL, Fabric/NeoForge mod metadata, GitHub Actions and Modrinth version metadata.

**Spec:** `runtime-tests/SUPPORT.md` and the audit findings below, implementing the user request on 2026-10-05.

## Global Constraints

- Exact targets: `1.20.1`, `1.21.1`, `1.21.11`, `26.1.2`, `26.2`; Fabric covers all five and NeoForge the latter four.
- `26.2` remains the primary development version. Keep internal Stonecutter project names `26.1.x` / `26.2.x`.
- Keep fast automatic PR CI and manual heavy modpack checks. Preserve native server fallback where client APIs/bridges are unavailable.
- Use exact Modrinth version IDs; do not resolve moving latest versions during tests or publishing.
- Matching published metadata is a prerequisite, not a substitute for passing runtime tests. Do not publish test mods or unverified release bytes.

## Review Focus

- `26.1` vs `26.1.2`: upload patterns and platform game-version labels must use the built concrete version.
- Unsupported patches: mod metadata must not advertise untested `26.1` or `26.1.1` compatibility.
- Build names vs compatibility: Create Fly’s RC-labelled build explicitly supports stable `26.2`.
- Available vs executed coverage: C2ME pins exist for three Fabric lanes, but current runtime packs install/test it only on `26.2`.
- Rebuilt artifacts: version bumps and new pins invalidate previous jar hashes and need fresh evidence before publication.

## Audit findings

Checked 2026-10-05T07:24:08+00:00 against publisher Modrinth API metadata, existing jar metadata and repository configuration.

**All 42 configured dependency pins (39 distinct upstream versions) match their assigned Minecraft version and loader.** No Minecraft target migration is needed to correct the current compatibility packs.

| Mod | Published support relevant to our targets | Current coverage / decision |
|---|---|---|
| GlitchCore | All five target versions on Fabric; all four on NeoForge | Keep current matching pins |
| Biomes O’ Plenty | All five / four; modern 26.1 lane explicitly `26.1.2` | Keep pins; publish as `26.1.2` |
| TerraBlender | All five / four; pinned modern artifact explicitly `26.1.2` | Keep pins; broad internal predicates are not extra tested support |
| Distant Horizons | All five / four; 26.1.2 release also declares 26.1 and 26.1.1 | Joint pack still targets `26.1.2`; update old modern DH pins separately |
| Create Fly | Fabric `1.21.11`, `26.1.2`, `26.2` | Keep pins; `phlsMPgT` lists both `26.2-rc-2` and stable `26.2`, and jar requires `>=26.2- <26.3-` |
| C2ME Fabric | Upstream artifacts exist for all five target versions | Keep runtime coverage latest-only; newer same-target alpha is a separate tested upgrade |

Publisher sources: [BOP 26.1.2](https://modrinth.com/mod/biomes-o-plenty/version/8MVwdPgG), [TerraBlender 26.1.2](https://modrinth.com/mod/terrablender/version/NIXt6lha), [Create Fly 26.1.2](https://modrinth.com/mod/create-fly/version/wV5IQLKg), [Create Fly 26.2](https://modrinth.com/mod/create-fly/version/phlsMPgT), [DH version API](https://api.modrinth.com/v2/project/distanthorizons/version), [C2ME version API](https://api.modrinth.com/v2/project/c2me-fabric/version).

Keep `26.2` primary for this change. Several core mods now have 26.3 builds, but Create Fly has no published 26.3 build in the queried metadata; adding that target is separate feature work.

## Proposed dependency updates

| Lane | Current pin / build | Candidate pin / build |
|---|---|---|
| Fabric 26.1.2 | `c0SAW45X` / DH 3.2.0-b | `QHxoccsN` / DH 3.3.3; already used by our NeoForge 26.1.2 pack |
| Fabric + NeoForge 26.2 | `gBf0SaV1` / DH 3.2.0-b | `aAow4a9f` / DH 3.3.3 |
| Fabric 26.2, separate upgrade | `XQMx5J57` / C2ME alpha.0.13 | `JRbWYiJa` / C2ME alpha.0.56 |

DH 3.3.3 candidates were published 2026-09-29; the C2ME candidate was published 2026-10-03. These candidates declare the correct targets but have not been run in our packs. Retain the passing old pin if an upgrade fails; do not change Minecraft targets to hide a failure.

## Task 1: Align public Minecraft versions with built targets

**Files:** `.github/workflows/publish.yml`, `fabric/src/main/resources/fabric.mod.json`, `stonecutter.properties.toml`, `docs/MULTIVERSION.md`, `docs/RELEASING.md`, `runtime-tests/SUPPORT.md`.

**Interfaces:** Consumes the concrete Minecraft versions in `settings.gradle.kts`; produces production metadata and upload selectors for the same five concrete versions. No new public API.

- [x] Verify the current mismatch: the two publish matrix entries select `26.1`, while production filenames end in `+26.1.2.jar`. Verify Fabric’s `~${minecraft_version}` and NeoForge’s `[26.1,26.2)` admit versions beyond the exact tested target.
- [x] Change both publish matrix `mc: "26.1"` entries to `mc: "26.1.2"`; use that value consistently in download/upload patterns, displayed version names and game-version labels.
- [x] Make Fabric’s Minecraft dependency `${minecraft_version}` exact; set modern NeoForge Minecraft ranges to `[26.1.2]` and `[26.2]`. Keep loader and Kotlin dependency floors unless an upstream requirement demonstrates a needed change.
- [x] Update public docs to show concrete Minecraft `26.1.2`; describe `26.1.x` only as an internal project segment. Correct stale Create Fly RC/support wording and C2ME coverage wording.
- [x] Build all nine production jars with the established commands in `runtime-tests/VERIFICATION.md`. Inspect generated Fabric/NeoForge metadata and dry-run publish filename matching: exactly one production jar per lane, all nine selectors succeed, and both modern patch selectors use `26.1.2`. No upload or release creation in this check.
- [x] Commit the metadata/publication alignment as one change.

## Task 2: Upgrade matching compatibility builds without changing Minecraft targets

**Files:** `stonecutter.properties.toml`, `runtime-tests/SUPPORT.md`, `runtime-tests/VERIFICATION.md`.

**Interfaces:** Consumes Task 1’s exact targets and existing `minecraftTestBuild` / `minecraftTestCompat` tasks; produces fixed dependency pins and fresh installed-jar manifests.

- [x] Inspect candidate DH/C2ME jar requirements for Minecraft, Java and loader floors before changing pins; confirm the existing loader/toolchain pins satisfy them.
- [x] Change Fabric 26.1.2 DH to `QHxoccsN` and both 26.2 DH pins to `aAow4a9f`.
- [x] Run `./gradlew :fabric:26.1.x:minecraftTestCompat :fabric:26.2.x:minecraftTestCompat :neoforge:26.2.x:minecraftTestCompat --continue --no-daemon --max-workers=3`. Require all expected assertions and successful shutdown; inspect logs for failures and exact installed coordinates.
- [x] Commit the DH upgrade only after those packs pass.
- [x] Separately change Fabric 26.2 C2ME to `JRbWYiJa`, then run `./gradlew :fabric:26.2.x:minecraftTestCompat --no-daemon`. Require all seven cases, including concurrent C2ME generation and DH database creation. Commit only if it passes; otherwise retain `XQMx5J57` and record the actual incompatibility.
- [x] After the final mod version bump, run build/style/unit checks and `./gradlew minecraftTest --continue --no-daemon --max-workers=3` on the final release commit. Require 18 passed runs / 33 cases and refreshed production/test hashes.
- [x] Update `runtime-tests/VERIFICATION.md`; ensure the release path stages only the nine production jars and matches their bytes against the successful runtime manifests before publishing. Implement the release gate separately from this version audit if it is not already enforced.

## Pins checked before the upgrades

| Loader / project segment | Mod | Version ID | Upstream build | Declared Minecraft versions |
|---|---|---|---|---|
| fabric / 1.20.1 | glitchcore | `25HLOiOl` | 0.0.1.1 | 1.20.1 |
| fabric / 1.20.1 | biomesoplenty | `eZaag2ca` | 19.0.0.96 | 1.20.1 |
| fabric / 1.20.1 | terrablender | `kmob8AJ4` | 3.0.1.11 | 1.20.1 |
| fabric / 1.20.1 | distanthorizons | `6UnEfsRQ` | 3.3.3-1.20.1 | 1.20.1 |
| fabric / 1.21.1 | glitchcore | `sux8kYHe` | 2.1.0.2 | 1.21.1 |
| fabric / 1.21.1 | biomesoplenty | `fhyqphwv` | 21.1.0.14 | 1.21.1 |
| fabric / 1.21.1 | terrablender | `XNtIBXyQ` | 4.1.0.8 | 1.21.1 |
| fabric / 1.21.1 | distanthorizons | `9w34y8ai` | 3.3.3-1.21.1 | 1.21.1 |
| neoforge / 1.21.1 | glitchcore | `S2TfWrZR` | 2.1.0.2 | 1.21.1 |
| neoforge / 1.21.1 | biomesoplenty | `BtZKRp69` | 21.1.0.14 | 1.21.1 |
| neoforge / 1.21.1 | terrablender | `6e8GCrLb` | 4.1.0.8 | 1.21.1 |
| neoforge / 1.21.1 | distanthorizons | `9w34y8ai` | 3.3.3-1.21.1 | 1.21.1 |
| fabric / 1.21.11 | glitchcore | `CO7NeLTt` | 21.11.0.4 | 1.21.11 |
| fabric / 1.21.11 | biomesoplenty | `JJKbM72H` | 21.11.0.32 | 1.21.11 |
| fabric / 1.21.11 | terrablender | `chxo508B` | 21.11.0.0 | 1.21.11 |
| fabric / 1.21.11 | distanthorizons | `oNqCUHFk` | 3.3.3-1.21.11 | 1.21.11 |
| fabric / 1.21.11 | create | `fn0H9rSj` | 1.21.11-6.0.9-5 | 1.21.11 |
| fabric / 1.21.11 | c2me | `OOPL1nCG` | 0.4.0-alpha.0.19+1.21.11 | 1.21.11 |
| neoforge / 1.21.11 | glitchcore | `6dbbrOrO` | 21.11.0.4 | 1.21.11 |
| neoforge / 1.21.11 | biomesoplenty | `cCnnnC72` | 21.11.0.32 | 1.21.11 |
| neoforge / 1.21.11 | terrablender | `xjrV1Uvq` | 21.11.0.0 | 1.21.11 |
| neoforge / 1.21.11 | distanthorizons | `oNqCUHFk` | 3.3.3-1.21.11 | 1.21.11 |
| fabric / 26.1.x | glitchcore | `WNtSATXw` | 26.1.2.0.2 | 26.1.2 |
| fabric / 26.1.x | biomesoplenty | `8MVwdPgG` | 26.1.2.0.22 | 26.1.2 |
| fabric / 26.1.x | terrablender | `NIXt6lha` | 26.1.2.0.3 | 26.1.2 |
| fabric / 26.1.x | distanthorizons | `c0SAW45X` | 3.2.0-b-26.1.2 | 26.1, 26.1.1, 26.1.2 |
| fabric / 26.1.x | create | `wV5IQLKg` | 26.1.2-6.0.9-4 | 26.1.2 |
| fabric / 26.1.x | c2me | `h6qi8QiJ` | 0.4.0-alpha.0.31+26.1.2 | 26.1.2 |
| neoforge / 26.1.x | glitchcore | `mYUbCfgT` | 26.1.2.0.2 | 26.1.2 |
| neoforge / 26.1.x | biomesoplenty | `mqkuMsI9` | 26.1.2.0.22 | 26.1.2 |
| neoforge / 26.1.x | terrablender | `cWLGFrxw` | 26.1.2.0.3 | 26.1.2 |
| neoforge / 26.1.x | distanthorizons | `QHxoccsN` | 3.3.3-26.1.2 | 26.1, 26.1.1, 26.1.2 |
| fabric / 26.2.x | glitchcore | `SDUCBYRU` | 26.2.0.0.0 | 26.2 |
| fabric / 26.2.x | biomesoplenty | `MNZPk6V0` | 26.2.0.0.28 | 26.2 |
| fabric / 26.2.x | terrablender | `KQix25Qc` | 26.2.0.0.2 | 26.2 |
| fabric / 26.2.x | distanthorizons | `gBf0SaV1` | 3.2.0-b-26.2 | 26.2 |
| fabric / 26.2.x | create | `phlsMPgT` | 26.2-rc-2-6.0.9-1 | 26.2-rc-2, 26.2 |
| fabric / 26.2.x | c2me | `XQMx5J57` | 0.4.2-alpha.0.13+26.2 | 26.2 |
| neoforge / 26.2.x | glitchcore | `POAebwFo` | 26.2.0.0.0 | 26.2 |
| neoforge / 26.2.x | biomesoplenty | `Kpuzuokl` | 26.2.0.0.28 | 26.2 |
| neoforge / 26.2.x | terrablender | `TzDe72Nr` | 26.2.0.0.2 | 26.2 |
| neoforge / 26.2.x | distanthorizons | `gBf0SaV1` | 3.2.0-b-26.2 | 26.2 |

The `compat_c2me` entries for Fabric 1.21.11 and 26.1.2 are configured but not installed by the current runtime packs; this audit does not claim runtime C2ME coverage there.

## Execution record

Completed in the existing PR #76 worktree. Final mod version is `0.2.4`; Minecraft targets remain unchanged.
DH 3.3.3 passed three changed packs / eleven cases before C2ME was updated.
C2ME alpha.0.56 then passed a separate seven-case Fabric 26.2 run.
The DH 26.2 jar requires Fabric Loader 0.19.5, so that lane and its production dependency floor were raised.
The final 0.2.4 matrix passed eighteen scenarios / thirty-three cases; nine staged production jar hashes match both runtime scenarios.
Publishing checks out the supplied tag; manual release tags target the built commit, and staging selects only exact production filenames.
Automatic release runtime/hash enforcement remains a separate change; this PR records local verification and documents the comparison requirement.
See `runtime-tests/VERIFICATION.md` for final artifact hashes and coverage limits.
