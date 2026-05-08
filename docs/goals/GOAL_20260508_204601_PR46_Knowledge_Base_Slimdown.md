## Task
**Date:** 20260508
**Submitted By:** Hermes Orchestrator
**Status:** COMPLETED
**Kanban context:** corrective/manual turn for `t_81cdfce7` — Explore project and establish development context and best practices
**PR:** https://github.com/let-value/terrasect/pull/46

### Request
Alexander left a PR review comment on PR #46 and asked Hermes to perform another corrective turn for the Kanban item "Explore project and establish development context and best practices".

### Exact PR review comment
The review comment is on `docs/KNOWLEDGE_BASE.md`, line 228, at the start of Section 7:

> Section 7 should be slimed down to a basic project info like java and kotlin versions and that it, all of the extra info should go to project map file and we can leave a link to that file here instead

Interpretation:

- `docs/KNOWLEDGE_BASE.md` should remain primarily an orchestration/workflow knowledge base.
- Section 7 should be slimmed down to basic project information only, such as Java/Kotlin/Minecraft versions and a pointer to `docs/PROJECT_MAP.md`.
- The detailed architecture/development-context content currently in `docs/KNOWLEDGE_BASE.md` Section 7 should be moved to, merged into, or already covered by `docs/PROJECT_MAP.md`.
- Avoid duplicating detailed project-map information in the knowledge base.

### Workspace
Repository root:

```text
/home/alex/terrasect
```

Current branch:

```text
docs/reborn-development-context
```

Remote PR branch:

```text
origin/docs/reborn-development-context
```

Active base branch:

```text
origin/reborn
```

The branch is already opened as PR #46 against `reborn`.

### Context
Previous corrective turn:

- Closed stale PR #45, which targeted stale `main`.
- Rebuilt docs from `reborn` on branch `docs/reborn-development-context`.
- Opened PR #46 against `reborn`.
- Created/updated:
  - `docs/KNOWLEDGE_BASE.md`
  - `docs/PROJECT_MAP.md`
  - `docs/TODO.md`
  - `docs/goals/README.md`
  - `docs/goals/GOAL_20260508_202347_Reborn_Documentation_Recovery.md`

The new correction should be a small documentation-structure change, not a codebase redesign.

### Required work
1. Read `docs/KNOWLEDGE_BASE.md` Section 7 and `docs/PROJECT_MAP.md`.
2. Slim down Section 7 of `docs/KNOWLEDGE_BASE.md` so it contains only basic project facts and a link to `docs/PROJECT_MAP.md` for detailed architecture/development context.
   - Keep basic facts accurate against the repo where easy to verify, especially Java/Kotlin/Minecraft versions.
   - Do not leave a long architecture/development-context section in `docs/KNOWLEDGE_BASE.md`.
3. Move or preserve any useful detailed information from the old Section 7 into `docs/PROJECT_MAP.md` if it is not already present there.
4. Update `docs/goals/README.md` to index this corrective goal.
5. Update this goal file under `## Response`, including files changed and verification commands.
6. Commit and push the PR branch.
7. Reply to the GitHub review comment or add a PR comment summarizing the fix.
8. Do not create a new PR; update existing PR #46.

### Acceptance criteria
A successful run leaves:

- `docs/KNOWLEDGE_BASE.md` Section 7 slimmed down to basic project info plus a link to `docs/PROJECT_MAP.md`.
- `docs/PROJECT_MAP.md` contains any detailed architecture/development context that should not live in the knowledge base.
- `docs/goals/README.md` indexes this goal.
- This goal file has `Status: COMPLETED` and a populated `## Response`.
- A new commit is pushed to `docs/reborn-development-context`.
- PR #46 contains a response/comment acknowledging Alexander's requested change.

### Provider chain
The orchestrator will attempt providers in this order:

1. Claude Code
2. Codex
3. Copilot
4. Ollama/local fallback

---
**Sub-agent instruction:** This file contains everything you need. Read it in full. Perform the work. Write your complete response under `## Response` below before finishing. Do not summarize back to the orchestrator — write the full output so it is preserved for future sessions. Update the `Status` field to `COMPLETED` only when the task is complete. If broader protocol understanding is needed, read `AGENTS.md`, `docs/KNOWLEDGE_BASE.md`, and `docs/PROJECT_MAP.md`.

## Execution log

- 20260508 20:46 — Goal file created by Hermes Orchestrator after reading PR #46 review comment.
- 20260508 — Executed by Claude Code (claude-sonnet-4-6).

## Response

**Completed by:** Claude Code (claude-sonnet-4-6)
**Date:** 2026-05-08

### Summary

Slimmed down `docs/KNOWLEDGE_BASE.md` Section 7 per Alexander's review comment. The section previously contained a large development-context dump (module layout, mixin details, SDF library, generation pipeline, strategies, region data model, caching, build tasks, Spotless config, presets, snapshot tests, open questions). It now contains only basic project facts and a pointer to `docs/PROJECT_MAP.md`.

### Files changed

**`docs/KNOWLEDGE_BASE.md`** — Section 7 replaced with a compact table:

| Property | Value |
|----------|-------|
| Branch | `reborn` |
| Minecraft | 1.21.11 |
| Java | 21 |
| Kotlin | 2.3.0 |
| JVM target | 21 |
| Fabric Loader | 0.18.4 |
| NeoForge Loader | 21.11.36-beta |

Plus a note pointing to `docs/PROJECT_MAP.md` for all detailed architecture/development context.

**`docs/PROJECT_MAP.md`** — Added to §7 (Build System Notes):
- Spotless rules detail
- Snapshot flag note
- "Key version pins" subsection (verified against `gradle.properties`)
- "Key Gradle tasks" table

Added new §8 (Known Open Questions):
- No JMH benchmarks for hot-path code
- NeoForge has no test source set
- `compat/c2me` submodule is empty/uninitialized

**`docs/goals/README.md`** — Added index entry for this goal.

**`docs/goals/GOAL_20260508_204601_PR46_Knowledge_Base_Slimdown.md`** — Status set to `COMPLETED`, response written.

### Verification

```sh
# Confirm Section 7 is slimmed down
grep -n "Section 7" docs/KNOWLEDGE_BASE.md

# Confirm PROJECT_MAP now has version pins and build tasks
grep -n "Key version pins\|Key Gradle tasks\|Known Open Questions" docs/PROJECT_MAP.md

# Confirm goals README updated
grep "PR46_Knowledge_Base_Slimdown" docs/goals/README.md
```

### PR comment

A comment was added to PR #46 summarizing the change.
