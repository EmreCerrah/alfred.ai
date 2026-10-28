---
title: 'Story 1.1: Proje iskeleti ve modül sınırları'
type: 'feature'
created: '2026-10-28'
status: 'done'
baseline_commit: 'd707b6a524029ac1c547bec80343e961047e1e79'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** alfred için henüz kod yok. Sonraki her story'nin üzerine kurulacağı, modül sınırları ilk günden testle korunan ve Postgres'e bağlı çalışan bir zemin gerekiyor.

**Approach:** Spring Initializr ile Gradle Kotlin DSL projesi üret (Boot 4.1.1, Java 25, `ai.alfred`), dokuz Spring Modulith modülünü boş paketler olarak kur, izinli bağımlılık kenarlarını `verify()` ve bir kenar testiyle zorla. Compose'ta pinli Postgres ayağa kaldır, Modulith üzerinden modül başına Flyway koştur ve Postgres yoksa daemon'ın açık bir hatayla başlamamasını sağla.

## Boundaries & Constraints

**Always:**
- Boot 4.1.1, Java 25 toolchain, Spring Modulith 2.1.1, Spring AI 2.0.1. Initializr varsayılanları (Java 17, Groovy) kullanılmaz.
- Modüller `ai.alfred`'in doğrudan alt paketleri; her birinde `package-info.java`. İzinli kenarlar yalnızca: cli→{agent,voice}, voice→{agent,persona}, agent→{trust,persona,memory,llm,capability}, trust→{persona}, capability→{trust}, audit→{agent,trust,capability,llm}; persona, memory, llm hiçbir modüle bağımlı değil; hiçbir modül audit'e bağımlı değil.
- Postgres testleri Testcontainers ile gerçek Postgres'e karşı; Compose ve Testcontainers aynı pinli imajı kullanır.
- Modül başına Flyway (`spring.modulith.runtime.flyway-enabled=true`); `modulith` şeması ve `event_publication` tablosu migration ile oluşturulur.
- Tanımlayıcılar İngilizce/ASCII; config `alfred.<module>.*`.
- DB parolası repoda yok: Compose `.env`'den (gitignore'da), uygulama env var'dan okur; `.env.example` commit edilir.
- **Karar (Emre):** Gradle projesi repo kökündedir (`build.gradle.kts`, `src/`, `compose.yaml` kökte; omurgadaki `alfred/` ağacı repo kökü sayılır).

**Never:**
- Domain kodu, `LlmPort`, HTTP endpoint ya da Claude çağrısı (sonraki story'ler). Spring AI chat auto-config'i bu story'de kapalı kalır.
- Mock DB, `sh -c`, repoda gizli bilgi.
- Modül şemalarını Flyway dışında (ör. `schema-initialization`) oluşturmak.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Sınırlar temiz | Dokuz modül, ihlal yok | `ModularityTests` geçer | N/A |
| `internal` erişimi | Bir modül başka modülün `internal` sınıfını kullanır | `verify()` başarısız, build kırılır | Test fixture ile otomatik kanıtlanır |
| İzinsiz kenar | Ör. persona → agent ya da herhangi → audit | Kenar testi başarısız | N/A |
| Postgres hazır | Compose Postgres çalışıyor | Flyway koşar, `modulith.event_publication` var, context ayağa kalkar | N/A |
| Postgres yok | Erişilemeyen JDBC URL | Daemon başlamaz | Log'da Postgres'in zorunlu olduğunu söyleyen açık mesaj (FailureAnalyzer) |

</frozen-after-approval>

## Code Map

- Repo yeşil alan: kaynak kodu yok. Kökte `_bmad/`, `_bmad-output/`, `.claude/`, `.idea/`, `.gitignore` (yalnızca `.agents/`) var; bunlara dokunulmaz, yalnızca `.gitignore`'a satır eklenir.
- JDK: `~/.jdks/openjdk-25.0.1` mevcut; sistem `java` 1.8. Gradle wrapper'ı bu JDK ile koştur (`JAVA_HOME`), toolchain `languageVersion = 25`.
- Initializr şu an Boot 4.1.2'yi sunuyor, 4.1.1'i değil: 4.1.2 ile üret, `build.gradle.kts`'de plugin sürümünü 4.1.1'e sabitle. Wrapper Initializr zip'inden gelir.
- Modulith Flyway düzeni (`db/migration/__root`, `db/migration/<module>`) ve `event_publication` DDL'i, kurulan Modulith 2.1.1 jar'larından doğrulanır, tahmin edilmez.

## Tasks & Acceptance

**Execution:**
- [x] Initializr çıktısı → `settings.gradle.kts`, `build.gradle.kts`, `gradlew*`, `gradle/wrapper/` -- Boot 4.1.1, Java 25 toolchain, Modulith 2.1.1 BOM (+ `spring-modulith-starter-jdbc`, runtime), Spring AI 2.0.1 BOM, Web, JDBC, Flyway (+ `flyway-database-postgresql`), PostgreSQL, Testcontainers -- mimari stack
- [x] `src/main/java/ai/alfred/AlfredApplication.java` -- `@SpringBootApplication` -- giriş noktası
- [x] `src/main/java/ai/alfred/{agent,trust,persona,capability,llm,voice,memory,audit,cli}/package-info.java` -- `@ApplicationModule(allowedDependencies = …)` ile modülü ve izinli kenarlarını bildir -- AD-1/AD-2
- [x] `src/test/java/ai/alfred/ModularityTests.java` -- `verify()` + gerçek bağımlılık grafiği izinli tabloyla birebir eşleşir testi (boş modüllerde bile tablo kodda durur) -- yasak kenarlar ve audit kuralı
- [x] `src/test/java/ai/alfred/fixtures/violation/**` -- `internal` sınıfa erişen iki modüllü küçük fixture; `ApplicationModules.of(fixture).verify()`'nin fırlattığını kanıtlayan test -- "ihlal build'i kırar" AC'si
- [x] `compose.yaml`, `.env.example`, `.gitignore` -- pinli `postgres` imajı, `127.0.0.1:5432`, kimlik `.env`'den; `.env` ve `build/`, `.gradle/` ignore -- AD-15 ruhu
- [x] `src/main/resources/application.yml` -- datasource env var'dan, `spring.modulith.runtime.flyway-enabled=true`, Spring AI chat auto-config kapalı (1.2 açar) -- başlangıç
- [x] `src/main/resources/db/migration/__root/V1__modulith_event_publication.sql` -- `modulith` şeması + Modulith 2.1.1 Postgres `event_publication` DDL'i; event registry'nin bu şemayı kullanması config'te -- AD-8
- [x] `src/main/java/ai/alfred/PostgresRequiredFailureAnalyzer.java` (+ `META-INF/spring.factories`) -- JDBC bağlantı hatasını "alfred requires PostgreSQL…" açıklamasına çevir -- AD-7
- [x] `src/test/java/ai/alfred/StartupTests.java` -- Testcontainers ile context açılır ve `modulith.event_publication` var; erişilemez URL'de başlatma başarısız ve çıktıda analyzer mesajı var -- I/O matrisi

**Acceptance Criteria:**
- Given temiz checkout ve çalışan Docker, when `./gradlew build`, then tüm testler geçer.
- Given `.env` doldurulmuş, when `docker compose up -d`, then pinli Postgres sağlıklı durumdadır.

## Implementation Notes

- Initializr 4.1.2 ile üretildi; `build.gradle.kts`'de Boot plugin'i 4.1.1'e sabitlendi. Çözülen sürümler: Modulith 2.1.1, Spring AI 2.0.1, `anthropic-java-core` 2.52.0, Flyway 12.4.0, Gradle wrapper 9.8.2. Initializr'ın eklediği `spring-ai-spring-boot-testcontainers` kullanılmadığı için çıkarıldı; event registry için `spring-modulith-starter-jdbc` eklendi.
- Modulith Flyway düzeni jar'dan doğrulandı: `db/migration/__root` önce, sonra `db/migration/<module>`; her modül kendi `flyway_schema_history_<module>` tablosunu kullanır ve yalnızca migration dosyası olan modüller için koşar. Bu story'de yalnızca `__root` var.
- `__root/V1` DDL'i `spring-modulith-events-jdbc` 2.1.1 `schemas/v2/schema-postgresql.sql`'den `modulith` şemasına taşındı (archive tablosu dahil). `spring.modulith.events.jdbc.schema=modulith`, `schema-initialization.enabled=false`.
- Spring AI chat auto-config'i `spring.ai.model.chat=none` ile kapalı (Story 1.2 açar).
- Postgres `postgres:18.6` (Docker Hub'daki en yeni 18.x); PG 18 imajı için volume `/var/lib/postgresql`'e bağlı. Testcontainers aynı sabiti kullanır ve bir test `compose.yaml` ile eşleşmeyi kontrol eder.
- Compose projesine sabit `name: alfred` verildi; aksi hâlde klasör adı (`bmad_test`) kullanılıyordu. AD-20'deki korunan Compose projesi bu ada dayanacak.
- `server.address: 127.0.0.1` şimdiden ayarlandı (AD-10); port seçimi Story 1.3'e bırakıldı. Not: geliştirme makinesinde 8080 başka bir süreçte, smoke run `--server.port=0` ile yapıldı.
- `ALFRED_DB_PASSWORD` varsayılansız zorunlu; testler bunu `@SpringBootTest(properties=…)` ile verir (ServiceConnection gerçek kimliği ezer).
- FailureAnalyzer `SQLException` SQLSTATE sınıfı `08` (bağlantı hatası) üzerinden çalışır; PostgreSQL sürücüsüne derleme bağımlılığı gerekmez. Mesajlar İngilizce (geliştirici log'u, kullanıcı metni değil).
- Fixture testi: Modulith kök tipte `@Modulithic` ister ve varsayılan olarak test sınıflarını içe almaz; `ApplicationModules.of(FixtureApplication.class, location -> true)` kullanıldı. Fixture `ai.alfred` dışında (`fixture.violation`) durur; ana model `ImportOption.DoNotIncludeTests` ile yüklenir.
- Doğrulama: `./gradlew build` BUILD SUCCESSFUL, 7/7 test (Modularity 4, Startup 3). `docker compose up -d --wait` → `alfred-postgres-1` healthy, `127.0.0.1:5432`. Jar gerçek Postgres'e karşı başladı ("Started AlfredApplication"), `modulith.event_publication` ve `_archive` oluştu.
- Review patch'leri (Triage #1–#4, #6–#11): "Postgres yok" testi URL'i komut satırı argümanıyla verir ve context'i kapatır; event registry probe testi eklendi; analyzer 08/28/3D000'ı kapsar ve `PostgresRequiredFailureAnalyzerTests` null yollarını sınar; datasource URL `ALFRED_DB_NAME`'i kullanır; wrapper checksum'ı sabitlendi; image eşleşmesi satır bazında. Compose Postgres 5432'de açıkken `./gradlew build`: 16/16 test geçti; jar gerçek Postgres'e karşı başladı.

## Spec Change Log

## Review Triage Log

| # | Kaynak | Bulgu | Verdict | Kanıt / Rota |
|---|---|---|---|---|
| 1 | edge-case | `SpringApplicationBuilder.properties()` varsayılan özellik; `application.yml` URL'i ezer, test aslında 5432'ye bağlanır | high | Doğrulandı: default properties en düşük öncelik. Compose Postgres açıkken 28P01 → analyzer null → test kırılır. **patch**: komut satırı argümanı |
| 2 | edge-case | Compose Postgres açıkken `./gradlew build` kırılır | high | #1 ile aynı kök neden. **patch** |
| 3 | edge-case | `run()` başarılı olursa context kapanmaz | low | Gerçek, düzeltme doğrudan (try-with-resources). **patch** |
| 4 | verification-gap | Event registry'nin `modulith` şemasını kullandığını hiçbir test sınamıyor | medium | Önceden doğrulanmış; yalnızca `information_schema` sayımı vardı. **patch**: `@ApplicationModuleListener` probe + satır kontrolü |
| 5 | verification-gap | `server.address` loopback bağlaması test edilmiyor | medium | Önceden doğrulanmış; Story 1.3 AC'si. **defer** |
| 6 | blind, edge-case, verification-gap | Analyzer yalnızca SQLSTATE 08; yanlış parola/olmayan DB rehbersiz | low | Gerçek, hint parola diyor; düzeltme doğrudan koşul genişletme. **patch** (28xxx, 3D000) |
| 7 | blind | Analyzer'ın null yolları test edilmiyor | low | Gerçek. **patch**: parametreli unit test |
| 8 | blind, edge-case | `ALFRED_DB_NAME` daemon URL'inde kullanılmıyor | medium | Doğrulandı: URL `/alfred` sabitti. **patch**: `${ALFRED_DB_NAME:alfred}` |
| 9 | blind | Hint ile `.env.example` değişken adları uyuşmuyor | low | #8 ile aynı kök; hint metni güncellendi. **patch** |
| 10 | blind | `distributionSha256Sum` yok | low | Gerçek, tek satır. **patch** |
| 11 | edge-case | `image:` substring eşleşmesi `postgres:18.60`'ı kabul eder | low | Gerçek, doğrudan düzeltme. **patch**: satır eşitliği |
| 12 | blind | Daemon `.env`'i okumuyor | false | Tasarım gereği: spec "uygulama env var'dan okur"; `.env.example` bunu söylüyor; systemd unit (4.2) ortamı sağlayacak |
| 13 | blind | Parola yoksa placeholder hatası, dostça mesaj değil | low | Gerçek ama Spring mesajı değişken adını söylüyor; düzeltme yeni guard ekler. Reddedildi |
| 14 | blind | `ALLOWED` tablosu `allowedDependencies` ile sessizce kayabilir | false | İki yönde de kayma test kırar: annotation'a eklenip tabloya eklenmeyen kenar edge testini, tersi `verify()`'yi kırar |
| 15 | blind | Edge testi boş modüllerde vakum; negatif test yok | low | Gerçek ama mantık tek `isSubsetOf`; yeni fixture karmaşıklık ekler. Reddedildi |
| 16 | blind | 5432 portu sabit | low | Tek kullanıcı makinesi; parametre eklemek karmaşıklık. Reddedildi |
| 17 | blind, verification-gap | `Path.of("compose.yaml")` çalışma dizinine bağlı | low | Gradle ve IDE Gradle runner proje dizininde koşar. Reddedildi |
| 18 | blind | `server.port` sabitlenmemiş | low | Port seçimi Story 1.3'te; bu story HTTP endpoint içermez. Reddedildi |
| 19 | blind | `chat: none` anahtarsız açılışı kanıtlanmamış | false | `WithPostgres` context'i `ANTHROPIC_API_KEY` olmadan açılıyor ve geçiyor |
| 20 | edge-case | `isSubsetOf(null)` belirsiz hata | low | `declaresExactlyTheArchitectureModules` aynı durumu net mesajla önce yakalar. Reddedildi |
| 21 | edge-case | Spec "birebir" diyor, test alt küme | low | Düzeltme spec'i düzenlemek olur. Reddedildi |
| 22 | edge-case | Fixture yolu spec'tekinden farklı (`fixture/violation`) | low | Düzeltme spec'i düzenlemek olur; sapma Implementation Notes'ta gerekçeli. Reddedildi |

## Design Notes

Kenar testi `verify()`'yi tamamlar: `allowedDependencies` boş bırakıldığında Modulith "her şeye izin" anlamına aldığı için persona/memory/llm gibi yaprak modüller ancak açık bir grafik testiyle korunur. Örnek:

```java
Map<String, Set<String>> ALLOWED = Map.of("persona", Set.of(), "cli", Set.of("agent", "voice"), /* ... */);
modules.forEach(m -> assertThat(names(m.getDirectDependencies(modules))).isSubsetOf(ALLOWED.get(m.getIdentifier().toString())));
```

## Verification

**Commands:**
- `JAVA_HOME=~/.jdks/openjdk-25.0.1 ./gradlew build` -- expected: BUILD SUCCESSFUL, Modularity/Startup testleri geçer (Docker Desktop çalışıyor olmalı)
- `docker compose up -d && docker compose ps` -- expected: postgres `healthy`
