# Terrasect

Terrasect is a multiloader (Fabric + NeoForge) Minecraft world generation mod. It lets you carve a
dimension into named **regions** — using hex, Voronoi, and other tiling strategies — and attach
constraints to each region: noise, climate, height, biome, structure allow/block/force rules, mob
spawn rules, and loot table rules. Regions are declared in TOML presets or through a Kotlin DSL, and
are resolved deterministically per-seed at world generation time. Region boundaries are resolved via
signed distance fields, the hot path is designed to be allocation-free, and results are cached at the
chunk level so per-block lookups stay cheap.

> [!IMPORTANT]
> **AI disclosure.** LLMs were used extensively in developing this mod. We acknowledge that some
> parts of the codebase are poorly written and may contain bugs or AI-generated slop. The author
> doesn't have the time, resources, or Java/Kotlin expertise to fully support an intricate codebase
> like this one. The point of this project was the idea, not the implementation: region-based world
> generation constraints is a genuinely good idea that deserved to exist. Ideas can't be copyrighted,
> and AI-generated code even less so — everyone is encouraged to fork this project or recreate the
> idea from scratch.

## Supported Minecraft versions

| MC version | Loaders | Notes |
|---|---|---|
| `1.20.1` | Fabric | Back-compat target (Loom-based build, no client gametests) |
| `1.21.1` | Fabric, NeoForge | Back-compat target |
| `1.21.11` | Fabric, NeoForge | Back-compat target |
| `26.1` | Fabric, NeoForge | |
| `26.2` | Fabric, NeoForge | Latest; primary / active development version |

One shared codebase targets every version above via [Stonecutter](https://stonecutter.kikugie.dev/)
source preprocessing plus a small compat-shim layer — see [`docs/MULTIVERSION.md`](docs/MULTIVERSION.md).

## Project Structure

```
terrasect/
├── common/          # All shared logic — mixins, region model, generation pipeline, handlers
├── fabric/          # Fabric entrypoints only
├── neoforge/        # NeoForge entrypoints only
├── compat/c2me/     # Git submodule — optional C2ME-fabric performance compat
├── e2e/             # Fabric client gametests (separate Stonecutter matrix)
├── e2e-compat/      # Third-party mod compatibility gametests
├── versions/        # Stonecutter-generated per-version projects — git-ignored, do not edit
├── settings.gradle.kts       # Stonecutter version matrix
├── stonecutter.gradle.kts    # Active dev version + Spotless config (no separate root build.gradle.kts)
└── gradle.properties
```

`common/`, `fabric/`, and `neoforge/` are not themselves buildable Gradle projects — every buildable
project is version-qualified (`:<version>-<loader>`, e.g. `:26.2.x-fabric`).

See [`docs/PROJECT_MAP.md`](docs/PROJECT_MAP.md) for the full package-by-package architecture
reference.

## Building

Build every version/loader combination:

```bash
./gradlew build
```

Build a specific version/loader:

```bash
./gradlew :26.2.x-fabric:build
./gradlew :26.2.x-neoforge:build
```

## Running

```bash
./gradlew :26.2.x-fabric:runClient
./gradlew :26.2.x-fabric:runServer
./gradlew :26.2.x-neoforge:runClient
./gradlew :26.2.x-neoforge:runServer
```

Swap `26.2.x` for any other version project in the support matrix above (e.g. `1.21.11-fabric`).

## Testing

```bash
./gradlew :26.2.x-common:test                    # unit + snapshot tests
./gradlew :26.2.x-common:test -PupdateSnapshots  # regenerate snapshot references
./gradlew :e2e:26.2.x:runClientGameTest          # client gametests for a given version
```

## Configuration

Terrasect loads TOML presets from `<game>/config/terrasect` at startup. See
[`docs/CONFIGURATION.md`](docs/CONFIGURATION.md) for the full schema, and
[`docs/KNOWN_ISSUES.md`](docs/KNOWN_ISSUES.md) for config properties that are currently parsed but
not yet wired to runtime behavior.

## Development

- **Code style:** enforced by Spotless — `ktfmt` (Google style) for Kotlin, `google-java-format` for
  Java. Run `./gradlew spotlessApply` before committing; `./gradlew spotlessCheck` is what CI checks.
- **Shared logic lives in `common/`.** Loader modules (`fabric/`, `neoforge/`) only provide entrypoints
  and lifecycle hooks — see [`docs/PROJECT_MAP.md`](docs/PROJECT_MAP.md).
- **Releasing:** see [`docs/RELEASING.md`](docs/RELEASING.md) for the CI/release/publish workflow.

## Contributing

Issues and pull requests are welcome.

1. **Fork the repo and branch off `main`.** Keep each PR focused on one change; unrelated cleanup
   belongs in its own PR.
2. **Match the existing code style.** Run `./gradlew spotlessApply` before committing —
   `spotlessCheck` is enforced in CI. Prefer direct, comment-free code over abstractions or
   compatibility wrappers; see the guardrails in [`AGENTS.md`](AGENTS.md).
3. **Test your change.** Run `./gradlew :26.2.x-common:test` for anything touching `common/`, and
   add or update snapshot/unit tests alongside the change. If your change touches a mixin or
   anything version-divergent, it must pass `SmokeGameTest` across the client gametest matrix (see
   [`docs/MULTIVERSION.md`](docs/MULTIVERSION.md)) — a green compile proves nothing about whether a
   mixin actually applies at runtime on every version.
4. **Open the PR against `main`** with a short description of what changed and why, and which
   commands you ran to verify it.

Given the [AI disclosure](#terrasect) above, AI-assisted contributions are perfectly welcome here —
just make sure you've actually run and verified the change yourself before opening the PR.

For anything beyond a small fix — new constraint types, new strategies, structural refactors — open
an issue first to discuss the approach.

## Related Projects

Terrasect isn't the first or only mod exploring region-based world generation control, and it won't
be the last — see the [AI disclosure](#terrasect) above. We'd rather point people at the wider
ecosystem than pretend this is the only option.

**Prior art / predecessors:**

- _(none listed yet — if you know of an earlier mod that explored this space, open a PR adding it
  here)_

**Similar / newer projects:**

- _(none listed yet — open a PR to add yours)_

If you maintain a mod with similar goals (old or new), open a PR adding it to one of the lists
above, and feel free to link back to Terrasect from your own README. We're happy to return the
favor.

## License

MIT License. See [`LICENSE`](LICENSE) for details.
