# Goal: Investigate client GameTest crash

## Context
- Repository: /home/alex/terrasect
- Worktree: /home/alex/terrasect/.worktrees/noise-narrative-constraints
- Branch: noise-narrative-constraints
- Crash report: /home/alex/terrasect/.worktrees/noise-narrative-constraints/fabric/build/run/clientGameTest/crash-reports/crash-2026-05-10_08.30.52-client.txt

## Question to validate
The crash may be caused by running the client GameTest on a server without a graphical interface. Validate or reject that hypothesis by inspecting the crash report and the relevant client GameTest wiring/runtime assumptions.

## Instructions for Claude Code
1. Read this goal file first.
2. Inspect the crash report and surrounding logs if needed.
3. Validate whether the lack of a graphical interface is the primary cause, or whether another issue is more likely.
4. Use the Terrasect noise/client GameTest context already present in the worktree as needed.
5. Write the complete investigation result back into this same goal file, including:
   - root cause assessment
   - evidence for or against the graphical-interface hypothesis
   - any follow-up code/test changes needed
   - any verification you performed
   - any blockers
   - any session/provenance info if Claude Code exposes it
6. Do not make unrelated changes.

---

## Investigation Result

**Investigator:** Claude (claude-sonnet-4-6), session 2026-05-10

---

### Root Cause Assessment

**Hypothesis: CONFIRMED.** The crash is entirely and exclusively caused by running the Fabric client GameTest (`runClientGameTest`) in an environment without a functioning display server (X11 or Wayland). GLFW — the OpenGL windowing layer used by LWJGL/Minecraft — cannot find any platform to attach to and raises a hard fatal exception before any game logic runs.

---

### Evidence For the GUI/Headless Hypothesis

**1. Crash exception is GLFW platform detection failure, not a mod error.**

```
java.lang.IllegalStateException: Failed to initialize GLFW, errors:
  GLFW error during init: [0x1000E]Failed to detect any supported platform
    at com.mojang.blaze3d.platform.GLX._initGlfw(GLX.java:58)
    at com.mojang.blaze3d.systems.RenderSystem.initBackendSystem(RenderSystem.java:193)
    at net.minecraft.client.Minecraft.<init>(Minecraft.java:486)
```

Error code `0x1000E` is `GLFW_PLATFORM_UNAVAILABLE` — GLFW found no X11 socket, no Wayland compositor, and no other supported windowing backend. This is the canonical headless-Linux crash for LWJGL applications.

**2. Crash report confirms "GFLW Platform: \<error\>"** (sic typo in MC report).

The System Details section in the crash report shows:
- `GFLW Platform: <error>` — no platform was selected.
- `Backend API: Unknown` — no GL context was ever created.
- `Window size: <not initialized>` — window creation never started.
- The AMD Radeon 780M GPU is present in hardware, so this is not a missing GPU issue; the GPU driver is simply not exposed because no display session is active.

**3. The crash happens before any Terrasect code is on the render path.**

From `latest.log`:
- `[Render thread/INFO] (terrasect) Hello from Terrasect on Fabric!` — mod init completed fine.
- `[Render thread/INFO] (terrasect) Initializing Terrasect common...` — common init completed fine.
- The crash follows at LWJGL/GLFW initialization, entirely inside Minecraft/Blaze3D infrastructure.

**4. No display/headless configuration is set in the Gradle task.**

`fabric/build.gradle` lines 121–135 configure `runClientGameTest` with only:
```groovy
it.systemProperty('fabric.client.gametest.disableNetworkSynchronizer', 'true')
```
There is no `DISPLAY` env var, no `LIBGL_ALWAYS_SOFTWARE`, no Xvfb wrapper, and no `java.awt.headless` property (which would not help GLFW anyway).

---

### Evidence Against Alternative Causes

| Alternative hypothesis | Verdict |
|---|---|
| Mod code bug (Terrasect) | **Rejected.** Crash occurs inside `Minecraft.<init>` before any gametest runs. |
| Missing dependencies / classpath | **Rejected.** The log warning about missing Java class dirs is harmless (Kotlin-only project). All 52 mods loaded successfully. |
| LWJGL version mismatch | **Rejected.** LWJGL 3.3.3-snapshot loads and reports correctly; the failure is at platform detection, not library loading. |
| Driver / GPU issue | **Rejected.** GPU is enumerated in the crash report system details; the problem is no display session, not a GPU fault. |

---

### Follow-up: What Needs to Change

The client GameTest task requires a display. Two standard approaches for headless/CI Linux environments:

**Option A — Xvfb virtual framebuffer (simplest, no code change needed)**

Before running the task, start a virtual display:
```bash
Xvfb :99 -screen 0 1280x1024x24 &
DISPLAY=:99 ./gradlew :fabric:test
```
Or set the environment variable in `runClientGameTest`:
```groovy
if (name == 'runClientGameTest') {
    it.environment('DISPLAY', System.getenv('DISPLAY') ?: ':0')
    it.environment('LIBGL_ALWAYS_SOFTWARE', '1')  // optional: Mesa software rendering if no HW GL
}
```
The Gradle task itself does not start Xvfb — that must happen at the shell/CI level.

**Option B — Mesa software rendering with EGL (no real X server, but heavier)**

Set `DISPLAY=:0` plus `LIBGL_ALWAYS_SOFTWARE=1` and use `eglplatform=surfaceless`; this requires Mesa and can run without X11 but requires LWJGL EGL support.

**Recommended path for CI:** install `xvfb`, run `Xvfb :99 &`, export `DISPLAY=:99`, then run `./gradlew :fabric:test`. This is the standard approach for Fabric client GameTests in headless pipelines (Modrinth, GitHub Actions `ubuntu-latest`).

---

### Verification Performed

- Read crash report: `fabric/build/run/clientGameTest/crash-reports/crash-2026-05-10_08.30.52-client.txt` — exception and system details confirmed.
- Read runtime log: `fabric/build/run/clientGameTest/logs/latest.log` — confirmed mod initializes before crash, no Terrasect stackframe involved.
- Read `fabric/build.gradle` — confirmed no display or headless JVM args in `runClientGameTest`.
- Read `fabric/src/main/resources/fabric.mod.json` — confirmed three `fabric-client-gametest` entrypoints registered, all requiring a live Minecraft client (and thus a display).

---

### Blockers

None — the root cause is fully understood. The fix is an environment/CI configuration change (start Xvfb before the Gradle test task), not a code change in Terrasect.

---

### Summary

The crash is 100% a missing-display problem. The Fabric client GameTest spins up a full Minecraft client process (not a headless server), which calls GLFW to open a window. With no X11 or Wayland session available in the environment where the task ran, GLFW immediately fails with `GLFW_PLATFORM_UNAVAILABLE`. No Terrasect code is implicated. Fix is to run the task inside an Xvfb virtual display session.
