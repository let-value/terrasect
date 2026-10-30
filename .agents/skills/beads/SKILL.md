---
name: beads
description: Use Beads for durable project tasks, dependencies, blockers, handoffs, shared work memory, and GitHub issue synchronization with runtime gh credentials.
---

# Beads

Use Beads as the sole shared project task and lifecycle record. Store project
plans, ownership, blockers, verification, and handoffs in issues; use
`bd remember` for durable project memory. Every executor follows this workflow.

## First Step

Run:

```bash
bd prime
```

If that prints nothing, check whether the repository has an active Beads workspace:

```bash
bd where
```

## Preferred Route

Use the `bd` CLI when shell access is available. It is the most compact and direct Beads interface.

## Core CLI Workflow

1. Find work:

```bash
bd ready
bd list --status=open
bd list --status=in_progress
```

2. Inspect before editing:

```bash
bd show <id>
```

3. Claim work atomically:

```bash
bd update <id> --claim
```

4. Create durable follow-up work when implementation reveals new tasks:

```bash
bd create "Short title" --description="Why this exists and what needs to be done" --type=task --priority=2
```

5. Close completed work:

```bash
bd close <id> --reason="Completed"
```

## What Belongs In Beads

Use Beads for:

- shared project tasks
- blockers and dependencies
- discovered follow-up work
- work that must survive thread reset, compaction, or handoff
- status that another person or agent should be able to resume

Use agent-local planning tools only for the current turn's execution checklist. Do not treat them as shared project state.

## Rules

- Do not create markdown TODO files as the source of truth when Beads is available.
- Do not use `bd edit`; it opens an interactive editor. Use `bd update` flags instead.
- Prefer `--json` when parsing `bd` output programmatically.
- If hooks are installed, `bd prime` may already be injected. Run it manually when context is missing.
- Do not auto-close or mutate tasks unless the work is actually complete.

## GitHub issue synchronization

For Terrasect, retrieve the token from gh for each command:

```bash
GITHUB_TOKEN="$(gh auth token --hostname github.com)" GITHUB_REPOSITORY=let-value/terrasect bd github sync --pull-only
```

Pass the token only through the command's environment. Never print the token, put it in command
arguments, or persist it in Beads configuration, files, or shell profiles.
The existing gh login remains managed by gh.

Default to `status` or `--dry-run` when investigating. A bare `sync` is
bidirectional and can publish local tasks and change remote issues; use the
direction and issue selection authorized by the user. GitHub issue sync is
separate from Dolt database replication.

## Database synchronization

Terrasect shares its Dolt history through the GitHub origin under
`refs/dolt/data`. Run `bd bootstrap` after a fresh clone. For an existing
database, pull with `bd dolt pull` before starting work and publish durable
updates with `bd sync`. Native auto-push is enabled, with its default five-minute
throttle. Claude and Codex hooks also run `bd sync` at session start and turn end,
so they pull remote changes and flush pending updates. Hook sync temporarily
disables native auto-push to avoid a redundant push. Worktrees on one machine
already share the local database and need no separate initialization.

Pull/push synchronize separate replicas; claims on different machines are not
a global lock. Coordinate task ownership across machines and stop on merge
conflicts rather than force-pushing or discarding another agent's changes.
