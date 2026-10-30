# Shared task workflow

Beads (`bd`) is the source of record for Terrasect tasks, dependencies, decisions,
and handoffs. Every agent uses the same issue database and lifecycle, regardless
of provider. GitHub issues are imported through `bd github`. Planning, ownership,
execution status, verification, and handoffs belong in Beads.

## Start a session

Install Beads with `brew install beads` on macOS. In this initialized checkout, run:

```sh
bd prime
bd status
bd ready
```

Read `AGENTS.md` for project rules. `CLAUDE.md` and `GEMINI.md` point to that same
file. Project hooks load Beads context for Claude and Codex; restart existing
sessions after setup. Agents with shell access use the CLI; no MCP server is
required for this workflow.

For a new clone without a database, initialize with:

```sh
bd init --prefix terrasect --server --skip-agents --non-interactive
bd config set dolt.local-only true
bd dolt remote remove origin
bd setup claude
bd setup codex
```

Initialization may create a local Git commit. This creates a new empty database,
not a copy of another checkout's tasks. Restore a Dolt backup when continuing an
existing project database on another machine.

## Work on a task

Create a ticket for new work, including its scope and acceptance criteria:

```sh
bd create "Describe the outcome" --type task --priority 2 \
  --description "Context, scope, and relevant files" \
  --acceptance "Observable conditions for completion"
bd show <id>
bd --actor codex-session-name update <id> --claim
```

Use a distinct actor per concurrent agent/session, for example with `BEADS_ACTOR`.
A claim conflict means another executor owns the task; do not force it away.
For longer work, maintain the claim with `bd heartbeat <id>`. Release abandoned
claims with `bd unclaim` or inspect `bd reclaim --help` before recovery.

Record blockers with `bd dep add <task> <blocker>`. Put the implementation plan,
workspace path, branch, PR, verification results, and next action in the ticket:

```sh
bd update <id> --append-notes "Handoff: workspace, branch/PR, progress, checks, next action"
```

Record any working directory or worktree in the issue. Git worktrees share the
main checkout's Beads database through Git common-directory
discovery. Do not initialize a separate database in each worktree. Run `bd where`
to verify the resolved location. Existing worktrees need the new instruction and
hook files in their checked-out revision, or agents must read them from main.

Close only after the acceptance criteria and relevant checks pass. Record the
result and any remaining work in Beads before handing the task to another agent.

```sh
bd close <id> --reason "Outcome and verification evidence"
```

Do not duplicate tasks in Markdown queues or goal files. Keep architectural
reference material in project docs and link it from tickets. Switching executors
means updating the same ticket with a handoff, not creating another task record.

## Storage and recovery

For GitHub issue operations, retrieve the token from the current `gh` login for
each command without storing it in Beads:

```sh
GITHUB_TOKEN="$(gh auth token --hostname github.com)" GITHUB_REPOSITORY=let-value/terrasect bd github sync --pull-only
```

Plain `bd github sync` is bidirectional. This does not enable Dolt replication.

The local Dolt server supports concurrent clients. `.beads/metadata.json` and
`.beads/config.yaml` describe the setup; `.beads/dolt/` is ignored by Git and
contains the live database. Git commits of source/configuration do not back up
issues. JSONL exports are interchange files, not full database backups.

Remote synchronization is disabled for this initial local setup. Do not assume
another machine or a fresh clone has these tasks. Configure a shared Dolt remote
explicitly before enabling cross-machine sync. Do not commit or push source or
tracker data without authorization.

This machine has a local backup at
`/Users/alex/.local/share/beads/backups/terrasect`; the initial backup was verified.
Automatic backups are off in server mode. Refresh it with `bd backup sync` after
task updates.
To configure a destination on another machine:

```sh
bd backup init /absolute/path/outside/the/checkout
bd backup sync
bd backup status
```

Use `bd backup restore --help` for recovery. A local backup protects against
checkout loss, but an off-machine backup is still needed for machine loss.

Verify the installation with `bd doctor --server`, `bd setup codex --check`, and
`bd setup claude --check`. Claude's check may warn about a missing managed section
because `CLAUDE.md` is a symlink; the shared Beads instructions are in `AGENTS.md`.
