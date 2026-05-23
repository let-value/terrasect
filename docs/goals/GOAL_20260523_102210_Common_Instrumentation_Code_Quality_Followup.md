Status: COMPLETE
Workspace: /home/alex/terrasect/.worktrees/common-instrumentation
Branch: feature/common-instrumentation
Base: reborn
Provider: OpenAI Codex gpt-5.5 via Hermes

Task: Read commits ae9ee8c and fbd3655, then apply the same code-quality direction across the common instrumentation worktree: consistent `log` logger naming, no redundant instrumentation-handle declarations, clean instrumentation call sites, no stale reset/debug scaffolding, and no formatting artifacts introduced by those commits.

Commit review:
- ae9ee8c removed repeated local `TerrasectInstr.*` declarations and hoisted scoped instrumentation handles.
- fbd3655 renamed logger fields/call sites to `log`, removed unused reset trace scaffolding, and cleaned stale direct references to the old logging helpers.

Changes made:
- Converted hoisted instrumentation handles in `ChunkContext`, `ClimateHandler`, `NoiseHandler`, and `StructureHandler` from mutable `var` to immutable `val`.
- Removed blank-line/whitespace artifacts left behind where redundant local instrumentation declarations had been deleted.
- Cleaned remaining logger-message artifacts from the `LOGGER` -> `log` pass, especially leading spaces in GameTest log/assertion text.
- Let Spotless normalize affected Kotlin/Java formatting, including the structure-recording mixin shadow annotation and gametest import/wrapping cleanup.

Verification:
- `source ~/.bash_profile >/dev/null 2>&1 || true; ./gradlew spotlessApply :common:compileKotlin :common:test --tests terrasect.instrumentation.MetricsTest`
- `source ~/.bash_profile >/dev/null 2>&1 || true; ./gradlew :common:compileKotlin :common:test --tests terrasect.instrumentation.MetricsTest :fabric:compileKotlin :fabric:compileGametestKotlin :neoforge:compileKotlin`
- `git diff --check`
- Searched Kotlin sources for stale `LOGGER`, mutable `instr`, redundant `TerrasectInstr` locals, leading-space log messages, and `resetOriginTrace`; no matches remain.

Notes:
- The first Gradle attempt failed before sourcing `~/.bash_profile` because Java was not on PATH; sourcing it exposed mise Java 21 and all verification commands passed.
