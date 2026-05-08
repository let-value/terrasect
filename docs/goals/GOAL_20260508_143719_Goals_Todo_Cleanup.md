## Task
**Date:** 20260508
**Submitted By:** Hermes Agent
**Status:** COMPLETED

### Request
Now let's cleanup terrasect goals folder and todo.md file, clearly define what was already done and what is still needs to be done

### Context
Workspace root: `/home/alex/terrasect`.

Relevant files inspected before cleanup:
- `/home/alex/terrasect/docs/TODO.md`
- `/home/alex/terrasect/docs/KNOWLEDGE_BASE.md`
- `/home/alex/terrasect/docs/PROJECT_MAP.md`
- `/home/alex/terrasect/docs/goals/GOAL_20260507_0939_Project_Map.md`
- `/home/alex/terrasect/docs/goals/GOAL_20260507_0939_Workflow_Review.md`

Observed state before cleanup:
- `TODO.md` contained only process instructions and no active TODO lines.
- `GOAL_20260507_0939_Workflow_Review.md` was `Status: COMPLETED` and contains a substantive review of the orchestration workflow.
- `GOAL_20260507_0939_Project_Map.md` was `Status: COMPLETED` but its header had been degraded by earlier agent output (`... Initial context details are assumed ...`). The project map artifact exists at `docs/PROJECT_MAP.md`.
- No `GOAL_Project_Phoenix_Revival_20260507.md` file exists in `docs/goals`; that earlier reference was hallucinated/noise and should not be represented as real work.
- The unresolved real Terrasect work is to update the project map with explicit architecture separation and SDF usage for region boundaries.

### Acceptance criteria
- `docs/TODO.md` should clearly show the active pending work and avoid listing completed work as TODO items.
- `docs/goals/README.md` should summarize what goal files exist, what is completed, and what still needs a new goal.
- No hallucinated Project Phoenix goal should be created or referenced as real work.
- This cleanup goal should document exactly what was changed.

---
**Sub-agent instruction:** This cleanup was performed directly by Hermes Agent because the user requested cleanup of local documentation state. The goal file records the work and is already marked `COMPLETED`.

## Response
**Completed by:** Hermes Agent
**Date:** 20260508

Cleanup performed:
- Created this goal file to record the cleanup request and outcome.
- Added/updated `docs/goals/README.md` as the goals-folder index.
- Rewrote `docs/TODO.md` so it contains one clear pending item: update `docs/PROJECT_MAP.md` with explicit architecture separation and SDF region-boundary explanation.
- Preserved the two existing completed goal files instead of deleting them.
- Explicitly noted that the earlier `Project Phoenix Revival` file does not exist and was hallucinated/noise, not a real Terrasect goal.
