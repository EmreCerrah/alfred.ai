# Input reconciliation — PRD + addendum vs ARCHITECTURE-SPINE

- Spine: `ARCHITECTURE-SPINE.md` (draft, 2026-10-12) + `.memlog.md`
- Inputs: `prds/prd-Alfred.ai-2026-10-07/prd.md` (final), `addendum.md`
- Date: 2026-10-12
- Rule applied: a requirement inside one module needs only a Capability-map home; cross-module or invariant-level requirements need an AD (or an explicit Deferred row with a reason).

## Summary

All 20 FRs have a Capability-map home, and most hard constraints landed cleanly (Claude-only, Java/Spring, modular monolith, LLM tool execution disabled, deterministic fail-closed Tier, SafetyWord outside the LLM, offline stop, 15-turn cap, 80/100% budget, append-only masked audit, ephemeral Conversation, language in resources). The gaps are at the seams: alfred's own Tools against alfred's own infrastructure, outbound messages during a running Task, and what happens to audit/budget when Postgres is down.

## Gaps (ranked)

### G1 — HIGH — The agent's own Tools can reach its secrets and its audit store (self-protection missing)

- **Input:** Addendum lessons: "Ajanın okuyabildiği alanda gizli bilgi (token, şifre) bulunmasın" and "`docker.sock` ≈ root". FR-19: "alfred kendi işlem kaydını değiştiremez ve silemez". PRD open Q6: alfred's own deps must not be the target of a cleanup request.
- **Spine:** AD-15 stores the API key in env or a "user-only-readable config file". AD-10 writes the HTTP token to a 0600 file. The daemon runs as that same user, so `cat` on either file is a **Tier 0** read (PRD Tier table), and the output goes to Claude. AD-7 enforces immutability only through a DB role with INSERT+SELECT. But the Docker Tool runs with docker-group (≈root) rights against the same Docker daemon that runs alfred's own Postgres. `docker exec <pg> psql -U postgres ...`, `docker stop <pg>` (Tier 1) and `docker volume rm <pg-data>` (Tier 2, which only needs a SafetyWord) all bypass or destroy the audit. The memlog closes Q6 as "no Redis", but the general concern now applies to alfred's own Compose stack (postgres, stt, tts).
- **Why it matters:** FR-19 and SM-5 rely on the audit not being editable. The spine's guarantee holds only at the SQL-role layer, which the Tool layer can go around. The docker.sock note from the addendum never made it into the spine.
- **Suggested fix:** Add an AD such as "alfred's own assets are protected from its own Tools". `trust` keeps a deterministic denylist (or forces Tier 2 plus an explicit flag) for paths and objects alfred owns: the key/config file, the token file, the Compose project's containers and volumes, the systemd unit, and the audit DB superuser credentials. Tier 0 reads of the secret files are refused, not just raised in Tier. Optionally run Postgres with a superuser password alfred cannot read. State in the spine that docker group = root and that this is an accepted risk.

### G2 — HIGH/MEDIUM — No outbound channel during a running Task (interim reply, ~3 s target)

- **Input:** FR-3 sets a reply start of about 3 s. In a multi-step Task alfred gives a short interim reply ("Bakıyorum, efendim.") and speaks the result later. FR-14/FR-15 prompts and the stop acknowledgement also go out while the Task is still alive.
- **Spine:** AD-6 makes the control path synchronous. AD-13 makes voice plain HTTP request/response with no streaming. The dependency graph has `voice --> agent` and `cli --> agent` only, and AD-2 forbids any edge not in the diagram. So `agent` cannot push to `voice`/`cli`, and neither listens to agent events (only `audit` does). The thin-client ↔ daemon HTTP API shape (blocking call, SSE, long-poll) is not specified. The ~3 s target shows up only in the Deferred STT benchmark row. The budget across STT, Claude turn and TTS is not split up, and the LLM response is not streamed.
- **Why it matters:** FR-3's interim reply and SM-1's demo pacing cannot be built inside the current edges without breaking AD-2. `voice` and `cli` will each make up their own answer.
- **Suggested fix:** Add an AD for the outbound/progress path. Example: `agent` publishes `TaskProgressed` / `ReplyReady` / `ConfirmationRequested` events, `cli` and `voice` may listen to them (add those edges), and the daemon→client channel is SSE (or similar) under AD-10. Record the ~3 s latency budget split (STT / first LLM turn / TTS) as the M3 benchmark target, and note that the interim phrase comes from `persona` without an LLM call.

### G3 — MEDIUM — Postgres-down mode leaves Tier 0 unaudited and budget fail-open

- **Input:** FR-19 says *every* Eylem gets a row. FR-17/FR-18 require cost tracking, the 80% warning and refusing new Tasks at 100%.
- **Spine:** AD-7 says that while Postgres is unreachable, Tier 1–2 are blocked but "Tier 0 okumalar sürebilir". Both the event publication registry and the cost ledger (AD-12) live in Postgres. So in that mode, Tier 0 Actions have no audit row, and Claude calls keep running with no ledger write and no way to check the budget. The spine does not say whether the budget check fails open or closed.
- **Why it matters:** This quietly weakens FR-19 ("her Eylem") and leaves FR-18 enforcement undefined in a state the spine itself allows.
- **Suggested fix:** Pick one and write it into AD-7/AD-12. Either (a) no LLM calls or Tasks while Postgres is down, with alfred saying so (simplest, consistent with FR-9), or (b) a local append-only fallback buffer for audit and cost that is replayed on recovery, with the budget check failing closed.

### G4 — MEDIUM — Voice character and budget missing from voice-engine selection; "no plugin marketplace" not pinned

- **Input:** The addendum's TTS selection criterion for the MVP voice is male, calm, mature, warm, low-mid pitch, clear diction. PRD §4.1 says the persona shows "ses karakteri TTS'te". PRD Q3 makes **both** the ~3 s target and the SM-6 budget the STT criteria. PRD §5 says there is no third-party plugin or skill marketplace.
- **Spine:** The Deferred row says engines are chosen by benchmark "FR-3 ~3 sn hedefine karşı" only. Voice character and cost (for the Groq/Azure fallbacks) are not listed. Piper `tr_TR-dfki-medium` gender is noted as unconfirmed only in the memlog. `capability` exposes a "Tool SPI" (AD-2) that MCP will plug into later, but no rule says Tools are compiled-in and first-party only, with no runtime discovery or loading.
- **Suggested fix:** Extend the Deferred row's criteria to latency + voice character + monthly cost. Add one rule line to AD-3 or AD-11: "Tool SPI is compile-time, first-party only; no runtime plugin loading; MCP (V0.2) goes behind AD-3."

### G5 — LOW — Smaller unsettled seams

- **FR-14 warn-once state:** "Somut risk" comes from LLM judgement, but the "warned once → user insists → execute without re-asking" state crosses turns. No owner is named (`agent` vs `trust`), so it may loop or re-ask.
- **FR-15 retry semantics:** AD-5 says "every other input is rejected". It does not say whether the pending Action stays pending with the same word, or gets a regenerated word (UJ-1 edge: "kelimeyi tekrar ister"; FR-15: "her seferinde yeniden üretilir").
- **Language seams:** The SafetyWord vocabulary (user-facing, language-specific, generated in `trust`), the "yeni konuşma" trigger detection and the STT/TTS language setting sit outside `persona`. AD-18 says user-facing text lives only in `persona`. Clarify ownership so English can be added later (PRD §5).
- **FR-1 channel sensitivity:** The "≤3 sentences for simple voice replies" rule needs `persona` to know the channel. AD-9 says behaviour is identical across channels. Note that trust/flow is identical while style may depend on the channel.
- **Desktop integration from systemd:** `xdg-open`/`swaymsg` (FR-11, "uygulama başlatmak") need `WAYLAND_DISPLAY`/`SWAYSOCK`/D-Bus in the user service environment. This is covered only implicitly by the Deferred packaging row. Worth one line, since a daemon started before Sway cannot open the browser.
- **SafetyWord echo rejection (PRD Q1):** Deferred to M3 with a reason. Acceptable, but it is the only FR-15 clause with no mechanism yet. Keep it visible as an M3 entry gate.

## Landed cleanly (no action)

| Requirement | Where |
| --- | --- |
| FR-1/FR-2 persona, Turkish, no hard-coded language | `persona`, AD-18, AD-17 |
| FR-4 CLI standalone, same behaviour | AD-9, AD-10 |
| FR-5 ephemeral Conversation | AD-16 |
| FR-6..FR-8 multi-step diagnosis, scope, verification | `agent` (module-internal; Capability map) |
| FR-9 honest failure; Claude/network unreachable → no Action | AD-11, Domain errors convention |
| FR-10 Tools, summarised output | `capability`, AD-3; Dış içerik convention |
| FR-11 YouTube find-then-open; method choice | `capability`; Deferred (Q4); `xdg-open` in seed |
| FR-12 deterministic, fail-closed, sudo, compound commands | AD-4 |
| FR-13 backup first → Tier 1 | AD-4 |
| FR-15 random single-use word, exact match, not via LLM | AD-5, AD-3 |
| FR-16 offline stop, process group, no auto-rollback | AD-14, AD-10 (rebinding via Sway config: packaging) |
| FR-17/FR-18 ledger, 80%/100%, 15-turn cap configurable | AD-12 (but see G3) |
| FR-19 fields, append-only, masking | AD-7 (but see G1, G3) |
| FR-20 day summary | AD-2 [ASSUMPTION], `audit` Tool |
| §5 Claude-only, API key, no Pro | AD-11, AD-15 |
| §5 Java/Spring, own loop, LLM tool exec off | AD-11, Stack |
| §5 deps in Docker, monolith on host, systemd | Design Paradigm, AD-9, Structural Seed |
| §5 web content as data | Consistency Conventions (Dış içerik) |
| §5 privacy: no masking toward Claude | Not contradicted |
| Addendum: Spring AI as model access only | AD-11 |
| Addendum: swaymsg/xdg-open | Structural Seed (see G5 env note) |
| Addendum: cost levers (caching, truncation, tiered routing) | AD-12; Deferred |
| §6 no microservices | Design Paradigm |
