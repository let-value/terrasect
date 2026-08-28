# Releasing

Terrasect releases flow through four workflows in `.github/workflows/`:

| Workflow | Trigger | Purpose |
| --- | --- | --- |
| `ci.yml` | PR + push to `main` | Unit tests, spotless, loader builds, smoke gametests. Never publishes. |
| `runtime-tests.yml` | push to `main` | Boot-artifact smoke matrix across all 9 lanes via HeadlessMC (see [Local runtime-test commands](#local-runtime-test-commands)). |
| `release.yml` | `v*` tag / dispatch | Build all loader jars, stage them on a draft GitHub release. |
| `publish.yml` | **manual dispatch only** | The release gate — preflight → publish → post-publish verification. Only job that ships to Modrinth / CurseForge and holds registry secrets. |

## `publish.yml` — the release gate (manual)

This is the only job that deploys Terrasect to Modrinth / CurseForge, and it carries the registry
credentials. It is a three-stage pipeline; **publish cannot run if preflight fails**, and each
post-publish verification stage runs only after (and only if) the corresponding registry shipped.

### Inputs (`workflow_dispatch`)

- `tag` — **required**. The release tag holding the built jars (from `release.yml`, e.g. `v1.0.0`).
  Every stage checks out this exact tag via `actions/checkout@v4` with `ref: ${{ inputs.tag }}`, so
  the gate runs against the same source that produced the release jars — never default `HEAD`.
- `target` — `both`, `modrinth`, or `curseforge`. Selects which registries are published and which
  post-publish verification stages run.
- `version-type` — `release`, `beta`, or `alpha`.

### Stage 1 — `preflight` (required, runs first)

Runs before anything is published. If any step fails, the workflow stops and **nothing is
published** (`publish` has `needs: preflight`, so a failed/skipped preflight skips the publish
matrix entirely).

1. **Check out the exact tag** (`ref: ${{ inputs.tag }}`) and read `mod.version` from
   `stonecutter.properties.toml` at that commit.
2. **Download the release jars** from the tag with `gh release download --pattern 'terrasect-*.jar'`.
3. **Assert expected, unique release jars** — decode each `terrasect-<loader>-<version>+<mc>.jar`
   into `(loader, version, mc)`, assert every jar's version equals `mod.version`, and assert the
   decoded lane set matches the published matrix **exactly** (no missing lane, no extra jar, no
   duplicate). Fabric publishes to all nine lanes; CurseForge is Forge-only, so only the four
   NeoForge lanes are expected there.
4. **Preflight validation** — `./gradlew runtimeTestInfrastructureCheck`, which runs the offline
   descriptor validate task (`runtimeTestDescriptorValidate`) **and** warms the tool/bootstrap +
   build-cache pipeline.
5. **Cold → warm cache assertion** — re-run `runtimeTestBootstrap` and assert it is
   `UP-TO-DATE` / `FROM-CACHE`. Any re-execution means a cacheable task's inputs changed and the
   cache is no longer effective.

### Stage 2 — `publish` (gated on preflight)

Per-lane `Kir-Antipov/mc-publish@v3.3` matrix (9 lanes), gated on preflight via `needs:
preflight`. Downloads the single lane's jar from the release and publishes it to Modrinth and/or
CurseForge with loader-appropriate dependencies (`fabric-api` + `fabric-language-kotlin` on Fabric,
`kotlin-for-forge` on NeoForge). Skipped automatically when preflight fails/skips or the lane isn't
in `target`.

### Stage 3 — post-publish verification (bounded, per registry)

After the lane actually ships, each selected registry is verified by resolving the **exact
just-published** Terrasect version from that registry via Ferium (not a local jar) and booting it
through HeadlessMC, asserting the resolved jar is exactly `mod.version`. Runs only for registries in
`target`, and only after `publish`.

- **Modrinth** — `:loader:<segment>:runtimeTestMODRINTHPublished` with
  `-Pterrseaect.runtimeTestPublishedVersion=<mod.version>`. Modrinth resolution is public, so **no
  registry secret is required**. All nine lanes.
- **CurseForge** — `:loader:<segment>:runtimeTestCURSEFORGEPublished` with the same property plus
  `-Pterrseaect.curseForgeTerrasectProjectId=1615147` and the read-only `CURSEFORGE_API_KEY`
  injected into the process env (never a task input). **NeoForge lanes only** (Terrasect never
  publishes Fabric to CurseForge).

Each verification step includes one bounded retry (3 attempts, 60 s apart) to absorb brief registry
propagation delay after publish; diagnostics are uploaded on failure regardless, so a propagation
delay is never silent.

## Required repository configuration

### Secrets

| Secret | Used by | Scopes / notes |
| --- | --- | --- |
| `MODRINTH_TOKEN` | `publish.yml` (publish), `pages.yml` (`sync-modrinth`) | Modrinth PAT; needs **Create versions** (plus **Read versions**/**Read projects**, bundled by default). One token with all scopes covers both. |
| `CURSEFORGE_TOKEN` | `publish.yml` (publish) | CurseForge API token. |
| `MODRINTH_PROJECT_ID` | `publish.yml` (publish) | Modrinth project id / slug. |
| `CURSEFORGE_PROJECT_ID` | `publish.yml` (publish) | CurseForge numeric project id. |
| `CURSEFORGE_API_KEY` | `publish.yml` (post-publish verify) | **Read-only** CurseForge API key for the public resolution Ferium performs. Injected into the process env only — never a task input, never written to caches or logs. |

> `GH_TOKEN` is supplied automatically by the runner as `${{ github.token }}` (with `contents: read`)
> for the preflight release-jar download; it needs no manual setup.

### Repo settings

- **Settings → Pages → Source: GitHub Actions** — one-time setup required before `pages.yml` can
  deploy (see below).

## `release.yml` — build artifacts

Triggered by pushing a `v*` tag or manually via dispatch. Builds all loader jars (named
`terrasect-<loader>-<modversion>+<mcversion>.jar`), uploads them as a `terrasect-jars` workflow
artifact, and attaches them to a draft GitHub release (`v<mod.version>` if not tag-triggered).
Publish the draft release manually after review, then run `publish.yml` with that tag.

## `pages.yml` — deploy user-facing docs

Triggered by pushing a `v*` tag or manually via dispatch. Two jobs:

- `deploy`: runs `pages/build.sh` (renders `pages/content/*.md` with pandoc into one stitched
  `pages/dist/index.html`) and publishes to GitHub Pages via `actions/deploy-pages`. Requires the
  repo's **Settings → Pages → Source** set to "GitHub Actions" once, before the first run.
- `sync-modrinth`: best-effort (`continue-on-error`), PATCHes `pages/content/summary.txt` and
  `pages/content/description.md` straight to the Modrinth project page. No-ops if the Modrinth
  secrets aren't set. See [`pages/README.md`](../pages/README.md) for why the content lives there
  and the CurseForge limitation (no public API for editing a project description).

## Local runtime-test commands

The runtime-test tasks live in `buildSrc` (registered from `RuntimeTestDsl`) and are the same ones
the CI matrix exercises. They are **opt-in**: none is invoked by the normal build, `spotlessCheck`,
or unit tests. Tool downloads (HeadlessMC + Ferium) and real registry resolution are the only parts
that touch the network; everything else is offline.

### Task reference

| Task | Scope | Network | What it does |
| --- | --- | --- | --- |
| `runtimeTestDescriptorValidate` | root | none | Offline: validate every descriptor (`runtime-tests/*.yaml`). Fails on any missing lane / unsupported pair / header mismatch. |
| `runtimeTestValidationExpectations` | root | none | Offline: assert the same parser rejects malformed/unsupported/missing-lane/header-mismatch shapes, and that the real matrix passes. |
| `runtimeTestBootstrap` | root (@CacheableTask) | HMC + Ferium only | Download + verify HeadlessMC and Ferium into `build/runtime-tools/bootstrap`. Cached; a warm run is `UP-TO-DATE`/`FROM-CACHE`. |
| `runtimeTestInfrastructureCheck` | root | via bootstrap | Preflight: descriptor validate + warm tool/bootstrap + cache pipeline. |
| `runtimeTestBuild` | root (all lanes) | HMC/Minecraft | Boot every locally built jar through HeadlessMC. |
| `runtimeTestLaunchDryRun` | root (all lanes) | none | Offline dry-run: assemble the exact HeadlessMC launch argv per lane (the verifiable controlled execution path). |
| `runtimeTestCompat` | root (all lanes) | HMC + Ferium | Boot compat-modpack scenarios (local jar + Ferium-resolved third-party mods). |
| `runtimeTestPublished` | root (all lanes) | HMC + Ferium + registry | Boot the published Terrasect artifact from Modrinth/CurseForge via Ferium. |
| `runtimeTestResolve` | root (all lanes) | registry only | Resolve Modrinth/CurseForge fixtures with Ferium. Add `-Pterrseaect.runtimeTestResolveDryRun=true` for a fully offline config-validity check. |
| `runtimeTestAll` | root | all above | Build + published + compat + resolve. |

Per-version tasks are addressable as `:<loader>:<segment>:<task>` where `<segment>` is the
Stonecutter id (`1.20.1`, `1.21.1`, `1.21.11`, `26.1.x`, `26.2.x`):

- `:fabric:26.2.x:runtimeTestLaunch` — boot the built jar for one lane.
- `:fabric:26.2.x:runtimeTestMODRINTHPublished` — boot the published Modrinth jar (asserts exact version).
- `:neoforge:26.2.x:runtimeTestCURSEFORGEPublished` — boot the published CurseForge jar (asserts exact version).
- `:fabric:26.2.x:runtimeTestCompat` — boot the compat scenario for one lane.

### One-lane vs full-matrix

```bash
# --- one lane (build-artifact smoke) ---
./gradlew :fabric:26.2.x:runtimeTestLaunch --console=plain

# --- one lane (published Modrinth, verify exact version) ---
./gradlew :neoforge:26.2.x:runtimeTestMODRINTHPublished \
  -Pterrseaect.runtimeTestPublishedVersion=0.2.3 --console=plain

# --- one lane (published CurseForge) ---
./gradlew :neoforge:26.2.x:runtimeTestCURSEFORGEPublished \
  -Pterrseaect.runtimeTestPublishedVersion=0.2.3 \
  -Pterrseaect.curseForgeTerrasectProjectId=1615147 --console=plain

# --- one lane (compat) ---
./gradlew :fabric:26.2.x:runtimeTestCompat --console=plain

# --- full matrix (all lanes) ---
./gradlew runtimeTestBuild            # boot every locally built jar
./gradlew runtimeTestPublished        # boot every published artifact
./gradlew runtimeTestCompat           # boot every compat scenario
./gradlew runtimeTestAll              # everything above

# --- offline preflight / validation (no tool download) ---
./gradlew runtimeTestDescriptorValidate       # validate descriptors only
./gradlew runtimeTestValidationExpectations   # assert the rejection pipeline
./gradlew runtimeTestInfrastructureCheck      # validate descriptors + warm tool/cache

# --- offline resolve dry-run (validates configs, no download) ---
./gradlew -Pterrseaect.runtimeTestResolveDryRun=true runtimeTestResolve

# --- offline dry-run launch argv (verifiable controlled execution path) ---
./gradlew runtimeTestLaunchDryRun
```

**Gradle properties** (all optional):

- `-Pterrseaect.runtimeTestPublishedVersion=<version>` — the exact Terrasect version the published
  artifact under test must match. Defaults to `mod.version`; set it to pin and verify a specific
  published version end to end. A mismatch fails hard — the task never silently resolves/boots
  another version or a local jar.
- `-Pterrseaect.curseForgeTerrasectProjectId=<id>` — Terrasect's CurseForge numeric project id
  (default `1615147`). Not baked anywhere; injected per run.
- `-Pterrseaect.runtimeTestResolveDryRun=true` — run every resolve task in dry-run mode (writes +
  validates the isolated Ferium config, no download).

### Local prerequisites

- **Java 25** (Temurin) — the active version (`26.2`) compiles to Java 25 bytecode; the runtime-test
  jobs and local runs use it. Older lanes are still built through the same Gradle toolchain, so
  sharing one JDK is fine.
- **`gh` CLI** — only needed for the preflight release-jar download (`gh release download`). Not
  required for local runtime tests.
- **`./gradlew`** — Gradle 9.5.0 + Stonecutter 0.9.6 (see `gradle/wrapper/`).
- **Network** — only for the tool bootstrap (`runtimeTestBootstrap`: HeadlessMC + Ferium
  download/verify) and for real registry resolution (`runtimeTestPublished` / `runtimeTestResolve`).
  Descriptor validate, validation expectations, dry-runs, and `-Pterrseaect.runtimeTestResolveDryRun=true`
  are fully offline.

### Cache locations & invalidation

Terrasect-owned runtime state lives under `build/runtime-tools/`:

- `build/runtime-tools/bootstrap/` — the cached HeadlessMC + Ferium tool tree (`hmc/`, `ferium/`).
- `build/runtime-tools/runtime/<loader>-<Platform-or-compat-or-segment>…/` — per-lane runtime dirs
  (HMC launcher log, Minecraft `logs/`, `crash-reports/`) — the post-publish failure artifacts.
- `build/runtime-tools/resolve/<label>…/` — Ferium resolve work dirs + deterministic manifests.

The expensive bootstrap is a `@CacheableTask`, so it is served from the Gradle build cache on a warm
run. To force a fresh tool download or clear cached state:

```bash
rm -rf build/runtime-tools          # drop the Terrasect-owned tool/cache tree
./gradlew cleanBuildCache           # clear the Gradle build cache (if enabled)
./gradlew runtimeTestBootstrap --rerun-tasks   # force the bootstrap to re-execute
```

> HeadlessMC and Minecraft also keep their own caches under your user home (launcher cache, game
> assets). Those are unrelated to Terrasect's build state; clear them only when a lane misbehaves for
> reasons outside `build/runtime-tools/`.

## Descriptor updates

Descriptors drive the release-form runtime testing and live under `runtime-tests/*.yaml` (parsed by
the self-contained reader in `buildSrc/src/main/kotlin/YamlReader.kt`, so there is no third-party
library on the buildSrc classpath and configuration stays offline).

- **`build.yaml`** — one BUILD scenario per supported `(loader, mc)` lane (locally built jar).
- **`published-modrinth.yaml`** — PUBLISHED (Modrinth) scenarios; every lane (all are published here).
- **`published-curseforge.yaml`** — PUBLISHED (CurseForge) scenarios; NeoForge lanes only.
- **`compat.yaml`** — COMPAT (third-party modpack) scenarios with pinned third-party mods.

Rules that the offline validate task enforces (a violation fails `runtimeTestDescriptorValidate`):

- The manifest header (`mod_id` / `latest` / `mod_version`) **must match the host** build, or
  validation fails. Bump `mod.version` in `stonecutter.properties.toml` and update all headers together.
- Every supported lane must have a BUILD scenario, at least one PUBLISHED scenario across the
  two registries, and a COMPAT scenario. A missing lane is a hard error, not a silent skip.
- Every scenario lane must be a supported `(loader, mc)` pair (the fixed matrix in
  `RuntimeTestPins.SUPPORTED_MATRIX`).
- COMPAT lanes require an explanatory `note`; non-compat lanes must not reference `externalMods`.
- **The descriptor reader is strict: every value must stay on a single physical line** (no multi-line
  / continuation values). Keep third-party pins pinned by Modrinth *version id* (the Maven
  coordinate), not by human version string.

The supported matrix itself is pinned once in `buildSrc/src/main/kotlin/RuntimeTestPins.kt`
(`SUPPORTED_MATRIX`) so the descriptor validation and the CI smoke matrix agree on the exact same set.

## Tool pin / checksum updates

The release tools are pinned in a single place:
`buildSrc/src/main/kotlin/RuntimeTestPins.kt`.

- **HeadlessMC** — `HMC_VERSION`, `HMC_JAR_URL`, `HMC_JAR_SHA256`.
- **Ferium** (`v4.7.1`) — `FERIUM_*_URL` / `FERIUM_*_SHA256` for linux-nogui and the per-arch
  macOS releases (`macos-arm` / `macos-x64`).

To bump a tool: download the release asset from its authoritative GitHub release, compute its
SHA-256, and update **version + url + sha256** together in `RuntimeTestPins.kt`. The macOS URL/sha
are chosen from the machine architecture at run time (there is no single `ferium-macos-nogui.zip`).
Never copy these values elsewhere — every download task derives its URL + checksum from this table,
so keep exactly one place to bump a tool. After changing a pin, re-run `runtimeTestInfrastructureCheck`
and the affected lanes locally.

## The release-gate flow (end to end)

1. **Bump** `mod.version` in `stonecutter.properties.toml`. Update the manifest headers in
   `runtime-tests/*.yaml` to match.
2. **Pin / verify** any release-tool bumps in `RuntimeTestPins.kt`.
3. **Local preflight (optional but recommended)**:
   ```bash
   ./gradlew runtimeTestInfrastructureCheck --console=plain
   ./gradlew :fabric:26.2.x:runtimeTestMODRINTHPublished \
     -Pterrseaect.runtimeTestPublishedVersion=0.2.3 --console=plain
   ```
4. **Tag and push**: `git tag v<version> && git push origin v<version>`. This triggers `release.yml`,
   which builds the jars and attaches them to a draft GitHub release, and `pages.yml`.
5. **Review** the draft GitHub release; publish it.
6. **Run `publish.yml`** with that tag (target `both`). The gate runs preflight → publish →
   post-publish verification. It **cannot publish** if preflight fails, and each registry's
   verification runs only after that registry ships.

### Known caveats

- **Registry propagation** — right after publish, a registry's index can lag briefly before the new
  version is resolvable by Ferium. The post-publish verification includes a bounded retry to absorb
  this; if it still fails after the retries, re-run `publish.yml` (the jars are already on the tag).
- **CurseForge is Forge-only** — Terrasect never publishes its Fabric build to CurseForge, so the
  CurseForge published fixtures and verification cover only the NeoForge lanes. Every lane is still
  covered on CurseForge's sibling (Modrinth).
