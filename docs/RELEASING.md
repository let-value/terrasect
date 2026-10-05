# Releasing

Release readiness requires passing build checks and clean/third-party **runtime** tests for all
nine production variants. Use native server smoke coverage where the client API/bridge is unavailable; see
[`runtime-tests/SUPPORT.md`](../runtime-tests/SUPPORT.md).

Five workflows in `.github/workflows/`:

## `ci.yml` — PR verification

The build job runs formatting, unit tests, and all nine production builds. Five lightweight smoke
jobs restore the previous CI behavior: Fabric 1.20.1/1.21.1 run the server GameTest; newer Fabric
versions run only `SmokeGameTest,LootConstraintBlockAllGameTest` in the development client.
Third-party dependencies and heavy client scenarios are excluded from automatic PR/main checks.

## `runtime-tests.yml` — on-demand production modpack verification

Manual `workflow_dispatch` only. Select clean (`build`), third-party (`compat`) or `both` packs.
The nine-lane matrix invokes Gradle version-qualified tasks and uploads evidence on success/failure.
Run the same checks locally:

```sh
./gradlew :fabric:26.2.x:minecraftTestBuild :fabric:26.2.x:minecraftTestCompat --continue
./gradlew :neoforge:26.2.x:minecraftTestBuild :neoforge:26.2.x:minecraftTestCompat --continue
./gradlew minecraftTest --continue
```

Leave `TERRASECT_SKIP_COMPAT` unset for runtime tasks. Ordinary builds and checks do not configure
third-party compatibility projects. `minecraftTestSupport` explains which lanes use server fallback.
A successful fast PR check alone is not the complete release modpack evidence.

Gradle builds the final production jar and a separate installable GameTest mod, resolves dependencies,
and starts a real Minecraft client through pinned HeadlessMC. Fabric uses the Fabric client GameTest
API. NeoForge uses native NeoForge Terrasect plus the Fabric test mod through Sinytra Connector,
Forgified Fabric API, Launchpad, and Kotlin runtimes. Lanes without the client API/bridge use a separate native-loader server test mod and real dedicated
servers. Those smoke tests assert constraint activation, biome admission, commands and forced
structure generation, then shut down. A generic startup marker never counts as a test.
Mapped versions remap the test jar; unobfuscated 26.x versions package it directly.

`minecraftTestBuild` runs the clean Smoke and LootConstraintBlockAll client tests or ServerSmokeGameTest
on server fallback lanes. `minecraftTestCompat`
runs the pinned third-party packs and assertions listed in SUPPORT.md. Gradle rejects missing/zero
executions, assertion failures, crashes, and timeouts, even if earlier tests completed. Launches
always start with fresh worlds; launcher/dependency downloads and prepared packs can be cached.

Runtime evidence lives in `build/minecraft-test/`:

- `results/<loader>-<version>-<build|compat>.json`: outcome, executed tests, exact jars and SHA-256,
  dependencies, graphics limitation, and log path.
- `logs/<loader>-<version>-<build|compat>.log`: command and captured client output.
- `modpacks/<loader>-<version>-<build|compat>/runtime-test-manifest.json`: installed pack inventory.
- `runtime/<loader>-<version>-<build|compat>/`: client logs and crash reports.

CI uploads these diagnostics on success and failure, excluding authentication data. Compatibility
profiles live in `runtime-tests/modpacks/compat`; Gradle coordinates in `stonecutter.properties.toml`
replace Ferium's selected versions with exact pins. Tool versions/checksums live in
`buildSrc/src/main/resources/minecraft-test.properties`.

HeadlessMC uses LWJGL stubs and dummy assets. Distant Horizons rendering is disabled; tests cover
world generation, LOD data and Ponder screen state. GPU rendering and screenshot correctness require
a separate graphical client run and are not established by this pipeline.

## `release.yml` — build artifacts
Triggered by pushing a `v*` tag or manually via workflow dispatch. Builds all loader jars (named `terrasect-<loader>-<modversion>+<mcversion>.jar`), uploads them as a `terrasect-jars` workflow artifact, and attaches them to a draft GitHub release (`v<mod.version>` if not tag-triggered). Publish the draft release manually after review.

## `publish.yml` — deploy to Modrinth / CurseForge

Manual only (workflow dispatch). Inputs:

- `tag` — the release tag holding the jars (from `release.yml`).
- `target` — `both`, `modrinth`, or `curseforge`.
- `version-type` — `release`, `beta`, or `alpha`.

Checks out the selected release tag and publishes each version+loader jar as its own platform version via mc-publish, with loader-appropriate dependencies (fabric-api + fabric-language-kotlin on Fabric, kotlin-for-forge on NeoForge).

## `pages.yml` — deploy user-facing docs

Triggered by pushing a `v*` tag or manually via workflow dispatch. Two jobs:

- `deploy`: runs `pages/build.sh` (renders `pages/content/*.md` with pandoc into one stitched
  `pages/dist/index.html`) and publishes `pages/dist` to GitHub Pages via `actions/deploy-pages`.
  Requires the repo's **Settings → Pages → Source** set to "GitHub Actions" once, before the first
  run.
- `sync-modrinth`: best-effort (`continue-on-error`), PATCHes `pages/content/summary.txt` and
  `pages/content/description.md` straight to the Modrinth project page. No-ops if the Modrinth
  secrets aren't set. See [`pages/README.md`](../pages/README.md) for why the content lives there and
  the CurseForge limitation (no public API for editing a project description — that listing has to
  be updated by hand from the same source file).

### Required repository configuration
Secrets:

- `MODRINTH_TOKEN` — Modrinth PAT. `publish.yml` needs **Create versions** (plus **Read
  versions**/**Read projects**, which Modrinth bundles in by default); `pages.yml`'s `sync-modrinth`
  job additionally needs **Write projects** to PATCH the project description. One token with all of
  these scopes covers both workflows.
- `CURSEFORGE_TOKEN` — CurseForge API token.
- `MODRINTH_PROJECT_ID` — Modrinth project id or slug.
- `CURSEFORGE_PROJECT_ID` — CurseForge numeric project id.

Repo settings:

- **Settings → Pages → Source: GitHub Actions** — one-time setup required before `pages.yml` can
  deploy.

Publishing uses exact Minecraft labels `1.20.1`, `1.21.1`, `1.21.11`, `26.1.2`, and `26.2`;
`26.1.x` and `26.2.x` are internal Gradle project segments. The release workflow currently builds
jars without enforcing runtime manifests; verify the final version and jar hashes before publishing.

### Cutting a release

1. Bump `mod.version` in `stonecutter.properties.toml`.
2. Require fresh complete passing evidence for every lane in SUPPORT.md for that final version,
   compare the release jars with the runtime manifest hashes, and review CI artifacts.
3. Tag and push: `git tag v<version> && git push origin v<version>`. This also triggers `pages.yml`.
4. Review the draft GitHub release `release.yml` creates; publish it.
5. Run `publish.yml` with that tag (target `both`).
