## Task
**Date:** 20260620
**Submitted By:** Hermes orchestrator
**Status:** PENDING

### Request
Just like we have a pr for mob constraints origin/feature/mob-spawn-constraints-implementation we need to create a separate worktree and pr for loot generation manipulation, the Minecraft has a loot table and its probably position based so our goal is to use that position and influence the generated loot based on the region constraints, delegate to claude code to start a new work tree, unpack Minecraft and begin investigating loot generation code in Minecraft

### Context
- Project: Terrasect Minecraft mod
- Repo root: /home/alex/terrasect/.worktrees/loot-generation-manipulation
- Base branch: reborn
- New worktree branch: feature/loot-generation-manipulation
- Existing related PR: #56, feature/mob-spawn-constraints-implementation, for the mob constraints runtime slice
- The goal of this task is investigation and setup, not implementation yet
- We want a separate workspace/worktree and a separate PR for loot generation manipulation
- The investigation should begin by unpacking Minecraft and locating the relevant loot generation code paths
- Likely areas of interest: loot table selection, world/position-sensitive loot generation, region-constraint integration points, and any data available at generation time that can be used to bias loot
- Keep durable progress, findings, blockers, and next steps in this goal file

### Acceptance criteria
- A dedicated worktree exists for the loot-generation task
- Claude Code has been tasked against that worktree to begin investigation
- Minecraft has been unpacked or the exact unpack command/result has been recorded
- The goal file contains a concise investigation summary with the relevant classes/files and the next likely implementation path
- A PR exists or has been prepared for the new branch/worktree
- The goal file status is updated to COMPLETED or BLOCKED with a clear blocker

### Run metadata
- Provider: Claude Code (delegated)
- Session id: TBD
- Retry count: 0
- Retry after: TBD
- Blocked reason: TBD

---
**Sub-agent instruction:** This file contains everything you need. Read it in full. Perform the work. Write your complete response under `## Response` below before finishing. Update the `Status` field to `COMPLETED` when done. If broader protocol understanding is needed, read `docs/KNOWLEDGE_BASE.md`.

## Response
<!-- Sub-agent: write your full response here -->
**Completed by:**
**Date:**

[Response content]
