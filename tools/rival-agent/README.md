# tools/rival-agent — the core of `run-rival-agent`

A **native handler** (the Claude Code session already running here) launches a **rival agent** (a
fresh Codex CLI process on the ChatGPT plan) inside a disposable worktree pinned to one commit id,
confined by Codex's own sandbox rooted at that worktree with the network off. The rival runs its own
reads, builds and repros there; the broker is its door out for what the sandbox refuses: it asks, the
handler runs the command under its own permission rules or declines, and the answer flows back. The
rival returns findings against one schema and the handler posts them to the PR after a
sensitive-value check. `NOTES.md` holds the design rationale and the accepted exposures.

Ported from the Splotch repository's tooling; the launcher lives in
`.claude/skills/run-rival-agent/scripts/launch-codex.mjs` and the how-to in that skill's `SKILL.md`.
The files in this folder import nothing from outside it.

## Entry points

| File                    | Role                                                                                                                                  |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `broker-server.mjs`     | The stdio MCP server the rival sees. One tool, `run(command, why)`. Reads `RIVAL_SESSION_DIR`. Child of the rival process.            |
| `broker.mjs`            | The handler's CLI: `next` blocks for the next request or the finished findings, `reply` answers one request or declines it, `status`. |
| `post-review.mjs`       | Posts a session's findings to a PR as one `COMMENT` review with anchored comments and a hidden marker; adopts an existing marked one.  |
| `validate-findings.mjs` | Checks a findings document against `findings.schema.json`.                                                                            |

## Supporting modules

| File                     | Owns                                                                                                                                     |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `launch.mjs`             | The vendor-neutral launch: scope resolution, session, worktree, packet, prompt, streaming the rival, validating its findings, the ledger. |
| `spool.mjs`              | The session directory layout and the request/reply files both broker processes watch.                                                    |
| `worktree.mjs`           | Scope resolution to base/head OIDs (the uncommitted scope becomes a snapshot commit), the disposable worktree, the packet.               |
| `stream.mjs`             | The NDJSON runner with the stall watchdog, plus the Codex (and Claude) event renderers and reducers.                                     |
| `ledger.mjs`             | Rounds per unit of work, the rival's thread id for resuming, the three-round cap.                                                        |
| `round-authorization.mjs` | The one explicit extra round for an exhausted PR (see below).                                                                            |
| `prompt.mjs`             | Assembles `rival-prompt.md` for a scope, round, the vendor's tool boundary, and any extra instructions.                                  |
| `rival-prompt.md`        | The rival's contract: the project, where it is, how it executes, how to review, what to return.                                          |
| `rival-prompt-hybrid.md` | The execution section: the rival's own sandboxed shell first, `run` for what that sandbox refuses.                                       |
| `rival-prompt-broker.md` | An alternative execution section where every command goes through the handler (unused by the Codex launcher).                           |
| `findings.schema.json`   | The findings document Codex's `--output-schema` enforces and the poster consumes.                                                        |

## Where things live

Nothing is written inside the repository. A session lives under
`os.tmpdir()/pogo-paste-rival-agent/<uuid>/` (on macOS that is `/var/folders/…/T/`), owner-only, and
its disposable worktree is a `worktree/` directory inside it, removed when the launcher exits (git's
own bookkeeping for it lives in `.git/worktrees/` and is pruned). The round ledger lives under
`~/.config/pogo-paste-rival-agent/ledger/`.

```text
session.json        written by the launcher: rival, scope, worktree, round
packet/             diff.patch, commits.txt, files.txt, scope.json — what the rival reads
requests/<seq>.json the rival's run(command, why) calls, in order
replies/<seq>.json  the handler's answers: exit + output, or declined + reason
outputs/<seq>.out   the full captured output the handler redirected into
findings.json       the validated findings document, once the rival finishes
done.json           written by the launcher when findings validated
failed.json         written by the launcher when the rival exited without valid findings
rival.ndjson        the raw stream log
tmp/                the rival's private TMPDIR, kept out of the sandbox's reach of the spool root
worktree/           the disposable checkout (removed at exit)
```

The worktree gets `npm ci --ignore-scripts --prefer-offline` when the reviewed commit has a
`package-lock.json`, and nothing otherwise. The Gradle project under `android/` is never built by the
launcher; a rival that needs a build asks the handler through the broker.

## Failure behaviour

Every CLI prints one JSON document on stdout and diagnostics on stderr, and exits nonzero on any
failure. The stream watchdog terminates the rival's whole process group after ten minutes with
neither a stream event nor broker traffic. Before pinning a PR scope, the launcher waits up to
`PR_HEAD_SETTLE_TIMEOUT_MS` for the PR's `headRefOid` to match the branch tip `git ls-remote`
reports on `origin`, and refuses with both commit ids when GitHub has not caught up with a push. It
refuses a PR scope outright when `origin` is not `KyleMit/pogo-paste`. The poster refuses a head or
base that moved since the review and never posts twice for one range.

A failed reviewer resume stops the launch and preserves the ledger. It never automatically starts a
fresh reviewer; that takes an explicit `--fresh`.

## Sensitive findings and safe manual recovery

The poster checks the rendered review body, inline comment bodies and inline paths for credential
shapes (GitHub, OpenAI, AWS and Google tokens, private keys, secret assignments, Basic/Bearer
headers) and labelled device identifiers (`adb -s <serial>`, `serial=…`, iOS UDIDs) before the POST.
A match fails closed: no review is created and the diagnostics name only the field and category.

To recover a blocked review:

1. Keep `findings.json` in the session directory. Make a separate copy there named
   `sanitized-findings.json`.
2. Replace sensitive literal values with `[REDACTED]`, keeping every finding in order with its path,
   line, startLine, side and severity unchanged, and every unverified entry.
3. Re-run `node tools/rival-agent/post-review.mjs --pr <n> --session <dir> --sanitized-findings
   <absolute-path-to-copy>`. The copy is schema-checked, anchor-checked, scanned again and marked as
   sanitized in the posted body.

There is no force-publish option.

### One explicit extra round

An exhausted PR (three rounds) can continue once in its original conversation only when the human
user explicitly asks for it. Pass `--round-authorization-file <absolute path>` to the launcher with
the same `--pr`. It is incompatible with `--fresh`, `--end-session`, a question, and non-PR scopes.
The file is strict JSON:

```json
{
  "schemaVersion": 1,
  "repoRoot": "/absolute/original/checkout",
  "pullRequest": 7,
  "rival": "codex",
  "rivalSessionId": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
  "ledgerSha256": "<sha256 of the exact existing ledger file bytes>",
  "priorRounds": 3,
  "authorizedRound": 4,
  "authorization": {
    "kind": "direct-human-message",
    "quote": "<exact direct human authorization>",
    "source": "<durable chat and user-message reference>",
    "recordedAt": "2026-10-08T00:00:00.000Z"
  }
}
```

Mismatches, unknown fields, and replay fail closed; the claim is recorded under the ledger's
`round-authorizations/` directory and consumed once the stream starts, even if the rival fails. An
agent's inferred approval is not authorization.
