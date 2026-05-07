# TODO

Scratchpad for the orchestrator. One line per item.

**How to process an item:**
1. Pick any item from the list below.
2. Create a goal file for it in `docs/goals/` following the template in `docs/KNOWLEDGE_BASE.md`.
3. Delegate it: tell a sub-agent "Read `docs/goals/GOAL_[name].md` and write your response there."
4. Wait for the goal file `Status` to become `COMPLETED`.
5. Delete the line from this file.

Do not mark items done here — just delete them when the goal is completed.

---

- Add Sub-Agent Contract section to KNOWLEDGE_BASE.md (what every sub-agent must do regardless of who invoked it)
- Add state machine transition rules to KNOWLEDGE_BASE.md (PENDING → CLAIMED → IN_PROGRESS → COMPLETED/FAILED)
- Add concurrency guard rule to KNOWLEDGE_BASE.md (agent must check status is PENDING before claiming a ticket)
- Fix KNOWLEDGE_BASE.md section numbering (Section 2 header missing, Usage Reporting buried in Section 1)
- Update GOAL_20260507_0939_Workflow_Review.md — embed full KNOWLEDGE_BASE content into Context section (currently just references the file)
