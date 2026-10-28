# llm modülü

`LlmPort`, alfred'in bir dil modeliyle konuşmasının tek yoludur. Dışarıya yalnızca domain tipleri açılır (`LlmRequest`, `LlmResponse`, `LlmMessage`, `ToolDefinition`, `ToolCall`, `ToolResult`, `Usage`, `StopReason`, `LlmUnavailable`, `LlmRequestRejected`). Anthropic SDK tipleri bu paketin dışına çıkmaz. `ModularityTests.keepsModelProviderTypesInsideTheLlmModule` bunu doğrular.

## Karar: doğrudan Anthropic Java SDK (Spring AI değil)

AD-11'deki birincil yol Spring AI 2.0.1 `ChatModel` idi. Story 1.2'de bu yol bırakıldı ve AD-11'in yedek yolu olan doğrudan SDK seçildi (Emre, 2026-10-28).

- **Neden:** Spring AI 2.0.1 `AnthropicChatModel`, assistant turunu geri gönderirken blokları text → thinking → tool_use sırasıyla yeniden kuruyor (`AnthropicChatModel.java:769-779`). API'nin döndürdüğü sıra ise thinking → text → tool_use. `claude-haiku-5-5`'te her thinking bloğu kendisinden önce gelen her şeye bağlıdır (preserved thinking). Yeniden sıralama bu yüzden geçmişin düzenlenmesi sayılır. 2026-08-31 sonrası açılan hesaplarda tool döngüsünün ikinci turu 400 alır, eski hesaplarda akıl yürütme sessizce kaybolur.
- **Sonuç:** `com.anthropic:anthropic-java` 2.75.0 açıkça pinlendi. Spring AI bağımlılıkları çıkarıldı; MCP (V0.2) ile geri gelebilirler ama `LlmPort` imzası değişmez.

## Her işlemin tek yolu

| İşlem | Yol |
|---|---|
| Mesaj gönderme | `ClaudeLlmAdapter.complete` → `client.beta().messages().create(...)` (beta endpoint, `thinking-binding-controls-2026-08-01`) |
| İstek kurma | `ClaudeRequestMapper.toParams` (deterministik: aynı geçmiş, aynı baytlar) |
| Yanıt okuma | `ClaudeRequestMapper.toResponse`. Bloklar `BetaContentBlock.toParam()` ile `ProviderContent` içinde saklanır |
| Tool çıktısı kırpma | `ClaudeRequestMapper.trim` (`alfred.llm.max-tool-result-chars`) |
| Prompt caching | İki kesme noktası: system bloğundaki `cache_control` sabit tools + system önekini kapsar (render sırası tools → system → messages). İstek düzeyindeki otomatik `cache_control` tool döngüsünde büyüyen geçmişi önbelleğe alır; önceki mesajların baytlarına dokunmaz |
| Maliyet defteri, bütçe, Tur sınırı | `MeteredLlmPort` (tek `LlmPort` bean'i) → `CostLedger` (`llm.call_ledger`) + `Pricing`. Adapter (`ClaudeLlmAdapter`) yalnızca package-private `ModelCall` arayüzünden çağrılır, bu yüzden hiçbir çağrı defteri atlayamaz |
| Aylık harcama sorgusu | `CostLedger.monthToDate()`. Kullanıcıya açan Tool 1.8'de gelir |

## Haiku 5.5 kuralları (AD-11)

- **Thinking:** Adaptive ve varsayılan olarak açık. `block_binding.prefix_mismatch_behavior=error` ile geçmişte bir düzenleme olursa sessizce düşmek yerine 400 alınır; bu `LlmRequestRejected` olarak döner. Effort `alfred.llm.effort` ile ayarlanır, varsayılanı `medium`.
- **Geri gönderme:** Assistant turu, `ProviderContent` içinde API'nin döndürdüğü blokların kendisini, aynı sırayla taşır. Metni boş thinking blokları da buna dahildir. Çağıran taraf bu mesajı olduğu gibi geçmişe ekler.
- **Sabitler:** `system` ve `tools` bir Conversation boyunca sabit kalır. Geçmiş yalnızca sona eklenir.
- **Yasaklar:** `temperature`, `top_p` ve `top_k` gönderilmez. `LlmRequest` assistant ile biten bir geçmişi (prefill) reddeder. `fallbacks` gönderilmez; Haiku 5.5'te yok.
- **Stop reason:** `refusal` → `StopReason.REFUSAL`, `model_context_window_exceeded` → `StopReason.CONTEXT_WINDOW_EXCEEDED`. Diğer bilinmeyen değerler → `OTHER`.

## Bütçe ve Tur sınırı (AD-12, FR-17, FR-18)

1. **Çağrıdan önce:** Tek bir transaction içinde ve bir advisory lock altında şu kontroller yapılır:
   - Task'ın Tur sayısı ≥ `alfred.llm.max-turns-per-task` ise → `LlmTurnLimitReached`.
   - Ayın toplamı (kapanmış maliyetler + bekleyen rezervasyonlar) + bu çağrının rezervasyonu > `alfred.llm.monthly-budget-usd` ise → `LlmBudgetExhausted`.
   - Rezervasyon satırı yazılamıyorsa → `LlmUnavailable`.
2. **Rezervasyon:** En kötü durumdur: tahmini prompt'un tamamı cache'e yazılmış sayılır ve buna `max-tokens` kadar output eklenir. Prompt tahmini, JSON gövde uzunluğunun yarısıdır.
3. **Çağrıdan sonra:** Satır gerçek kullanımla kapanır (`SETTLED`). Model hata verdiyse satır `FAILED` olur, maliyeti 0 yazılır, ama Tur sayılır.
4. **%80 uyarısı:** Ayın toplamı `budget-warning-ratio` oranına ilk ulaştığında, o yanıtta `LlmResponse.budgetWarning` işaretlenir; bu ay başına bir kez olur (`llm.budget_warning`).
5. **Ay:** `alfred.llm.budget-zone` takvimine göre hesaplanır (varsayılan Europe/Istanbul).
6. **Fiyatlar:** `alfred.llm.pricing` altındadır. Prompt 100K token'ı geçerse çağrının tamamı uzun kartla fiyatlanır.
7. **Çökme sonrası:** Daemon bir çağrının ortasında çökerse, `RESERVED` kalan satır tahmini tutarıyla sayılmaya devam eder. Bu bilinçli olarak fail-closed bir tercihtir. Tahmin, `max-tokens` 16000 ile output için ~0.008 USD, artı prompt'un cache yazımı fiyatıdır; büyük prompt'larda bir senti geçebilir.
8. **Ayın son kuruşları:** Rezervasyon en kötü durum olduğu için ayın son ~0.008 USD'si kullanılamaz. Alfred bu durumda "bütçe doldu ya da kalanı bir isteğe yetmiyor" der.
9. **Başarısız çağrılar:** Maliyetleri 0 yazılır (story kararı). Zaman aşımına uğrayan ya da SDK tarafından yeniden denenen (`max-retries`) bir çağrı yine de faturalanmışsa, defter gerçek harcamanın biraz altında kalır. Console'daki aylık limit son güvenlik ağıdır.

## Hatalar

| Durum | Tip |
|---|---|
| `ANTHROPIC_API_KEY` yok; ağ hatası; 401/403; yeniden denemeden sonra 429; 5xx | `LlmUnavailable`. Hiçbir Eylem yapılmaz (FR-9) |
| 400/404/413/422 (ör. düzenlenmiş geçmiş, istek fazla büyük) | `LlmRequestRejected`. Aynı istek tekrar denense de aynı sonucu verir |
| Aylık bütçe dolacak | `LlmBudgetExhausted` (`turnsUsed`, Task'ın kaçıncı adımda durduğunu söyler) |
| Task'ın Tur sınırı doldu | `LlmTurnLimitReached` |
| Maliyet defteri yazılamıyor | `LlmUnavailable` (fail-closed) |

## Testler

- `./gradlew test`: Deterministik testler: istek gövdesi, append-only, stop reason ve config bağlama. Hata eşlemesi yerel bir HTTP stub'ına karşı her durum koduyla denenir. Live testler bu göreve dahil değil.
- Geçersiz `alfred.llm.*` değerleri (bilinmeyen effort, sıfır ya da negatif sınırlar) daemon başlarken hata verir.
- `./gradlew liveTest`: `@Tag("live")` testleri. `ANTHROPIC_API_KEY` gerekir. Her koşu bir sentten çok daha az tutar.
