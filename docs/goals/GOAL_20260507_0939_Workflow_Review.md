## Task
**Date:** 2026-05-07
**Submitted By:** Hermes Agent
**Status:** COMPLETED

Review the newly defined "Model Runner Evaluation Workflow" documented in `terrasect/docs/KNOWLEDGE_BASE.md`. Provide a critical analysis pointing out logical gaps, concurrency hazards, missing error paths, or deviations from best practices in complex agent orchestration. Feedback must be structured and actionable.

---
**Sub-agent instruction:** Read `terrasect/docs/KNOWLEDGE_BASE.md` in full. Perform the review. Then append your complete response under the `## Response` section below before finishing. Do not summarize — write the full output there so it is preserved for future sessions.

## Response
**Completed by:** claude-sonnet-4-6 (Claude Code)
**Date:** 2026-05-07

### Critical Analysis: Model Runner Evaluation Workflow

---

#### 1. The Workflow Is a Linear Sequence, Not a State Machine (High Severity)

The current procedure describes steps 1→2→3→4 as if they always execute cleanly. Real orchestration fails mid-sequence: a provider returns a partial result, a file write is interrupted, or a sub-agent times out after starting work. There is no concept of "current state" that survives failure.

**Gap:** If Claude Code starts a task and crashes, the orchestrator has no way to know whether to retry, resume, or escalate. The ticket will remain `PENDING` forever.

**Recommendation:** Model the workflow as an explicit state machine with persisted state in the ticket file:
```
PENDING → CLAIMED → IN_PROGRESS → COMPLETED
                              ↘ FAILED → RETRYING | ESCALATED
```
The sub-agent must update `Status:` in the ticket at each transition. The orchestrator polls status rather than assuming linear completion.

---

#### 2. Failures Produce No Actionable Output (High Severity)

The fallback chain (Claude → Codex → Copilot → Ollama) specifies what to do when a provider is "unavailable" but never defines what "unavailable" means or what information gets passed to the next provider.

**Gap:** If Claude Code fails due to a rate limit vs. a malformed prompt vs. a tool permission error, these require completely different responses. The next agent in the chain receives no information about why the previous one failed.

**Recommendation:** Require every agent handoff to produce a structured error object written to the ticket before escalating:
```markdown
## Handoff
**From:** claude-sonnet-4-6
**Reason:** rate_limit_exceeded
**Retry after:** 2026-05-07T17:30:00Z
**Partial output:** [any work completed]
**Next agent:** codex
```
The next agent reads this and knows exactly what happened and what (if anything) was already done.

---

#### 3. No Human Gate Between Planning and Execution (Critical)

The workflow goes directly from "select a provider" to "delegate the entire task." For non-trivial development tasks, this means a sub-agent can make irreversible changes (file edits, commits, deployments) without any human review of the plan.

**Gap:** This is the "Commitment Fallacy" — the orchestrator commits to a full execution path based on a single availability check, with no checkpoint for the human to redirect.

**Recommendation:** Insert a mandatory plan-review step for any task that touches the codebase:
1. Sub-agent produces a plan (no writes yet) and writes it to the ticket under `## Plan`.
2. Orchestrator pauses and surfaces the plan to the human.
3. Human approves, rejects, or amends.
4. Only after explicit approval does the sub-agent execute.

This gate should be skippable only for read-only tasks (analysis, queries, reporting).

---

#### 4. The Ollama Fallback Has No Sanity Check (Medium Severity)

The fallback to local Ollama is triggered when all paid services are unavailable. But "unavailable" covers a wide range: temporary rate limits, expired API keys, network outages, misconfigured credentials, or the task being fundamentally too complex for local inference.

**Gap:** Falling back to Ollama for a task that requires deep Minecraft worldgen knowledge and codebase context will silently produce low-quality or wrong output, with no signal to the human that this happened.

**Recommendation:** Before engaging Ollama, the orchestrator must categorize the failure:
- **Temporary** (rate limit, transient network): wait and retry the primary provider.
- **Systemic** (invalid key, quota exhausted for billing period): escalate to human.
- **Task complexity mismatch**: warn the human that Ollama may not be adequate for this task and request confirmation before proceeding.

---

#### 5. Structural Issues in KNOWLEDGE_BASE.md (Low Severity)

- "Mandatory Usage Reporting" is embedded inside Section 1 with no heading, making it invisible when skimming. Needs its own `### 4. Post-Task Reporting` section.
- Section numbering skips: goes from `### 1` to the provider list to `### 3` — `### 2` is missing entirely.
- The entire document is addressed to the orchestrator. Sub-agents reading a ticket have no instructions. The KNOWLEDGE_BASE needs a dedicated "Sub-Agent Contract" section describing what every sub-agent is required to do regardless of which orchestrator sent the task.

---

#### 6. Concurrency Is Unaddressed (Medium Severity)

Nothing prevents the orchestrator from spawning two agents against the same ticket simultaneously (e.g., retrying while the first agent is still running). Both would append to the `## Response` section, producing garbled output.

**Recommendation:** The `CLAIMED` status transition (see point 1) acts as a soft lock. Add a rule: an agent MUST check that status is `PENDING` before claiming a ticket. If status is already `CLAIMED` or `IN_PROGRESS`, it must not proceed and must notify the orchestrator.

---

#### Summary

| Issue | Severity | Fix |
|---|---|---|
| Linear sequence, no state persistence | High | Explicit state machine with ticket status transitions |
| Failures carry no structured context | High | Mandatory `## Handoff` block before escalating |
| No human gate before execution | Critical | Plan-then-approve step for all codebase-touching tasks |
| Ollama fallback is unconditional | Medium | Categorize failure before triggering fallback |
| KNOWLEDGE_BASE structure is broken | Low | Fix numbering, extract Usage Reporting section, add Sub-Agent Contract |
| Concurrent agent writes unguarded | Medium | Status-based soft locking |

