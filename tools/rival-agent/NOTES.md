# run-rival-agent — design notes

Why the pairing has the shape it has. This is for someone changing it, not running it, and is
deliberately not linked from `SKILL.md`. The tooling was built and measured in the Splotch
repository and ported here; the measurements below are from there.

Vocabulary: the **native handler** is the agent running in the current runner and holding its
permissions; the **rival agent** is the other vendor's CLI in a disposable worktree. Profile rungs,
a broker allowlist or denylist, and a command menu were each proposed and rejected as a second policy
engine.

## Why a handler and a rival, not a read-only reviewer

A read-only reviewer could read and run `git`, but could not run a build or a repro, so every
behavioural claim it made was an assertion. The pairing keeps the rival inside Codex's sandbox and
adds one door: a broker MCP server whose single tool asks the agent that launched it to run a
command. The handler already has a permission system (Claude's permission mode, deny rules, the
auto-mode classifier), so the broker holds no policy at all: it writes the request to a spool and
blocks; the handler runs the command verbatim or declines with a reason, and the answer flows back
as data. A request the handler would refuse for itself is refused for the rival.

On a seeded-defect bench (twelve seeds, two repetitions), the sandbox-first hybrid found 18/18 seeds
with zero control false positives and needed the handler for nothing, at the same wall clock and
token cost as an every-command-through-the-handler pairing that found 17/18. So the rival runs its
own commands first and escalates only what the sandbox refuses.

## Launch pins that each closed a real escape

* `--ignore-user-config`: a `-c mcp_servers=…` override merges into the configured table, so the only
  way to leave the user's MCP servers behind is to leave the config behind. The configured `model`
  is read back and passed as `-m`.
* `default_tools_approval_mode="approve"` on the broker: MCP calls are auto-rejected under
  `approval_policy="never"` otherwise.
* `approval_policy="never"`: with on-request approval Codex escalates out of the sandbox, and a
  configured auto-reviewer can approve it with no human in the loop.
* `--disable apps …`: the built-in `apps` server exposes GitHub write tools; a rival once posted a
  review to its own PR through it.
* `codex exec resume` rejects `-C` and filters threads by directory, so resume drops `-C` and passes
  `--all` (every round has a fresh worktree).
* MCP tool timeouts are raised to the one-hour pending-request budget, since a brokered command waits
  for a handler turn plus the command.

## Accepted exposures

Accepted under the premise that every diff here is authored by the owner or the owner's own agent
sessions.

* **The findings document is an outbound channel.** Codex's sandbox reads the whole disk
  (`~/.codex/auth.json` included), and findings are posted verbatim, so a prompt injected through a
  diff could copy a credential into a finding. The poster's shape check catches common credential
  forms; read a rival's findings before trusting the post. The real fix, if ever needed, is a
  credential-free account or container for the rival.
* **The `/tmp` spool root on Linux.** Codex's workspace-write sandbox allows writes under `$TMPDIR`
  and `/tmp`. On macOS the rival's private `TMPDIR` inside its session removes the spool root
  (`os.tmpdir()/pogo-paste-rival-agent/`) from its writable set. On Linux `os.tmpdir()` is `/tmp`,
  so a sandboxed rival there could forge a request into a sibling session; a forged request is still
  judged by the handler before anything runs.
* **Web search stays on.** It cannot write, and with the findings already an outbound channel,
  pinning it off closes nothing and costs the reviewer vendor documentation.

## pogo-paste specifics

* The worktree installs Node dependencies with `npm ci --ignore-scripts` when a lockfile exists. A
  PR-controlled install script would otherwise run on the handler's machine before anyone read the
  diff.
* The Android Gradle project is never built at launch. Gradle needs the network and writes
  `~/.gradle`, neither of which the sandbox allows, so a rival that wants `./gradlew` asks through
  the broker and the handler decides.

## Unvalidated

* The ten-minute stall budget and the one-hour pending-request budget are set by argument, not
  measurement.
* The posting identity: reviews post as the user's `gh` account with a hidden marker naming the
  rival and range.
