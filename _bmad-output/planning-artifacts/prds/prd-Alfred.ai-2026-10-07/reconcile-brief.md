---
title: "Reconciliation: brief -> PRD + addendum"
input: briefs/brief-Alfred.ai-2026-10-05/brief.md (final)
compared: prd.md, addendum.md, .memlog.md
date: 2026-10-08
---

# Reconciliation: brief vs PRD + addendum

Scope: content in the brief that is missing, contradicted or silently weakened in PRD + addendum. Differences explained by a memlog decision are excluded (listed at the end for traceability).

## Gaps

| # | Brief says (short quote) | Status in PRD/addendum | Should land | Severity |
|---|---|---|---|---|
| G1 | "agent döngüsü... Spring üzerinde sıfırdan yazılır"; portfolio message #1: "önce *Spring ekosistemine hâkim*"; "deterministik Java kodu" | Missing. PRD and addendum never mention Spring or Java. §5 "Kendi kodu" keeps "no agent framework" but drops the stack; SM-4 (README) drops the "Spring mastery" portfolio message. No memlog decision overrides it. | PRD §5 Kısıtlar (Java/Spring as constraint) + §2.1 JTBD "Kurucu olarak" / SM-4; addendum Mimari tercih | High |
| G2 | Vision: "Proaktiflik daha fazla yetki değil, daha fazla dikkat demektir"; "alfred fark eder ve danışır, sınırları aşmaz"; growth = "bir ilişkinin derinleşmesi" (remember how Emre likes to work); north star "'Alfred, şuna bir bakar mısın?' demek yetmeli" | Missing. PRD §1 Vision covers learning + personality only; no long-term direction. The invariant that future proactivity/memory (V0.2, V1) must never widen authority is not recorded anywhere, though it constrains V1 design and the trust model. Memlog "Vision" entry is about not competing, does not override this. | PRD §1 Vizyon (one paragraph) + §7.2 note on V1 (proactive != more authority); addendum for relationship/memory narrative | High |
| G3 | Character: "proaktif ama asla ukala değil", "Panik yapmaz", "analitik, ketum", "saygılı ama aşırı resmi değil"; samples "Anlaşıldı. Redis yanıt vermiyor gibi görünüyor. Bir göz atayım." / "Görünen o ki build bugün de işbirliği yapmamaya karar vermiş." | Weakened. FR-1/§4.1 keep calm/short/evidence/dry humour/"efendim", but "never smug/condescending" and "doesn't panic under failure" have no acceptance criterion; brief sample lines not in addendum dialogue set used for acceptance tests. | FR-1 bullets (no condescension, no alarmist tone in incidents); addendum Örnek diyaloglar | Med |
| G4 | Voice: "sakin, olgun, sıcak, net ve hafif resmi bir erkek sesi"; "karakterli bir Türkçe ses bulunamazsa sonradan değiştirilebilsin" | Weakened. FR-3 only says engines are swappable. Memlog defers the *Alfred-like (British butler)* voice, not the baseline MVP voice profile. PRD §4.1 claims "ses karakteri TTS'te" but no FR/criterion defines it. Addendum notes Piper has 3 Turkish voices with no selection criterion. | FR-3 bullet (MVP TTS voice selection criteria: male, calm, mature, warm, clear) + addendum Ses section | Med |
| G5 | Automatic (no asking): "Uygulama başlatmak, tarayıcıda içerik açmak"; "Git ve Docker komutları (geri alınamayanlar hariç)" | Silently weakened via fail-closed. Kademe table lists only a few Git/Docker ops; "uygulama başlatmak", `docker compose up/build`, `git pull/checkout/branch` etc. are unlisted, so FR-12 "bilinmeyen her komut -> Kademe 2" makes them require a security word. Contradicts brief's default-trust stance and risks demo step 4 ("portları değiştir ve tekrar çalıştır") hitting T2. The tier model itself is a memlog decision; the coverage gap is not. | PRD §4.5 table (add app launch, compose up/build, routine git ops to T0/T1) or FR-12 note; full rule list in addendum | Med |
| G6 | "Daha sonra: ... Bilgisayar kontrolü (ekran/fare)" (roadmap item) | Contradicted. PRD §6 Hedef olmayanlar: "Ekranı ya da fareyi kontrol eden bir 'bilgisayar kullanan ajan' değildir" turns a later-roadmap item into a non-goal. No memlog decision. | PRD §6 -> qualify as "MVP'de değil", move to §7.2 Daha sonra | Med |
| G7 | Danışman tavrı: "işini yapar, sonra söyler; her komutta araya girmez. Danışmanlığı yalnızca bir şey fark ettiğinde" + example noticing history ("son üç çalıştırmada Redis bağlantısı başarısız olmuş. Önce Redis'i kontrol edeyim mi?") | Partially covered (FR-14 T1 "yalnızca somut risk"). Missing: (a) an explicit anti-requirement against over-consulting / step-by-step permission asking at T0/T1; (b) noticing context from prior runs/observations before acting (advisor value), which is the brief's flagship advisor example. | FR-14 or new criterion under §4.3; addendum example | Med |
| G8 | Purpose priority: "alfred üç şeye hizmet eder, önem sırasıyla: 1. Öğrenmek 2. Göstermek 3. Kullanmak" | Weakened. PRD lists goals but not their ranking; no tie-break rule for scope trade-offs (e.g. checkpoint "M1 dört haftada bitmezse kapsam daraltılır" — what gets cut first). | PRD §1 or §7.1 Kontrol noktası (priority order as tie-breaker) | Low |
| G9 | Differentiator #2: "Modele güvenmeyen bir güven modeli — teknik olarak en değerli parça"; incidents came from "sınırı modelin kendi muhakemesine bırakmak" | Weakened. Mechanism kept (FR-12), but PRD §1 names only personality as differentiator; the trust model's role as portfolio centrepiece and the rationale are gone. Turkish as differentiator #3 likewise only implicit. | PRD §1 Vizyon (one sentence) / SM-4 (architecture diagram should show the trust gate) | Low |
| G10 | Voice emergency stop (V0.2): "Yerel olarak tanınır, Claude'a gitmez, ağ yokken de çalışır" | Weakened. §7.2 lists "sesli durdurma kelimesi" without these properties. By analogy FR-16 does not state that `Super+Esc` works without Claude/network or while the loop is blocked on an API call. | FR-16 bullet (stop independent of Claude/network); §7.2 V0.2 note | Low |
| G11 | "Kalıcı hafıza (PostgreSQL + vektör arama)" (V0.2) | Detail dropped; PRD §7.2 says only "Kalıcı hafıza". Not in addendum either. | addendum (later-version tech notes) | Low |
| G12 | Roadmap rationale: "M1 ses yerine metinle başlar, çünkü öğrenme hedefi agent döngüsüdür"; "Öğrenmenin büyük kısmı burada"; "Bu bir tahmin, söz değil" | Rationale dropped; M2 silently expanded with cost control + audit log (additive, consistent with memlog FRs, but not noted as a change from brief). | PRD §7.1 (one-line rationale + note M2 expanded) | Low |
| G13 | Demo is "60 saniyelik", 6 exchanges, "tek çekimde" | Implicit latency need (~several seconds per voice round-trip incl. STT, Claude, tools, TTS) not captured as a criterion; only GPU question in §9. | §8 SM-1 or an NFR note; addendum Ses | Low |
| G14 | Learning success: "agent döngüsünü bir başkasına beyaz tahtada anlatabilir" | Replaced by Medium article (memlog: single article evidences both goals). Whiteboard criterion silently dropped; acceptable but not acknowledged. | Optional: SM-2 note | Low |

## Excluded (explained by memlog, not gaps)

- "teknik danışman" -> "kıdemli teknik yardımcı", "ölçülü esprili" -> "kuru İngiliz mizahı", "efendim" address (persona decisions).
- Confirmation: natural-language ask + random word (resolved conflict).
- Three-tier model; reversible file edits with backup; unbacked overwrite = T2 (trust model confirmed).
- "Emre adına mesaj/e-posta göndermek" confirmation rule set aside (no messaging tool in MVP).
- Text UI: CLI only, web UI deferred.
- Host monolith + Docker for dependencies only.
- No redaction / no forbidden folders (accepted risk kept).
- Blog post -> Medium article on DDD + agent loop.
- Alfred-like British voice deferred to later.
