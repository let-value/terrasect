# Agent Delegation Workflow

Operational reference for how Hermes orchestrates delegated task execution via
`docs/TODO.md` and `docs/goals/`. For project architecture facts, see
[`docs/PROJECT_MAP.md`](PROJECT_MAP.md).

---

## Section 0 — Separation of Concerns

Terrasect uses two coordination layers with different purposes:

### Human ↔ Hermes coordination: Kanban

Use the Hermes Kanban board for work tracking between Alexander and Hermes:

- Board: `terrasect`
- Tenant: `terrasect`
- Workspace: `dir:/home/alex/terrasect`
- Default skill for worker context: `terrasect`
- Workers must verify that `terrasect` actually loaded before making changes; if the skill is missing, treat the run as blocked/misconfigured and stop instead of improvising from generic context.

Kanban items are high-level, human-visible work packets: investigation tracks, environment setup, workflow design, development milestones, review gates, and future agentic-development planning. Kanban is the durable queue for "what Alexander and Hermes are coordinating next."

Backlog/planning cards should be kept `blocked` (or otherwise non-ready) until Alexander and Hermes intentionally choose to execute them. Ready cards assigned to an on-disk profile may be picked up by the gateway dispatcher automatically.

### Hermes ↔ external-agent delegation: `docs/TODO.md` + `docs/goals/`

Use repository docs for external-agent task execution:

- `docs/TODO.md` is the external-agent scratch queue.
- `docs/goals/GOAL_[YYYYMMDD]_[HHMMSS]_[ShortDescription].md` files are self-contained task contracts.
- External agents must read the goal file, perform the work, write the complete response back into the goal file, and set `Status: COMPLETED`.
- `docs/TODO.md` lines are deleted only after the corresponding goal file is verified as `COMPLETED`.

Do not use `docs/TODO.md` as the Alexander↔Hermes planning board. Do not use Kanban as a substitute for self-contained external-agent goal files when delegating to providers or sub-agents.

### Orchestrator-first worker model

Terrasect coordination is orchestrator-first:

- Hermes in this topic acts as an orchestrator, not a direct implementer by default.
- Future Kanban workers should also understand themselves as orchestrators unless a task explicitly assigns hands-on implementation.
- Orchestrator workers should manage task context, create/maintain goal files, decompose work, delegate to the agreed provider chain, verify goal-file completion, and report concise handoffs.
- All durable communication with delegated agents belongs in the relevant `docs/goals/GOAL_*.md` file, not only in inline chat/process output.
- Delegation prompts should be minimal once the goal file exists: ask the agent to read the goal file, perform the work, write its complete response into the same file, and set `Status: COMPLETED`.
- Claude-backed tasks should use a dedicated workspace/worktree distinct from the orchestrator's transient checkout. Record that workspace path in the goal file, reuse the same workspace when the topic is revisited, and keep both the workspace and the PR alive until the task is explicitly closed.

---

## Section 1 — Goal Capture and Documentation

Every task starts with a goal file. Goal files live in `docs/goals/`.

**File name format:** `GOAL_[YYYYMMDD]_[HHMMSS]_[ShortDescription].md`

**The goal file is the single source of truth for the task.** It must be fully self-contained — a sub-agent handed only this file must be able to understand the task completely and execute it without needing clarification from the orchestrator. This means the orchestrator must embed all necessary context directly in the file at creation time:

- The full user request (unmodified)
- **The absolute path to the workspace root** (e.g. `/home/alex/terrasect`) — sub-agents run in isolated terminal sessions with no knowledge of the project location. For Claude Code-backed work, use the dedicated workspace/worktree path rather than a one-off branch checkout.
- Background context required to understand the task (relevant prior decisions, constraints, affected files, related goals)
- Acceptance criteria or expected output shape
- Any constraints the sub-agent must respect

Because the goal file is exhaustive, **delegation is trivial**: the orchestrator simply tells the sub-agent to read the file and write its response there. No additional context needs to be passed at invocation time.

**Goal file template:**
```markdown
## Task
**Date:** [YYYYMMDD]
**Submitted By:** [Orchestrator name]
**Status:** PENDING

### Request
[Full user prompt, unmodified]

### Context
[Everything the sub-agent needs to understand the task:
background, prior decisions, affected files, related goals, constraints.
Be exhaustive — nothing relevant should live only in the orchestrator's memory.]

### Acceptance criteria
[What a correct, complete response looks like]

### Run metadata
[Provider name, conversation/session id, retry_count, retry_after, blocked_reason if the run stopped because of limits]

When Claude Code is the provider and you need a reliable conversation id, prefer print mode with `--output-format json` and store the returned `session_id` here rather than trying to infer it from prose output.

---
**Sub-agent instruction:** This file contains everything you need. Read it in full. Perform the work. Write your complete response under `## Response` below before finishing. Do not summarize — write the full output so it is preserved for future sessions. Update the `Status` field to `COMPLETED` when done. If broader protocol understanding is needed, read `docs/AGENT_WORKFLOW.md`.

## Response
<!-- Sub-agent: write your full response here -->
**Completed by:** [agent/model name]
**Date:** [date]

[Response content]
```

**Delegation goal (what the orchestrator passes when invoking an agent):**

```
Read docs/goals/GOAL_[name].md in full. Perform the work described in it.
Then write your complete response into that file under the ## Response section.
Set Status: COMPLETED when done.
Do NOT return the response as a summary message — write it to the file.
Your only reply to me should be: "Written to goal file."
```

**After delegation — mandatory verification:**

The orchestrator MUST verify the file was actually written:

1. **Read the goal file** after the sub-agent returns.
2. **If `Status` is `COMPLETED` and `## Response` is populated** → success. Proceed to close the TODO item.
3. **If the response came back inline** (goal file unchanged) → the orchestrator must copy the inline response into the `## Response` section of the goal file, then set `Status: COMPLETED`.
4. **Never assume the file was updated.** Always verify by reading it.
5. **Never mark a task done based on what the sub-agent said in chat.** The goal file is the source of truth — if the file does not say `COMPLETED`, the task is not done.

---

## Section 2 — Provider Chain Execution

Providers cannot be queried for limits before invocation. Do not attempt pre-flight checks. Instead, invoke the highest-priority provider and interpret its response. The chain is:

**Claude Code (real delegated work) → Codex (Hermes-direct execution) → Copilot → Ollama (local fallback)**

For each provider in order:

1. **Invoke** the provider with the task.
2. **Interpret the response** using the rules below.
3. **Act** based on the outcome — either accept the result or advance to the next provider.

### Response interpretation rules

| Response type | How to detect | Action |
|---|---|---|
| **Success** | Agent writes a substantive response to the ticket | Accept. Set ticket `Status: COMPLETED`. Stop the chain. |
| **Limit exhausted** | Agent says it has no quota, is rate-limited, or refuses with a usage/billing message | Append a single log line to the ticket (see format below), record the provider session/conversation id when available, set `Status: BLOCKED`, and advance to the next provider only if another provider is available. |
| **Partial / error** | Agent produces incomplete output, tool failures, or an explicit error that is not a limit message | Write a structured `## Handoff` block (see format below). Keep the ticket retryable only if the failure is transient; otherwise set `Status: FAILED`. Advance to the next provider so it can recover from known state. |
| **All providers failed** | Chain exhausted with no success | If the failure was limit exhaustion, leave the ticket `BLOCKED` with retry metadata; otherwise set `Status: FAILED`. Surface to human for manual intervention. Do not silently discard. |

### Limit exhaustion log line (append to ticket body)

```
- [YYYYMMDD HH:MM] <provider> skipped: limit exhausted
```

For retry bookkeeping, also preserve the provider's conversation/session id in the goal file or ticket metadata when the CLI exposes it.

### Handoff block (append before advancing on partial/error failure)

```markdown
## Handoff
**From:** [provider name]
**Reason:** [one-line description — e.g., tool_permission_denied, timeout, parse_error]
**Partial output:** [paste any useful partial work, or "none"]
**Next provider:** [name]
```

The next provider MUST read the `## Handoff` block before starting so it understands what was already attempted and what failed.

### Ollama fallback guard

Before invoking Ollama, the orchestrator must classify why the chain reached this point:

- **All providers hit limits:** Proceed with Ollama but add a warning in the ticket that output quality may be reduced.
- **All providers errored (not limits):** This is likely a task or environment problem, not a provider problem. Surface to human before invoking Ollama — local inference will probably fail for the same reason.

---

## Section 3 — Ticket State Machine

Every goal file has a `Status` field. Valid transitions:

```
PENDING → CLAIMED → IN_PROGRESS → COMPLETED
                 ├──→ BLOCKED
                 └──→ FAILED

BLOCKED ──(cron recovery after retry_after)──→ PENDING
```

| Status | Who sets it | When |
|---|---|---|
| `PENDING` | Orchestrator | At file creation |
| `CLAIMED` | Sub-agent | Immediately upon starting work |
| `IN_PROGRESS` | Sub-agent | When actively writing the response |
| `COMPLETED` | Sub-agent | When the full response is written |
| `BLOCKED` | Sub-agent or Orchestrator | On transient failures such as provider limit exhaustion; preserve retry metadata for cron recovery |
| `FAILED` | Sub-agent or Orchestrator | On unrecoverable error |

**Concurrency guard:** Before a sub-agent sets `CLAIMED`, it MUST read the current `Status` value. If `Status` is not `PENDING`, the ticket is already owned — stop immediately and do not proceed. Report the conflict to the orchestrator.

**The orchestrator never sets `CLAIMED`, `IN_PROGRESS`, or `COMPLETED`.** Those transitions belong to the sub-agent. The orchestrator only creates tickets (`PENDING`) and handles `FAILED`/`BLOCKED` outcomes.

**The orchestrator may set or preserve `BLOCKED`** when a provider run stops due to limits, but it must also record the provider session/conversation id and retry metadata so the cron recovery job can requeue it later.

---

## Section 4 — Sub-Agent Contract

Every sub-agent, regardless of which provider it is or who invoked it, MUST do the following:

1. **Read the full goal file** before doing any work. Do not start from the delegation message alone.
2. **Set `Status: CLAIMED`** in the goal file immediately upon starting.
3. **Set `Status: IN_PROGRESS`** when actively writing the response section.
4. **Write the complete response** into the `## Response` section of the goal file. Do not return the response inline to the orchestrator. Do not summarize. Write the full output.
5. **Set `Status: COMPLETED`** when done. This is the signal that the orchestrator uses to detect completion.
6. **Never leave the file in `IN_PROGRESS`** if an error occurs — set `FAILED` and write the reason.

A sub-agent that returns its answer in the chat instead of in the goal file has failed its contract. The response will be lost between sessions.

---

## Section 5 — Orchestrator Rules

The orchestrator is responsible for coordination only. It does not do the work itself.

**Mandatory steps for every task:**
1. Create a goal file in `docs/goals/` using the template in Section 1.
2. Populate the file fully — request, context, acceptance criteria.
3. Invoke the first provider in the chain with the delegation message.
4. Monitor the goal file's `Status` field for `COMPLETED` or `FAILED`.
5. On `COMPLETED`: delete the item from `docs/TODO.md`.
6. On `FAILED`: surface to human. Do not silently retry with the same prompt.

### Transient provider-limit recovery

When a provider stops because of limits/quota exhaustion:

1. Record the provider name and the provider's conversation/session id in the goal file when available.
2. Set `Status: BLOCKED` and add `blocked_reason`, `retry_count`, and `retry_after` metadata.
3. Do not keep re-running the same prompt manually.
4. Let the cron recovery job (`kanban-limit-recovery-scan`) scan for eligible blocked items and move them back to `PENDING` when usage is expected to be available again.
5. Keep non-retryable failures as `FAILED`.

**What the orchestrator must NOT do:**
- Do the task itself instead of delegating.
- Ask the user for confirmation before each mechanical step.
- Pass context to the sub-agent in the chat message — everything goes in the goal file.
- Mark TODO items as `IN_PROGRESS` — only delete them when the goal file reaches `COMPLETED`.
- Invoke a provider without first creating the goal file.
- Accept a response that came back inline (in chat) instead of written to the goal file.
- Throw away the task's dedicated workspace/worktree or close the PR between visits; keep both alive until the user explicitly says the topic is done.

---

## Section 6 — User Override

If the user explicitly specifies a provider (e.g., "Use Copilot for this", "Only use Ollama"), skip directly to that provider. Still apply the response interpretation rules and write outcomes to the ticket.

*Compliance with this workflow is mandatory for all future task initiations.*
