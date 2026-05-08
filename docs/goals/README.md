# Terrasect Goals Index

This folder stores durable goal files used by the Terrasect orchestration workflow. Goal files are the source of truth for delegated or documented work.

## Completed goals

### `GOAL_20260507_0939_Workflow_Review.md`
- Status: `COMPLETED`
- Outcome: Reviewed the orchestration/model-runner workflow and identified gaps around state transitions, provider handoff, concurrency, and failure handling.
- Follow-up incorporated into `docs/KNOWLEDGE_BASE.md`: explicit state machine, handoff blocks, sub-agent contract, verification rules, and provider-chain behavior.

### `GOAL_20260507_0939_Project_Map.md`
- Status: `COMPLETED`
- Outcome: Produced the first Terrasect project-map draft and wrote the artifact to `docs/PROJECT_MAP.md`.
- Caveat: the goal file header/context was degraded by an earlier agent write and contains placeholder text. Treat `docs/PROJECT_MAP.md` as the readable artifact, and treat this goal file as historical evidence of the first project-map pass.

### `GOAL_20260508_143719_Goals_Todo_Cleanup.md`
- Status: `COMPLETED`
- Outcome: Cleaned up the goals-folder state and rewrote `docs/TODO.md` to clearly separate completed work from pending work.

### `GOAL_20260508_195652_Explore_Project_Context_Best_Practices.md`
- Status: `COMPLETED`
- Outcome: Claude Code explored the repository and wrote a grounded development-context report covering module layout, versioning, build/test tasks, hot-path rules, future-agent best practices, and orchestrator guidance.
- Follow-up incorporated into `docs/KNOWLEDGE_BASE.md`: new Section 7 — Terrasect Development Context.

## Not real goals / hallucinated references

### `GOAL_Project_Phoenix_Revival_20260507.md`
- Status: does not exist.
- This was an erroneous/hallucinated earlier reference and is not part of Terrasect work.
- Do not recreate it unless the user explicitly asks for an unrelated Project Phoenix task.

## Still needs work

### Project map architecture/SDF update
- Create a new goal file before execution.
- Intended task: update `docs/PROJECT_MAP.md` with:
  - explicit architecture separation between `common`, `fabric`, `neoforge`, and `versions`;
  - clear ownership boundaries for shared logic vs loader-specific integration;
  - a grounded explanation of how SDFs are or should be used for region boundaries;
  - correction/removal of unsupported claims from the first project-map draft after inspecting the actual code.
- This item is tracked in `docs/TODO.md`.
