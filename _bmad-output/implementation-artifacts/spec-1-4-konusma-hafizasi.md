---
title: 'Story 1.4: Konuşma hafızası'
type: 'feature'
created: '2026-11-09'
status: 'done'
baseline_commit: '78ff064553fd6c23fa9aad1a53b9ffb1ca6da1e9'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/src/main/java/ai/alfred/llm/README.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Her mesaj bugün boş geçmişle Claude'a gidiyor; alfred "onu" ya da "az önceki" dendiğinde neyi kastettiğini bilemiyor (FR-5).

**Approach:** `memory` modülüne tek, paylaşılan, yalnızca bellekte duran bir Conversation eklenir. `agent` her Task'ta geçmişi isteğe koyar, başarılı yanıtla birlikte sona ekler, "yeni konuşma" komutunu LLM'e gitmeden işler ve geçmiş eşiği aşınca bir kez sıfırlama önerir.

## Boundaries & Constraints

**Always:**
- **Yalnızca ekleme (AD-11):** Geçmiş kırpılmaz, düzenlenmez, sıralaması değişmez. Assistant turu `ProviderContent` ile olduğu gibi saklanır. System ve tools Conversation boyunca sabit kalır.
- **Yaşam döngüsü (AD-16, FR-5):** Yalnızca "yeni konuşma" ya da daemon yeniden başlatması bitirir. Süre sınırı yoktur. `memory` Postgres'e yazmaz.
- **Bağımlılık:** `memory` hiçbir modüle bağımlı olmaz, dolayısıyla `LlmMessage`'ı tanımaz; Conversation mesaj tipi üzerinden generic'tir.
- **Komut:** Metin `persona`'dadır (AD-18), koda gömülmez. Tam eşleşir: baştaki/sondaki boşluk, sondaki `.`/`!` ve büyük-küçük harf yok sayılır (persona dilinin kurallarıyla); `yeni konusma` da kabul edilir. Komut sıradan bir Task'tır (`taskId` + `Reply`, AD-23), Claude'u çağırmaz ve bütçe tükenmişken de çalışır. Task aktifken gelirse diğer mesajlar gibi `AgentBusy` alır.
- **Sıfırlama önerisi:** `alfred.memory.suggest-reset-tokens` son çağrının prompt boyutuyla (input + cache okuma + cache yazma) karşılaştırılır. Eşik aşılınca persona metni yanıtın sonuna bir kez eklenir (bütçe uyarısından sonra); "yeni konuşma" sonrası yeniden verilebilir. Varsayılan 80000: uzun bağlam fiyatının (100K) altında.

**Never:** Otomatik özetleme ya da kırpma. Konuşmayı diske/Postgres'e yazmak. Süreyle sıfırlama. Komutu LLM'e sınıflandırtmak. Tool döngüsü (1.8/1.9).

**Karar (planlama):** Geçmişe yalnızca tamamlanan alışveriş girer: kullanıcı mesajı ve yanıt birlikte, başarıdan sonra eklenir. Başarısız bir istek geçmişte iz bırakmaz, böylece aynı mesajı yeniden yazmak mükerrer tur üretmez.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Referans | "Redis container'ının adı ne?", sonra "Onun loglarını özetle" | İkinci istek: User, Assistant (aynı nesne), User sırasıyla | N/A |
| Sıfırlama | "Yeni Konuşma." | Kısa teyit `Reply`; LLM çağrısı yok; sonraki istekte yalnızca yeni mesaj | N/A |
| Yeniden başlatma | Yeni daemon | Geçmiş boş | N/A |
| Eşik | Prompt > eşik, iki yanıt üst üste | İlk yanıta öneri eklenir, ikincisine eklenmez; geçmiş tam kalır | N/A |
| LLM hatası | Unavailable, bütçe, Tur, rejected | Failure; geçmiş değişmez | Mevcut persona metinleri |
| Kırpılmış yanıt | `MAX_TOKENS`, metin dolu | Yanıt gösterilir, geçmişe eklenir | N/A |
| Eklenemez yanıt | `REFUSAL`, `OTHER` ya da boş metin | Metin varsa gösterilir, yoksa `agent.failed`; geçmişe eklenmez (boş tur sonraki her çağrıyı 400'e düşürür) | N/A |
| Bağlam doldu | `CONTEXT_WINDOW_EXCEEDED` | Failure: "yeni konuşma" önerilir; geçmiş değişmez | N/A |
| Çok uzun prompt | 400/413 → `LlmRequestRejected` | `llm.rejected` metni "yeni konuşma" çıkışını da söyler | N/A |

</frozen-after-approval>

## Code Map

- `src/main/java/ai/alfred/agent/Agent.java` -- `submit`/`run`; değişecek yer. `finish` `emit`'ten önce kalır.
- `src/main/java/ai/alfred/memory/package-info.java` -- boş modül; bağımlılık eklenmez.
- `src/main/java/ai/alfred/persona/Persona.java` -- `text()`, `locale`; komut eşleşmesi buraya.
- `src/main/java/ai/alfred/llm/Usage.java`, `Pricing.java` -- prompt boyutu hesabı tek yerde olmalı.
- `src/main/java/ai/alfred/llm/LlmRequest.java` -- boş ya da Assistant ile biten geçmişi reddeder; değişmez.
- `src/main/java/ai/alfred/llm/MeteredLlmPort.java`, `ClaudeLlmAdapter.bodyLength` -- rezervasyon geçmişle zaten büyüyor; değişmez.
- `src/test/java/ai/alfred/llm/FakeLlmPort.java` -- `StopReason` ve `Usage` senaryolaması eklenecek.
- `src/test/java/ai/alfred/cli/ChannelTests.java` -- paylaşılan context; geçmiş testler arasında birikebilir.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/ai/alfred/memory/Conversation.java`, `MemoryProperties.java` -- thread-safe `Conversation<E>`: `messages()`, `append(...)` (atomik), `reset()`, `claimResetSuggestion(promptTokens)` -- Tek paylaşılan Conversation (AD-9); saat yok.
- [x] `src/main/java/ai/alfred/llm/Usage.java` -- `promptTokens()`; `Pricing` de bunu kullanır -- Tek hesap.
- [x] `src/main/java/ai/alfred/persona/Persona.java`, `persona/tr/messages.properties`, `system-prompt.md` -- `isCommand(key, input)`; metinler: `command.new-conversation`, `conversation.reset`, `conversation.suggest-reset`, `conversation.context-full`; `llm.rejected` güncellenir; prompt'a hafıza ve "sıfırlamayı yalnızca kullanıcı yapar" bilgisi -- Model sıfırladığını iddia etmesin.
- [x] `src/main/java/ai/alfred/agent/Agent.java` -- Matristeki davranış -- Karar döngüsü.
- [x] `src/main/resources/application.yml` -- `alfred.memory.suggest-reset-tokens: 80000`.
- [x] `src/test/java/ai/alfred/memory/ConversationTests.java`, `MemoryModuleTests.java` -- Ekleme, sıfırlama, tek seferlik öneri, eşik doğrulaması; ArchUnit: `memory` JDBC/`java.sql` kullanmaz; `db/migration/memory` yok.
- [x] `src/test/java/ai/alfred/agent/AgentTests.java`, `persona/PersonaTests.java`, `llm/FakeLlmPort.java` -- Matrisin tamamı; komut varyantları, benzer ama eşleşmeyen metinler ("yeni konuşma başlat" LLM'e gider).
- [x] `src/test/java/ai/alfred/agent/ConversationLiveTests.java` -- `@Tag("live")`: iki turlu gerçek çağrı; referans çözülür, thinking blokları ERROR modunda kabul edilir -- Yazılır, koşulmaz.

**Acceptance Criteria:**
- Given iki başarılı mesaj, when üçüncü istek gider, then önceki istek mesajları yeni isteğin aynen önekidir.
- Given Spring context, when uygulama başlar, then tek `Conversation` bean'i `Agent`'a enjekte edilir ve `ApplicationModules.verify()` geçer.
- Given Conversation uzun süre boşta, when yeni mesaj gelir, then geçmiş durur (kodda saat ya da zamanlayıcı yoktur).

## Implementation Notes

- **`memory`:**
  - `Conversation<E>` thread-safe (`synchronized`), saatsiz ve yalnızca eklenebilir; `append` ya hepsini ekler ya hiçbirini.
  - `MemoryProperties` tek `Conversation<?>` bean'ini tanımlar. `agent` onu `Conversation<LlmMessage>` olarak alır; Spring generic eşleşmesindeki gevşek geri dönüş sayesinde. `StartupTests` tek bean'i ve 80000 eşiğini doğruluyor.
- **`agent.Agent`:**
  - Komut sıradan bir Task olarak işleniyor, Claude'u çağırmıyor.
  - Geçmişe yalnızca `END_TURN`/`MAX_TOKENS` ve dolu metin ekleniyor.
  - %80 bütçe uyarısı her sonuçta (başarısızlıklar dahil) gösteriliyor; sıfırlama önerisi ondan sonra geliyor.
- **`persona`:**
  - `isCommand` girdiyi hem `tr` hem `Locale.ROOT` ile küçültüyor, NFC'ye çeviriyor ve `|` ile ayrılmış biçimlerden biriyle tam eşleşme arıyor.
  - System prompt'a "Hafızan" bölümü eklendi.
- **`llm`:** `Usage.promptTokens()` prompt boyutu toplamını tek yerde hesaplıyor; `Pricing` de bunu kullanıyor.
- **Bilinen sınır (Triage #5):** Geçmişin önek garantisi `max-active-tasks: 1` politikasına dayanıyor. Alt ajanlar ya da çoklu Task gelirse Conversation'a nesil sayacı ya da kilit gerekecek.
- **Doğrulama:**
  - `./gradlew build`: 185 test, 0 hata, 2 atlanan (Windows'ta POSIX testleri).
  - `ConversationLiveTests` (`@Tag("live")`) yazıldı ama koşulmadı.

## Spec Change Log

## Review Triage Log

| # | Kaynak | Bulgu | Verdict | Kanıt / Rota |
|---|---|---|---|---|
| 1 | blind, edge-case, verification-gap | `toLowerCase(tr)` ASCII `I`'yı `ı` yapıyor; "YENI KONUSMA" komut sayılmıyor ve Claude'a gidiyor | medium | Doğrulandı: `Persona.normalize`. Spec büyük-küçük harfi yok sayıyor ve ASCII biçimi kabul ediyor. **patch**: `Locale.ROOT` ile de karşılaştır; test |
| 2 | blind, edge-case | `CONTEXT_WINDOW_EXCEEDED` ve boş yanıt yollarında %80 bütçe uyarısı eklenmiyor; ayda bir verildiği için kayboluyor | medium | Doğrulandı: `answer` `budgetWarning`'e bakmadan dönüyor. **patch**: uyarı Failure metnine de ekleniyor; iki test |
| 3 | verification-gap | `suggest-reset-tokens: 80000` varsayılanının context'e ulaştığını hiçbir test doğrulamıyor | low | Ön doğrulanmış; düzeltme tek assert. **patch**: `StartupTests` |
| 4 | blind | Ayrık ş testi ham birleşik karakterle yazılmış; bir araç NFC'ye çevirirse `Normalizer` yolu sessizce test dışı kalır | low | Byte'lar bugün U+0327 içeriyor ama kırılgan. **patch**: `̧` kaçışı |
| 5 | blind, edge-case | `max-active-tasks` > 1 olursa eşzamanlı Task'lar geçmişi iç içe ekler; çağrı sürerken gelen sıfırlama eski alışverişi yeni konuşmaya sızdırır | low | Gerçek ama yalnızca politika değişince: MVP'de değer 1 ve AD-9 tek Task diyor. Düzeltme (nesil sayacı ya da kilit) karmaşıklık ekliyor. Reddedildi; alt ajanlar geldiğinde ele alınmalı |
| 6 | edge-case | Kabul kriteri (önek) yalnızca `max-active-tasks=1` iken tutuyor | low | #5 ile aynı kök neden. Reddedildi |
| 7 | blind | `MAX_TOKENS` yanıtı kesildiği belirtilmeden gösteriliyor | low | Dondurulmuş matris "yanıt gösterilir, geçmişe eklenir" diyor; düzeltme niyeti değiştirir. Reddedildi |
| 8 | blind | Bean `Conversation<?>`; başka bir modül farklı tiple enjekte edebilir | low | Bugün tek tüketici `agent`; ek koruma karmaşıklık. Reddedildi |
| 9 | blind | 100K uzun bağlam fiyatına geçişte ikinci uyarı yok | low | Spec tek öneri istiyor (80K). Reddedildi |
| 10 | blind | `ChannelTests` geçmişi yalnızca `@AfterEach`'te sıfırlıyor | false | `reset()` hem `@BeforeEach` hem `@AfterEach` taşıyor (ChannelTests:97-98) |
| 11 | blind | Sprint durumu `in-progress`, spec bölümleri boş | false | İş akışı bunları step-05'te dolduruyor |
| 12 | blind | Live test `prefix_mismatch_behavior=ERROR` ayarına güveniyor ama diff bunu göstermiyor | false | `ClaudeRequestMapper:50` ERROR gönderiyor (Story 1.2) |
| 13 | blind | Sıfırlama önerisi, geçmişe eklenmeyen bir yanıtta (REFUSAL/OTHER) harcanıyor | false | O yanıt da metinle kullanıcıya gösteriliyor, öneri görülüyor; boş yanıtta `claim` hiç çağrılmıyor |
| 14 | blind | Eşzamanlı ekleme testi yarışı zayıf kanıtlıyor | low | Atomiklik `synchronized` ile yapısal; test bir sigorta. Reddedildi |
| 15 | blind, edge-case | Komut metninde boş alternatif olursa "." girdisi sıfırlamayı tetikler | low | Yalnızca yanlış yapılandırmada; bugünkü metinde boş alternatif yok, boş mesaj zaten `submit`'te reddediliyor. Reddedildi |

## Verification

**Commands:**
- `./gradlew build` (JAVA_HOME=~/.jdks/openjdk-25.0.1) -- expected: tüm testler geçer, `live` hariç.

**Manual checks (if no CLI):**
- `./gradlew liveTest` -- API anahtarıyla Emre koşar.
