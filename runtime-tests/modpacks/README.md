# Terrasect Ferium lane profiles

These are native Ferium 4.7.1 configuration files.

## Compatibility profiles

One per supported `(loader, Minecraft)` lane:

- `fabric-1.20.1.json`
- `fabric-1.21.1.json`
- `neoforge-1.21.1.json`
- `fabric-1.21.11.json`
- `neoforge-1.21.11.json`
- `fabric-26.1.json`
- `neoforge-26.1.json`
- `fabric-26.2.json`
- `neoforge-26.2.json`

These contain Terrasect plus the compatible fixture set for the lane.

## Release profiles

Single-mod profiles for release-artifact verification:

- `release-modrinth-fabric-1.20.1.json`
- `release-modrinth-fabric-1.21.1.json`
- `release-modrinth-neoforge-1.21.1.json`
- `release-modrinth-fabric-1.21.11.json`
- `release-modrinth-neoforge-1.21.11.json`
- `release-modrinth-fabric-26.1.json`
- `release-modrinth-neoforge-26.1.json`
- `release-modrinth-fabric-26.2.json`
- `release-modrinth-neoforge-26.2.json`
- `release-curseforge-neoforge-1.21.1.json`
- `release-curseforge-neoforge-1.21.11.json`
- `release-curseforge-neoforge-26.1.json`
- `release-curseforge-neoforge-26.2.json`

The Modrinth release set covers all nine lanes. The CurseForge release set covers the four
NeoForge lanes that are published there.

Ferium represents a custom mod set as a `profile`; the native `modpacks` array is reserved for
published Modrinth/CurseForge modpack projects and is intentionally empty here.

The checked-in profiles are reviewable native Ferium profiles. The `fabric-*` and `neoforge-*`
compatibility profiles contain Terrasect plus the compatible registry mods for that lane; the
`release-*` profiles contain only the published Terrasect project. They do not contain local paths.

For local build-artifact E2E tests, the Gradle pipeline generates an isolated profile under
`build/runtime-tools/ferium/<scenario>/`, stages the built jar under that profile's `user/`
directory, and points Ferium's `output_dir` at the sibling `mods/` directory. Ferium then resolves
the registry mods and copies the local jar into the same output before the downstream HeadlessMC
task runs.

Run one manually with an isolated output directory, for example:

```bash
FERIUM_CONFIG_FILE=runtime-tests/modpacks/fabric-26.2.json ferium upgrade
```

The profiles use project identifiers rather than version IDs because Ferium 4.7.1 resolves the
latest compatible release for a configured project. Exact fixture version IDs remain recorded in
`runtime-tests/compat.yaml` and `stonecutter.properties.toml` for the existing e2e-compat lanes;
these standalone profiles intentionally do not claim exact-version pinning for newly added lanes.

## Gradle E2E stages

The Gradle task graph is explicit and ordered for every supported lane:

```text
runtimeTest<loader>-<mc>CompatPrepare
  -> runtimeTest<loader>-<mc>CompatDownload
  -> :<loader>:<stonecutter-segment>:runtimeTestCompat
```

Published-artifact lanes use the same `Prepare -> Download -> HeadlessMC` shape, with no local jar
staged and an exact Terrasect version assertion after Ferium resolves the registry artifact.

Every HeadlessMC launch includes `-offline`, the supported CI account mode. This keeps launches
non-interactive and does not require credentials in profiles, task inputs, logs, or artifacts.

Useful entry points:

```bash
# Prepare every generated profile and local-build staging directory.
./gradlew runtimeTestFeriumPrepare

# Resolve/download every generated profile (networked; requires bootstrapped tools).
./gradlew runtimeTestFeriumDownload

# Prepare, resolve, and launch one compatibility lane through HeadlessMC.
./gradlew :fabric:26.2.x:runtimeTestCompat

# Offline profile validation; does not bootstrap tools or contact a registry.
./gradlew -Pterrseaect.runtimeTestResolveDryRun=true runtimeTestResolve
```
