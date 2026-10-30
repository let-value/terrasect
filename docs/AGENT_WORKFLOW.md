# Shared task workflow

Beads (`bd`) is the source of record for Terrasect tasks, dependencies, decisions,
and handoffs. Every agent uses the same issue database and lifecycle, regardless
of provider. GitHub issues are imported through `bd github`. Planning, ownership,
execution status, verification, and handoffs belong in Beads.

## Start a session

Install Beads with `brew install beads` on macOS. In this initialized checkout, run:

```sh
bd prime
bd dolt pull
bd status
bd ready
```

Read `AGENTS.md` for project rules. `CLAUDE.md` and `GEMINI.md` point to that same
file. Project hooks load Beads context for Claude and Codex; restart existing
sessions after setup. Agents with shell access use the CLI; no MCP server is
required for this workflow.

For a new clone without a database, retrieve the shared history with:

```sh
bd bootstrap
bd setup claude
bd setup codex
```

`bd bootstrap` retrieves the Dolt history stored under `refs/dolt/data` on the
GitHub origin. A normal `git clone` alone does not retrieve the database.
Do not run `bd init` to replace an existing shared database.

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

Plain `bd github sync` is bidirectional and changes GitHub issue records.
Database replication uses the separate Dolt commands below.

The local Dolt server supports concurrent clients. `.beads/metadata.json` and
`.beads/config.yaml` describe the setup; `.beads/dolt/` is ignored by Git and
contains the live database. Git commits of source/configuration do not back up
issues. JSONL exports are interchange files, not full database backups.

The Dolt remote is `git+https://github.com/let-value/terrasect.git`. Database
history lives under `refs/dolt/data`, separate from source branches. Pull before
starting work and push durable updates and handoffs:

```sh
bd sync
```

Native auto-push is enabled after eligible Beads commands, with the default
five-minute throttle. Claude and Codex session-start and turn-end hooks run
`BD_DOLT_AUTO_PUSH=false bd sync --no-adopt` to pull, check conflicts, repair
blocker state, and push. Sync failures surface as hook failures. Restart existing
agent sessions after changing hooks. There is no background polling: terminal-only
work and long-running sessions can use `bd sync` to refresh immediately.
Normal `git push` does not publish database changes.
Replicas on different machines are synchronized rather than one live database:
coordinate ownership before simultaneous work, and stop on merge conflicts
instead of forcing a push. Source commits and pushes still require authorization.

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
