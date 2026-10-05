# KubeJS / Night Config regression (#77)

Investigation on 2026-10-05, starting at main `68d3ddc`. The reporter's Minecraft, loader,
mod versions and original log remain unknown; these results cover the pinned combination below.

## Pins and artifact controls

- Minecraft 1.21.1, NeoForge 21.1.234, Java 21.0.12.1, Kotlin for Forge 5.10.0.
- KubeJS 2101.7.2-build.377 ([Modrinth THIGFPwf](https://modrinth.com/mod/kubejs/version/THIGFPwf)).
- Rhino 2101.2.8-build.91 ([SqkDvOLG](https://modrinth.com/mod/rhino/version/SqkDvOLG)).
- KubeJS includes Better Advanced Tooltips 2101.1.0-build.1; it needs no separate installation.
- Untouched NeoForge production jars downloaded from GitHub releases; each SHA-256 verified
  against its release asset digest:

| Terrasect | SHA-256 |
|---|---|
| 0.2.3+1.21.1 | `2785fb0cc8632c510c686e68be30610a71d0a5ccf14633754c74f4f230b9c1dc` |
| 0.2.4+1.21.1 | `c0a929a525970d1e744b031958d178f5ca9ef6c2b2c353c8bb45cbfd17392750` |

Dedicated-server controls use identical fresh installations, differing only in Terrasect/KubeJS
presence. Terrasect runs also include the separate server test mod.

| Installation | Exit | Result |
|---|---|---|
| KubeJS + Rhino, no Terrasect | 0 | World started, `Done`, clean stop |
| Terrasect 0.2.3 alone | 1 | Duplicate Night Config TOML module before mod initialization |
| Terrasect 0.2.3 + KubeJS + Rhino | 1 | Same duplicate module failure |
| Terrasect 0.2.4 alone | 0 | World started, smoke assertions passed, clean stop |
| Terrasect 0.2.4 + KubeJS + Rhino | 0 | World started, smoke assertions passed, clean stop |

The first failure is `java.lang.module.ResolutionException: Module ... reads more than one
module named com.electronwill.nightconfig.toml`. The consumer module named in the message varies;
the duplicate module is the same. NeoForge already provides core/TOML 3.8.3 in its libraries.
Terrasect 0.2.3 additionally nests `META-INF/jarjar/toml-3.8.3.jar` with that automatic module name.
0.2.4 removes it through PR #75. The pinned KubeJS/Rhino jars do not bundle Night Config.
This reproduction does not require KubeJS, and does not establish the reporter's exact environment.

## Retained checks

```sh
./gradlew :neoforge:1.21.1:minecraftTestBuild \
  :neoforge:1.21.1:minecraftTestCompat \
  :neoforge:1.21.1:minecraftTestKubejsClient --no-parallel --max-workers=3
```

The compatibility server pack includes pinned KubeJS and Rhino alongside its existing BOP,
TerraBlender, GlitchCore and Distant Horizons dependencies. Server checks now execute `/ts print`
as well as locate/query, exercising the region TOML writer. Native NeoForge server checks reload
startup-generated configuration and both default presets, round-trip their TOML, and record the
resolved core/parser/writer module and source locations.

The extra client pack contains native NeoForge Terrasect, the native test mod, KubeJS, Rhino and
Kotlin for Forge. Its `ScreenEvent.Init.Post` listener dismisses Minecraft's first-launch
accessibility onboarding, requires a real `TitleScreen`, checks configuration/TOML, records a
completion marker, and stops Minecraft. No Fabric client GameTest API or Connector is required.
HeadlessMC uses LWJGL stubs and dummy assets: this verifies client startup, not GPU rendering.

The client task belongs to root `minecraftTestCompat`/`minecraftTest` and the manual runtime workflow's
NeoForge 1.21.1 compatibility selection. Ordinary PR CI remains unchanged. Results and inventories
are written through the existing collector under `build/minecraft-test/{results,logs,modpacks}`;
the extra scenario id is `neoforge-1.21.1-kubejsclient`.

A separate 0.2.4 client process was launched with `config.toml`, `example.toml` and
`climate_debug.toml` already present. It reached `TitleScreen`, passed the same configuration
checks and exited 0. Offline Realms authentication and unavailable sound-device errors appear
in the headless client logs; they do not prevent the asserted startup or clean exit.

The native client regression was also run with its prepared production jar replaced by untouched
published 0.2.3 (test mod and all other dependencies unchanged): module resolution failed,
Minecraft exited 1, and Gradle rejected the run before any completion marker. The normal 0.2.4
client/server production jar SHA-256 exactly matches the published 0.2.4 digest above.

The final affected-lane run passed all 11 scenarios in 4m18s: clean/compat servers for Fabric
1.20.1/1.21.1 and NeoForge 1.21.1/1.21.11/26.2, plus the native KubeJS client check. Each result
recorded its required assertion marker and exit 0. Existing API-based client lanes were unchanged
and were not rerun for this test-only change.

Formatting checks and all five common unit suites passed (1,150 cases). All four NeoForge
server test-mod variants compiled; the buildSrc collector/process suite passed all six cases.

No production packaging change is needed for the reproduced NeoForge case. Fabric provision stays
unchanged; these NeoForge results do not claim Fabric/KubeJS compatibility.
