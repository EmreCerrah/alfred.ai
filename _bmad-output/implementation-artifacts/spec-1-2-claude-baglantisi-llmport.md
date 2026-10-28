---
title: 'Story 1.2: Claude bağlantısı: LlmPort ve Haiku 5.5 smoke test''i'
type: 'feature'
created: '2026-10-28'
status: 'done'
baseline_commit: '12ac9151d6a6d10ad98b50c4f086edc2497d5e33'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Karar döngüsünün Claude'la sağlayıcıdan bağımsız konuşabileceği bir port yok ve `claude-haiku-5-5` ile tool çağrısının, prompt caching'in ve Haiku 5.5'in preserved-thinking kurallarının bu yığında çalıştığı kanıtlanmadı.

**Approach:** `llm` modülünde yalnızca domain tipleri açan bir `LlmPort` ve tek bir Claude adapter'ı kur; framework tool yürütmesi yok, tool çağrıları `agent`'a veri olarak döner. Varsayılan testler deterministik (istek gövdesi, append-only geçmiş, hata eşlemesi); gerçek API'ye giden smoke test `@Tag("live")` ile ayrı koşar.

## Boundaries & Constraints

**Always:**
- Public API: `LlmPort` + domain tipleri (`LlmRequest`, `LlmResponse`, `LlmMessage`, `ToolDefinition`, `ToolCall`, `Usage`, `StopReason`, `LlmUnavailable`). Her çağrı `taskId` (UUID) taşır.
- Spring AI ve Anthropic tipleri `llm` dışına çıkmaz; bir mimari test bunu doğrular.
- Model `alfred.llm.model=claude-haiku-5-5`, effort `alfred.llm.effort` (varsayılan `medium`), `max-tokens` config'te; API anahtarı yalnızca `ANTHROPIC_API_KEY` env var'dan (AD-15).
- İstek gövdesinde `temperature`, `top_p`, `top_k` ve assistant prefill yok; `messages` her zaman user turu ile biter.
- Preserved thinking: assistant yanıtının content blokları (thinking dahil, boş metinli olanlar da) sırası ve içeriği değişmeden geçmişe eklenir ve aynen geri gönderilir; system + tools bir Conversation boyunca sabit; geçmiş yalnızca sona eklenir. Tool çıktısı geçmişe eklenmeden önce kırpılır.
- Sabit system + tools öneki için prompt caching.
- Claude'a erişilemezse ya da anahtar yoksa tipli `LlmUnavailable`; istisna yutulmaz. `stop_reason: refusal` tipli `StopReason.REFUSAL` olarak döner.

**Never:**
- Framework'ün tool yürütmesi; `sh -c`; anahtarın repoya, log'a ya da koda girmesi.
- Bütçe/Tur sınırı ve maliyet defteri (Story 1.5); `agent` karar döngüsü (1.3, 1.9).
- `fallbacks` parametresi (Haiku 5.5'te yok).

**Kararlar (Emre, 2026-10-28):**
- **Adapter doğrudan Anthropic Java SDK'dır** (AD-11'in yedek yolu). Gerekçe: Spring AI 2.0.1 `AnthropicChatModel` assistant turunu text → thinking → tool_use sırasıyla yeniden kurar (`AnthropicChatModel.java:769-779`); bu Haiku 5.5'in preserved-thinking kontrolünde geçmiş düzenlemesi sayılır. `com.anthropic:anthropic-java` 2.75.0 açıkça pinlenir; Spring AI bağımlılıkları ve `spring.ai.*` config'i çıkarılır (MCP ile V0.2'de geri gelir). Karar `llm` README'sine ve mimari memlog'a yazılır.
- **Live testler yazılır ama bu oturumda koşulmaz.** Emre `ANTHROPIC_API_KEY` ile `./gradlew liveTest`'i kendisi koşar; story o zamana kadar `review`'da kalır.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Tool çağrısı | Tek tool tanımı, tool gerektiren soru | `LlmResponse` içinde `ToolCall`, `StopReason.TOOL_USE` | N/A |
| Tool sonucu turu | Önceki yanıt + tool sonucu geçmişte | Nihai metin yanıtı, `StopReason.END_TURN` | Preserved-thinking uyumsuzluğu 400 olarak yüzeye çıkar, yutulmaz |
| Append-only | N. ve N+1. istek | N+1'in system, tools ve ilk N mesajı N ile bayt düzeyinde aynı | Deterministik test |
| Yasak alanlar | Herhangi bir istek | Gövdede `temperature`/`top_p`/`top_k` yok, son mesaj user | Deterministik test |
| Caching | Aynı önekle ardışık iki çağrı | İkinci yanıtta `Usage.cacheReadTokens > 0` | Live test |
| Anahtar yok | `ANTHROPIC_API_KEY` tanımsız | `LlmUnavailable` | Daemon yine başlar; hata çağrıda |
| Ağ yok | Erişilemeyen base URL | `LlmUnavailable` (tipli), yeniden deneme sonrası | N/A |

</frozen-after-approval>

## Code Map

- `src/main/java/ai/alfred/llm/package-info.java` -- modül bildirimi; bağımlılığı yok (yaprak), dokunulmaz.
- `src/test/java/ai/alfred/ModularityTests.java` -- `ALLOWED` tablosu; `llm` yaprak kalır. Yeni mimari test buraya eklenir (ArchUnit modulith ile classpath'te).
- `src/main/resources/application.yml` -- `spring.ai.model.chat: none` satırı ve yorumu çıkar; `alfred.llm.*` eklenir.
- `build.gradle.kts` -- `spring-ai-starter-model-anthropic`, `springAiVersion` ve Spring AI BOM çıkar; `com.anthropic:anthropic-java:2.75.0` eklenir.
- Doğrulanan SDK yüzeyi (2.52.0 jar ve skill referansı): `MessageCreateParams.builder().model(String)`, `.thinking(ThinkingConfigAdaptive)`, `.outputConfig(OutputConfig.builder().effort(...))`, `.systemOfTextBlockParams(...)` + `CacheControlEphemeral`, `Tool.builder().cacheControl(...)`, `ContentBlock.toParam()`, `usage().cacheReadInputTokens()`, `AnthropicServiceException`. `block_binding` tipli değil: `putAdditionalProperty`.
- Haiku 5.5 kuralları: thinking varsayılan açık (adaptive), effort varsayılan `medium`, örnekleme parametreleri ve prefill 400; en küçük cache'lenebilir önek 512 token, bu yüzden live testin system + tools öneki bunun üstünde olmalı.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/ai/alfred/llm/{LlmPort,LlmRequest,LlmResponse,LlmMessage,ToolDefinition,ToolCall,Usage,StopReason,LlmUnavailable}.java` -- domain tipleri (record / sealed); assistant mesajı sağlayıcıya özgü blokları `llm`-içi opak bir değerle taşır, `agent` onu açmadan geri verir -- AD-2, AD-11
- [x] `src/main/java/ai/alfred/llm/internal/ClaudeLlmAdapter.java` (+ istek eşleyici, `LlmProperties`) -- `client.beta().messages()` ile istek kurar: adaptive thinking + effort, sabit önekte cache, yasak alan yok, `block_binding.prefix_mismatch_behavior=error` (beta `thinking-binding-controls-2026-08-01`); hata eşleme -> `LlmUnavailable` -- AD-11, AD-12
- [x] `src/main/resources/application.yml`, `build.gradle.kts` -- `alfred.llm.*`; bağımlılıklar; `liveTest` Gradle görevi (`@Tag("live")`), varsayılan `test` onu dışlar -- AC
- [x] `src/test/java/ai/alfred/llm/internal/ClaudeRequestMappingTests.java` -- yasak alanlar, son mesaj user, append-only bayt öneki, thinking bloklarının sırası/boş metni korunur -- matris
- [x] `src/test/java/ai/alfred/llm/internal/ClaudeLlmAdapterFailureTests.java` -- anahtar yok ve erişilemeyen host -> `LlmUnavailable` -- matris
- [x] `src/test/java/ai/alfred/llm/ClaudeSmokeLiveTests.java` -- `@Tag("live")`: tool çağrısı, tool sonucu turu -> metin, ikinci çağrıda cache okuması -- matris
- [x] `src/test/java/ai/alfred/ModularityTests.java` -- `llm` dışında `org.springframework.ai..` / `com.anthropic..` import'u yok -- AC
- [x] `src/main/java/ai/alfred/llm/README.md`, `_bmad-output/planning-artifacts/architecture/architecture-Alfred.ai-2026-10-12/.memlog.md` -- her LLM işleminin tek yolu ve doğrudan SDK kararının gerekçesi -- AD-11

**Acceptance Criteria:**
- Given temiz checkout, when `./gradlew build`, then live testler koşmaz ve geri kalan her şey geçer.
- Given `ANTHROPIC_API_KEY` tanımlı, when `./gradlew liveTest`, then smoke set geçer.

## Implementation Notes

- **Paket yerleşimi (spec'ten sapma):** Adapter, eşleyici, `LlmProperties` ve `LlmConfiguration` `ai.alfred.llm.internal` yerine `ai.alfred.llm` içinde, ama **package-private**. Sebep: `ProviderContent`'in constructor'ı ve `blocks()` erişimcisi package-private; böylece içeriği yalnızca `llm` okuyabiliyor, `agent` ise yalnızca taşıyabiliyor. `internal` alt paketinde bu erişim mümkün olmazdı. Modulith açısından package-private tipler zaten public API değil. Testler de aynı pakette.
- **Spec'e eklenen tipler:** Public API'ye `ToolResult` (tool sonucunu modele geri göndermek için) ve `LlmRequestRejected` (400/404/422; ör. düzenlenmiş geçmiş) eklendi. `LlmUnavailable` ağ, kimlik doğrulama, 429 ve 5xx durumlarını kapsıyor.
- **SDK:** `anthropic-java` 2.75.0 ile beta endpoint. `BetaThinkingConfigAdaptive.blockBinding(PrefixMismatchBehavior.ERROR)` tipli olarak var, `putAdditionalProperty` gerekmedi. Beta header'ı `thinking-binding-controls-2026-08-01`.
- **Caching:** System bloğunda tek bir `cache_control` noktası var (render sırası tools → system). Live testteki system prompt'u Haiku 5.5'in 512 token alt sınırının üstünde.
- **Tool şeması:** `ToolDefinition.inputSchemaJson` ayrıştırılıyor; `type` dışındaki anahtarlar `InputSchema`'ya aynen aktarılıyor. Bir test gövdeyi doğruluyor.
- **Kırpma:** Deterministik. İlk N karakter tutuluyor ve sona `[truncated: N of M characters shown]` ekleniyor. Story 1.8, kırpmayı ayrıca `capability` tarafında da yapacak.
- **Gradle:** `test` görevi `live` etiketini dışlıyor. `liveTest` görevi yalnızca o etiketi koşuyor.
- **Kaldırılanlar:** `spring-ai-starter-model-anthropic`, Spring AI BOM ve `spring.ai.model.chat` config'i.
- **Doğrulama:** `./gradlew build` BUILD SUCCESSFUL, 25 test geçti (yeni testler: llm 8, mimari 1). Daemon context'i `ANTHROPIC_API_KEY` olmadan açılıyor. **Live testler bu oturumda koşulmadı** (Emre'nin kararı). Matristeki "Tool çağrısı", "Tool sonucu turu" ve "Caching" satırlarını yalnızca `ClaudeSmokeLiveTests` kapsıyor; bu satırlar Emre `./gradlew liveTest`'i koşana kadar doğrulanmamış sayılır.
- **Mimari kayıt:** Gerekçe `llm/README.md`'de ve architecture `.memlog.md`'de.
- **Review patch'leri** (Triage #1–#17): `LlmProperties` başlangıçta doğrulanıyor; adapter 413'ü Rejected sayıyor, client'ı kapatıyor ve anahtar yoksa uyarıyor; `toResponse` try içinde; `CONTEXT_WINDOW_EXCEEDED` eklendi; istek düzeyinde otomatik `cache_control` eklendi; kırpma surrogate çiftini bölmüyor; şema ve null kontrolleri eklendi; `ProviderContent` eşitliği tanımlı. Testler: HTTP stub'ı ile durum kodu eşlemesi, stop reason, tool sonucu gövdesi, config bağlama. `./gradlew build`: 49 test geçti.

## Spec Change Log

## Review Triage Log

| # | Kaynak | Bulgu | Verdict | Kanıt / Rota |
|---|---|---|---|---|
| 1 | verification-gap, blind | HTTP durum → istisna eşlemesi test edilmiyor; 400'ün `LlmUnavailable`'a düşmesi fark edilmez | medium | Önceden doğrulanmış. **patch**: yerel `HttpServer` stub'ı ile 400/404/413/422 → Rejected, 401/403/429/500/529 → Unavailable |
| 2 | verification-gap, blind | Stop reason eşlemesi yalnızca TOOL_USE için test ediliyor | medium | Önceden doğrulanmış. **patch**: parametreli test |
| 3 | verification-gap, blind | Tool sonucunun kırpılması ve `is_error` istek gövdesinde doğrulanmıyor | medium | Önceden doğrulanmış. **patch**: `/messages/2` testi |
| 4 | verification-gap | `application.yml` `alfred.llm.*` varsayılanları bağlanıp kontrol edilmiyor | medium | Önceden doğrulanmış. **patch**: `Binder` testi |
| 5 | edge-case, blind | 413 `LlmUnavailable` sayılıyor; aynı istek hep aynı şekilde başarısız olur | low | Doğrulandı. **patch**: 413 → Rejected |
| 6 | edge-case, blind | `AnthropicClient` kapatılmıyor; OkHttp thread'leri sızar | low | Doğrulandı. **patch**: `DisposableBean.destroy` |
| 7 | edge-case, blind | `LlmProperties` doğrulanmıyor; effort yazım hatası her çağrıda 400 olur, `max-tool-result-chars` ≤ 0 `trim`'i patlatır | medium | Doğrulandı. **patch**: compact constructor doğrulaması (bilinen effort kümesi, pozitif sınırlar) |
| 8 | edge-case | Env'den gelen anahtar boşluk içeriyorsa her çağrı 401 | low | Gerçek, tek satırlık düzeltme. **patch**: `strip()` |
| 9 | edge-case, blind | `trim` surrogate çiftini bölebilir | low | Doğrulandı. **patch** |
| 10 | edge-case, blind | `ToolResult`/`ToolCall` null kontrolü yok | low | Doğrulandı, doğrudan düzeltme. **patch** |
| 11 | edge-case, blind | `"null"` ya da object olmayan şema sessizce değişiyor veya NPE veriyor | low | Doğrulandı. **patch**: açık `IllegalArgumentException` |
| 12 | edge-case | `toResponse` try dışında; okunamayan yanıt eşlenmemiş istisna olarak çıkar | low | Doğrulandı. **patch**: try içine alındı (`AnthropicException` → Unavailable) |
| 13 | edge-case, blind | `model_context_window_exceeded` `OTHER`'a düşüyor | low | Gerçek; büyüyen append-only geçmiş için gerçekçi. **patch**: `CONTEXT_WINDOW_EXCEEDED` |
| 14 | blind | Yalnızca sabit önek önbelleğe alınıyor; tool döngüsünün geçmişi her turda yeniden faturalanıyor | low | Gerçek. Düzeltme tek satır: istek düzeyinde otomatik `cache_control`; mesaj baytlarına dokunmaz. **patch** |
| 15 | blind | `ProviderContent` equals/hashCode yok | low | Gerçek; record eşitliği kimliğe düşüyordu. **patch** |
| 16 | blind | API anahtarı yokken başlangıçta uyarı yok | low | Tek satır log. **patch** |
| 17 | blind | Live test ilk yanıtta thinking bloğu olduğunu doğrulamıyor | low | Gerçek; sıralama riskini gözlemlenebilir yapar. **patch** |
| 18 | edge-case | `toParams`'taki `IllegalArgumentException` kontratta yok | false | Geçersiz şema bir kod hatasıdır; erken ve yüksek sesli `IllegalArgumentException` doğru davranış |
| 19 | edge-case | 400 hesap/kredi hatası Rejected sayılıyor | low | Gerçek ama iki durumda da Eylem yapılmıyor. Ayırt etmek hata mesajında metin eşleştirmesi gerektirir; kırılgan. Reddedildi (1.5/1.9 kullanıcı mesajında yeniden bakılır) |
| 20 | edge-case, blind | `LlmRequest` geçmiş yapısını (ilk mesaj user, tool_result eşleşmesi) doğrulamıyor | low | Gerçek ama API zaten 400 → Rejected; yerel doğrulama karmaşıklık ekler. Reddedildi |
| 21 | edge-case | Boş system ya da user metni | low | API 400 → Rejected. Reddedildi |
| 22 | edge-case | `max_tokens` ile yarım kalmış `tool_use` | low | Çağıran `MAX_TOKENS`'ı görüyor; karar döngüsü (1.9) ele alır. Reddedildi |
| 23 | blind | `taskId` kullanılmıyor | low | AD-23 gereği taşınıyor; maliyet defteri ve Tur sınırı 1.5'te kullanacak. Reddedildi |
| 24 | blind | Live test anahtar yoksa atlanmıyor, kırılıyor; cache okuması garanti değil | low | Kırılması bilinçli (sessiz atlama doğrulama sayılmaz); cache varsayımı kayda geçti. Reddedildi |
| 25 | blind | Beta header'ı, birden çok text bloğunun birleştirilmesi test edilmiyor | low | Header body dışında; davranış SDK'nın. Reddedildi |
| 26 | edge-case | Spec `internal` paketi diyor, kod kök pakette | low | Düzeltme spec'i değiştirmek olur; sapma Implementation Notes'ta gerekçeli. Reddedildi |
| 27 | verification-gap | Live satırlar (tool çağrısı, tool sonucu turu, caching) doğrulanmadı | low | Emre'nin kararı: live testler bu oturumda koşulmadı. Kayıtlı, story `review`'da |

## Verification

**Commands:**
- `JAVA_HOME=~/.jdks/openjdk-25.0.1 ./gradlew build` -- expected: BUILD SUCCESSFUL, live testler hariç
- `./gradlew liveTest` -- expected: smoke set geçer (anahtar gerekli)

**Manual checks (if no CLI):**
- Anthropic Console'da aylık harcama limiti `alfred.llm.monthly-budget-usd` (5 USD) değerinden düşük değil (Emre ayarlar).
