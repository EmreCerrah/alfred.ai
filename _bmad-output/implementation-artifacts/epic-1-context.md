# Epic 1 Context: Emre, alfred'e CLI'dan sorar ve kanıta dayalı bir teşhis alır

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Emre terminalden bir soru sorar ("Redis neden durdu?"); alfred yalnızca okuma (Kademe 0) Eylem'leriyle kanıt toplar, çok adımlı bir teşhis koyar ve bunu Alfred üslubuyla anlatır. Durum değiştiren her istek "henüz bu yetkim yok" diyerek reddedilir. Her Eylem write-ahead olarak kayda geçer, bütçe ve Tur sınırı her LLM çağrısından önce uygulanır. Bu epic, sonraki tüm epic'lerin üzerine kurulacağı iskeleti (modül sınırları, güvenli kanal, `LlmPort`, güven kapısı, işlem kaydı) kurar. M1 kontrol noktası: dört haftada bitmezse kapsam daraltılır.

## Stories

- Story 1.1: Proje iskeleti ve modül sınırları
- Story 1.2: Claude bağlantısı: `LlmPort` ve Haiku 5.5 smoke test'i
- Story 1.3: Güvenli CLI kanalı ve Alfred'le ilk sohbet
- Story 1.4: Konuşma hafızası
- Story 1.5: Maliyet, bütçe ve Tur sınırı
- Story 1.6: Write-ahead işlem kaydı
- Story 1.7: Komut ayrıştırma ve Kademe sınıflandırması
- Story 1.8: Okuma tool'larını onaylı yürütme
- Story 1.9: Çok adımlı teşhis
- Story 1.10: "Bugün ne yaptın?"

## Requirements & Constraints

- Tüm yanıtlar Türkçe ve Alfred karakterinde: "efendim" hitabı, sakin, kısa, kanıta dayalı; mizah en fazla bir kısa cümle ve seyrek. Hipotez ile doğrulanmış sonuç ayrılır; kanıt yetersizse açıkça söylenir.
- CLI tek başına çalışır; ince istemci iş mantığı içermez.
- Konuşma yalnızca bellekte tutulur; "yeni konuşma" ya da daemon yeniden başlatması bitirir, süre dolması bitirmez.
- Çok adımlı teşhis: durum, loglar, ilgili config; istek kapsamında kalınır (yalnızca teşhis istendiyse düzeltme önerilir, uygulanmaz).
- Dürüst başarısızlık: durum, denenenler, belirsiz olan, sistemin bırakıldığı durum, sonraki seçenek. Claude, ağ ya da Postgres yoksa hiçbir Eylem yapılmaz.
- Maliyet: aylık 5 USD; %80'de bir kez uyarı, %100'de yeni Task yok; Task başına Tur sınırı varsayılan 15. Ay sınırı Europe/Istanbul takvimine göre.
- İşlem kaydı yalnızca eklenebilir, gizli bilgiler maskeli; satırda zaman, istek, Tool+komut, Tier, onay durumu, sonuç, maliyet, `taskId`, `parentTaskId`.
- Dil koda gömülmez; Türkçe metinler kaynak dosyalarda.
- Windows yalnızca geliştirme makinesi (build, testler, Testcontainers); çalışma ortamı Fedora + Sway.

## Technical Decisions

- **Paradigma:** Hexagonal, Spring Modulith modüler monolit. Base package `ai.alfred`; dokuz modül doğrudan alt paket: `agent`, `trust`, `persona` (core), `capability`, `llm`, `voice`, `memory`, `audit`, `cli`. Modül-içi gizli kod `internal` alt paketinde.
- **İzinli bağımlılık kenarları (başkası yasak):** cli→agent, cli→voice, voice→agent, voice→persona, agent→trust, agent→persona, agent→memory, agent→llm (`LlmPort`), agent→capability (yalnızca `ApprovedAction` alan port), trust→persona, capability→trust, audit→capability (Tool SPI). `audit` diğerlerini yalnızca olayla dinler; hiçbir modül `audit`'e derleme bağımlılığı kurmaz. Döngü yasak.
- **Doğrulama:** `ApplicationModules.of(AlfredApplication.class).verify()` her build'de koşan test; Postgres testleri Testcontainers ile gerçek Postgres'e karşı (mock DB yok).
- **Core saflığı:** `agent`, `trust`, `persona` Spring AI, Anthropic SDK, HTTP istemcisi ya da ses motoru tipi import etmez; Spring AI/Anthropic tipleri `llm` dışına çıkmaz.
- **Stack:** Java 25, Spring Boot 4.1.1, Spring Modulith 2.1.1, Spring AI 2.0.1 (`spring-ai-starter-model-anthropic`), Gradle Kotlin DSL, Flyway + Testcontainers (Boot BOM), PostgreSQL (Compose, sürüm pinli). Model `claude-haiku-5-5`, yalnızca config'te.
- **Veri:** Modül başına Postgres şeması (şema adı = modül adı) ve Flyway migration konumu (`db/migration/<module>/`); `spring.modulith.runtime.flyway-enabled=true`; şemalar migration'da oluşturulur. `event_publication` paylaşılan `modulith` şemasında. Modüller arası JOIN/yazma yok.
- **Başlangıç:** Daemon Postgres ve Flyway'i zorunlu tutar; Postgres yoksa başlamaz.
- **Kontrol yolu senkron, audit olay tabanlı:** olaylar transaction içinde yayımlanır, event publication registry (JDBC) kalıcılaştırır; `ActionStarted` süreç başlamadan commit edilir.
- **Güven kapısı:** `ApprovedAction` yalnızca `trust`'ta üretilir (package-private constructor), tek kullanımlık, seri hâle getirilmez; Kademe 0 için de otomatik. Komut argv'ye ayrıştırılır, `ProcessBuilder` ile çalıştırılır, `sh -c` yok; modellenmeyen yapı ve sarmalayıcı komutlar Tier 2.
- **Kanal:** HTTP yalnızca `127.0.0.1`; başlangıçta rastgele token, 0600 dosya; `Origin`/`Host` doğrulaması; POST + SSE; hatalar `ProblemDetail` (401 token, 403 Origin/Host).
- **Konvansiyonlar:** Tanımlayıcılar İngilizce ve ASCII; sözlük: Konuşma→`Conversation`, Görev→`Task`, Tur→`Turn`, Eylem→`Action`, Kademe→`Tier`, Güvenlik kelimesi→`SafetyWord`, İşlem kaydı→`AuditLog`, OnaylıEylem→`ApprovedAction`. Config `alfred.<module>.*`; ID UUID, zaman `Instant` UTC, para `BigDecimal` USD; olaylar geçmiş zaman `record`. SLF4J, yapısal alanlar `taskId`, `actionId`. API anahtarı env var ya da 0600 dosyada, repoda yok.

## Cross-Story Dependencies

- 1.1 tüm story'lerin zeminidir (modüller, `verify()`, Compose Postgres, modül başına Flyway).
- 1.2 (`LlmPort`) → 1.3, 1.4, 1.5, 1.9. 1.2 smoke test'i başarısız olursa `llm` adapter'ı doğrudan Anthropic SDK'ya geçer; `LlmPort` imzası değişmez.
- 1.6 (audit) → 1.8 (write-ahead yürütme), 1.10 (gün özeti).
- 1.7 (sınıflandırıcı) → 1.8 → 1.9.
- Kapsam daraltma sırası: zorunlu yol 1.1 → 1.2 → 1.3 → 1.6 → 1.7 → 1.8 → 1.9, artı 1.5'in bütçe/Tur sınırları; ertelenebilir: önce 1.10, sonra 1.4, sonra 1.5'teki aylık harcama sorgusu.
- Epic 2, 1.7/1.8'in güven kapısını Kademe 1 ve 2'ye genişletir.
