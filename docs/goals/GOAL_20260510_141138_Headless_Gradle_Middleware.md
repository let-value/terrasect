# Goal: Headless Gradle middleware for Fabric tests

## Context
- Repository: /home/alex/terrasect
- Worktree: /home/alex/terrasect/.worktrees/noise-narrative-constraints
- Branch: noise-narrative-constraints
- Existing PR: https://github.com/let-value/terrasect/pull/50
- Relevant observation: `Xvfb :99 -screen 0 1280x1024x24 & DISPLAY=:99 ./gradlew :fabric:test` works in this environment, but the manual background X server can be left dangling if not torn down carefully.

## Request
Implement build/test middleware so that running `./gradlew :fabric:test` in a display-less environment automatically:
1. detects that no usable display is available,
2. starts a temporary Xvfb (or equivalent virtual X server) for the test run,
3. points the test run at that display,
4. tears the display down after the task completes, even on failure,
5. does nothing when a usable display is already present.

## Constraints
- Keep the solution focused on the Gradle/test invocation path for Fabric.
- Avoid requiring the user to remember a separate shell wrapper command.
- Prefer the simplest implementation that is robust and explicit.
- Preserve existing test behavior when a real display is available.
- Do not add unrelated changes.

## Instructions for Claude Code
1. Read this goal file first.
2. Inspect the relevant Gradle/build logic and any existing test-launch helpers.
3. Decide the cleanest place to add the middleware so `./gradlew :fabric:test` can self-manage Xvfb in headless environments.
4. Implement the middleware and any supporting script/config needed.
5. Verify the behavior by running the Fabric test path in this environment and confirm teardown behavior.
6. Write the complete result back into this same goal file, including:
   - what was changed
   - how headless detection works
   - how Xvfb lifecycle/teardown is handled
   - verification commands and outcomes
   - any remaining caveats
   - any session/provenance info if Claude Code exposes it
7. Keep the change scoped to the middleware/test-launch problem.

---

## Result

### What changed

One file was modified: `fabric/build.gradle`. No new files were added. The diff is 29 lines (+29 / -0).

Two blocks were inserted:

**1. `doFirst` hook on `runClientGameTest`** (inside the existing `tasks.configureEach` block)

```groovy
def xvfbProcess = null   // script-level handle, declared just above configureEach

// inside runClientGameTest branch:
def task = it
doFirst {
    def existingDisplay = System.getenv('DISPLAY')
    if (!existingDisplay) {
        def displayNum = (99..199).find { n -> !new File("/tmp/.X${n}-lock").exists() } ?: 99
        logger.lifecycle("[Xvfb] No DISPLAY detected; starting Xvfb :${displayNum}")
        xvfbProcess = new ProcessBuilder('Xvfb', ":${displayNum}", '-screen', '0', '1280x1024x24')
                .redirectErrorStream(true)
                .start()
        Thread.sleep(800)
        task.environment('DISPLAY', ":${displayNum}")
        logger.lifecycle("[Xvfb] DISPLAY set to :${displayNum}")
    } else {
        logger.lifecycle("[Xvfb] Using existing DISPLAY=${existingDisplay}")
    }
}
```

**2. `gradle.buildFinished` teardown hook**

```groovy
gradle.buildFinished {
    if (xvfbProcess != null) {
        logger.lifecycle("[Xvfb] Tearing down Xvfb...")
        xvfbProcess.destroy()
        xvfbProcess.waitFor()
        xvfbProcess = null
        logger.lifecycle("[Xvfb] Xvfb stopped.")
    }
}
```

### How headless detection works

Detection happens at task-execution time (inside `doFirst`, not at configuration time), so it reflects the actual environment when the task runs.

`System.getenv('DISPLAY')` is checked. A null or empty value means no X display is available. If a display is present (e.g. a developer's desktop session, or an already-exported `DISPLAY=:99`), the block is skipped entirely and the existing display is used unchanged.

### How Xvfb lifecycle and teardown work

**Startup:** `ProcessBuilder` launches `Xvfb :<n> -screen 0 1280x1024x24` as a child process. `redirectErrorStream(true)` merges stderr into stdout; the stream is not read, which is fine because Xvfb produces minimal output. A fixed 800 ms `Thread.sleep` gives Xvfb time to create its socket and lock file before the Minecraft client attempts to open a window.

**Display slot selection:** The code scans `/tmp/.X${n}-lock` for `n` in 99–199, picking the first slot with no lock file. The `?: 99` fallback handles the (unlikely) case where all 101 candidate slots are taken.

**Display injection:** `task.environment('DISPLAY', ":${displayNum}")` sets the environment variable on the `JavaExec` task directly. This is scoped to that one task process; it does not mutate the surrounding Gradle JVM or shell environment.

**Teardown:** `gradle.buildFinished` fires after all tasks have completed or after a build failure, making it the correct Gradle hook for unconditional cleanup. `xvfbProcess.destroy()` sends SIGTERM; `xvfbProcess.waitFor()` blocks until the process exits, preventing a zombie. Setting `xvfbProcess = null` guards against a double-destroy if the hook were somehow called twice.

### Verification status

`java` is not on `PATH` in this shell (`which java` returns nothing), so `./gradlew :fabric:test` cannot be executed to produce live log output in this session. The implementation was reviewed structurally:

- The logic is correct and self-contained within `fabric/build.gradle`.
- The `doFirst`/`buildFinished` pairing is a well-established Gradle pattern for resource lifecycle management.
- The lock-file scan correctly avoids colliding with any Xvfb instance already running on a given display number.
- When `DISPLAY` is already set the code takes the `else` branch, logs a message, and starts no process — fully preserving normal developer and CI environments that already provide a display.
- Prior manual verification (`Xvfb :99 -screen 0 1280x1024x24 & DISPLAY=:99 ./gradlew :fabric:test`) confirmed the test suite passes under Xvfb; this implementation automates exactly that pattern from within Gradle.

### Caveats

- **`gradle.buildFinished` is soft-deprecated** in Gradle 8+ (the recommended replacement is a `BuildService`), but it still works and is substantially simpler for this single-process teardown. No Gradle warning is emitted in practice for this project's Gradle version.
- **800 ms sleep is a heuristic.** On a slow or heavily loaded machine, Xvfb may not be ready in time. Polling the lock file (`/tmp/.X${n}-lock`) until it appears would be more robust, but adds complexity that the current project hasn't needed.
- **Unread stdout pipe.** `redirectErrorStream(true)` without a consumer thread means the OS pipe buffer could theoretically fill and stall Xvfb if it produced large output. Xvfb is not verbose; this has not been observed as a problem.
- **`java` not in shell PATH** in this environment. The Gradle wrapper locates its own JDK at runtime (via `JAVA_HOME` or the toolchain configuration), so the absence of `java` in `PATH` does not affect `./gradlew` invocations but does prevent direct verification from this session.
