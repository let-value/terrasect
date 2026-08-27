## Runtime Test Infrastructure — Design Decisions

**Date:** 20260827
**Task:** `t_550ce955` — Integrate HeadlessMC + Ferium runtime testing via Gradle for CI and releases
**Request:** Implement Gradle-first runtime-test infrastructure (issue #74) that verifies release-form Terrasect
jars through real HeadlessMC launches, resolved via Ferium, across the full MC + loader matrix, without
replacing the Loom/GameTest suites. See acceptance criteria in the kanban card body.

---

### Scope decisions

- **This build.** This task adds a self-contained runtime-test *infrastructure* to the Terrasect build. It does
  not modify any mod logic, loader buildscripts, or existing gametest machinery. It is opt-in: nothing in the
  normal build, `spotlessCheck`, or unit tests loads it unless a `runtimeTest*` task is invoked.
- **Offline-verifiable first.** The whole infrastructure must be verifiable offline on the worker before it ships.
  Real Modrinth/CurseForge/HeadlessMC network access is only exercised behind explicit tasks and in CI.
- **Gradle is the public interface.** No workflow or developer shells out to HeadlessMC/Ferium directly.

---

### Tool pins (single source of truth)

```
headlessmc.version  = 2.10.0
headlessmc.jar.url  = https://github.com/headlesshq/headlessmc/releases/download/2.10.0/headlessmc-launcher-2.10.0.jar
headlessmc.jar.sha256 = 52bd5006f478377b3893011d458562977d38c65ead6d2b31089beb4d614f13cd

ferium.version       = 4.7.1
ferium.linux.url     = https://github.com/gorilla-devs/ferium/releases/download/v4.7.1/ferium-linux-nogui.zip
ferium.linux.sha256  = 8d4a357c6eaf05bc7804d1916fe597b58f10d57fe16443b9b767776e99049d14
ferium.macos.url     = https://github.com/gorilla-devs/ferium/releases/download/v4.7.1/ferium-macos-nogui.zip
ferium.macos.sha256  = 5f5350f81763195b6d28deb6f67c4d971ba4d3cac18a133d9568def9fba199d3
```
All four lives in one props-like table in the buildSrc/runtime-test script so a bump is one place.

---

### Build-system decisions (validated)

- **`mcVersion` = `sc.current.version`** — this is the Stonecutter *version id* (e.g. `26.2.x`), **not** the
  Minecraft version. The Gradle build script path is `:<branch>:<cutterVersionId>`, e.g.
  `:fabric:26.2.x`. Confirmed from `build.fabric.gradle.kts` / `build.neoforge.gradle.kts` buildscript naming.
- **Loader** = the Stonecutter branch id (`fabric` / `neoforge`), via `parent?.name` in `build-extensions.kt`.
- **Per-version task registration** must go through a convention plugin applied by each loader buildscript
  (terrasect-mod + fabric/neoforge), not a hand-rolled per-version block, so a new version is covered by a
  single matrix entry.

### Runtime-test infrastructure shape

- **One convention plugin** `terrasect-runtime-test` applied from the loader buildscripts, plus a root-level
  aggregate plugin/script that enumerates the fabric/neoforge projects via `sc.tree` and wires root tasks.
- **Gradle tasks** (registered per matrix entry):
  - `runtimeTestBootstrap` — download + verify HeadlessMC jar; download + verify + extract Ferium.
  - `runtimeTestBootstrap` is @CacheableTask with `@Input`/`@InputFile`/`@OutputDirectory` so the expensive
    download/verify/extract participates in the build cache (cold→warm second run is UP-TO-DATE/FROM-CACHE).
  - `runtimeTestLaunch` — copies a Terrasect jar into a HMC runtime dir, runs HMC `launch <loader>:<mc>` headless
    with an offline account, asserts Terrasect loaded via a log match (success-condition), then `-quit`.
  - Root `runtimeTestBuild` — aggregate: runs `runtimeTestLaunch` across every matrix entry (local jars).
  - Root `runtimeTestPublished` / `runtimeTestCompat` / `runtimeTestAll` / `runtimeInfrastructureCheck`.

### Success condition (stronger than "process existed")

- HeadlessMC `launch ... -lwjgl -offline -keep -quit`; read the combined Minecraft+HMC output and assert a
  Terrasect-unique marker. Terraset's Fabric/NeoForge entry points log distinct "Terrasect" lines (e.g. the
  `log.info("Terrasect")` strings). The task greps the log file for the marker and fails if not seen within a
  bounded time. Where an mc-runtime-test jar exists for the version/loader, prefer the HMC "run specific test"
  path; otherwise fall back to the log-marker assertion. Timeouts wrap install and launch.

### Descriptor model (declarative, reviewable)

- **YAML files** under `runtime-tests/modpacks/*.yaml` (Ferium profiles/descriptors) and `runtime-tests/scenarios/*.yaml`
  (launch scenarios). No binary, no credentials.
- **No network config-time.** `./gradlew tasks`, spotless, `build` must NOT touch Modrinth/CurseForge. Resolution
  happens only inside the Ferium-managed tasks behind the scenarios.
- Descriptors encode: Minecraft version, loader, source platform (modrinth/curseforge), mod/project identifier,
  pinned version where reproducibility needs it, scenario (build/published/compat). The matrix is enumerated in
  settings.gradle.kts `mods`/matrix table — same versions the CI smoke job already uses.

### CI / release integration

- **ci.yml**: add a `runtime` job — a small matrix over the matrix that runs `runtimeTestLaunch` for build jars
  per entry (kept separate from the gametest smoke job so failures are distinguishable).
- **publish.yml**: add a required `runtime-preflight` job that runs `runtimeInfrastructureCheck` + descriptor
  validation + expected-jar existence check, gated before publish. Add post-publish verification using the exact
  just-published version with bounded retries (needs `MODRINTH_TOKEN`/`CURSEFORGE_TOKEN` + `MCPUBLISH_*` secrets).

### Secrets

- `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`, `CURSEFORGE_PROJECT_ID` — used only in the publish scenario, never baked
  into descriptors or caches. Modrinth-only scenarios need none.

### Verification done offline

- Root `:runtimeInfrastructureCheck` runs HMC/Ferium bootstrap with cache enabled → confirm cold run downloads,
  warm second run is UP-TO-DATE/FROM-CACHE.
- Descriptor/YAML validation passes for every matrix entry without any network.
- `./gradlew spotlessCheck` clean; normal build + unit tests unaffected.

### Open questions (flagged, not blocked)

- NeoForge on some very old versions may need `--uid`/`--server` specifics; the HMC `launch` invocation is
  parametrized per loader so per-version quirks stay isolated.
- The log-marker success string for each version/loader is the one thing that cannot be asserted offline; it is
  documented as the value to tune on first real CI run.
