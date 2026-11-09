---
title: 'Story 1.5: Maliyet, bütçe ve Tur sınırı'
type: 'feature'
created: '2026-10-28'
status: 'done'
baseline_commit: 'a504eaf64fc4fc23342db67ccf4d830ecd3b2cca'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/src/main/java/ai/alfred/llm/README.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** alfred artık Claude'u gerçekten çağırabiliyor, ama hiçbir çağrı kaydedilmiyor ya da sınırlanmıyor. Döngüye giren bir Task ya da uzun bir ay, 5 USD'lik bütçeyi fark edilmeden tüketebilir.

**Approach:** `llm` modülüne Postgres'te bir maliyet defteri ve tek `LlmPort` bean'i olarak çalışan bir sayaç katmanı eklenir. Bu katman her çağrıdan önce aylık bütçeyi, Task'ın Tur sayısını ve defterin yazılabilirliğini atomik olarak kontrol eder. Çağrı için tahmini maliyeti ayırır (rezervasyon) ve çağrıdan sonra gerçek maliyetle kapatır. Sınır aşılırsa çağrı yapılmaz, tipli bir hata döner (fail-closed). `agent`, hatayı ve %80 uyarısını `persona` metinleriyle kullanıcıya iletir.

## Boundaries & Constraints

**Always:**
- **Defter:** `llm` şemasında, Flyway ile oluşturulur (`db/migration/llm/`). Her LLM çağrısı (yardımcı ve yeniden deneme dahil) bir satır, yani bir Turn'dür. Satır şunları taşır: `taskId`, model, zaman (`Instant`, UTC), token kullanımı (input, output, cache okuma, cache yazma) ve maliyet. Maliyet USD cinsinden `BigDecimal`'dır (FR-17, AD-12).
- **Çağrı öncesi kontrol:** Tek bir transaction içinde ve ay başına bir Postgres advisory lock altında yapılır, böylece eşzamanlı çağrılar sınırı birlikte aşamaz. Sıra:
  1. Task'ın Tur sayısı `alfred.llm.max-turns-per-task` (varsayılan 15) sınırına ulaştıysa → `LlmTurnLimitReached`.
  2. Ayın harcaması, bekleyen rezervasyonlar ve bu çağrının rezervasyonu toplamı `alfred.llm.monthly-budget-usd` (varsayılan 5) değerini aşacaksa → `LlmBudgetExhausted`.
  3. Rezervasyon satırı yazılamıyorsa → `LlmUnavailable`.
  Üç durumda da model çağrılmaz.
- **Rezervasyon:** Tahmini input maliyeti artı `max-tokens` kadar output maliyetidir. Çağrıdan sonra satır gerçek kullanımla kapatılır. Çağrı başarısız olursa maliyet 0 yazılır; Tur yine sayılır.
- **Fiyatlar config'tedir** (`alfred.llm.pricing`), Haiku 5.5'in yayımlanmış fiyatlarıyla:
  - Prompt ≤ 100K token: input 0.10, output 0.50 USD/MTok.
  - Prompt daha uzunsa: 0.50 / 2.50.
  - Cache okuma 0.1x, cache yazma 1.25x input fiyatı.
- **Ay sınırı:** `alfred.llm.budget-zone` saat dilimine göre hesaplanır (varsayılan Europe/Istanbul). Yeni takvim ayında harcama sıfırdan sayılır.
- **%80 uyarısı:** Bir ayda yalnızca bir kez verilir. `llm` sonucu `LlmResponse` içinde işaretler; `agent` bunu persona metniyle yanıtın sonuna ekler.
- **Kullanıcıya iletim:** `agent` her hatayı persona metniyle `AgentOutput.Failure` olarak iletir. Bütçe tükendiyse, Alfred yeni bir iş başlatmadığını ve nedenini söyler. Tur sınırı ve Task sırasında tükenen bütçe FR-9 biçiminde raporlanır.

**Never:**
- Bütçe ya da Tur kontrolünü `agent`'a ya da başka bir modüle dağıtmak.
- Defter yazılamazken çağrı yapmak.
- Fiyatları ya da bütçeyi koda gömmek.

**Karar (Emre, 2026-10-28):**
- "Bu ay ne kadar harcadık?" sorgusu bu story'de yapılmaz. Tool altyapısıyla (1.8) gelecek ve `deferred-work.md`'ye yazılır. Defter, bu sorgunun okuyacağı ay toplamını (`CostLedger.monthToDate`) yine de sağlar.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Kayıt | Başarılı bir çağrı | Defterde `taskId`, token'lar ve gerçek USD maliyet | N/A |
| %80 | Ay toplamı bütçenin %80'ini ilk kez geçti | Yanıt + uyarı metni; aynı ay ikinci kez uyarı yok | N/A |
| %100 | Ay toplamı + rezervasyon > bütçe | Model çağrılmaz, `LlmBudgetExhausted`; Alfred yeni iş başlatmadığını söyler | N/A |
| Task sırasında %100 | Aynı Task'ın sonraki çağrısı sınırı aşacak | Çağrı yapılmaz; FR-9 raporu | N/A |
| Tur sınırı | Task 15 çağrı yaptı | 16. çağrı yapılmaz, `LlmTurnLimitReached`; FR-9 raporu | N/A |
| Defter yazılamıyor | Postgres hatası | Çağrı yapılmaz, `LlmUnavailable` | Fail-closed |
| Eşzamanlı | Sınıra yakınken iki çağrı aynı anda | En fazla biri geçer, toplam sınırı aşmaz | Advisory lock |
| Yeni ay | Europe/Istanbul'da ay değişti | Toplam sıfırdan; %80 uyarısı yeniden verilebilir | N/A |
| Başarısız çağrı | Model hata verdi | Rezervasyon 0 maliyetle kapanır, Tur sayılır | Hata yutulmaz |

</frozen-after-approval>

## Code Map

- `src/main/java/ai/alfred/llm/ClaudeLlmAdapter.java` -- şu an `@Component LlmPort`. Tek `LlmPort` bean'i yeni sayaç katmanı olur; adapter `llm` içinde package-private bir model-çağrısı arayüzüne iner. `ChannelTests`'teki `@Primary FakeLlmPort` bu yüzden çakışmaz.
- `src/main/java/ai/alfred/llm/{LlmResponse,LlmProperties,ClaudeRequestMapper}.java` -- `LlmResponse`'a uyarı bayrağı eklenir. `LlmProperties`'e bütçe, Tur, fiyat ve saat dilimi eklenir, compact constructor'da doğrulanır (1.2'deki kalıp). Input token tahmini için istek gövdesinin JSON uzunluğu kullanılır (`ClaudeRequestMapper.toParams` + `_body()`).
- `src/main/java/ai/alfred/agent/Agent.java` -- `run`'daki catch'ler yeni tiplerle genişler; uyarı yanıta eklenir.
- `src/main/resources/persona/tr/messages.properties` -- yeni metinler: `llm.budget-exhausted`, `llm.turn-limit`, `llm.budget-warning`.
- `src/main/resources/db/migration/__root/V1__...sql` -- Modulith Flyway düzeni hazır; `db/migration/llm/V1__ledger.sql` eklenir ve kendi history tablosunu (`flyway_schema_history_llm`) kullanır.
- `src/test/java/ai/alfred/TestcontainersConfiguration.java` -- Postgres testleri için.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/resources/db/migration/llm/V1__ledger.sql` -- `llm` şeması ve `llm.call_ledger` (rezervasyon/kapanış durumu, token alanları, `reserved_usd`, `cost_usd`), `llm.budget_warning (month PK)` -- AD-8
- [x] `src/main/java/ai/alfred/llm/{CostLedger,Pricing,MeteredLlmPort,LlmBudgetExhausted,LlmTurnLimitReached}.java` -- advisory lock altında kontrol ve rezervasyon, çağrı, kapanış; %80 bayrağı; fiyat hesabı (≤100K / >100K, cache çarpanları) -- AD-12
- [x] `src/main/java/ai/alfred/llm/{ClaudeLlmAdapter,LlmResponse,LlmProperties}.java`, `application.yml` -- adapter package-private arayüze iner; `budgetWarning` bayrağı; yeni config doğrulanır -- AD-12
- [x] `src/main/java/ai/alfred/agent/Agent.java`, `persona/tr/messages.properties` -- yeni hatalar ve uyarı kullanıcıya persona metniyle -- FR-18
- [x] `src/test/java/ai/alfred/llm/PricingTests.java` -- fiyat kademeleri, cache çarpanları, `BigDecimal` -- matris
- [x] `src/test/java/ai/alfred/llm/MeteredLlmPortTests.java` -- Testcontainers + sahte model çağrısı: kayıt, %80 bir kez, %100, Task sırasında %100, Tur sınırı, defter yazılamıyor, eşzamanlılık, yeni ay (sabit saat ile), başarısız çağrı -- matris
- [x] `src/test/java/ai/alfred/agent/AgentTests.java`, `src/main/java/ai/alfred/llm/README.md` -- hata ve uyarı metinleri; README'de "Token sayımı, bütçe, Tur sınırı" satırı güncellenir -- FR-18
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- "Bu ay ne kadar harcadık?" sorgusu 1.8'e ertelendi -- karar

**Acceptance Criteria:**
- Given temiz checkout, when `./gradlew build`, then tüm testler geçer.
- Given defterde bu aya ait satırlar, when ay toplamı sorgulanır, then saat dilimine göre yalnızca bu ayın kapanmış maliyetleri ve bekleyen rezervasyonlar toplanır.

## Implementation Notes

- **Yapı:** `MeteredLlmPort` tek `LlmPort` bean'idir ve `LlmConfiguration`'da kurulur. `ClaudeLlmAdapter` artık `LlmPort` değil, package-private `ModelCall`'dır. Bu yüzden `llm` dışından defteri atlayarak modele ulaşmanın bir yolu yok. `ChannelTests`'teki `@Primary FakeLlmPort` olduğu gibi çalışıyor.
- **Config:** Bütçe, Tur sınırı ve fiyat ayarları `alfred.llm` önekini `LlmProperties` ile paylaşan ayrı bir `MeteringProperties` kaydında. Anahtar adları omurganın Consistency Conventions'ındakiyle aynı (`alfred.llm.monthly-budget-usd`, `alfred.llm.max-turns-per-task`). Ayrı kayıt sayesinde 1.2 testlerindeki positional `LlmProperties` constructor'ları değişmedi.
- **Atomiklik:** Tur sayımı, ay toplamı ve rezervasyon tek bir transaction içinde ve sabit bir anahtarla alınan `pg_advisory_xact_lock` altında yapılıyor. Model çağrısı transaction'ın dışında. Eşzamanlı ikinci çağrı ilk çağrının rezervasyonunu gördüğü için sınırı birlikte aşamıyorlar; bunu bir test kanıtlıyor.
- **Rezervasyon:** En kötü durum alınıyor: tahmini prompt'un tamamı cache yazımı sayılıyor ve `max-tokens` kadar output ekleniyor. Prompt tahmini JSON gövde uzunluğu / 2. AC "sınırı aşacak çağrı yapılmaz" bunu istiyor; bedeli, bütçenin son ~1 sentinin (16000 output token × 0.50 USD/MTok ≈ 0.008 USD) kullanılamaması.
- **Kapanış:** `settle` ya da `settleFailed` başarısız olursa yalnızca log yazılıyor. Çağrı zaten yapıldığı için rezervasyon tahmini tutarıyla sayılmaya devam ediyor (fail-closed). Çökme sonrası `RESERVED` kalan satırlar da aynı şekilde sayılıyor.
- **%80 uyarısı:** `llm.budget_warning` tablosunda `ON CONFLICT DO NOTHING` ile ay başına bir kez veriliyor. Uyarı, eşiği geçiren yanıtın kendisinde işaretleniyor; `agent` persona metnini yanıtın sonuna ekliyor.
- **Agent:** `LlmBudgetExhausted.turnsUsed() == 0` ise `llm.budget-exhausted` ("yeni bir iş başlatmadım"), değilse `llm.budget-exhausted-mid-task` (FR-9 biçimi) kullanılıyor. `LlmTurnLimitReached` için `llm.turn-limit`, sınırı argüman olarak alıyor.
- **Live smoke test:** Adapter'ı doğrudan çağırıyor, bu yüzden defterde kayıt bırakmıyor.
- **Ertelenen:** "Bu ay ne kadar harcadık?" sorgusu (Emre'nin kararı), `deferred-work.md`'ye yazıldı. `CostLedger.monthToDate()` hazır.
- **Doğrulama:** `./gradlew build` BUILD SUCCESSFUL, 120 test (daemon 105, client 15). Yeni testler:
  - `PricingTests` 5.
  - `MeteredLlmPortTests` 8, Testcontainers ile: kayıt, %80 bir kez, %100, Task sırasında %100, Tur sınırı, başarısız çağrı, defter yazılamıyor (tablo yeniden adlandırılarak), eşzamanlılık, Europe/Istanbul'da ay geçişi.
  - `AgentTests` +3, `PersonaTests` +3, `LlmPropertiesTests` +2.
- **Review patch'leri** (Triage #1–#14):
  - Defter: `TransactionException` → `LlmUnavailable`. `Error` da `FAILED` olarak kapanıyor.
  - %80 uyarısı yalnızca kapanmış harcamaya bakıyor ve rezervasyonun ayını kullanıyor.
  - Ret metni ve README düzeltildi.
  - Yeni testler: deterministik lock, ölü DB, çağrı ortasında kırılan defter, tahmin, `Prices` doğrulaması.
  - `./gradlew build`: 127 test (daemon 112, client 15).

## Spec Change Log

## Review Triage Log

| # | Kaynak | Bulgu | Verdict | Kanıt / Rota |
|---|---|---|---|---|
| 1 | verification-gap, edge-case | DB erişilemezse `tx.execute` `CannotCreateTransactionException` (`TransactionException`) fırlatıyor; yalnızca `DataAccessException` yakalandığı için Alfred `agent.failed` diyor | medium | Önceden doğrulanmış. Hâlâ fail-closed, ama tip ve metin yanlış. **patch**: `TransactionException` da `LlmUnavailable`; ölü DataSource ile test |
| 2 | verification-gap | Eşzamanlılık testi olasılıksal; advisory lock silinse de çoğu zaman geçer | medium | Önceden doğrulanmış. **patch**: test lock'u kendi bağlantısında tutuyor, rezervasyonun beklediğini deterministik gösteriyor |
| 3 | verification-gap, blind | `estimatePromptTokens` / `bodyLength` hiç koşturulmuyor | medium | Önceden doğrulanmış. **patch**: tahmin ≥ gövde/2 ve geçmişle büyüyor testi |
| 4 | verification-gap, blind | `settle`, `settleFailed` ve `claimBudgetWarning` hataları test edilmiyor | medium | Önceden doğrulanmış. **patch**: çağrı ortasında defter kırılınca yanıt korunuyor ve modelin kendi hatası yayılıyor testleri |
| 5 | edge-case, verification-gap | `model.call`'dan `Error` gelirse satır sonsuza kadar `RESERVED` kalıyor | low | Doğrulandı. **patch**: `RuntimeException \| Error` |
| 6 | edge-case, blind | %80 eşiği başka çağrıların bekleyen rezervasyonlarıyla da tetikleniyor | low | Doğrulandı. **patch**: uyarı kontrolü yalnızca kapanmış harcamayı topluyor |
| 7 | edge-case | Gece yarısında rezervasyon ile uyarı farklı aylara düşebiliyor | low | Gerçek, düzeltme doğrudan. **patch**: `Reservation.month` uyarıya aktarılıyor (kullanılmayan `turn` alanı da kalktı) |
| 8 | blind, edge-case | `PersonaTests` `llm.turn-limit`'i ve argümanını kontrol etmiyor | low | **patch** |
| 9 | blind | Ret testleri defterde satır yazılmadığını doğrulamıyor | low | **patch** |
| 10 | blind | `PricingTests` yorumu sayılarla uyuşmuyor | low | **patch** |
| 11 | blind | README'deki "çökmede bir sentin altında" iddiası yanlış; ayın son kuruşlarının kullanılamadığı belgelenmemiş | low | Doğrulandı (16000 max-tokens ≈ 0.008 USD). **patch**: README düzeltildi |
| 12 | blind | Ret metni bütçe tam dolmadan "doldu" diyor ve ham config anahtarını Alfred'in ağzından söylüyor | low | **patch**: "doldu ya da kalanı bir isteğe yetmiyor"; config anahtarı kaldırıldı |
| 13 | blind | `Prices` doğrulaması test edilmiyor | low | **patch** |
| 14 | blind | Testlerde tam nitelikli sınıf adları | low | **patch**: import'lar |
| 15 | blind, edge-case | Başarısız çağrı her zaman 0 maliyetle yazılıyor; zaman aşımı ya da 5xx'ten sonra Claude yine faturalamış olabilir | low | Gerçek ama dondurulmuş matris "rezervasyon 0 maliyetle kapanır" diyor; düzeltme niyeti değiştirir. Etkisi çağrı başına en fazla ~1 sent; Console limiti son ağ. README'de belgelendi; **Emre'ye karar olarak sunuldu** |
| 16 | blind, edge-case | SDK `max-retries` yeniden denemeleri ayrı Tur ya da rezervasyon sayılmıyor | low | #15 ile aynı kök neden (faturalanmış ama sayılmamış deneme). README'de belgelendi; **Emre'ye karar olarak sunuldu** |
| 17 | blind | Ara bir `TOOL_USE` yanıtındaki %80 uyarısı çok adımlı Task'ta kaybolabilir | medium | Bugün tek tur olduğu için tetiklenmiyor; 1.9'un karar döngüsünde ortaya çıkar. **defer** (`deferred-work.md`) |
| 18 | edge-case | Tahmin eşiğin altındayken gerçek prompt eşiği geçerse rezervasyon kısa kartla fiyatlanmış olur | maybe-false | Tahmin gövde/2 bir üst sınır; tahmin ≤ 100K ise gerçek token da ≤ 100K olmalı. Sınırın Türkçe ve imza bloklarında tuttuğunu ölçmek gerekir. Etkisi düşük. Reddedildi |
| 19 | blind, edge-case | chars/2 tahmini Türkçe ve imzalar için doğrulanmadı | maybe-false | #18 ile aynı; ilk live harcama verisiyle ölçülür. Reddedildi |
| 20 | blind | Fiyatlar modele bağlı değil; model değişirse yanlış fiyatlanır | low | Tek model (AD-12), config yorumunda belirtilmiş. Model başına harita karmaşıklık ekler. Reddedildi |
| 21 | blind, edge-case | Çökme sonrası `RESERVED` satırlar hiç uzlaştırılmıyor | low | Bilinçli fail-closed; README'de. Reddedildi |
| 22 | blind | `FAILED` satırda hata türü ve `settled_at` yok | low | Bugün okuyan yok; AuditLog (1.6) olay ayrıntısını tutacak. Reddedildi |
| 23 | edge-case | Tahmin sırasında oluşan istisna tipli değil | low | `IllegalStateException` bir kod hatasıdır, yüksek sesli olması doğru. Reddedildi |
| 24 | edge-case | Spec'teki dosya ve kolon adları (`V1__ledger.sql`, `month`) koddakinden farklı | low | Düzeltme spec'i değiştirmek olur. Reddedildi |

## Verification

**Commands:**
- `JAVA_HOME=~/.jdks/openjdk-25.0.1 ./gradlew build` -- expected: BUILD SUCCESSFUL
