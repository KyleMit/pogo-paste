# Trust boundary

No installation step: Claude Code runs its Bash tool on the host, so the launcher and the broker CLI
run straight from the checkout.

## Billing

Every invocation bills the ChatGPT plan's included usage rather than metered API credits, by three
independent checks in `scripts/codex-subscription-auth.mjs`:

* `~/.codex/auth.json` must report `auth_mode: "chatgpt"`, hold no stored `OPENAI_API_KEY`, and carry
  an access token. An API-key login fails the run instead of silently metering it.
* Every credential in `API_BILLING_ENVIRONMENT_KEYS` is stripped from the child environment. Codex
  prefers an inherited credential over the stored login, so one exported in the parent shell would
  otherwise move the run onto API billing. A stripped variable is named on stderr.
* `model_provider`, `cli_auth_credentials_store`, and `openai_base_url` are pinned on the command
  line, above any config file.

`npm run rival:health` runs the same guard plus `codex login status`, and treats any nonzero probe
exit as a failure.

## What the rival can do

The rival runs inside Codex's workspace-write sandbox rooted at the disposable worktree with the
network off, and reaches the handler through the broker for what that sandbox refuses. The launch
pins (`scripts/launch-codex.mjs`, `buildCodexArgs`):

* `--ignore-user-config`, so none of the user's MCP servers attach. Auth still resolves from
  `CODEX_HOME`; the configured `model` is read back and passed as `-m`.
* `mcp_servers={broker={…}}` with `default_tools_approval_mode="approve"`: exactly one server, the
  broker. `tool_timeout_sec` is raised to the one-hour pending-request budget.
* `approval_policy="never"`, so the sandbox cannot be escalated out of without you.
* `sandbox_mode="workspace-write"` rooted at the worktree, and
  `sandbox_workspace_write.network_access=false`. The worktree's `npm ci` runs with
  `--ignore-scripts`, because the reviewed commit owns `package.json`.
* `--disable apps hooks browser_use browser_use_external browser_use_full_cdp_access computer_use
  multi_agent image_generation`. `apps` exposes GitHub write tools; hooks run outside the sandbox.

Web search stays enabled. Codex's sandbox reads the whole disk, so treat anything readable as
reachable by a query or a finding.

## The sandbox

Writes inside the worktree and the rival's private `TMPDIR` (inside its session directory) succeed;
writes to the home directory (including `~/.gradle`) and the canonical checkout are refused; a commit
fails because the worktree's gitdir lives under the canonical checkout's `.git`; DNS fails. So
`npm run build` runs in the sandbox, while `./gradlew` and `adb` come to you through the broker.

The findings document is itself an outbound channel: it is posted verbatim, so a prompt injected
through the diff could copy a readable credential into a finding. The poster scans for common
credential and device-identifier shapes and refuses to post on a match
(`tools/rival-agent/README.md`, "Sensitive findings"), but read a rival's findings before trusting
the post.

## What the handler does

Every brokered command runs through this session's Bash tool with the rival's command text inline, so
the permission mode, deny rules, and the auto-mode classifier judge it exactly as they would judge
your own command. The broker never executes anything and holds no allowlist or denylist.

Prompt text never enters the parent command line: the rival prompt, extra instructions, and any
question are delivered on stdin, from absolute regular files capped at 256 KiB.

A `SIGINT`, `SIGTERM`, or `SIGHUP` on the launcher terminates the rival's whole process group. The
watchdog terminates a rival that emits no stream event and no broker traffic for ten minutes; an
unanswered request counts as traffic for up to an hour.

## Rounds

A review is keyed to the checkout plus the PR number, the commit, or the branch, and its Codex thread
id is recorded owner-only under `~/.config/pogo-paste-rival-agent/ledger/`. Three rounds is the
budget; `--fresh` starts over and `--end-session` forgets the record.
