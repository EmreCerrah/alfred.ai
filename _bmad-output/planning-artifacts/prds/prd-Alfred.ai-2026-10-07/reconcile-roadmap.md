---
title: "Reconciliation: roadmap-brainstorm.md vs PRD"
created: 2026-10-08
input: briefs/brief-Alfred.ai-2026-10-05/inputs/roadmap-brainstorm.md
compared_against: prd.md, addendum.md, .memlog.md, brief.md
---

# Reconciliation: roadmap-brainstorm.md → PRD

Scope: the 16-phase roadmap brain dump, compared against the final brief, the memlog, the PRD and the addendum. The brief and memlog decide sequencing, so their deliberate deferrals and overrides are **not** listed here. That covers wake word, persistent memory, MCP and web UI in V0.2; proactive and scheduled work in V1; multi-agent, multi-OS and English later; Claude-only and no Ollama; `rm` as Tier 2 rather than blocked; and no forbidden folders.

## A. MVP-relevant items that were silently dropped

### A1. Project lookup by name ("Gateway projesinin durumunu kontrol et")
- **Roadmap:** Phase 1 target scenario "Gateway projesinin durumunu kontrol et → Git/Docker/FS". Phase 2 lists "Project detection, project context".
- **PRD state:** FR-6 and FR-10 assume alfred already knows what "Redis" or "Gateway" refers to. No FR says how a spoken project or container name becomes a path, repo or compose project inside one Konuşma. Persistent project memory is deferred to V0.2, but finding the project within a session is not deferred anywhere.
- **Land in:** PRD §4.3 (new acceptance criterion on FR-6, or a small FR covering name→project/container resolution in the session, asking the user when the match is ambiguous). Add an addendum note on where projects live (for example `~/projects`).
- **Severity:** med

### A2. Brief's "sending messages/e-mail on Emre's behalf" is missing from the Tier 2 list
- **Source chain:** The roadmap's Communication tool category and Gmail ecosystem fed into the brief's confirm-required list ("Emre adına mesaj veya e-posta göndermek"). The memlog decided to "fold into T2". The PRD §4.5 Tier 2 row added shutdown/reboot but **not** message sending.
- **Impact:** MVP has no messaging tool, and fail-closed sends unknown commands to Tier 2, so there is no immediate hole. But the memlog decision was not carried out, and the rule needs to exist before any V1 integration (Gmail, Calendar, GitHub comments).
- **Land in:** PRD §4.5 Tier 2 examples table ("Emre adına mesaj/e-posta göndermek").
- **Severity:** med

### A3. Tech stack (Java 21 / Spring Boot) not recorded for architecture
- **Roadmap:** "Core: Java 21, Spring Boot, Spring AI, MCP". The brief keeps it ("Spring üzerinde sıfırdan yazılır"; portfolio message "Spring ekosistemine hâkim").
- **PRD/addendum state:** Neither mentions Java or Spring. §5 says only "kendi kodu", and the addendum lists other technical preferences but not the language or framework. The PRD rightly leaves out "how", but the addendum is the home for technical preferences and now omits the most basic one. It also does not settle the tension between Spring AI (from the roadmap) and the "no framework that hides the loop" rule.
- **Land in:** addendum.md "Mimari tercih" section: Java/Spring Boot as the host stack, and Spring AI allowed only as an LLM client, not as the agent loop or tool orchestrator.
- **Severity:** med (high for architecture handoff)

### A4. Parallel evidence gathering
- **Roadmap:** Phase 6 Planning: "Docker/Redis/Logs paralel → analiz → kök neden".
- **PRD state:** FR-6 says "sırayla planlar". This is probably fine for the MVP, but no decision is logged, so it reads as a silent narrowing.
- **Land in:** PRD FR-6 note, or addendum (architecture may parallelise Tier 0 reads).
- **Severity:** low

### A5. REST API interface
- **Roadmap:** Phase 1 interfaces: "CLI, REST API, basit Web UI". The memlog resolved CLI for the MVP and the PRD defers web UI to V0.2, but a REST/programmatic API is not mentioned anywhere.
- **Land in:** PRD §7.2 V0.2 line next to web UI, or explicitly dropped.
- **Severity:** low

## B. Long-term items missing from §7.2 "MVP dışında" / §6 Non-goals

### B1. Computer control (screen/mouse/apps) moved from "later" to "non-goal" with no logged decision
- **Roadmap:** Phase 8 Computer Control ("IntelliJ'de Gateway'i aç"). The brief keeps it under "Daha sonra: Bilgisayar kontrolü (ekran/fare)".
- **PRD state:** §6 says "Ekranı ya da fareyi kontrol eden bir 'bilgisayar kullanan ajan' değildir", and it is absent from §7.2. The memlog has no decision downgrading it. This contradicts the brief.
- **Land in:** PRD §7.2 "Daha sonra" (and qualify §6 as "MVP'de değildir"), or log a memlog decision if Emre really wants it removed for good. Launching apps ("Uygulama başlatmak", brief Otomatik list) is also not in the PRD Tier 0/1 tables.
- **Severity:** high

### B2. Learning from experience (Advanced Memory)
- **Roadmap:** Phase 13 "Experience → Outcome → Store → Retrieve", for example a successful `./gradlew bootRun` stored as a known workflow. The brief Vision echoes this: "her başarılı iş bir sonrakini kolaylaştırır".
- **PRD state:** §7.2 lists only "Kalıcı hafıza". The difference between remembering facts and reusing outcomes and workflows is lost.
- **Land in:** PRD §7.2 "Daha sonra" ("Deneyimden öğrenme: başarılı iş akışlarını proje hafızasına kaydetme").
- **Severity:** med

### B3. Developer-assistant depth (build/run/health-check of Emre's projects)
- **Roadmap:** Phase 2: Maven/Gradle/Java/Spring Boot/PostgreSQL awareness, build/error/dependency analysis, and "Gateway'i çalıştır → Java version → dependency → başlat → log izle → health check".
- **PRD state:** The MVP covers Docker/Git/shell diagnosis only. Project-aware run and build workflows appear nowhere in §7.2.
- **Land in:** PRD §7.2 V0.2 or V1 (pairs naturally with persistent project memory).
- **Severity:** med

### B4. Personal ecosystem integrations and proactive event sources
- **Roadmap:** Phases 10 and 15: GitHub (PR opened → analyse → test → report), CI/CD, Calendar, Email, Cloud/AWS/servers, monitoring.
- **PRD state:** V1 has only a generic "Proaktif bildirimler". External service integrations (GitHub, Gmail, Calendar, cloud) are not named, so the "bütün dijital çalışma ortamı" vision has no visible entry.
- **Land in:** PRD §7.2 "Daha sonra" ("Harici servis entegrasyonları: GitHub, e-posta, takvim, bulut"), with a pointer to A2's Tier 2 rule for sending.
- **Severity:** low-med

### B5. Memory privacy policy ("neleri kesinlikle hatırlamamalı")
- **Roadmap:** Open questions on what to remember, what never to remember, and local vs cloud.
- **PRD state:** Answerable as "nothing persists" for the MVP (FR-5). But when persistent memory lands in V0.2 there is no placeholder saying a do-not-remember policy is needed.
- **Land in:** PRD §7.2 V0.2 "Kalıcı hafıza" sub-bullet, or addendum.
- **Severity:** low
