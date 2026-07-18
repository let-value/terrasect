# Releasing

Three workflows in `.github/workflows/`:

## `ci.yml` — PR verification
Runs on every PR (and pushes to `main`):
- `build` job: `spotlessCheck`, all unit tests, and every loader jar (`:<version>-fabric:build` / `:<version>-neoforge:build` across the full matrix).
- `smoke` job (per-version matrix): the portable smoke gametests only. Old-paradigm versions (1.20.1, 1.21.1) run the headless server smoke via `runGameTest`; client-capable versions run `runClientGameTest -Ptest=SmokeGameTest,LootConstraintBlockAllGameTest` under Xvfb. The heavy client gametests (terrain digests, constraint suites, dimension/archetype probes) never run on GitHub — they stay local (`./gradlew :e2e:26.2.x:runClientGameTest` with no `-Ptest` filter).

## `release.yml` — build artifacts
Triggered by pushing a `v*` tag or manually via workflow dispatch. Builds all loader jars (named `terrasect-<loader>-<modversion>+<mcversion>.jar`), uploads them as a `terrasect-jars` workflow artifact, and attaches them to a draft GitHub release (`v<mod.version>` if not tag-triggered). Publish the draft release manually after review.

## `publish.yml` — deploy to Modrinth / CurseForge
Manual only (workflow dispatch). Inputs:
- `tag` — the release tag holding the jars (from `release.yml`).
- `target` — `both`, `modrinth`, or `curseforge`.
- `version-type` — `release`, `beta`, or `alpha`.

Publishes each version+loader jar as its own platform version via mc-publish, with loader-appropriate dependencies (fabric-api + fabric-language-kotlin on Fabric, kotlin-for-forge on NeoForge).

### Required repository configuration
Secrets:
- `MODRINTH_TOKEN` — Modrinth PAT with version-create scope.
- `CURSEFORGE_TOKEN` — CurseForge API token.
- `MODRINTH_PROJECT_ID` — Modrinth project id or slug.
- `CURSEFORGE_PROJECT_ID` — CurseForge numeric project id.

### Cutting a release
1. Bump `mod.version` in `stonecutter.properties.toml`.
2. Tag and push: `git tag v<version> && git push origin v<version>`.
3. Review the draft GitHub release `release.yml` creates; publish it.
4. Run `publish.yml` with that tag (target `both`).
