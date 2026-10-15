---
reviewed: ARCHITECTURE-SPINE.md (status: draft, updated 2026-10-12)
lens: good-spine rubric
inputs: [ARCHITECTURE-SPINE.md, .memlog.md, research-stt-stack.md, prds/prd-Alfred.ai-2026-10-07/prd.md]
date: 2026-10-12
---

# Spine Review: Rubric Lens

## Verdict

**Needs revision before finalize.** The spine is short, mostly holds decisions rather than rationale, has valid mermaid, verified stack versions, and maps all of FR-1..FR-20. The trust gate (AD-3/AD-4/AD-5) is well built. Four real divergence points are still open, though: the daemon/client interaction contract, how the audit guarantee works at runtime, where trust gets the system state its Tier rules need, and the Task/Conversation concurrency model. Each of these would let separately built units (cli, voice, agent, trust, audit, thin client) diverge. Several medium gaps follow, including diagram edges that contradict AD-18 and a Deferred item (SafetyWord normalization) that can split voice and trust.

| Severity | Count |
| --- | --- |
| Critical | 0 |
| High | 4 |
| Medium | 7 |
| Low | 7 |

Checklist summary:

| Check | Result |
| --- | --- |
| Fixes real divergence points, misses none | Partial: H1, H2, H3, H4 missing |
| Every AD Rule enforceable and prevents its divergence | Partial: AD-6/AD-7 (H2), AD-11 (M2), AD-7 role rule (M5), AD-2 diagram vs AD-18 (M1) |
| Nothing Deferred lets two units diverge | Fails once: SafetyWord STT normalization (M3); packaging partly (M7) |
| Named tech verified-current | Pass (research §2/§3, 2026-10-12); minor note L2 |
| Covers PRD capabilities | Mostly: FR-3 interim reply (H1/M4), FR-13 (H3), FR-14 and FR-8 enforcement (M6) are thin |
| Every owned dimension decided/deferred/open | Partial: concurrency silent (H4); ops envelope partly silent (M7); no Open Questions section (L5) |
| Terse, valid mermaid, no placeholders | Pass; a few "pinned at scaffold" soft placeholders (L3) |

## Findings

### High

**H1: Daemon and client interaction contract is undecided**
- *Location:* AD-9, AD-10, Structural Seed, Deferred "Dağıtım paketleme"
- *Problem:* AD-10 secures the channel but never defines its shape. Several flows cannot be expressed as one HTTP request/response: a multi-Turn Task, the FR-3 interim reply ("Bakıyorum, efendim") followed by the final answer, the FR-15 SafetyWord prompt that waits for the user's next input, and FR-14's warn-then-insist step. The `cli` controller, the thin client, the `voice` adapter and the future web UI will each invent their own approach (blocking call, polling, SSE, WebSocket). The thin client's technology is also undecided. If it is a JVM jar, JVM startup on every PTT press and release eats into the ~3 s FR-3 budget. Finally, the port number and token file path are not fixed, and client and daemon must agree on both.
- *Fix:* Add an AD such as "Channel protocol". Example: `POST /tasks` returns a taskId, and `GET /tasks/{id}/events` (SSE) streams typed messages (`interim`, `final`, `approval-required{prompt}`, `warning`, `failed`). Approval and insist replies go to `POST /tasks/{id}/input`. Fix the token path (e.g. `$XDG_RUNTIME_DIR/alfred/token`) and how the port is chosen (fixed config value or written next to the token). Decide the thin client's technology, e.g. a POSIX shell script using `curl`, or a native binary, with a rule against a JVM client on the hotkey path.

**H2: The audit guarantee (AD-6 + AD-7) cannot be enforced as written**
- *Location:* AD-6, AD-7, SM-5
- *Problem:*
  1. The Spring Modulith event publication registry and `@ApplicationModuleListener` are transaction-bound. Events published outside a transaction are not recorded, and transactional listeners do not fire. AD-6 makes the control path plain synchronous calls in modules that have no database (`agent`, `trust`, `capability`), so audit events may never be persisted.
  2. No rule says the approval record must be durable **before** execution. If the async audit listener runs after the Action, a crash leaves a Tier 1–2 Action with no record, which breaks SM-5.
  3. "`trust` does not issue Tier 1–2 approval when Postgres is unreachable" needs a health or "audit writable" port in core. No such port exists, and core must not import infrastructure (AD-2).
  4. By default, Spring Boot with Flyway and a DataSource fails to start when Postgres is down. That contradicts the degraded mode ("Tier 0 continues") and AD-9's "CLI works on its own".
- *Fix:* Change the rule to something like: "Before a Tier 1–2 `ApprovedAction` is executed, an `ActionApproved` record is committed in a transaction (registry or direct `audit` API through an inverted port `AuditGate` defined in `trust`, implemented in `audit`); commit failure = no execution. Event publishing that must be audited happens inside `@Transactional` boundaries." Also decide how startup behaves without Postgres (e.g. lazy DataSource and Flyway on a retry/health path) or drop the degraded-mode claim.

**H3: Tier rules need system state, but trust has no way to read it**
- *Location:* AD-4 (FR-13 clause), AD-2 diagram, AD-5/FR-15
- *Problem:* Several Tier decisions depend on the live system: "file is clean in Git" (Tier 1), "overwrite without backup" (Tier 2), FR-13 "take a backup first, then Tier 1", and FR-15 "say what is affected and its impact" (e.g. `redis-data → 2.4 GB`). `trust` is core and must not import infrastructure. The diagram forbids `trust → capability` (that would also form a cycle with `capability → trust`). So each implementer will choose differently: put git and filesystem calls in `trust`, have `agent` pre-fetch facts, or let the LLM claim them. The last option undermines "the model cannot lower a Tier". The spine also never says how the non-shell Tools (file, git, docker, browser) are described to `trust` for classification.
- *Fix:* Add a rule. For example, `trust` defines an outbound port `ActionFacts` (git-tracked/clean, file exists, backup present, resource size), implemented in `capability` through Tier-0 probes and wired by inversion, so the diagram edge stays `capability → trust`. Facts never come from LLM output. Every Tool exposes a canonical `ActionDescriptor` (tool, verb, targets, raw command) that `trust` classifies. Who builds the backup Action and how it is chained also belongs in the rule.

**H4: The Task and Conversation concurrency model is silent**
- *Location:* AD-9, AD-14, AD-16, AD-5
- *Problem:* AD-9 says both channels share "the same Task", but the spine never says: how many Tasks can run at once, whether a new request while one is running is queued, rejected, or cancels the old one, whether there is one global Conversation or one per channel, which Task `alfred stop` cancels, and which channel's "next input" AD-5 compares against while a SafetyWord is pending (for example, the word is shown in the CLI but a voice utterance arrives). `agent`, `cli`, `voice`, `memory` and `trust` will each assume something different.
- *Fix:* Add an AD such as: "At most one active Task; there is one global Conversation; a new request while a Task is active is rejected with a typed busy result (or queued, but pick one); EmergencyStop targets the active Task; a pending SafetyWord binds to the active Task and is answered by the next input from any channel." Adjust if needed, but write it down.

### Medium

**M1: The dependency diagram contradicts AD-18**
- *Location:* Design Paradigm mermaid, AD-2 ("unlisted edges are forbidden"), AD-18
- *Problem:* AD-18 binds `trust` (format of the approval prompt) and says other modules fetch text by key. The diagram has no `trust → persona` edge and no `voice → persona` edge, yet `voice` has to say "Buyurun?" when PTT is pressed (FR-3). Under AD-2, those calls are illegal.
- *Fix:* Add `voice --> persona` and either add `trust --> persona` or state that `trust` returns structured approval data and `agent` renders it through `persona`. Then remove `trust` from AD-18's Binds.

**M2: AD-11's tool-execution switch is "ve/veya" (and/or) and not tested**
- *Location:* AD-11; research-stt-stack.md §3.2 and open item 4
- *Problem:* "and/or" leaves the choice to the implementer. Research says 2.0 moved tool execution into `ToolCallingAdvisor`. Whether `internalToolExecutionEnabled` still exists is an open item. This is the PRD §5 safety constraint, so its Rule must be a single mechanism with a check that can fail.
- *Fix:* Pick one: the `ChatModel` is called directly, and `ChatClient` is never used (enforce with an ArchUnit rule), plus the global property set to `false` as a second safeguard. Add a test asserting that a response containing tool calls comes back to `agent` without any Tool being run. Resolve research open item 4 before coding.

**M3: The SafetyWord STT-normalization deferral lets voice and trust diverge**
- *Location:* Deferred row "SafetyWord yankı reddi ... normalizasyon", AD-5
- *Problem:* AD-5 says "exact" comparison in `trust`. STT output will contain case, punctuation and Turkish diacritics (e.g. "Yeşil." vs "yeşil", "ş" vs "s"). If `voice` normalizes and `trust` does too, or neither does, the behaviour differs by channel. That breaks FR-4 ("same behaviour") and could make FR-15 either unusable or too loose. The *owner* of normalization is a cross-unit decision. Only the echo-rejection mechanism can safely wait for M3.
- *Fix:* Decide now: `trust` owns one canonical normalization (trim, lowercase with Turkish locale, strip punctuation; diacritics kept) applied to both channels; `voice` passes the raw transcript. Keep only echo rejection under Deferred. Also note that PTT (capture only while the key is held) is the MVP's primary echo guard.

**M4: The Java ↔ Python voice contract and the latency path are undecided**
- *Location:* AD-13, Stack ("STT / TTS servisleri"), research §4
- *Problem:* The `voice` adapters and the Python services are built separately. Endpoints, audio format (e.g. 16 kHz mono PCM WAV), the TTS output format, and whether the daemon or the service captures the microphone and plays audio are not fixed. The Structural Seed has `modules → devices`, but nothing says how a systemd daemon reaches PipeWire. Research §4 also says the ~3 s budget depends on **streaming Claude output and starting TTS on the first sentence**. AD-13 forbids streaming (STT/TTS), and the spine never decides whether `LlmPort` streams. That choice shapes the `LlmPort` signature for `agent`, `llm` and `voice`.
- *Fix:* Add a short contract to AD-13: endpoints, audio format, who owns capture and playback. Decide whether `LlmPort` exposes streaming of the final text and whether TTS is sentence-chunked (request/response per sentence still fits "no streaming" on the Python side). Name the Python runtime and framework or mark it Deferred with that interface fixed.

**M5: Audit immutability can be bypassed, and alfred's own stack is not protected**
- *Location:* AD-7 (DB role rule), AD-8 (Flyway per module)
- *Problem:* (a) If the runtime role also runs Flyway, it owns the tables and can `UPDATE` or `DELETE`, so "INSERT + SELECT only" needs a separate migration/owner role. (b) alfred has shell and Docker Tools. `docker exec <postgres> psql -U postgres ...`, or `docker volume rm` on the Postgres volume, deletes the audit log. These are Tier 2, but FR-19 says alfred cannot change or delete its own log at all. This is the same class of problem as PRD open question 6 (the demo's "clean old volume" must not hit alfred's own resources), which the spine does not record.
- *Fix:* State: "Flyway runs as owner role `alfred_owner`; the daemon connects as `alfred_app` with INSERT/SELECT on audit." Add a `trust` rule: Actions targeting alfred's own Compose project, containers or volumes (identified by Compose project label) are refused outright, not just Tier 2. Alternatively, record a conscious acceptance of the risk.

**M6: FR-14 (warn once, then honour insist) and FR-8 (verify after state change) have no enforceable owner**
- *Location:* Capability Map rows §4.3 and §4.5 (they cite AD-3/AD-4, which say nothing about either); `agent` responsibility row
- *Problem:* The spine does not decide: who detects "a concrete risk" (deterministic rules in `trust` or the LLM), how "Biliyorum, yine de yap" is recognised, and whether `agent` forces a verification Turn after every Tier ≥1 Action or leaves it to the prompt. Implementers will split between deterministic and prompt-based behaviour, which is the divergence a spine exists to stop.
- *Fix:* Add one-line rules. For example: "FR-14 risk is detected by the LLM (advisory only, can never lower a Tier); the insist reply is handled by `agent` as a Task input." And: "`agent` will not report success for a Tier ≥1 Action until a later verification Action in the same Task has run (enforced in the loop, not the prompt)."

**M7: The operational and environmental envelope is partly silent**
- *Location:* Structural Seed "Ortamlar", Deferred "Dağıtım paketleme" and "Operasyon"
- *Problem:* Decided: one host, Windows for dev only, no staging. Silent or deferred too broadly:
  - A systemd *user* service does not inherit the Sway session environment (`WAYLAND_DISPLAY`, `SWAYSOCK`, `XDG_RUNTIME_DIR`, PipeWire, `DOCKER_HOST`). Without it, `xdg-open` (FR-11), `swaymsg` and audio fail. This is a runtime-model decision, not packaging.
  - The lifecycle link between the daemon and Compose is undecided (does the daemon start Compose? `After=`/`Requires=`?). It interacts with H2.
  - Windows dev cannot exercise process groups (AD-14) or POSIX shell Tools, and Testcontainers needs Docker Desktop. There is no decision on where Linux-only tests run.
  - No CI decision, although AD-1 says `verify()` "breaks the build" and the repo uses GitHub PRs.
  - Log destination (journald?) and retention are not stated.
- *Fix:* Add a line to the Structural Seed or a small AD: "The unit imports the Sway session env (`systemctl --user import-environment` from Sway config)." Add "the unit `Requires=` the Compose stack, or the daemon tolerates its absence (see H2)." Add "logs go to journald." Add "CI: GitHub Actions on ubuntu runs the full test suite, including Linux-only capability tests; Windows runs a subset", or move CI explicitly to Deferred with a reason.

### Low

**L1: An [ASSUMPTION] sits inside a Rule**
- *Location:* AD-2, last sentence (FR-20 tool via `audit → capability` SPI)
- *Problem:* A binding Rule should not rest on an assumption.
- *Fix:* Mark it decided (it is consistent and acyclic) or move it to an Open Questions section.

**L2: The Anthropic SDK version note is misleading**
- *Location:* Stack, Anthropic Java SDK row
- *Problem:* The Spring AI 2.0.1 BOM (released 2026-08-21) will not bring 2.71.0 (2026-10-09). The "fills gaps" use (caching control) may need a newer SDK than the BOM supplies.
- *Fix:* Change the row to "the version from the Spring AI 2.0.1 BOM (check `dependencyInsight` at scaffold)", and add: "if a gap needs a newer SDK, override via a BOM property, never a direct pin."

**L3: Soft placeholders**
- *Location:* Stack ("wrapper sürümü scaffold'da sabitlenir", "sürüm scaffold'da pinlenir"); STT/TTS row
- *Problem:* These are acceptable as Deferred, but the Python runtime version and framework are not listed at all.
- *Fix:* Name the PostgreSQL major version (e.g. 18) and the Python version, or list both in Deferred.

**L4: Some rules are scoped too narrowly or mixed with unrelated concerns**
- *Location:* AD-1, AD-15
- *Problem:* AD-1 mixes a testing rule (Testcontainers) into the boundary AD. AD-15 covers only the Anthropic key. Postgres credentials and the CLI token are not covered.
- *Fix:* Move the Testcontainers rule to Consistency Conventions ("Testing"). Widen AD-15 to "all secrets (Anthropic key, DB passwords)".

**L5: There is no Open Questions section**
- *Location:* Whole document
- *Problem:* PRD open question 5 (budget adequacy) and question 6 (Redis / alfred's own resources) are not carried into the spine. Question 6 appears only in the memlog.
- *Fix:* Add a short Open Questions section, or fold them into Deferred and M5.

**L6: AD-14's kill semantics are incomplete**
- *Location:* AD-14
- *Problem:* Killing the process group of a `docker`/`docker compose` CLI call does not stop work already handed to dockerd. `xdg-open` detaches the browser.
- *Fix:* Add one line: "stop kills the client process; operations already handed to dockerd or detached apps are not rolled back and are reported as such (FR-9)."

**L7: The ApprovedAction terminology is imprecise**
- *Location:* AD-3
- *Problem:* A single-use object with consumed-state is not a value object, and the rule does not say where consumption is tracked.
- *Fix:* Call it a "capability token". State that consumption is tracked in `trust` (e.g. a consumed-id set checked on `capability`'s execute call), or that it holds an internal atomic flag.

## Positive notes

- The trust gate design (package-private `ApprovedAction`, exact command, single use, synchronous path, fail-closed Tier) is strong and enforceable.
- Stack versions are verified against release pages dated 2026-08/10 (research §2–§3) and form a consistent set.
- Both mermaid diagrams are syntactically valid. The document is terse and puts decisions ahead of rationale.
- The Capability Map covers FR-1..FR-20 with no orphan FR.
