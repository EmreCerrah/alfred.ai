---
title: 'Story 1.3: Güvenli CLI kanalı ve Alfred''le ilk sohbet'
type: 'feature'
created: '2026-10-28'
status: 'done'
baseline_commit: '4b3e23b22e1baba709d4dce73edcd2314503ceb4'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/src/main/java/ai/alfred/llm/README.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Emre henüz alfred'le konuşamıyor: daemon'a giden bir kanal, isteği Task olarak yürüten bir `agent` ve Alfred'in kişiliğini taşıyan bir `persona` yok.

**Approach:** `cli` modülü yalnızca `127.0.0.1`'de, token ve Host/Origin kontrolüyle korunan bir HTTP API açar (POST + SSE). `agent` her mesajı `TaskRegistry`'de bir Task olarak `LlmPort`'a götürür ve yanıtı SSE ile yayar. `persona` system prompt'u ve sabit metinleri kod dışındaki kaynak dosyalarından verir. İnce `alfred` istemcisi token'ı dosyadan okur, mesajı POST eder ve SSE'den gelen yanıtı terminale yazar.

## Boundaries & Constraints

**Always:**
- HTTP yalnızca `127.0.0.1`'e bind eder. Her başlangıçta rastgele bir token (en az 256 bit) üretilir. Token, portla birlikte yalnızca kullanıcının okuyabildiği bir runtime dosyasına yazılır (POSIX'te 0600).
- Her istek `Authorization: Bearer <token>` taşır; yoksa ya da yanlışsa `401` döner. Token doğruysa ama `Host` bir loopback adı değilse ya da herhangi bir `Origin` başlığı varsa `403` döner. Hatalar `ProblemDetail` biçimindedir. Token karşılaştırması sabit zamanlıdır. Token log'a yazılmaz.
- Sözleşme: `GET /api/events` bir SSE akışıdır (`reply`, `error` olayları; her olay `taskId` taşır). `POST /api/messages` `202` ve `taskId` döner.
- Her istek, `TaskRegistry`'de yeni bir UUID `taskId` ile bir Task olur. Task tamamlandığında, başarısız olduğunda ya da iptal edildiğinde registry'den çıkar. `alfred.agent.max-active-tasks` (MVP: 1) doluyken gelen yeni istek kuyruğa alınmaz, `409` ile reddedilir (AD-9).
- System prompt ve kullanıcıya dönük sabit metinler (ör. LLM erişilemez mesajı) `src/main/resources/persona/<dil>/` altında durur; `agent` bunları `persona` API'sinden anahtarla ister (AD-18). Dil `alfred.persona.language` (varsayılan `tr`).
- Yanıtlar Türkçedir, "efendim" diye hitap eder, aşırı resmî kalıp kullanmaz, kısadır, mizah en fazla bir kısa cümledir (FR-1).
- `LlmUnavailable` → `error` olayı ve persona'dan gelen açıklama; hiçbir Eylem yapılmaz.
- İnce istemci iş mantığı içermez (FR-4). `401` alırsa ya da bağlantı koparsa runtime dosyasını yeniden okur ve kullanıcıya hata göstermeden yeniden bağlanır.
- Kişilik davranışları `@Tag("live")` senaryo testleriyle ve bir kontrol listesiyle elle değerlendirilir; varsayılan build'de koşmaz.

**Never:**
- Konuşma hafızası (1.4), bütçe ve Tur sınırı (1.5), Tool kullanımı (1.8): bu story'de her mesaj tek bir LLM turudur, Tool tanımı gönderilmez.
- CORS açmak; tarayıcıdan erişime izin vermek; token'ı URL'ye koymak.
- Türkçe metni Java koduna gömmek.

**Kararlar (Emre, 2026-10-28):**
- İnce istemci Java ile yazılır, ayrı bir Gradle alt projesi olarak (`client/`): JDK `HttpClient`, Spring yok, küçük bir jar ve `bin/alfred` başlatıcısı (POSIX betiği + Windows `.cmd`). Daemon'ın Spring Modulith modellerine dahil değildir.
- Live persona senaryoları yazılır ama bu oturumda koşulmaz. Emre `./gradlew liveTest` koşana kadar story `review`'da kalır.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Bind | Daemon başlar | Dinlenen adres `127.0.0.1` (deferred-work'teki test) | N/A |
| Token yok / yanlış | `Authorization` yok ya da hatalı | `401` + `ProblemDetail` | N/A |
| Origin / Host | Doğru token, `Origin: https://evil.example` ya da `Host: evil.example` | `403` + `ProblemDetail` | N/A |
| Sohbet | Doğru istek, "Merhaba alfred" | `202 {taskId}`; SSE'de aynı `taskId` ile `reply` olayı | N/A |
| Task yaşam döngüsü | Task biter ya da hata verir | Registry'den çıkar | Test: sonrasında yeni istek kabul edilir |
| Meşgul | Bir Task aktifken ikinci istek | `409` + `ProblemDetail`, kuyruk yok | N/A |
| LLM erişilemez | `LlmPort` `LlmUnavailable` fırlatır | SSE `error` olayı, persona metni | Task registry'den çıkar |
| Token değişti | Client açıkken daemon yeniden başladı | Client dosyayı yeniden okur, isteği bir kez tekrarlar | İkinci `401` kullanıcıya gösterilir |
| Runtime dosyası | Daemon başladı | Dosya var; POSIX'te izinler `rw-------` | Windows'ta kullanıcı profili ACL'i |

</frozen-after-approval>

## Code Map

- `src/main/java/ai/alfred/{agent,persona,cli}/package-info.java` -- boş modüller. İzinli kenarlar: cli→agent, agent→{persona, llm}. Yeni kenar gerekmiyor.
- `src/main/java/ai/alfred/llm/*` -- `LlmPort`, `LlmRequest(taskId, system, tools, messages)`, `LlmUnavailable`, `LlmRequestRejected`. Yeniden kullanılır, değiştirilmez.
- `src/main/resources/application.yml` -- `server.address: 127.0.0.1` zaten var. `server.port` yok, 8080 dev makinesinde dolu. Port `alfred.cli.port` (varsayılan `0`, yani rastgele) olur ve runtime dosyasına yazılır.
- `src/test/java/ai/alfred/ModularityTests.java` -- `ALLOWED` tablosu değişmez.
- `_bmad-output/implementation-artifacts/deferred-work.md` -- bind testi bu story'de kapanır.
- `_bmad-output/planning-artifacts/prds/prd-Alfred.ai-2026-10-07/addendum.md` -- kişilik tanımı ve örnek diyaloglar; system prompt'un ve live senaryoların kaynağı.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/ai/alfred/persona/*` + `src/main/resources/persona/tr/{system-prompt.md,messages.properties}` -- `Persona.systemPrompt()`, `Persona.text(key)`; dil config'ten -- AD-18
- [x] `src/main/java/ai/alfred/agent/*` -- `Agent.submit(text)` → `taskId`; `TaskRegistry` (max-active-tasks, kaldırma); sanal thread'de tek LLM turu; çıkışlar `AgentOutputListener` arayüzüne (agent API'sinde; `cli` uygular) -- AD-9, AD-23
- [x] `src/main/java/ai/alfred/cli/*` -- `RuntimeFile` (port + token, 0600), `ChannelSecurityFilter` (401 → 403 sırasıyla, sabit zamanlı karşılaştırma), `MessagesController` (POST), `EventsController` (SSE emitter'ları, `AgentOutputListener`), `ProblemDetail` hataları -- AD-10
- [x] `client/` alt projesi (`settings.gradle.kts`'e eklenir) + `bin/alfred`, `bin/alfred.cmd` -- REPL (argümansız) ve tek seferlik mod (`alfred "mesaj"`); 401'de ya da kopmada dosyayı yeniden okur -- FR-4
- [x] `src/test/java/ai/alfred/cli/*` -- `RANDOM_PORT` ile gerçek HTTP testleri: bind adresi, 401, 403 (Origin, Host), 202 + SSE `reply`, 409, runtime dosyası izinleri; `LlmPort` sahte bir bean -- matris
- [x] `src/test/java/ai/alfred/agent/*` -- registry yaşam döngüsü, `LlmUnavailable` → `error` -- matris
- [x] `src/test/java/ai/alfred/persona/PersonaLiveTests.java` + `src/test/resources/persona/live-checklist.md` -- `@Tag("live")` addendum senaryoları; hitap, ton, uzunluk, mizah kontrol listesi -- FR-1
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- bind kaydı kapandı notu

**Acceptance Criteria:**
- Given çalışan daemon, when Emre `alfred` yazıp "Merhaba alfred" der, then terminalde Türkçe ve "efendim" hitaplı kısa bir yanıt görünür.
- Given temiz checkout, when `./gradlew build`, then live testler hariç her şey geçer.

## Implementation Notes

- **Sözleşme:** `GET /api/events` bir SSE akışıdır. Abonelikte bir `: connected` yorumu gönderilir; böylece istemci POST etmeden önce abone olduğunu bilir. Olaylar `reply` ve `error`, veri `{"taskId", "text"}`. `POST /api/messages` gövdesi `{"text"}`, yanıtı `202 {"taskId"}`. Diğer durumlar: `400` boş mesaj, `409` meşgul (detail Alfred'in sözleri, `agent.busy`). `401` ve `403`'ü filtre yazar (`application/problem+json`), `400` ve `409`'u Spring `ProblemDetail`.
- **Port ve runtime dosyası:** `alfred.cli.port` varsayılanı `0`, yani her başlangıçta boş bir port. Port ve token `<runtime dir>/daemon.json` dosyasına yazılır. Dosya önce bir geçici dosyaya yazılıp atomik olarak taşınır; POSIX'te dosya 0600, dizin 0700. Kapanışta dosya silinir. Runtime dizini sırasıyla: `ALFRED_RUNTIME_DIR` → `$XDG_RUNTIME_DIR/alfred` → `%LOCALAPPDATA%lfred` → `~/.local/state/alfred`. Daemon (`CliProperties`) ve istemci (`DaemonEndpoint`) bu çözümlemeyi ayrı ayrı yapıyor; iki projede aynı test vakaları bunu sabitliyor.
- **Filtre sırası:** Önce token (`401`), sonra `Host` loopback mu (`403`), sonra herhangi bir `Origin` (`403`). `Host` testi ham soketle yapılıyor, çünkü JDK `HttpClient` `Host` başlığını ayarlamaya izin vermiyor.
- **Bind testi:** Dış IP'ye soketle bağlanma Windows güvenlik duvarında zaman aşımına düşüyor; bu kanıt sayılmaz. Test bunun yerine Tomcat connector'ının bağlandığı adresi okuyor. `deferred-work.md` kaydı kapandı.
- **agent → cli:** Bağımlılık `AgentOutputListener` (agent API'si) üzerinden; `cli`'deki `EventStream` bunu uygular. `agent`, `cli`'ye derleme zamanında bağımlı değil. Bir listener'ın hatası diğerlerini susturmuyor.
- **`TaskRegistry`:** `tryStart` synchronized, böylece iki istek son boş yeri aynı anda alamaz. Task bir sanal thread'de koşar ve `finally` ile registry'den çıkar.
- **Persona:** `Persona.load(lang)` yalnızca diğer modüllerin testleri için public; Spring kendi constructor'ını kullanıyor (`@Autowired`). Sabit metinler `messages.properties` içinde ve UTF-8 okunuyor. Argümanlı bir metin eklenirse `MessageFormat` kuralı geçerli: kesme işaretleri `''` yazılmalı.
- **İstemci:** `client/` alt projesi; JDK `HttpClient` ve Jackson 3, Jackson sürümü Boot BOM'dan geliyor. Bağımlılıklarıyla tek bir jar (`client/build/libs/alfred-client.jar`); başlatıcılar `bin/alfred` (LF) ve `bin/alfred.cmd`. Her mesaj için önce SSE'ye abone olur, sonra POST eder ve kendi `taskId`'sinin olayını bekler. `401` alırsa dosyayı yeniden okuyup bir kez tekrar dener. Çıkış kodları: 0 yanıt, 1 hata ya da ret, 2 daemon yok. Gerçek bir konsolda konsolun karakter setini, pipe'larda ve terminal emülatörlerinde UTF-8'i kullanır. Bu ayar, Git Bash'te Türkçe karakterlerin bozulmasını gidermek için uçtan uca denemede eklendi.
- **Test altyapısı:** `TestcontainersConfiguration` public yapıldı. `FakeLlmPort` (`ai.alfred.llm` test paketinde) diğer modüllerin testleri için senaryolu bir `LlmPort`; `reset()`, paylaşılan Spring context'inde testleri birbirinden ayırıyor.
- **Doğrulama:** `./gradlew build` BUILD SUCCESSFUL, 84 test (daemon 74, client 10). Ayrıca uçtan uca elle denendi: daemon jar'ı + Compose Postgres + `bin/alfred`, anahtar olmadan. Tek seferlik ve REPL modlarında Alfred'in `llm.unavailable` metni doğru Türkçe karakterlerle geldi, çıkış kodu 1. Daemon durduktan sonra istemci "daemon is not running" dedi, çıkış kodu 2. **Live persona senaryoları koşulmadı** (Emre'nin kararı). "Merhaba alfred"e gerçek bir Alfred yanıtı ve kişilik kontrol listesi, Emre `./gradlew liveTest` koşana kadar doğrulanmamış sayılır.
- **Review patch'leri** (Triage #1–#25):
  - Güvenlik filtresi artık her yolu koruyor.
  - Task slotu yanıttan önce boşalıyor.
  - `InvalidMessage` ile boş ya da fazla uzun mesaj (`alfred.agent.max-message-chars`) Alfred'in sözleriyle 400 alıyor.
  - Bozuk JSON da problem+json ile yanıtlanıyor.
  - Runtime dizini değiştirilmiyor, özel değilse reddediliyor; kapanışta yalnızca kendi dosyası siliniyor.
  - İstemci: yanıt zaman aşımı, açık `error` eşleşmesi, `Console.isTerminal()`, reddedilen bağlantıda dosyanın yeniden okunması, REPL çıkış kodları. `EndpointSource` yalnızca test için bir dikiş.
- **Doğrulama:** `./gradlew build` BUILD SUCCESSFUL, 103 test (daemon 88, client 15). 2 POSIX izin testi Windows'ta atlandı; Fedora'da koşacaklar.

## Spec Change Log

## Review Triage Log

| # | Kaynak | Bulgu | Verdict | Kanıt / Rota |
|---|---|---|---|---|
| 1 | blind, edge-case | Filtre ham URI öneki kontrol ediyor; `/api;x=1/messages` ya da `/%61pi/messages` token olmadan controller'a ulaşır | high | Doğrulandı: `getRequestURI()` ham ve `;x=1` içeriyor, Spring MVC ise matrix parametresini atıp eşliyor. **patch**: yol istisnası yok, filtre her isteği koruyor. Testte her iki biçim de 401 |
| 2 | blind | Yalnızca `/api/**` korunuyordu; `/error` gibi diğer yollarda Host/Origin kontrolü yok | medium | #1 ile aynı kök neden. **patch** |
| 3 | blind | Slot, yanıt yayımlandıktan sonra boşalıyor; hemen gelen istek sahte 409 alır | medium | Doğrulandı (testler geçici çözümle bunu örtüyordu). **patch**: `finish` → `emit` sırası; geçici çözümler kaldırıldı |
| 4 | blind, edge-case | `executor.execute` reddedilirse slot sonsuza kadar sızar | low | Doğrulandı. **patch** |
| 5 | edge-case | `Error` yakalanmıyor; ne yanıt ne hata olayı gider, istemci asılı kalır | low | Doğrulandı. **patch**: `RuntimeException \| Error` → `agent.failed` |
| 6 | blind, edge-case, verification-gap | İstemcide zaman aşımı yok; daemon susarsa `alfred` sonsuza kadar bekler | medium | Doğrulandı. **patch**: 5 dk yanıt süresi, çıkış kodu 2 |
| 7 | blind, edge-case, verification-gap | `reply` dışındaki her olay ERROR sayılıp bekleme bitiyor | low | Doğrulandı. **patch**: yalnızca `error` eşleşiyor, bilinmeyenler atlanıyor |
| 8 | blind | JDK 22+ `System.console()` yönlendirmede de dolu; pipe'ta konsol karakter seti seçilebilir | medium | Gerçek (Java 25). **patch**: `Console.isTerminal()` |
| 9 | blind, edge-case | REPL: pipe'ta `> ` stdout'u kirletiyor, çıkış kodu yok sayılıyor, daemon yokken soru sormaya devam ediyor | low | Doğrulandı. **patch** |
| 10 | edge-case | Bağlantı reddedilince (yeni port) dosya yeniden okunmuyor; spec "kopmada yeniden okur" diyor | medium | Doğrulandı, spec'ten sapma. **patch**: reddedilen bağlantıda dosya yeniden okunuyor, port değiştiyse bir kez tekrar deneniyor |
| 11 | edge-case | 202 gövdesinde `taskId` yok ya da SSE JSON bozuk → Jackson istisnası REPL'i düşürür | low | Doğrulandı (Jackson 3 istisnaları unchecked). **patch** |
| 12 | blind, edge-case | Var olan paylaşımlı dizine (ör. `$HOME`) chmod 0700 uygulanıyor | medium | Doğrulandı. **patch**: var olan ve özel olmayan dizin reddediliyor, değiştirilmiyor |
| 13 | blind, edge-case | İkinci daemon dosyanın üzerine yazarsa, ilk kapanan dosyayı siliyor | low | Doğrulandı. **patch**: yalnızca kendi token'ını taşıyan dosya siliniyor. Tek örnek kilidi reddedildi (karmaşıklık) |
| 14 | edge-case | Ayrı yönetim sunucusunun `WebServerInitializedEvent`'i yanlış portu yazar | low | Tek koşul. **patch** |
| 15 | blind | `IllegalArgumentException` handler'ı fazla geniş; boş mesaj hatası İngilizce | low | Doğrulandı. **patch**: `InvalidMessage` + persona metni |
| 16 | blind, edge-case | Mesaj uzunluğunda sınır yok; 1.5'teki bütçe gelene kadar pahalı çağrı riski | medium | Gerçek. **patch**: `alfred.agent.max-message-chars` (20000), persona metniyle 400 |
| 17 | blind | Bozuk JSON gelince yanıt problem+json değil | low | **patch**: `spring.mvc.problemdetails.enabled` + test |
| 18 | blind, edge-case | `MessageFormat` kesme işareti tuzağı | low | **patch**: properties dosyasına kural yazıldı + argümanlı metin testi |
| 19 | edge-case | `bin/alfred` symlink üzerinden çağrılınca jar'ı bulamaz | low | **patch**: `readlink -f` |
| 20 | edge-case | Live testte cümle sınırı 4; kural en fazla 3 | low | Doğrulandı (split son cümleyi ayırmıyor). **patch** |
| 21 | verification-gap | `AlfredClient` çıkış kodları ve stdout/stderr ayrımı test edilmiyor | medium | Önceden doğrulanmış. **patch** |
| 22 | verification-gap | Stub taskId'yi tamponluyor; "önce abone ol" sırası doğrulanmıyor | medium | Önceden doğrulanmış. **patch**: stub artık yalnızca o an abone olanlara gönderiyor |
| 23 | verification-gap | Runtime dizini 0700 kontrolü her durumda geçiyor | medium | Önceden doğrulanmış. **patch**: `RuntimeFileTests` (eksik dizin, 0755 dizin; POSIX'e bağlı) |
| 24 | verification-gap | Kapanışta `daemon.json`'un silinmesi test edilmiyor | low | **patch**: #13 ile birlikte `RuntimeFileTests` |
| 25 | verification-gap | Ölü SSE abonelerinin çıkarıldığı gösterilmiyor | medium | **patch**: `dropsSubscribersThatHaveGoneAway` |
| 26 | blind | İstemci ve daemon çalışma dizinini ayrı ayrı çözüyor; `alfred.cli.runtime-dir` istemciye görünmüyor | low | Gerçek ama iki tarafta aynı test vakaları var. Ortak kaynak alt projeler arası bağımlılık ister. Reddedildi; override için `ALFRED_RUNTIME_DIR` kullanılmalı (notlarda) |
| 27 | blind | Eski (crash) dosya PID ile algılanmıyor | low | İstemci "nothing listens" diyor; zarar küçük. Reddedildi |
| 28 | blind | İstemci gerçek daemon'a karşı uçtan uca test edilmiyor | low | Ayrı Gradle projesi; uçtan uca elle denendi (notlar). Reddedildi |
| 29 | blind | Test geçici dizini temizlenmiyor | low | OS temp; zararsız. Reddedildi |

## Verification

**Commands:**
- `JAVA_HOME=~/.jdks/openjdk-25.0.1 ./gradlew build` -- expected: BUILD SUCCESSFUL
- `./gradlew liveTest` -- expected: persona senaryoları yanıt üretir; kontrol listesiyle elle değerlendirilir (anahtar gerekli)

**Manual checks (if no CLI):**
- Daemon + `alfred` ile uçtan uca bir sohbet (anahtar gerekli).
