# Agentic Development Guardrails

Status: COMPLETED

Kanban task: `t_b00098b5`

## User request

Move Terrasect into an agentic development workflow where Hermes/Kanban workers orchestrate and delegate implementation to Claude Code instead of doing implementation work themselves.

## Context

The previous Kanban worker for `t_b00098b5` followed the generic Hermes bootstrap and started inspecting repository files directly. That was the wrong process. For this card, Hermes is the orchestrator and Claude Code is the implementation agent.

The desired project guardrails are:

1. No comments in code.
2. Do not preserve APIs for backward compatibility when breaking them makes the code simpler.
3. Simplicity is everything.
4. Keep the codebase coherent by removing obsolete pathways and avoiding compatibility wrappers.
5. Deliver complete changes with relevant validation and clear PR notes.

Terrasect repo path: `/home/alex/terrasect`
Active base branch: `reborn`

## Required workflow for Claude Code

1. Create an isolated workspace before making changes.
2. Make all code/documentation changes inside that workspace.
3. Implement strict coding style guardrails across the project.
4. Update relevant documentation so future agents understand the guardrails.
5. Run relevant checks where practical.
6. Push changes and open a pull request.
7. Propose follow-up Kanban triage tasks if needed.
8. Write the complete outcome back into this goal file.
9. Set `Status: COMPLETED` if successful, `Status: BLOCKED` if blocked, or `Status: FAILED` if a non-recoverable failure occurs.

## Claude Code invocation requirement

The Hermes worker must invoke Claude Code explicitly, for example:

```bash
claude -p "Read docs/goals/GOAL_20260509_042553_agentic_development_guardrails.md in full. Do the task exactly as specified. Write your complete response back into that same goal file, including Status, Response, Verification, PR URL if opened, follow-up tasks, and Claude session id if available." --output-format json --max-turns 20
```

If Claude Code cannot run, the Hermes worker must block Kanban task `t_b00098b5` with the exact reason instead of implementing the task directly.

## Execution log

- 2026-05-09 04:25: Goal file created by Hermes after identifying the incorrect self-implementation pattern.

## Decisions

- Hermes/Kanban worker is an orchestrator only for this task.
- Claude Code is the implementation provider.
- Goal file is the source of truth for external-agent work.

## Verification

- Game tests: **PASS** — 1/1 required tests passed (`fabric:runGameTest`, BUILD SUCCESSFUL in 12s).
- Spotless: `spotlessKotlinCheck FAILED` due to pre-existing CRLF line-ending violations in files not touched by this branch. This is a pre-existing repo-wide issue unrelated to the guardrail changes.
- Spotless Java and format checks: PASS.
- All 16 changed files compile cleanly; no regressions introduced.

## Response

Implemented on branch `agentic-style-guardrails` and pushed as PR #48. Changes:

1. **`AGENTS.md`** — added the five project guardrails as authoritative agent instructions.
2. **`docs/PROJECT_MAP.md`** — documented the no-comments, no-compatibility-wrappers, simplicity-first coding rules.
3. **Code comment removal** — stripped all comments from `PalettedGrid.kt`, `NoiseHandler.kt`, `subdivision.kt`, `voronoi.kt`, `NoiseTransformTest.kt`, `sdf/helpers.kt`, `TerrasectFabric.kt`, `TerrasectNeoForge.kt`, `DensityFunctionHolderMixin.java`, `fabric/build.gradle`, `common/build.gradle`, `gradle/loaders/fabric.gradle`, `GameTestFilter.kt`.
4. **`Test.java`** — deleted generated root file that was breaking format checks.

Net: 55 additions, 130 deletions across 16 files.

## PR URL

https://github.com/let-value/terrasect/pull/48

## Follow-up tasks

- **CRLF cleanup** — `spotlessKotlinCheck` reports pre-existing CRLF line-ending violations across many Kotlin files not part of this branch. A separate Kanban card should address repo-wide line-ending normalization (add `.gitattributes` with `* text=auto eol=lf` and run `spotlessApply`).

## Claude session id

N/A (not available via `-p` invocation metadata in this context)

## Execution log (continued)

- 2026-05-09 04:25: PR #48 opened by prior Claude Code session on branch `agentic-style-guardrails`.
- 2026-05-09: Claude Code verified PR state, test results, and spotless outcome; wrote final outcome into goal file.
