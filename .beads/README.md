# Terrasect task tracking

Beads is the shared task record for all project agents. Start with `bd prime`,
`bd status`, and `bd ready`. See [the workflow](../docs/AGENT_WORKFLOW.md) for
claiming work, handoffs, agent setup, worktrees, and backups.

GitHub issues are imported through `bd github` using a token retrieved from gh
for each operation. The live Dolt database is local and ignored by Git.
Configuration and agent hooks are tracked; source commits do not contain the
tasks. Dolt remote replication is disabled until a shared remote is chosen.
