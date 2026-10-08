---
title: "Reconcile: brief addendum vs PRD"
input: briefs/brief-Alfred.ai-2026-10-05/addendum.md
compared: prd.md, addendum.md, .memlog.md (PRD 2026-10-07)
created: 2026-10-08
---

# Reconcile: brief addendum vs PRD

Scope: content in the brief addendum that is missing, contradicted, or silently weakened in `prd.md` + PRD `addendum.md`, after allowing for `.memlog.md` decisions. Deliberate overrides (Claude-only brain, CLI-only text UI, no secret redaction, wake word / voice stop word deferred to V0.2, PRODUCTION rule deferred, old READ/WRITE/DELETE risk engine) are excluded.

Note: much of this material is preserved in `inputs-extract.md`, but that is a working extract, not a PRD deliverable. Architecture will read `prd.md` + `addendum.md`, so anything only in `inputs-extract.md` is treated as a gap.

## High

### H1. Agent loop: Spring AI internal tool execution must be disabled
- Quote: "Spring AI `ChatClient` tool çağrılarını varsayılan olarak kendi içinde yürütür … güven modelinin araya girmesini engeller. Spring AI'nin dahili tool yürütmesi kapatılır."
- PRD state: §5 says frameworks that hide the loop are not used, and §0/§1 treat "LLM istemcisi" as buy-not-build. Nothing says the LLM client must not execute tools itself. A reader can pick Spring AI with default tool execution and silently bypass FR-12/FR-15.
- Land: PRD addendum (architecture notes) + one line in §5 "Kendi kodu": the LLM client returns tool calls; only alfred's loop executes them, after the trust gate.

### H2. Voice runtime contradiction inside PRD addendum (single process vs separate Python services)
- Quote: "Bu bileşenler Python tabanlı olduğu için ayrı servisler olarak çalışmaları Java/Python karışımını da çözer."
- PRD state: PRD addendum "Mimari tercih" lists `ses` as a module and says "hepsi tek bir süreçte ve tek bir dağıtım olarak çalışmalı", while "Docker" section says voice services run in Compose. Brief addendum's rationale (Python engines → separate services) is lost, leaving the contradiction unresolved.
- Land: PRD addendum "Mimari tercih": voice module in the monolith is an adapter/port; STT/TTS engines run as separate (Python) services in Compose.

### H3. Host-native rationale and desktop integration facts missing
- Quotes: "`docker.sock` mount etmek pratikte root yetkisi vermek demektir"; "alfred çekirdeği host'ta native servis olarak çalışır (ör. systemd user service)"; "`swaymsg` IPC (JSON) … `xdotool` gibi X11 araçları çalışmaz. URL açmak için `xdg-open`."
- PRD state: memlog/addendum keep only the conclusion ("alfred host'ta, bağımlılıklar Docker'da"). The why (mic/speaker, desktop, Docker control vs container isolation; docker.sock ≈ root), the systemd user service shape, and the Wayland constraints for FR-11 (browser open) and FR-16 (Super+Esc on Sway) are not in the PRD folder deliverables.
- Land: PRD addendum, new "Mimari için notlar" section (carry brief addendum items 1 and 3 verbatim-ish, marked "mimari aşamada doğrulanacak").

## Medium

### M1. Trait "never arrogant / ukala değil" and "discreet" dropped
- Quote: "Intelligent/Analytical · Calm/Patient · Loyal/Reliable · Discreet · Mildly witty · Proactive · Never arrogant." / "Proaktif ama ukala değil"
- PRD state: FR-1 and addendum persona cover calm, short, evidence-based, dry humour, "efendim". Missing: never arrogant / not a know-it-all, discreet (ketum), patient, loyal/reliable. The "Kısa kısa" Docker example hints at it but no rule forbids lecturing or condescension.
- Land: PRD §4.1 description (trait list) + FR-1 acceptance bullet ("Öneri verirken ukala/öğretici ton kullanmaz; kararı kullanıcıya bırakır").

### M2. Proactive advise pattern and its trigger undefined
- Quote: "Gateway'i başlatabilirim. Ancak son üç çalıştırmada Redis bağlantısı başarısız olmuş. Önce Redis'i kontrol etmemi ister misiniz?" (User → Command, Alfred → Analysis + Recommendation, User → Decision, Alfred → Action)
- PRD state: FR-14 covers "warn once on concrete risk" for T1, and memlog adds proactive pre-checks. The canonical example relies on cross-session history, which conflicts with session-only memory (FR-5). PRD does not say whether the audit log may serve as that evidence, nor give this four-step interaction pattern.
- Land: PRD §4.3/FR-14 (state the pattern) + open question: may alfred read its own audit log as evidence for proactive advice in MVP?

### M3. Voice character traits for MVP Turkish voice silently weakened
- Quote: "Erkek, İngiliz aksanlı, sakin, olgun, sıcak, alçak/orta perde, net diksiyon, hafif resmi. Birebir taklit değil, ilham. … alfred önce Türkçe konuşacak; İngiliz aksanı İngilizce sürüm için geçerli."
- PRD state: only "Alfred'e özgü ses" (deferred) survives. The accent-independent traits (male, calm, mature, warm, low/mid pitch, clear diction, slightly formal) could and should guide MVP Turkish TTS voice choice (e.g. among Piper voices), but nothing in PRD/addendum says so. Brief also says character is distributed to "Voice → TTS stili", which PRD §4.1 keeps only abstractly.
- Land: PRD FR-3 acceptance bullet or addendum "Ses" section: MVP voice selection criteria. English catchphrases ("Very well." "Right away.") → addendum, Later/English.

### M4. Security lessons beyond tiers: egress control and scoped credentials
- Quote: "Sadece read/write/execute katmanı yetmez. Secret izolasyonu, network egress kontrolü ve kapsamı daraltılmış kimlik bilgileri gerekir." (also Cursor incident: agent deleted prod DB "kodda bulduğu alakasız bir token ile")
- PRD state: secret isolation was deliberately overridden (memlog: no redaction). Egress control and scoped credentials were never decided either way; PRD addendum's lesson list omits them. Risk the Cursor incident shows (agent using a credential found in files) is not addressed by any FR.
- Land: PRD §5 "Güvenlik" or open questions: explicit accept/reject for egress control and scoped credentials; architecture note.

### M5. Typed, policy-checked tool layer vs generic shell
- Quote: "Her aksiyon tipli bir Java komutu olur, policy engine onaylar ve audit'e yazılır."
- PRD state: FR-10/FR-12 classify Actions deterministically, but the Tool is a free-form shell. The brief's differentiator (typed commands as the unit of policy) is neither adopted nor rejected; it materially affects how FR-12 classification is built.
- Land: PRD addendum architecture notes (design direction for FR-12) or open question.

### M6. Spring AI 2.0 / Spring Boot 4.1 baseline
- Quote: "Spring AI 2.0. Haziran 2026'da GA oldu (Spring Boot 4.1 ile) … Doğrudan 2.0 ile başlanmalı (Jackson 3, paket değişiklikleri)."
- PRD state: absent from PRD addendum (research-digest only mentions Spring AI generically).
- Land: PRD addendum architecture notes, flagged "doğrulanmadı".

## Low

### L1. Positioning and three roles not carried
- Quote: "Your digital butler and technical advisor." Roles: "Assistant (hatırlat, ara, açıkla) · Advisor (analiz, teşhis, öneri) · Executor (uygula, çalıştır, otomatikleştir)."
- PRD state: Vision says "dijital uşak ve kıdemli teknik yardımcı" (advisor → yardımcı, slight weakening of the advisor stance). The Assistant/Advisor/Executor split is absent; useful for system prompt and for scoping (Assistant "hatırlat" is V1).
- Land: PRD addendum persona section.

### L2. Guiding principle and 7-step flow not stated verbatim
- Quote: "Alfred does not merely execute commands. Alfred understands intent, advises the user, acts when authorized, and verifies the outcome." / "Request → Understand → Analyze → Advise → Execute → Verify → Report."
- PRD state: §4.3 uses hypothesis → evidence → diagnosis → action → verification; Understand/Advise/Report steps are implicit (FR-7, Vision). Not wrong, but the canonical principle is the natural system-prompt anchor.
- Land: PRD §4.1 or §4.3 description; addendum persona.

### L3. System prompt draft and domain scope
- Quote: "You are Alfred. A highly intelligent personal assistant and technical advisor." … "You do not blindly execute commands." Domains: software development, system administration, research, automation, personal productivity.
- PRD state: not in PRD addendum.
- Land: PRD addendum persona section (raw material for prompt design).

### L4. Calm/respectful phrase bank
- Quotes: "Anlaşıldı. Redis yanıt vermiyor gibi görünüyor. Bir göz atayım."; "Hemen kontrol ediyorum." "Sanırım problemi buldum." "Bunu değiştirmeden önce onayınızı almam gerekiyor."; humour sample "Görünen o ki build bugün de işbirliği yapmamaya karar vermiş."
- PRD state: addendum dialogues cover the style, these specific lines are not included. FR-1 uses addendum dialogues as acceptance references, so missing lines narrow the reference set.
- Land: PRD addendum "Örnek diyaloglar".

### L5. Brand name and backronym
- Quote: "Marka: ALFRED. Opsiyonel açılım: Adaptive Logical Framework for Reasoning, Execution & Digital Assistance."
- PRD state: glossary defines alfred/Alfred only; ALFRED brand missing (relevant for README/SM-4).
- Land: PRD §3 glossary (one line) or addendum.

### L6. Voice stop word must be local and offline (for V0.2)
- Quote: "Sesli acil çıkış kod kelimesi, wake word ile aynı lokal anahtar kelime tanıma mekanizmasını kullanır; Claude'a gitmez, ağ yokken de çalışır."
- PRD state: feature correctly deferred to V0.2, but the constraint (local keyword spotting, never via Claude, works offline) is lost.
- Land: PRD §7.2 V0.2 bullet or addendum.

### L7. Voice engine candidates diverge
- Quote: "TTS: Piper arşivlendi; Kokoro / XTTS değerlendirilebilir. … Wake word: openWakeWord (Porcupine'in ücretsiz katmanı kapandı)."
- PRD state: PRD addendum leans to Piper (notes archival) and omits Kokoro. Not a contradiction (newer research), but Kokoro candidate is dropped.
- Land: PRD addendum "Ses" section, candidate list.

### L8. Differentiation ideas with no home
- Quotes: JVM/Spring depth tools ("Maven/Gradle build'leri, Actuator, stack trace'ler, Testcontainers, Flyway"); "event-driven triage … düzeltme önerir"; "alfred'i aynı zamanda bir MCP server olarak sunmak"; "Çözülmüş işleri yeniden yazmamak".
- PRD state: none listed. V0.2 "MCP desteği" is ambiguous (client vs server). Event-driven triage partly maps to V1 proactive notifications.
- Land: PRD §7.2 (clarify MCP client/server; add JVM-depth tools as later candidate) or addendum.

### L9. Local model fallback note
- Quote: "Qwen3 / Qwen3-Coder en güvenilir. 8B ve altındaki modeller tool çağrılarını kaçırıyor. Ana yol cloud model, Ollama ise gizlilik/maliyet fallback'i olmalı."
- PRD state: Claude-only is deliberate, but no "later" entry or addendum note keeps the Ollama fallback idea, which is relevant if open question 5 (budget) fails.
- Land: PRD §7.2 Later, or addendum cost notes.

### L10. Competitive context
- Quote: Goose "Mimari olarak alfred'e çok yakın"; Gemini CLI closed; computer-use covers original phase 8.
- PRD state: §6 mentions OpenClaw/Goose only as non-competitors. Fine for PRD; useful as architecture reference (Goose's MCP-extension model).
- Land: PRD addendum (optional).
