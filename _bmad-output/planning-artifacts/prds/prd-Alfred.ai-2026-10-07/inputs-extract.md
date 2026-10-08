---
title: "Inputs extract for PRD: alfred.ai"
created: 2026-10-07
sources:
  - planning-artifacts/briefs/brief-Alfred.ai-2026-10-05/brief.md (status: final)
  - planning-artifacts/briefs/brief-Alfred.ai-2026-10-05/addendum.md
  - planning-artifacts/briefs/brief-Alfred.ai-2026-10-05/inputs/roadmap-brainstorm.md (original 16-phase "JARVIS" brain dump)
  - planning-artifacts/briefs/brief-Alfred.ai-2026-10-05/.memlog.md (decision log)
  - brainstorming/brainstorm-alfred-personal-agent-2026-10-04/.memlog.md (only file in folder; 2 entries)
  - (context) prds/prd-Alfred.ai-2026-10-07/addendum.md + .memlog.md (PRD-phase decisions so far)
---

# Inputs extract for PRD: alfred.ai

Source tags: [B] brief, [A] brief addendum, [R] roadmap-brainstorm, [M] brief memlog, [BS] brainstorm memlog, [P] PRD addendum/memlog. Precedence: the brief is final and wins over [R] and earlier [M] entries unless noted.

Naming [A]: **alfred** = product, **Alfred** = character, **ALFRED** = brand. Optional backronym: *Adaptive Logical Framework for Reasoning, Execution & Digital Assistance*. Former working name: JARVIS [M].

## 1. Vision & positioning

- One-liner [B]: a Turkish-speaking **"dijital uşak ve teknik danışman"** (digital butler and technical advisor) living on Emre's personal Linux PC. Understands voice or text requests, does the work using Docker, Git, files and browser, verifies the result and reports briefly; consults when it notices a problem; always asks before anything irreversible. "Beyni Claude'dur; karar döngüsü, tool'lar ve sınırlar alfred'in kendi kodudur."
- Positioning [A]: not "AI that can use your computer" but **"Your digital butler and technical advisor."** Roles: chief-of-staff / butler / advisor. Three modes: **Assistant** (remind, search, explain) · **Advisor** (analyze, diagnose, recommend) · **Executor** (apply, run, automate).
- Principle [B/A]: *"alfred yalnızca komut çalıştırmaz. Niyeti anlar, danışır, yetkisi varsa uygular ve sonucu doğrular."* / *Alfred does not merely execute commands. Alfred understands intent, advises the user, acts when authorized, and verifies the outcome.*
- Interaction flow [A]: Request → Understand → Analyze → Advise → Execute → Verify → Report.
- Why it exists [B]: born from a learning wish, not a market gap. Emre had never worked with AI; inspired by JARVIS, but the target is Batman's Alfred — more humane, more helpful. Purposes in priority order:
  1. **Öğrenmek (learn):** understand from the inside how an agent decides and acts → agent loop, tool calling, planning written from scratch on Spring; STT/TTS/wake word use off-the-shelf components.
  2. **Göstermek (show/portfolio):** message 1 "Spring ekosistemine hâkim", message 2 "bir AI agent'ını uçtan uca kurabiliyor".
  3. **Kullanmak (use):** genuinely useful at home on his own Linux PC.
- Not a commercial product → "orta düzey titizlik" (medium rigor) [M].
- Differentiators vs OpenClaw / Goose (no feature race) [B]:
  1. **Character + advisor stance** — project identity; character drives tone, permissions and voice together.
  2. **"Modele güvenmeyen bir güven modeli"** — boundaries enforced by deterministic Java code, not Claude's judgment (most incidents in the space came from leaving boundaries to model reasoning). Technically the most valuable piece.
  3. **Turkish.** English later.
- Long-term vision [B]: growth = deepening relationship, not a feature list. First learns to **remember** (how Gateway works, how Emre likes things done), then **stops waiting** (notifies when a container dies or a PR opens). Invariant: notices and consults, never exceeds limits — "Proaktiflik daha fazla yetki değil, daha fazla dikkat demektir." End state: calm, trusted layer of Emre's whole digital workspace. North-star test: **"Alfred, şuna bir bakar mısın?" demek yetmeli.**
- Roadmap long-term evolution [R]: answering system → acting agent → computer-using agent → project/env-aware agent → experience-remembering agent → agent that notices problems unasked ("Personal AI OS").

## 2. Target user(s) & form factor

- **User:** single user — Emre (developer, Java/Spring background, AI novice). Multi-user household explicitly out [M/B].
- **Machine/OS:** one machine, **Fedora + Sway (Wayland, wlroots)**. Windows = development machine only; multi-OS out of scope [B].
- **Runtime shape [B/A]:** alfred core runs on the host as a local native service (e.g. systemd user service); helper services (PostgreSQL, Redis, Python voice services) may run in Docker Compose. Rationale: mic/speaker, desktop/browser and host shell/Docker control conflict with container isolation; mounting `docker.sock` ≈ root.
- **Window/app control [A]:** `swaymsg` IPC (JSON); X11 tools like `xdotool` don't work; URLs via `xdg-open`.
- **Voice (MVP):** push-to-talk → STT → agent → Turkish spoken reply (TTS). No wake word in MVP.
- **Text (MVP):** same agent reachable by text — "CLI veya basit web arayüzü" — used for debugging and as fallback. Voice and text share the same Claude agent loop [M].
- **Language:** Turkish in and out; English later, so **language must not be hard-coded** [B].
- **Voice persona:** calm, mature, warm, clear, slightly formal male voice; voice engine must be swappable (in case a characterful Turkish voice can't be found) [B].
- Anchor scenarios [M]: dev ("Docker'ı kontrol et") and home/everyday ("YouTube'da şu şarkıyı aç") — scope extends beyond the dev environment.
- User's own projects referenced as context: **Gateway** (Java 21 / Spring Boot / Docker / Redis / PostgreSQL) and **CaskKeeper** (Next.js / MongoDB / TypeScript) [R/BS].

## 3. Scope — MVP vs later phases

### MVP — "ilk çalışan alfred" [B]
1. **Voice:** push-to-talk → STT → alfred → Turkish voice reply (TTS).
2. **Text:** same agent via CLI or simple web UI (debug + fallback).
3. **Multi-step reasoning — "MVP'nin kalbi":** solves requests like "Redis loglarını incele, hatayı bul" over multiple steps by gathering evidence, forming a hypothesis, verifying. (Was briefly labeled "MVP+" then promoted to the heart of MVP [M].)
4. **Tools:** Shell, file (read/write), Git, Docker, open content in browser (YouTube).
5. **Trust model:** spoken confirmation word for irreversible ops; keyboard emergency stop.
6. **In-conversation memory:** remembers earlier turns of the same conversation (no persistence).

Initial actions deliberately simple (dev environment + browser control) so MVP ships fast [B].

### MVP done = 60-second demo, one take, unedited [B]
1. Emre presses push-to-talk → **"Buyurun?"**
2. "Docker'da ne çalışıyor?" → "3 container çalışıyor. Redis durmuş görünüyor."
3. "Redis loglarını kontrol et, olası hataları tespit et." → "Hatayı tespit ettim: Redis'in bağlı olduğu projede portlar yanlış verilmiş. Düzeltmemi ister misiniz?"
4. "Evet, config'lerdeki portları değiştir ve tekrar çalıştır." → fixes, restarts, verifies → "Her şey sorunsuz çalışıyor."
5. "Eski Redis volume'unu da temizle." → "Bu işlem geri alınamaz. Onaylamak için 'yeşil' deyin." → "Yeşil." → "Tamamlandı."
6. "Şimdi bana AC/DC'den bir şarkı aç." → "Peki." → most popular AC/DC song starts playing on YouTube.

### MVP milestones (no fixed date; ~8 h/week; rough estimate **8–10 weeks**, "bir tahmin, söz değil") [B]
| # | Milestone | Outcome |
|---|---|---|
| M1 | Text agent loop + shell & Docker tools | Answers typed questions, inspects logs, finds the error. Most learning here. Text first, voice later. |
| M2 | Trust model | Irreversible ops require confirmation word; `Super+Esc` stops running work. |
| M3 | Voice | Push-to-talk Turkish speech in, Turkish voice out. |
| M4 | Git, file, YouTube + demo | 60-s demo works in one take. |

**Checkpoint:** if M1 isn't done in 4 weeks, scope is cut.

### Later versions [B]
| Version | Content |
|---|---|
| V0.2 | Wake word ("Alfred") + spoken emergency-stop word · Persistent memory (PostgreSQL + vector search) · MCP support |
| V1 | Proactive notifications · Scheduled tasks |
| Later | English · Strong confirmation for production systems · Computer control (screen/mouse) · Multi-agent · Multi-OS |

### Original 16-phase roadmap [R] (superseded for sequencing, useful as backlog/examples)
1. Foundation ("konuşabiliyor"): LLM, conversation, prompt mgmt, tool calling, agent loop, context mgmt; CLI/REST/simple Web UI; Shell/FS/Git/Docker. Scenario: "Gateway projesinin durumunu kontrol et" → "Gateway çalışıyor, son commit…, container…, working tree clean."
2. Developer Assistant ("dev ortamımı biliyor"): Git, GitHub, Docker, Maven, Gradle, Java, Spring Boot, PostgreSQL, Redis, Linux; project detection/context; log/build/error/dependency analysis. "Gateway'i çalıştır" → find project → Java version → Docker deps → env → start → watch logs → health check → report.
3. Tool Ecosystem ("elleri"): Tool Registry; categories Developer, Computer, Browser, System, Communication, Database, Cloud.
4. MCP Layer: MCP client to external servers (GitHub, Docker, Filesystem).
5. Memory ("beni tanıyor"): short-term (conversation/task/context/tools), long-term (preferences, projects, technologies, common commands, past decisions), project memory; PostgreSQL + vector DB.
6. Planning: "Gateway neden çalışmıyor?" → analyze → plan → Docker/Redis/logs in parallel → root cause.
7. Observation: start → log → fail → Redis missing → Redis stopped → start Redis → retry.
8. Computer Control: screenshot/vision, keyboard/mouse, apps ("IntelliJ'de Gateway'i aç").
9. Voice: wake word, STT, TTS, interruption ("Hey Jarvis, Docker'da ne çalışıyor?") — **pulled into MVP core** [M].
10. Proactive: event → analyze → decide → notify/act (GitHub, Docker, DB, Calendar, Email, CI/CD…); "PR açıldı → analiz → test → rapor".
11. Automation: scheduler (daily/hourly/event/conditional); morning dev-env check → daily report.
12. Multi-Agent: Coding, DevOps, Research, then Security, DB, Testing, Docs, Travel, PA.
13. Advanced Memory: experience → outcome → store → retrieve ("`./gradlew bootRun` başarılı → bir dahakine bilinen workflow").
14. Safety & Permissions (see §5 — superseded).
15. Personal Ecosystem: Linux/IntelliJ/Docker/Files, GitHub/Gmail/Calendar, AWS/servers.
16. Final architecture: User → Voice/Chat → AI Core → Planning/Memory/Agent → Tool Registry → targets; Events (Scheduler, Webhook, Monitor).

### Differentiation candidates from research [A] (not adopted in brief; candidate backlog)
- JVM/Spring-specific depth: tools understanding Maven/Gradle builds, Actuator, stack traces, Testcontainers, Flyway (generic agents see `mvn verify` failures as plain text).
- Typed, policy-checked tool layer: every action a typed Java command, approved by a policy engine, written to audit; secrets never enter model context.
- Event-driven triage on existing backend stack: agent suggests fixes rather than acting.
- Expose alfred as an **MCP server** so its Java tools work from Claude Code / Goose.
- Don't rebuild solved things (generic coding agent, computer-use, voice) before the differentiating core is solid.

## 4. Explicit out-of-scope (MVP and/or overall)
- Multi-user / household users; multi-OS (Windows is dev-only) [B/M].
- Wake word and spoken emergency-stop word (→ V0.2) [B].
- Persistent/long-term memory, MCP (→ V0.2) [B].
- Proactive notifications, scheduled tasks (→ V1) [B].
- English, production-system strong confirmation, screen/mouse computer control, multi-agent (→ later) [B].
- Local LLM / Ollama / OpenAI — deferred; Claude only for now [M].
- Ready-made agent frameworks that hide the loop/planning (Spring AI internal tool execution, Embabel, LangChain4j/LangGraph4j) [A].
- Forbidden-folder list — intentionally none [B].
- Running alfred fully inside Docker [A].
- Commercial product goals [M].
- Microservices (and an unstructured classic monolith) [P].

## 5. Safety / permission / confirmation rules (current = brief "Sınırlar ve güven modeli")
- Default stance is trust: wide permissions on Emre's own PC; "alfred iş yapsın diye var." Single rule: **"Geri alınamayan her şey onay ister."**
- **Automatic (does it without asking, reports after):**
  - Read/create/edit files and folders — no access restriction.
  - Git and Docker commands (except irreversible ones).
  - Launching apps, opening content in browser.
- **Requires confirmation:**
  - Every irreversible op: deletion, `git reset --hard`, `git push --force`, `docker compose down -v` (volume removal), `docker system prune`, "ve benzerleri".
  - Commands needing `sudo`.
  - Shutdown / reboot.
  - Sending messages or email on Emre's behalf.
- **Classification is code, not model:** Claude proposes an action; alfred's deterministic rules classify it as reversible/irreversible, so a wrong model judgment can't breach the boundary.
- **Spoken confirmation word:** a different random word each time ("Redis volume'unu silmek için 'yeşil' deyin."). Only that exact word executes. Not accepted: plain "evet", a misheard sentence, background video audio, or alfred's own voice.
- Before risky ops (character spec [A]): explain what it will do, explain possible impact, ask for confirmation.
- **Emergency stop ("acil çıkış"):**
  - Rebindable keyboard shortcut, default `Super+Esc` (MVP).
  - User-chosen unique spoken stop phrase (e.g. **"kırmızı elma"**), recognized locally, never sent to Claude, works offline — V0.2 together with wake word (needs continuous listening; same local keyword tech as wake word).
  - Stop only **stops**; no automatic rollback. Undo is a separate explicit command.
- **Honesty:** never pretends a failed action succeeded; verifies the outcome after every action [B/A].
- **Consciously accepted risk:** contents of every file alfred reads go to the Claude API; no forbidden folders because no sensitive/production data is kept on the PC.
- Deferred: PRODUCTION → strong confirmation (later version).
- History, NOT to implement [A]: first risk engine idea READ auto · WRITE conditional confirm · DELETE explicit confirm · PRODUCTION strong confirm.
- Research lessons [A] (context, not adopted as requirements): Apr 2026 Cursor agent deleted production DB + backups using an unrelated token found in code; OpenClaw: 40k+ exposed instances, leaked API keys, malicious skills, prompt injection via email exfiltrating AWS keys; MCP Jan 2026: 42k exposed endpoints, 7 CVEs incl. CVSS 9.6 RCE. Lesson: read/write/execute tiers alone aren't enough — need secret isolation, network egress control, scoped credentials.

## 6. Personality / tone / voice (preserve)
- Traits [B/A]: Intelligent/Analytical · Calm/Patient · Loyal/Reliable · Discreet ("ketum") · Mildly witty ("ölçülü esprili") · Proactive · Never arrogant ("asla ukala değil"). Respectful but not overly formal; short-spoken.
- **Calm, doesn't panic:** "Anlaşıldı. Redis yanıt vermiyor gibi görünüyor. Bir göz atayım."
- **Respectful but not stiff:** no "Sayın efendim, emriniz üzerine…". Yes to: "Elbette." "Hemen kontrol ediyorum." "Sanırım problemi buldum." "Bunu değiştirmeden önce onayınızı almam gerekiyor."
- **Light humor, short and rare (not every reply):** "Görünen o ki build bugün de işbirliği yapmamaya karar vermiş."
- **Proactive but not a know-it-all:** "Gateway'i başlatabilirim. Ancak son üç çalıştırmada Redis bağlantısı başarısız olmuş. Önce Redis'i kontrol etmemi ister misiniz?" Pattern: User → Command; Alfred → Analysis + Recommendation; User → Decision; Alfred → Action.
- **Advisor stance [B]:** does the job, then reports; doesn't interrupt on every command. Advising kicks in only when it notices something.
- **Diagnostic discipline (system prompt draft) [A]:** 1) Kanıt topla 2) Hipotez kur 3) Doğrula 4) Çözüm öner 5) Yalnızca yetkiliyse uygula 6) Sonucu doğrula. "Never pretend an action succeeded if it did not."
- **System prompt draft (summary) [A]:** "You are Alfred. A highly intelligent personal assistant and technical advisor." Personality: calm, respectful, concise, analytical, discreet, mildly witty, proactive, never arrogant. Domains: software development, system administration, research, automation, personal productivity. "You do not blindly execute commands."
- **Character is distributed across the system, not just the prompt [A]:** Personality → tone/humor → replies; Decision → risk/confirmation → actions; Voice → TTS style.
- **Voice character [A]:** male, (British-accented — for English version), calm, mature, warm, low/medium pitch, clear diction, slightly formal. Inspiration, not imitation. EN lines: "Very well." "Right away." "I believe I've found the problem." TR lines: "Elbette." "Hemen ilgileniyorum." Turkish-first.
- Demo micro-copy: "Buyurun?" (PTT greeting), "Peki.", "Tamamlandı.", "Her şey sorunsuz çalışıyor." [B]

## 7. Success metrics / learning goals [B]
| Goal | Success if |
|---|---|
| MVP | 60-s demo runs in one take, unedited. |
| Learn | Emre can explain the agent loop to someone on a whiteboard **and** has written a blog post about it. |
| Show | README top has the demo video and an architecture diagram. |
| Use | alfred genuinely used every day for two weeks after MVP. |
- Schedule guardrail: M1 within 4 weeks or scope shrinks; ~8 h/week; 8–10 week estimate.

## 8. Tech decisions already made
- **LLM:** Claude (API) only — "sadece beyin". Local/Ollama deferred (research note: if added, Qwen3/Qwen3-Coder; ≤8B models miss tool calls; cloud primary, Ollama as privacy/cost fallback) [M/A].
- **Own code:** agent loop, tools, voice pipeline orchestration, boundaries — Java/Spring [M/B].
- **Agent loop hand-written [A]:** Spring AI `ChatClient` executes tool calls internally by default → hides the loop and blocks trust-model interception → **disable Spring AI internal tool execution**; Spring AI stays as model-access + MCP layer. Embabel etc. rejected.
- **Spring AI 2.0** (GA June 2026 with **Spring Boot 4.1**; MCP client/server in core, `@McpTool`; Jackson 3, package changes) — start directly on 2.0 [A] (to verify in architecture).
- Language: Java (roadmap: Java 21) [R]; Python for voice services as separate services [A].
- **Voice components (research, unverified) [A]:** wake word openWakeWord (Porcupine free tier closed); STT faster-whisper; TTS: Piper archived → Kokoro / XTTS candidates; Turkish characterful TTS limited → engine behind swappable interface. [P]: model choice/acquisition deferred to architecture/tech research; PRD states voice as a capability only — Emre unsure how to source Turkish models.
- **Deployment:** hybrid — core native on host (systemd user service); PostgreSQL, Redis, Python voice services in Docker Compose [A] (memlog marked "kullanıcı onayı bekleniyor", brief adopts it with "çalışabilir").
- **Architecture style [P]:** modular monolith — clear module boundaries (tools, voice, decision loop, confirmation/safety layer), single process, single deployment.
- **Desktop integration:** `swaymsg` IPC, `xdg-open` [A].
- **YouTube playback:** search URL only opens a results list → must find a video first (YouTube Data API or `yt-dlp` search) then open the most popular result's watch URL; no screen control/vision needed [A/M].
- **Persistent memory (V0.2):** PostgreSQL + vector search [B].
- Emergency stop voice phrase uses the same local keyword-spotting tech as wake word [A].
- Roadmap tech map (original, partly superseded) [R]: Java 21, Spring Boot, Spring AI, MCP; OpenAI, Ollama, embeddings, Whisper, TTS; PostgreSQL, Redis, vector DB; Docker/Compose/Linux; UI Next.js/React/WebSocket.
- Repo: github.com/EmreCerrah/alfred.ai [M].

## 9. Open questions & unresolved items
1. **Text interface form:** CLI vs simple web UI (brief says "veya") — which for MVP?
2. **Confirmation in text mode:** spoken random word is defined for voice; how is confirmation done when using text (type the word?) and how is the emergency stop exposed in the web/CLI?
3. **Irreversibility classifier:** exact rule set ("ve benzerleri") — file overwrite? `rm` of a file alfred just created? `git branch -D`, `docker rm`, `docker volume rm`, `kill`, package installs? Behavior for unknown/unclassifiable shell commands (default deny-confirm vs allow)? Piped/compound shell commands?
4. **File overwrite/edit:** brief says edits are automatic, but overwriting is effectively irreversible (memlog flagged "dosya üzerine yazma" as open; never explicitly resolved).
5. **Confirmation-word robustness:** how to exclude alfred's own TTS and background audio (echo cancellation? PTT-only confirmation?); vocabulary of random words; timeout/retry on mismatch.
6. **`Super+Esc` on Sway:** global shortcut must be bound in Sway config and routed to alfred — mechanism undecided; what "stop" means mid-tool (kill process tree? cancel Claude call?).
7. **STT/TTS choice for Turkish** and how to obtain/install models — explicitly deferred [P]; characterful Turkish male voice may not exist.
8. **Hybrid vs modular monolith:** separate Python voice containers vs "single process, single deployment" — reconcile in architecture.
9. **YouTube search provider:** YouTube Data API (needs API key) vs `yt-dlp`; definition of "en popüler"; which browser.
10. **In-conversation memory boundaries:** what is a "conversation/session" in PTT mode; context window management for long log inspection.
11. **Audit log / observability:** roadmap asks "Audit?", research recommends audit + secret isolation; brief has none for MVP.
12. **Secrets:** files incl. `.env`/keys go to Claude with no filter — accepted risk, but should known secret files be redacted? (Research lesson vs brief decision.)
13. **Retry/failure policy:** roadmap asks "Başarısızlığı nasıl anlamalı, tekrar deneyebilir mi?" — max steps/iterations, loop limits, cost limits not defined.
14. **What to remember / never remember** (relevant for V0.2 memory) [R].
15. **When to ask vs decide autonomously** beyond irreversibility — the advisor "notices something" trigger is not specified (e.g., how it knows "son üç çalıştırmada Redis başarısız olmuş" without persistent memory in MVP).
16. Java version (21 in roadmap; Spring Boot 4.1 implied) and Spring AI 2.0 facts need verification (research unverified).
17. Research items flagged "tek tek doğrulanmadı" → verify with `bmad-deep-recon` before critical decisions.
18. Brainstorm session (2026-10-04) was abandoned after 2 entries — no additional decisions there.

## 10. Contradictions between sources
1. **Permission model:** [R] phase 14 — WRITE (edit/delete file) needs confirmation, EXECUTE (docker restart, git push) needs confirmation, `rm` blocked; character spec [A] — WRITE conditional, DELETE explicit, PRODUCTION strong. **Brief (wins):** everything automatic except irreversible ops; nothing blocked outright; `git push` (non-force) and `docker restart` automatic.
2. **"Advise first" vs "act then report":** character text says advise → act when authorized; trust model says reversible actions happen without asking. Resolved [M]: trust model wins; advising only when something is noticed. Note demo step 3 still asks "Düzeltmemi ister misiniz?" before a reversible config edit — consistent with "noticed something → consult", but PRD should define when proposing vs just doing.
3. **Voice placement:** [R] voice = phase 9; [M/B] voice pulled into MVP core. Research [A] advises not building voice/computer-use before the differentiating core is solid — mitigated by M3 coming after M1/M2.
4. **Wake word name:** [R] "Hey Jarvis"; brief V0.2 wake word "Alfred". Demo step 1 originally implied wake word → changed to push-to-talk [M].
5. **YouTube scope:** memlog moved browser/YouTube to V0.2, then back to MVP; brief has it in MVP (M4). Brief V0.2 table no longer lists browser.
6. **Multi-step planning:** first "MVP+", then "MVP'nin kalbi" (brief final). Roadmap had Planning/Observation as phases 6–7.
7. **LLM provider:** [R]/early [M] stack lists OpenAI/Ollama; decision: Claude only.
8. **UI:** [R] Next.js/React/WebSocket; brief MVP: CLI or simple web UI.
9. **Deployment:** hybrid (host core + Compose services) [A] vs modular monolith "tek süreç, tek dağıtım" [P].
10. **Voice accent/language:** voice spec "İngiliz aksanlı" with English sample lines vs Turkish-first product; resolved: British accent applies to future English version only.
11. **Differentiators:** research [A]/[M insight] proposed JVM/Spring depth + typed policy tool layer + alfred-as-MCP-server; brief final picked character + code-enforced trust model + Turkish. Typed/policy tool layer overlaps with "trust model", but JVM depth and MCP-server exposure are absent from brief scope.
12. **Security posture:** research lesson calls for secret isolation, egress control, scoped credentials, audit; brief accepts all file contents going to Claude and has no forbidden folders or audit.
13. **Emergency stop by voice:** user wanted a spoken stop word; conflicts with PTT MVP (needs continuous listening) → resolved: keyboard-only in MVP, voice phrase V0.2.
14. **Redis ambiguity (minor):** demo's Redis belongs to Emre's project (e.g., Gateway); alfred's own infra also lists Redis in Compose — the PRD/demo should keep them distinct (and "clean old Redis volume" must not target alfred's own).
