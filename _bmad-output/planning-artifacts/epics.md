---
stepsCompleted: [step-01-validate-prerequisites, step-02-design-epics, step-03-create-stories, step-04-final-validation]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-Alfred.ai-2026-10-07/prd.md
  - _bmad-output/planning-artifacts/prds/prd-Alfred.ai-2026-10-07/addendum.md
  - _bmad-output/planning-artifacts/architecture/architecture-Alfred.ai-2026-10-12/ARCHITECTURE-SPINE.md
---

# alfred.ai - Epic Breakdown

## Overview

Bu belge, alfred.ai'nin PRD ve mimari omurgasından çıkan gereksinimleri uygulanabilir story'lere böler. UX tasarım belgesi yoktur. Arayüz CLI ve sestir; Alfred'in konuşma tarzı PRD addendum'undaki diyaloglarda tanımlıdır.

## Requirements Inventory

### Functional Requirements

FR1: alfred tüm yanıtlarını Alfred karakterinde ve Türkçe verir.
- "Efendim" diye hitap eder ve aşırı resmî kalıplar kullanmaz.
- Panik yapmaz, ketumdur ve ukala değildir. Fark ettiğini dayatmaz, önerir.
- Mizah en fazla bir kısa cümledir ve her yanıtta yer almaz.
- Addendum'daki örnek diyaloglar kabul testlerinde referanstır.

FR2: alfred bir teşhisi kanıtla desteklemeden kesin dille söylemez. Kanıt yetersizse bunu belirtir, hipotezi doğrulanmış sonuçtan ayırır.

FR3: Kullanıcı bas-konuş tuşuyla Türkçe konuşur, alfred Türkçe sesli yanıt verir.
- Tuşa basıldığında "Buyurun?" der.
- Ses motorları değiştirilebilir; motor değişikliği karar döngüsünü etkilemez.
- Kısa komutta, konuşmanın bitişinden yanıtın başlamasına kadar yaklaşık 3 sn geçer.
- Çok adımlı işlerde bu süre içinde bir ara yanıt verir ("Bakıyorum, efendim.").

FR4: Kullanıcı aynı istekleri CLI'dan yazarak verebilir.
- Onay ve durdurma dahil davranış sesli modla aynıdır.
- Ses bileşenleri kapalıyken CLI tek başına çalışır.

FR5: alfred aynı Konuşma içindeki önceki mesajları hatırlar ("onu yeniden başlat" gibi referansları çözer).
- Konuşma, "yeni konuşma" denince ya da alfred yeniden başlatılınca biter. Süre dolması Konuşma'yı bitirmez.
- Konuşma bitince hiçbir şey kalıcı olarak saklanmaz.

FR6: alfred bir sorunu çözmek için birden çok Tool çağrısını sırayla planlar ve her adımın sonucuna göre bir sonrakine karar verir.
- Örneğin "Redis loglarını incele" isteğinde container durumunu, logları ve config'i kendisi kontrol eder.
- Teşhisi söylerken dayandığı kanıtı kısaca belirtir.

FR7: alfred istek kapsamında kalır.
- Yalnızca teşhis istendiyse düzeltmeyi uygulamaz, önerir.
- "Hallet" gibi açık yetki teşhis ve düzeltmeyi birlikte kapsar; kademe kuralları yine geçerlidir.

FR8: alfred durumu değiştiren her Eylem'den sonra sonucu doğrular. Doğrulanmamış bir sonucu başarı olarak bildirmez.

FR9: alfred başaramadığında bunu açıkça kabul eder ve sistemi güvenli bırakır.
- Mesajında şunlar yer alır: durum, ne denendiği, neyin kesin olmadığı, sistemin hangi durumda bırakıldığı ve bir sonraki seçenek.
- Tool hatasında sonucu varsaymaz; mümkünse farklı bir yöntemle yeniden dener.
- Claude'a ya da ağa erişilemezse bunu söyler ve hiçbir Eylem yapmaz.

FR10: alfred shell komutu çalıştırabilir, dosya okuyup yazabilir, Git ve Docker işlemleri yapabilir.
- Her Eylem çalıştırılmadan önce bir Kademe ile sınıflandırılır.
- Tool çıktısı kullanıcıya ham değil, özetlenerek aktarılır.

FR11: Kullanıcı bir sanatçı ya da şarkı istediğinde alfred bir videoyu bulur ve tarayıcıda oynatır.
- Arama sonuç listesi değil, bir video açılır.
- Yalnızca sanatçı verildiyse sanatçının en popüler videosu açılır.

FR12: alfred her Eylem'i kendi deterministik kurallarıyla bir Kademe'ye atar.
- Model kademeyi düşüremez.
- Tanınmayan ya da sınıflandırılamayan Eylem kademe 2'dir.
- `sudo` içeren her Eylem kademe 2'dir.
- Birleşik komutlarda parçaların en yüksek kademesi geçerlidir. `>` dosya yazma sayılır. Ayrıştırılamayan komut kademe 2'dir.

FR13: alfred bir Eylem'i geri alınabilir hâle getirebiliyorsa önce bunu yapar. Örneğin git'te izlenmeyen bir dosyanın üzerine yazmadan önce yedek alır ve Eylem kademe 1'e iner.

FR14: Kademe 1 bir Eylem'de alfred yalnızca somut bir risk fark ederse bir kez uyarır. Kullanıcı ısrar ederse tekrar sormadan uygular.

FR15: Kademe 2 bir Eylem'den önce alfred neyin etkileneceğini, etkisini ve yaptığı doğrulamayı söyler, sonra rastgele bir güvenlik kelimesi ister.
- Eylem yalnızca kelime birebir söylendiğinde ya da yazıldığında çalışır. "Evet" onay sayılmaz.
- Kelime tek kullanımlıktır.
- alfred'in kendi sesi ya da arka plan sesi onay sayılmaz.
- Onay istemi korkutucu değildir.

FR16: Kullanıcı `Super+Esc` ile çalışan Görev'i anında durdurabilir.
- Durdurma devam eden Eylem'i ve sonraki Tur'ları keser.
- Otomatik geri alma yapılmaz.
- Kısayol yeniden atanabilir ve durdurma ağ bağlantısı olmadan da çalışır.

FR17: alfred her Görev'in yaklaşık maliyetini ve ayın toplam harcamasını kaydeder ve istendiğinde söyler.

FR18: Bütçe sınırları:
- Aylık bütçenin %80'inde alfred uyarır.
- %100'de yeni Görev başlatmaz ve nedenini söyler.
- Bir Görev en fazla 15 Tur sürebilir (ayarlanabilir); sınıra ulaşınca durur ve FR9'a göre raporlar.

FR19: alfred her Eylem için yerel ve yalnızca eklenebilen bir kayda satır ekler: zaman, istek, Tool/komut, Kademe, onay durumu, sonuç ve maliyet.
- alfred bu kaydı değiştiremez ve silemez.
- Gizli bilgiler kayda girmeden önce maskelenir.

FR20: Kullanıcı "Bugün ne yaptın?" diye sorduğunda alfred işlem kaydından kısa bir özet verir.

### NonFunctional Requirements

**Performans ve kaynak**
- NFR1: Kısa sesli komutta, konuşmanın bitişinden yanıtın başlamasına kadar yaklaşık 3 sn geçer. Çok adımlı işlerde bu süre içinde ara yanıt verilir.
- NFR2: Makinede GPU yoktur. STT/TTS bu hedefi CPU'da ya da bütçeye uygun bir bulut servisiyle karşılamalıdır.

**Maliyet**
- NFR3: Claude API bütçesi ayda 5 USD'dir. Aylık harcama 5 USD'yi aşmaz (SM-6).
- NFR4: Claude'a yalnızca API anahtarıyla erişilir; Claude Pro aboneliği kullanılmaz.

**Ortam ve platform**
- NFR5: Tek kullanıcı ve tek makine: Fedora + Sway (Wayland). Windows yalnızca geliştirme makinesidir.
- NFR6: Türkçe önceliklidir ama dil koda gömülmez; İngilizce sonradan eklenebilir.
- NFR7: alfred Java ve Spring ile yazılır.
  - Karar döngüsü, Tool katmanı ve güven modeli alfred'in kendi kodudur.
  - LLM istemcisinin kendi başına tool çalıştırması kapalıdır.
  - Core domain dışındaki her şey için hazır araçlar kullanılır.

**Güvenlik ve gizlilik**
- NFR8: Gizlilik, bilinçli bir risk olarak kabul edildi. Okunan dosyaların içeriği Claude'a maskelenmeden gider. Yalnızca işlem kaydı maskelenir.
- NFR9: Üçüncü taraf eklenti, skill ya da Tool pazaryeri yoktur. Web içeriği talimat değil, veri olarak ele alınır.
- NFR10: Güvenlik kelimesi olmadan çalışmış tek bir kademe 2 Eylem bile yoktur (SM-5).

**Dayanıklılık**
- NFR11: Acil durdurma, ağ ya da Claude bağlantısı olmadan da çalışır.
- NFR12: Claude, ağ ya da Postgres erişilemezse alfred nedenini söyler ve hiçbir Eylem yapmaz.

**Kişilik ve kalite**
- NFR13: Basit sesli yanıtlar en fazla 3 cümledir. Teşhis, onay ve başarısızlık mesajları bu sınırın dışındadır.
- NFR14: Maliyeti düşürmek için teşhis kalitesi feda edilmez (SM-C2). Kademe 2 kuralları kolaylık için gevşetilmez (SM-C1).

### Additional Requirements

Bu gereksinimler mimari omurgadan geliyor; AD numaraları omurgaya atıftır.

**Başlangıç projesi (Epic 1 Story 1'i etkiler)**
- Proje Spring Initializr ile üretilir: Spring Boot 4.1.1, Java 25 ve Gradle Kotlin DSL açıkça seçilir. Initializr varsayılanları Java 17 ve Groovy'dir.
- Bağımlılıklar: Spring Modulith 2.1.1, Web, JDBC, Flyway, PostgreSQL driver, Spring AI Anthropic 2.0.1 ve Testcontainers.
- Base package: `ai.alfred`.
- Dokuz modül: `agent`, `trust`, `persona`, `capability`, `llm`, `voice`, `memory`, `audit`, `cli`.
- **M1'in ilk işi bir smoke test'tir:** Spring AI 2.0.1 `ChatModel` ile `claude-haiku-5-5` üzerinden bir tool çağrısı yapılır.
  - Bu test gerekli, çünkü Spring AI 2.0.1 anthropic-java 2.52.0 getiriyor; Haiku 5.5 ise 2.69.0'da eklendi.
  - Test başarısız olursa `llm` adapter'ı doğrudan SDK'ya geçirilir (AD-11).

**Altyapı ve dağıtım**
- Docker Compose: PostgreSQL, STT servisi ve TTS servisi (Python, HTTP). Postgres sürümü scaffold'da pinlenir.
- Daemon bir systemd user service olarak çalışır.
  - `WAYLAND_DISPLAY` ve `SWAYSOCK` değişkenleri `systemctl --user import-environment` ile aktarılır.
  - Daemon başlangıçta Postgres'i ve Flyway'i zorunlu tutar (AD-7).
- Sway kısayolları: bas-konuş (`--release` ile) ve `Super+Esc` → `alfred stop`.

**Modül sınırları ve veri**
- `ApplicationModules.verify()` testi her build'de koşar. Postgres testleri Testcontainers ile çalışır (AD-1).
- Bağımlılık yönleri yalnızca omurgadaki diyagramdakilerdir. Core modüller altyapı tipi import etmez (AD-2).
- Veri sahipliği:
  - Her modülün kendi Postgres şeması ve kendi Flyway migration'ları vardır.
  - Modulith'te modül başına Flyway `spring.modulith.runtime.flyway-enabled=true` ile açılır.
  - `event_publication` tablosu paylaşılan `modulith` şemasında durur.
  - Modüller arası JOIN yoktur (AD-8).

**Güven kapısı**
- `ApprovedAction` yalnızca `trust` içinde üretilebilir (package-private constructor).
  - Ayrıştırılmış argv'yi, `cwd`'yi ve env allowlist'ini taşır.
  - Tek kullanımlıktır.
  - Kademe 0 için de otomatik üretilir.
  - FR13'teki yedek ve yazma adımları aynı `ApprovedAction` içinde sıralı çalışır (AD-3).
- Komutlar gerçek bir shell gramerine göre argv aşamalarına ayrıştırılır.
  - `capability` ayrıştırılmış argv'yi `ProcessBuilder` ile çalıştırır; `sh -c` kullanılmaz.
  - Modellenmeyen yapılar ve sarmalayıcı komutlar (`sudo`, `env`, `xargs`, `docker exec`, `find -exec`) kademe 2'dir (AD-4).
- Güvenlik kelimesi (AD-5):
  - Onay metnini `trust` gerçek komuttan kurar; metni `persona` render eder.
  - Yanlış kelimede yeni bir kelime üretilir; 3 yanlış denemede Eylem iptal edilir.
  - Kelime hiçbir kanalda LLM'e, `memory`'ye ya da log'a gitmez.
  - Durdurma bekleyen tüm kelimeleri geçersiz kılar.
- `TierFactProbe` port'unu `trust` tanımlar, `capability` uygular. Probe yanıt vermezse Eylem kademe 2'dir (AD-19).
- Korunan kaynaklar: alfred'in kendi gizli dosyaları, config'i, veritabanı ve Compose projesi DENIED'dır. Durum okuma kademe 0'dır (AD-20).
- Tool'lar yalnızca first-party olabilir ve çalışma anında yüklenmez (AD-21).

**İşlem kaydı (audit)**
- Kontrol yolu senkron çalışır. Audit olayları bir transaction içinde yayımlanır ve Modulith event publication registry ile kalıcılaştırılır (AD-6).
- Write-ahead kayıt: `ActionStarted` süreç başlamadan önce commit edilir.
  - Postgres yoksa hiçbir Eylem yapılmaz.
  - DB rolü kayıt tablosunda yalnızca `INSERT` ve `SELECT` yetkisine sahiptir.
  - Gizli bilgiler maskelenir (AD-7).

**Daemon, kanal ve Görev'ler**
- Daemon ile ince istemci modeli:
  - `TaskRegistry` ve `alfred.agent.max-active-tasks=1` politikası; global bir `currentTask` tekili yok.
  - Görev çalışırken yeni istek gelirse reddedilir ve kullanıcıya sorulur.
  - Ses ve CLI tek bir paylaşılan Konuşma kullanır (AD-9).
- HTTP kanalı (AD-10):
  - Yalnızca `127.0.0.1` dinlenir.
  - Her başlangıçta bir token üretilir ve 0600 izinli bir dosyaya yazılır.
  - `Origin` ve `Host` başlıkları doğrulanır.
  - İstemciden servise POST, servisten istemciye SSE kullanılır.
  - Hatalar `ProblemDetail` biçimindedir.
- Her şey `taskId` taşır; audit olayları ayrıca `parentTaskId` taşır (AD-23).

**LLM, maliyet ve ses**
- `LlmPort` (AD-11):
  - Spring AI ve Anthropic tipleri `llm` modülünün dışına çıkmaz.
  - Framework'ün tool çalıştırması kapalıdır.
  - Her LLM işleminin tek bir yolu vardır.
  - Haiku 5.5 kuralları: konuşma geçmişi yalnızca eklenir, tool çıktısı eklenmeden önce kırpılır, system ve tools sabit kalır, `temperature`/`top_p`/prefill gönderilmez.
- Model ve bütçe (AD-12):
  - Model `claude-haiku-5-5`'tir ve config'te durur.
  - Prompt caching kullanılır.
  - Maliyet defteri `llm` şemasındadır.
  - Bütçe ve Tur sınırı her LLM çağrısından önce kontrol edilir; kontrol başarısızsa çağrı yapılmaz.
  - Aylık bütçe kontrolü atomiktir.
- Ses (AD-13):
  - STT ve TTS port arkasındadır; Java ile Python servisleri düz HTTP ile konuşur.
  - Motor M3'ün başında CPU benchmark'ıyla seçilir.
  - Seçim ölçütleri: yaklaşık 3 sn hedefi, ses karakteri (erkek, sakin, olgun) ve SM-6 bütçesi.
- Acil durdurma (AD-14):
  - Her Eylem kendi process group'unda ve bir zaman aşımıyla çalışır.
  - Süreç başlamadan hemen önce iptal kontrol edilir.

**Gizli bilgiler, hafıza, dil ve kişilik**
- API anahtarı ortam değişkeninde ya da 0600 izinli bir dosyada durur; repoda bulunmaz (AD-15).
- Konuşma yalnızca bellekte tutulur; kalıcı olan tek kayıt AuditLog'dur (AD-16).
- Kod İngilizce ve ASCII'dir. TR↔EN sözlüğü bağlayıcıdır (AD-17).
- Kişilik metinleri ve kullanıcıya dönük sabit metinler `persona` kaynak dosyalarındadır (AD-18).
- Kademe 1 uyarı durumunu `agent` tutar (AD-22).

**Gözlemlenebilirlik ve konvansiyonlar**
- Loglama SLF4J ile yapılır; yapısal alanlar `taskId` ve `actionId`'dir. Log'a gizli bilgi ve güvenlik kelimesi yazılmaz.
- Config anahtarları `alfred.<module>.*` biçimindedir.
- ID'ler UUID'dir; zaman `Instant` (UTC) olarak, maliyet `BigDecimal` USD olarak tutulur.
- Olay adları geçmiş zamanda, immutable `record` olarak yazılır.

**Kilometre taşlarına bağlı açık işler**
- M3: Güvenlik kelimesinde yankı reddi (alfred'in kendi sesi ya da arka plan sesi onay sayılmamalı) ve STT eşleşme normalizasyonu.
- M4: YouTube video bulma yöntemi (Data API mi `yt-dlp` mi).
- M1–M4: Paketleme (unit dosyası, thin client dağıtımı).

### UX Design Requirements

UX tasarım belgesi yok. Kullanıcıya dönük davranış ve ton FR1, FR2, FR9, FR14, FR15 ve PRD addendum'undaki diyaloglarla tanımlıdır.

### FR Coverage Map

- FR1: Epic 1. Alfred karakteri ve Türkçe yanıt (`persona`).
- FR2: Epic 1. Kanıta dayalı konuşma.
- FR3: Epic 3. Bas-konuş ile ses ve ~3 sn yanıt.
- FR4: Epic 1. CLI ile yazı.
- FR5: Epic 1. Konuşma içi hafıza.
- FR6: Epic 1. Çok adımlı teşhis.
- FR7: Epic 1. İstek kapsamında kalma.
- FR8: Epic 1. Sonucu doğrulama. Epic 2'de durum değiştiren Eylem'lere genişler.
- FR9: Epic 1. Dürüst başarısızlık; Claude, ağ ya da Postgres yoksa Eylem yapılmaz.
- FR10: Tool'lar.
  - Epic 1: okuma tool'ları (shell, Docker, dosya).
  - Epic 2: yazma, Git ve Docker değişiklikleri.
- FR11: Epic 4. YouTube'da video açma.
- FR12: Deterministik sınıflandırma.
  - Epic 1: sınıflandırıcı ve kademe 0 yürütme; diğer kademeler reddedilir.
  - Epic 2: kademe 1 ve 2.
- FR13: Epic 2. Önce geri alınabilir yap (yedek).
- FR14: Epic 2. Kademe 1 uyarısı ve ısrar.
- FR15: Güvenlik kelimesi.
  - Epic 2: CLI.
  - Epic 3: ses ve yankı reddi.
- FR16: Epic 2. Acil durdurma (`Super+Esc`).
- FR17: Epic 1. Görünür maliyet.
- FR18: Epic 1. Bütçe ve Tur sınırları.
- FR19: Epic 1. Write-ahead ve yalnızca eklenebilen işlem kaydı.
- FR20: Epic 1. Gün özeti.

## Epic List

### Epic 1: Emre, alfred'e CLI'dan sorar ve kanıta dayalı bir teşhis alır (M1)

Emre terminalden "Redis neden durdu?" diye sorar. Alfred logları ve container durumunu okur, kanıta dayalı bir teşhis koyar ve bunu kendi üslubuyla anlatır.

alfred bu epic'te yalnızca kademe 0 (okuma) işlemleri yapar. Durumu değiştiren her şeyi "henüz yetkim yok" diyerek reddeder. Her Eylem kayda geçer ve bütçe korunur.

İçerdiği iskeletler:
- Başlangıç projesi ve Spring AI + Haiku 5.5 duman testi
- Güvenli HTTP kanalı (POST ve SSE)
- `persona`
- `LlmPort` ile bütçe ve Tur sınırı
- Karar döngüsü
- Bellekte tutulan Konuşma
- `trust` sınıflandırıcısı (yalnızca kademe 0 yürütülür)
- Write-ahead işlem kaydı

**FRs covered:** FR1, FR2, FR4, FR5, FR6, FR7, FR8, FR9, FR10 (okuma), FR12 (sınıflandırıcı), FR17, FR18, FR19, FR20

### Epic 2: alfred güvenle müdahale eder (M2)

alfred düzeltmeleri uygular: config yazar, container'ı yeniden başlatır, Git işlemleri yapar. Riskli işlemlerde bir kez uyarır. Geri alınamaz işlemlerde güvenlik kelimesini ister. `Super+Esc` her şeyi durdurur.

İçerdikleri:
- Tam `ApprovedAction`
- `TierFactProbe`
- Korunan kaynaklar
- Process group ile durdurma
- Sway kısayolu

**FRs covered:** FR8 (durum değiştiren Eylem'ler), FR10 (yazma, Git, Docker değişiklikleri), FR12 (kademe 1 ve 2), FR13, FR14, FR15 (CLI), FR16

### Epic 3: Emre, alfred'le konuşur (M3)

Emre bas-konuş ile Türkçe konuşur, Alfred sesle yanıt verir. Kısa komutlarda hedef ~3 saniyedir. Çok adımlı işlerde alfred araya "Bakıyorum, efendim" der. Güvenlik kelimesi sesle söylenir ve alfred'in kendi sesi onay sayılmaz.

İçerdikleri:
- Önce CPU benchmark'ı
- STT ve TTS servisleri
- Ses port'ları
- Yankı reddi

**FRs covered:** FR3, FR15 (ses)

### Epic 4: Demo günü: müzik ve tek seferde 60 saniye (M4)

alfred YouTube'da şarkı açar. Arka planda sürekli çalışan bir servis olarak çalışır. 60 saniyelik demo kesintisiz ve tek seferde çalışır.

İçerdikleri:
- YouTube Tool'u
- systemd paketleme
- Uçtan uca demo provası

**FRs covered:** FR11; SM-1 (demo)

## Epic 1: Emre, alfred'e CLI'dan sorar ve kanıta dayalı bir teşhis alır

Emre terminalden bir soru sorar. Alfred yalnızca okuma (Kademe 0) yaparak kanıt toplar ve teşhisi kendi üslubuyla anlatır.

Bu epic'teki kurallar:
- Durum değiştiren her istek reddedilir.
- Her Eylem, çalıştırılmadan önce kayda geçer (write-ahead).
- Bütçe ve Tur sınırı her LLM çağrısından önce uygulanır.

**Kapsam daraltma sırası (M1 dört haftada bitmezse):**
- Zorunlu yol: 1.1 → 1.2 → 1.3 → 1.6 → 1.7 → 1.8 → 1.9.
- 1.5'in bütçe ve Tur sınırları da zorunludur, çünkü bütçeyi korurlar.
- Gerekirse sonraya kaydırılabilecekler: önce 1.10, sonra 1.4, sonra 1.5'teki "Bu ay ne kadar harcadık?" sorgusu.

### Story 1.1: Proje iskeleti ve modül sınırları

As a geliştirici (Emre),
I want modül sınırları baştan test edilen ve Postgres'e bağlı çalışan bir Spring Boot iskeleti,
So that sonraki her story mimariye uygun bir zemine eklensin ve sınır ihlali build'i kırsın.

**Acceptance Criteria:**

**Given** yeni bir Gradle Kotlin DSL projesi
**When** Spring Initializr ile üretilir
**Then** Spring Boot 4.1.1, Java 25 ve base package `ai.alfred` kullanılır
**And** bağımlılık olarak Spring Modulith 2.1.1, Web, JDBC, Flyway, PostgreSQL driver, Spring AI Anthropic 2.0.1 ve Testcontainers eklenir
**And** Initializr'ın varsayılanları (Java 17, Groovy DSL) kullanılmaz

**Given** proje iskeleti
**When** kaynak ağacı incelenir
**Then** `agent`, `trust`, `persona`, `capability`, `llm`, `voice`, `memory`, `audit` ve `cli` modülleri `ai.alfred`'in doğrudan alt paketleridir ve her birinde bir `package-info.java` vardır
**And** `ModularityTests` içinde `ApplicationModules.of(AlfredApplication.class).verify()` çalışır ve geçer

**Given** bir modül, başka bir modülün `internal` paketindeki bir sınıfı kullanıyor
**When** `./gradlew test` çalıştırılır
**Then** `verify()` testi başarısız olur ve build kırılır

**Given** repo kökündeki `compose.yaml`
**When** `docker compose up -d` çalıştırılır
**Then** sürümü pinlenmiş bir PostgreSQL imajı ayağa kalkar

**Given** Postgres çalışıyor
**When** daemon başlatılır
**Then** Flyway modül başına çalışır (`spring.modulith.runtime.flyway-enabled=true`)
**And** `modulith` şeması `event_publication` tablosuyla birlikte oluşturulur ve daemon ayağa kalkar

**Given** Postgres erişilemiyor
**When** daemon başlatılmak istenir
**Then** daemon başlamaz ve log'a Postgres'in gerekli olduğunu belirten açık bir hata yazar (AD-7)

### Story 1.2: Claude bağlantısı: `LlmPort` ve Haiku 5.5 smoke test'i

As a geliştirici (Emre),
I want Claude'la domain tiplerimiz üzerinden konuşan bir `LlmPort` ve bu port'un Haiku 5.5 ile tool çağrısı yapabildiğinin kanıtını,
So that karar döngüsü sağlayıcıdan bağımsız kurulsun ve Spring AI 2.0.1 ile modelin uyumu baştan doğrulansın.

**Acceptance Criteria:**

**Given** `llm` modülü
**When** public API'si incelenir
**Then** dışarıya yalnızca `LlmPort` ve domain tipleri açılır (ör. `LlmMessage`, `ToolCall`, `ToolDefinition`, `Usage`)
**And** `llm` dışındaki hiçbir modül Spring AI ya da Anthropic SDK tipi import etmez; bu bir mimari testle doğrulanır

**Given** model id'si config'te `alfred.llm.model=claude-haiku-5-5` olarak tanımlı ve API anahtarı bir ortam değişkeninde
**When** bir test, tek bir tool tanımıyla `LlmPort` üzerinden istek gönderir
**Then** yanıt bir `ToolCall` içerir
**And** tool'u framework yürütmez: `spring.ai.chat.client.tool-calling.enabled=false` ayarı kullanılır ve `ChatModel` doğrudan çağrılır
**And** tool sonucu geçmişe eklenip ikinci istek gönderildiğinde model nihai metin yanıtını üretir

**Given** istek gövdesi
**When** gönderilir
**Then** `temperature`, `top_p` ya da assistant prefill içermez
**And** Conversation geçmişi yalnızca sona eklenerek büyür (AD-11)

**Given** smoke test Spring AI 2.0.1 ile başarısız oluyor ve sorun SDK sürümünden kaynaklanıyor
**When** düzeltme yapılır
**Then** iki yoldan biri seçilir: `anthropic-java` sürümü override edilir ya da `llm` adapter'ı doğrudan Anthropic Java SDK'ya geçirilir
**And** seçilen yol `llm` modülünün README'sine ve mimari memlog'una yazılır
**And** her iki durumda da `LlmPort` imzası değişmez

**Given** API anahtarı tanımlı değil ya da Claude'a erişilemiyor
**When** `LlmPort` çağrılır
**Then** tipli bir hata döner (ör. `LlmUnavailable`) ve istisna yutulmaz

**Given** gerçek API'ye giden smoke test
**When** test suite'i çalıştırılır
**Then** bu test ayrı bir etiketle işaretlidir (`@Tag("live")`) ve varsayılan `./gradlew test` içinde koşmaz

**Given** system prompt ve tool tanımları sabit
**When** ardışık çağrılar yapılır
**Then** bu sabit önek için prompt caching kullanılır ve yanıtın `usage` alanında cache okuması görülür

**Given** smoke test'e başlandı
**When** yaklaşık 4 saatlik bir oturum içinde Spring AI yolu çalışır hâle getirilemez
**Then** daha fazla zaman harcanmadan doğrudan SDK yoluna geçilir ve karar kayda yazılır

**Given** Anthropic Console
**When** API anahtarı oluşturulur
**Then** Console'da aylık harcama limiti, `alfred.llm.monthly-budget-usd` değerinden düşük olmayacak şekilde ayarlanmıştır; bu, koddan bağımsız son güvenlik ağıdır

### Story 1.3: Güvenli CLI kanalı ve Alfred'le ilk sohbet

As a Emre,
I want terminalde `alfred` yazıp Alfred'le konuşabilmek,
So that alfred'in karakterini ilk günden yaşayayım ve kanal en baştan güvenli olsun.

**Acceptance Criteria:**

**Given** daemon çalışıyor
**When** başlatılır
**Then** HTTP sunucusu yalnızca `127.0.0.1` adresine bind eder
**And** rastgele bir token üretir ve onu yalnızca kullanıcının okuyabildiği bir dosyaya (0600) yazar

**Given** token taşımayan ya da yanlış token taşıyan bir istek
**When** daemon'a ulaşır
**Then** `401` koduyla ve `ProblemDetail` gövdesiyle reddedilir

**Given** token'ı geçerli ama `Origin` ya da `Host` başlığı beklenmeyen bir istek (ör. bir web sayfasından gelen)
**When** daemon'a ulaşır
**Then** `403` koduyla ve `ProblemDetail` gövdesiyle reddedilir

**Given** `alfred` thin client'ı
**When** Emre `alfred` komutunu çalıştırıp bir mesaj yazar
**Then** client token'ı dosyadan okur ve mesajı `POST` ile gönderir
**And** yanıtı Server-Sent Events akışından alıp terminale yazar
**And** client hiçbir iş mantığı içermez (FR4)

**Given** daemon'a yeni bir kullanıcı isteği geldi
**When** `agent` isteği kabul eder
**Then** istek, `TaskRegistry`'de yeni bir `taskId` (UUID) ile bir Task olarak başlatılır
**And** Task tamamlandığında, başarısız olduğunda ya da iptal edildiğinde registry'den çıkar (AD-9, AD-23)

**Given** Emre "Merhaba alfred" yazar
**When** yanıt üretilir
**Then** yanıt Türkçedir, kullanıcıya "efendim" diye hitap eder ve "Sayın efendim, emriniz üzerine…" gibi aşırı resmî kalıplar içermez (FR1)
**And** system prompt ve sabit metinler kodda değil, `persona` kaynak dosyalarında durur (AD-18)

**Given** basit bir sohbet sorusu
**When** yanıt üretilir
**Then** yanıt kısadır ve mizah en fazla bir kısa cümleden ibarettir

**Given** client açıkken daemon yeniden başlatıldı ve token değişti
**When** client bir sonraki isteği gönderir
**Then** client yeni token'ı dosyadan yeniden okur ve kullanıcıya hata göstermeden yeniden bağlanır

**Given** kişilik davranışları LLM'e bağlı olduğu için deterministik değil
**When** doğrulanır
**Then** `@Tag("live")` etiketli küçük bir senaryo test seti, addendum'daki diyalogları referans alarak gerçek yanıtlar üretir
**And** yanıtlar bir kontrol listesiyle elle değerlendirilir (hitap, ton, uzunluk, mizah)

### Story 1.4: Konuşma hafızası

As a Emre,
I want alfred'in aynı konuşmada söylediklerimi hatırlamasını,
So that "onu" ya da "az önceki" dediğimde neyi kastettiğimi anlasın.

**Acceptance Criteria:**

**Given** Emre önce "Redis container'ının adı ne?", sonra "Onun loglarını özetle" der
**When** ikinci istek işlenir
**Then** alfred "onu"nun Redis container'ı olduğunu önceki mesajlardan çözer (FR5)

**Given** bir Conversation
**When** yeni mesajlar eklenir
**Then** geçmiş yalnızca sona eklenir; önceki mesajlar düzenlenmez ya da silinmez (AD-11)

**Given** Emre "yeni konuşma" yazar
**When** komut işlenir
**Then** Conversation sıfırlanır ve alfred bunu kısaca teyit eder
**And** bir sonraki istekte önceki konuşmaya ait referanslar çözülmez

**Given** daemon yeniden başlatıldı
**When** Emre yeni bir mesaj gönderir
**Then** önceki Conversation bellekte yoktur ve `memory` Postgres'e hiçbir şey yazmamıştır (AD-16)

**Given** Conversation uzun süre boşta kaldı
**When** Emre tekrar yazar
**Then** Conversation süre dolduğu için sıfırlanmamıştır

**Given** Conversation geçmişi `alfred.memory.suggest-reset-tokens` eşiğini aştı
**When** alfred bir sonraki yanıtı verir
**Then** geçmiş düzenlenmez ya da kırpılmaz (AD-11)
**And** alfred, her Tur'un pahalılaştığını belirterek bir kez "yeni konuşma" başlatmayı önerir

### Story 1.5: Maliyet, bütçe ve Tur sınırı

As a Emre,
I want alfred'in her Claude çağrısının maliyetini tutmasını ve bütçeyi aşmamasını,
So that 5 USD'lik aylık bütçem bir döngü yüzünden tükenmesin.

**Acceptance Criteria:**

**Given** `llm` şemasında bir maliyet defteri var
**When** bir LLM çağrısı tamamlanır
**Then** `taskId`, token kullanımı ve USD maliyeti (`BigDecimal`) deftere yazılır (FR17)

**Given** bir LLM çağrısı yapılmak üzere
**When** `LlmPort` çağrılır
**Then** çağrıdan önce aylık harcama, Task'ın Tur sayısı ve defterin yazılabilirliği kontrol edilir
**And** yardımcı ve yeniden deneme çağrıları dahil her çağrı bir Turn sayılır (AD-12)

**Given** aylık harcama `alfred.llm.monthly-budget-usd` değerinin %80'ine ulaştı
**When** alfred bir sonraki yanıtı verir
**Then** kullanıcıyı bir kez uyarır

**Given** aylık harcama bütçenin %100'üne ulaştı
**When** yeni bir istek gelir
**Then** `llm` çağrıyı yapmaz ve tipli bir hata döner
**And** alfred yeni Task başlatmadığını ve nedenini söyler (FR18)

**Given** bir Task `alfred.llm.max-turns-per-task` sınırına ulaştı (varsayılan 15)
**When** bir sonraki LLM çağrısı istenir
**Then** çağrı yapılmaz ve alfred durduğunu FR9 biçiminde raporlar

**Given** maliyet defteri yazılamıyor (Postgres hatası)
**When** bir LLM çağrısı istenir
**Then** çağrı yapılmaz (fail-closed)

**Given** bütçe sınırına yakınken iki çağrı aynı anda yapılmak isteniyor
**When** ikisi birden kontrol edilir
**Then** kontrol ve kayıt atomiktir, bu yüzden ikisi birlikte sınırı aşamaz

**Given** bir Task sürerken aylık harcama bütçenin %100'üne ulaştı
**When** Task bir sonraki LLM çağrısını istiyor
**Then** sınırı aşacak çağrı yapılmaz
**And** Task durur ve alfred FR9 biçiminde raporlar

**Given** yeni bir takvim ayı başladı (yerel saat dilimi Europe/Istanbul)
**When** bütçe kontrol edilir
**Then** aylık harcama sıfırdan sayılır; %80 uyarısı da yeni ayda tekrar verilebilir

**Given** Emre "Bu ay ne kadar harcadık?" diye sorar
**When** alfred yanıt verir
**Then** ayın toplam harcamasını ve bunun bütçeye oranını söyler

### Story 1.6: Write-ahead işlem kaydı

As a Emre,
I want alfred'in yaptığı her şeyin değiştirilemez bir kaydını,
So that sonradan neyin, neden ve hangi onayla yapıldığını görebileyim.

**Acceptance Criteria:**

**Given** `audit` şeması ve AuditLog tablosu Flyway ile oluşturuldu
**When** alfred'in çalışma anındaki DB rolünün yetkileri incelenir
**Then** AuditLog tablosunda yalnızca `INSERT` ve `SELECT` yetkisi vardır
**And** bir `UPDATE` ya da `DELETE` girişimini veritabanı reddeder (AD-7)

**Given** bir modül `ActionStarted` olayını bir transaction içinde yayımlar
**When** transaction commit edilir
**Then** olay aynı commit'te event publication registry'ye yazılır ve `audit` onu AuditLog'a ekler (AD-6)

**Given** bir Eylem başlatılmak üzere
**When** `ActionStarted` commit'i başarısız olur
**Then** Eylem başlatılmaz (write-ahead)

**Given** AuditLog'a bir satır yazılıyor
**When** satırın içeriği incelenir
**Then** şu alanları içerir: zaman (`Instant`, UTC), istek, Tool ve komut, Tier, onay durumu, sonuç, maliyet, `taskId` ve `parentTaskId` (MVP'de boş) (FR19, AD-23)

**Given** komut ya da çıktı bir şifre, token veya API anahtarı kalıbı içeriyor
**When** kayda yazılır
**Then** değer maskelenir
**And** log'a da açık hâliyle yazılmaz

**Given** daemon çalışırken Postgres erişilemez hâle geldi
**When** yeni bir Eylem istenir
**Then** Kademe 0 dahil hiçbir Eylem başlatılmaz
**And** alfred nedenini söyler ve bekler (NFR12)

### Story 1.7: Komut ayrıştırma ve Kademe sınıflandırması

As a Emre,
I want alfred'in her komutu, model ne derse desin, kendi kurallarıyla sınıflandırmasını,
So that güven kapısının kalbi süreç çalıştırmadan, saf unit testlerle doğrulanabilsin.

**Acceptance Criteria:**

**Given** `trust` modülü
**When** bir komut sınıflandırılır
**Then** komut gerçek bir shell gramerine göre argv aşamalarına ayrıştırılır
**And** Tier deterministik kurallarla atanır ve LLM çıktısı Tier'i düşüremez (FR12, AD-4)

**Given** birleşik bir komut (`docker logs redis | tail -50`)
**When** sınıflandırılır
**Then** parçaların en yüksek Tier'i geçerli olur
**And** `>` ya da `>>` dosya yazma sayılır

**Given** şunlardan birini içeren bir komut:
- modellenmeyen bir yapı: backtick, `$(...)`, `||`, `&`, heredoc, `eval`
- sarmalayıcı bir komut: `sudo`, `env`, `xargs`, `sh -c`, `docker exec`, `find -exec` / `-delete`
- tanınmayan bir komut
**When** sınıflandırılır
**Then** Tier 2 olur

**Given** ayrıştırıcının kapsamı
**When** tasarlanır
**Then** yalnızca basit komutları, pipe'ları (`|`) ve `>` / `>>` yönlendirmelerini modeller; tam bir bash grameri yazılmaz
**And** modellenmeyen her şey zaten Tier 2 olduğu için bu sınır güvenliği zayıflatmaz

**Given** sınıflandırıcı
**When** test edilir
**Then** her kural ve her Tier 2 kategorisi için süreç çalıştırmayan unit testler vardır

### Story 1.8: Okuma tool'larını onaylı yürütme

As a Emre,
I want alfred'in yalnızca Kademe 0 okuma komutlarını çalıştırmasını ve geri kalan her şeyi reddetmesini,
So that ilk günden kodla korunan bir güven kapısı olsun.

**Acceptance Criteria:**

**Given** bir Kademe 0 Eylem (ör. `docker ps`, `docker logs`, `cat`, `ls`, `git status`)
**When** onaylanır
**Then** `trust` otomatik olarak bir `ApprovedAction` üretir; bu nesne argv'yi, mutlak `cwd`'yi, env allowlist'ini ve bir `actionId`'yi taşır
**And** `ApprovedAction` yalnızca `trust` içinde oluşturulabilir (package-private constructor)
**And** ikinci kez tüketilirse hata fırlatır (AD-3)

**Given** `capability` modülü
**When** bir `ApprovedAction` yürütür
**Then** ayrıştırılmış argv'yi `ProcessBuilder` ile doğrudan çalıştırır; `sh -c` ya da `bash -c` yolu yoktur
**And** her Eylem kendi process group'unda, `alfred.capability.*` ile ayarlanan bir zaman aşımıyla çalışır

**Given** bir Kademe 1 ya da Kademe 2 Eylem
**When** bu epic'te istenir
**Then** yürütülmez
**And** alfred "henüz bu yetkim yok, efendim" anlamında kısa bir açıklama yapar

**Given** bir Eylem yürütülüyor
**When** başlar ve biter
**Then** `ActionStarted` yürütmeden önce, `ActionFinished` ise sonuçla birlikte yürütmeden sonra kayda geçer (Story 1.6)

**Given** bir Tool çıktısı
**When** kullanıcıya aktarılır
**Then** ham değil, özetlenmiş hâliyle aktarılır
**And** Claude'a gönderilmeden önce kırpılır (FR10, AD-11)

**Given** bir Tool çok büyük (ör. 100 MB'lık bir log) ya da UTF-8 olmayan ikili bir çıktı üretiyor
**When** çıktı okunur
**Then** `alfred.capability.max-output-bytes` sınırında kesilir ve ikili içerik metne zorlanmaz
**And** çıktı Claude'a, `memory`'ye ya da kayda olduğu gibi gitmez; alfred çıktının kesildiğini belirtir

### Story 1.9: Çok adımlı teşhis

As a Emre,
I want "Redis neden durdu?" diye sorduğumda alfred'in kanıt toplayıp teşhis koymasını,
So that sorunun sebebini tek tek komut çalıştırmadan öğreneyim.

**Acceptance Criteria:**

**Given** durmuş bir Redis container'ı
**When** Emre "Redis loglarını incele, hatayı bul" der
**Then** alfred birden çok Tur boyunca en az container'ın durumunu, loglarını ve ilgili config'i okur
**And** her adımın sonucuna göre bir sonraki adıma karar verir (FR6)
**And** teşhisi, dayandığı kanıtla birlikte kısaca söyler

**Given** kanıt yetersiz
**When** alfred yanıt verir
**Then** belirsizliği açıkça söyler (ör. "henüz emin değilim, efendim")
**And** hipotezi doğrulanmış sonuçtan ayırır (FR2)

**Given** çok adımlı bir Task
**When** ilk Tur tamamlanır
**Then** "Bakıyorum, efendim." gibi bir ara yanıt SSE ile client'a ulaşır

**Given** Emre yalnızca teşhis istedi
**When** alfred bir düzeltme önerir
**Then** düzeltmeyi uygulamaz, yalnızca önerir ("Düzeltmemi ister misiniz?") (FR7)

**Given** bir Tool hata verdi
**When** alfred devam eder
**Then** sonucu varsaymaz ve mümkünse farklı bir yöntemle yeniden dener
**And** o da başarısız olursa durur ve FR9 biçiminde raporlar: durum, denenenler, belirsiz olan, sistemin bırakıldığı durum ve sonraki seçenek

**Given** Claude ya da Postgres erişilemiyor
**When** bir istek gelir
**Then** alfred hiçbir Eylem yapmaz ve nedenini söyler (NFR12)

**Given** bir Task aktif
**When** Emre başka bir istek gönderir
**Then** istek kuyruğa alınmaz
**And** alfred meşgul olduğunu söyler ve aktif Task'ı durdurmak isteyip istemediğini sorar (`TaskRegistry`, `alfred.agent.max-active-tasks=1`, AD-9)

**Given** teşhis davranışı LLM'e bağlı olduğu için deterministik değil
**When** doğrulanır
**Then** `@Tag("live")` etiketli senaryo testleri, hazırlanmış arızalı bir demo ortamında (ör. durmuş bir Redis) gerçek teşhisler üretir
**And** sonuçlar bir kontrol listesiyle elle değerlendirilir: kanıt söylendi mi, kapsamda kalındı mı, belirsizlik doğru belirtildi mi

### Story 1.10: "Bugün ne yaptın?"

As a Emre,
I want gün sonunda "Alfred, bugün ne yaptın?" diye sorabilmeyi,
So that alfred'in gün içindeki işlerini ve harcamasını tek bir özette göreyim.

**Acceptance Criteria:**

**Given** AuditLog'da bugüne ait kayıtlar var
**When** Emre "Bugün ne yaptın?" diye sorar
**Then** alfred, `audit`'in `capability` Tool SPI'ına kaydettiği Kademe 0 okuma Tool'unu kullanır
**And** kısa bir özet verir, örneğin "Bugün iki teşhis yaptım, efendim. Silme işlemi yapmadım." (FR20)
**And** özet günün harcamasını da içerir

**Given** bugüne ait hiç kayıt yok
**When** aynı soru sorulur
**Then** alfred bugün bir iş yapmadığını kısaca söyler

**Given** özet Tool'u çalışıyor
**When** modül bağımlılıkları incelenir
**Then** `agent` ile `audit` arasında doğrudan bir derleme bağımlılığı yoktur ve `verify()` geçer (AD-2)

## Epic 2: alfred güvenle müdahale eder

alfred durum değiştiren Eylem'leri uygular. Somut bir risk görürse Kademe 1'de bir kez uyarır. Kademe 2'de güvenlik kelimesi ister ve kelime söylenmeden Eylem'i yürütmez. Kendi varlıklarına asla dokunmaz. `Super+Esc` her şeyi durdurur.

### Story 2.1: Gerçek sistem bilgisi ve korunan kaynaklar

As a Emre,
I want alfred'in risk kararlarını modelin yorumuna değil, sistemin gerçek durumuna dayandırmasını ve kendi varlıklarına hiç dokunmamasını,
So that yazma yetkisi verildiğinde Kademe'ler doğru belirlensin ve alfred kendi kaydını, veritabanını ya da anahtarını bozamasın.

**Acceptance Criteria:**

**Given** `trust` modülünün public API'sinde tanımlı `TierFactProbe` port'u
**When** `capability` modülü bu port'u uygular
**Then** probe şu soruları salt-okuma sorgularla sistemden yanıtlar: dosya git'te izleniyor mu, working tree temiz mi, yedek var mı, volume ya da dosya ne kadar büyük, hedef korunan bir kaynak mı
**And** modüller arası bağımlılığın yönü `capability → trust` olur ve `verify()` geçer (AD-19)

**Given** Tier'i etkileyen bir bilgi
**When** `trust` bu bilgiye ihtiyaç duyar
**Then** bilgiyi yalnızca `TierFactProbe`'dan alır; `agent`'tan ya da LLM çıktısından gelen bilgi Tier'i etkilemez

**Given** probe yanıt veremiyor ya da hata veriyor
**When** ilgili Eylem sınıflandırılır
**Then** Eylem Kademe 2 olur (fail-closed)

**Given** `alfred.trust.protected-resources` listesi şunları içerir: API anahtarı dosyası, HTTP token dosyası, alfred'in config dizini, kendi veritabanı ve Compose projesinin container'ları ile volume'ları
**When** ayrıştırılmış argv'nin hedefi bu listeyle eşleşen bir Eylem istenir (ör. `cat <token-dosyası>`, `docker volume rm <alfred-pg-volume>`, `docker compose -p alfred down`)
**Then** Eylem DENIED olur ve güvenlik kelimesi verilse bile yürütülmez
**And** alfred bunun kendi varlığı olduğunu ve dokunamayacağını kısaca söyler (AD-20)

**Given** korunan bir kaynağa yönelik salt-okuma bir durum sorgusu (ör. `docker ps`, `docker inspect <alfred-pg>`)
**When** sınıflandırılır
**Then** Kademe 0 olarak kalır

**Given** reddedilen bir Eylem
**When** reddedilir
**Then** red, onay durumu "DENIED" olarak işlem kaydına geçer

### Story 2.2: Kademe 1: yap, raporla, gerekirse bir kez uyar

As a Emre,
I want alfred'in geri alınabilir değişiklikleri sormadan yapıp raporlamasını ve yalnızca somut bir risk gördüğünde bir kez uyarmasını,
So that alfred iş yapsın ama beni gereksiz sorularla boğmasın.

**Acceptance Criteria:**

**Given** şu Eylem'lerden biri: container start/stop/restart, `git commit`, normal `git push`, yeni dosya oluşturma, yedeği alınmış ya da git'te temiz duran bir dosyayı düzenleme, `docker pull`, `docker compose up/build`, container'ı yeniden oluşturma ya da uygulama başlatma
**When** `trust` sınıflandırır
**Then** Eylem Kademe 1 olur ve `trust` bir `ApprovedAction` üretir (FR10, FR12)

**Given** somut bir risk yok
**When** Kademe 1 bir Eylem istenir
**Then** alfred Eylem'i yapar ve sonucu kısaca raporlar

**Given** durum değiştiren bir Eylem tamamlandı
**When** alfred sonucu bildirmeden önce
**Then** sonucu doğrular (ör. yeniden başlatılan container'ın çalıştığını kontrol eder)
**And** doğrulanamayan bir sonucu başarı olarak bildirmez (FR8)

**Given** somut bir risk var (ör. yeniden başlatılacak container'da aktif bağlantılar var)
**When** Kademe 1 bir Eylem istenir
**Then** alfred riski adıyla bir kez söyler ve Eylem'i bekletir (FR14)
**And** bekleyen uyarının durumu Task kapsamında `agent`'ta tutulur (AD-22)

**Given** alfred uyardı
**When** Emre "Biliyorum, yine de yap" der
**Then** alfred yeniden sormadan Eylem'i uygular

**Given** risk tespiti
**When** yapılır
**Then** yalnızca danışma niteliğindedir; Eylem'in Tier'ini düşüremez ve yükseltemez

### Story 2.3: Önce geri alınabilir yap

As a Emre,
I want alfred'in yedek alarak geri alınabilir hâle getirebildiği bir işlemi önce öyle yapmasını,
So that bir dosyanın üzerine yazılması gibi işlemler geri dönüşsüz olmasın ve gereksiz yere güvenlik kelimesi istenmesin.

**Acceptance Criteria:**

**Given** git'te izlenmeyen ya da working tree'si temiz olmayan bir dosya
**When** alfred bu dosyanın üzerine yazmak ister
**Then** `trust` iki adımlı tek bir `ApprovedAction` üretir: önce yedek, sonra yazma
**And** Eylem Kademe 1 olarak ele alınır (FR13, AD-3)

**Given** yedek adımı başarısız oldu
**When** `capability` sıradaki adıma geçecekken
**Then** yazma adımı koşmaz ve Eylem iptal edilir
**And** alfred FR9 biçiminde raporlar

**Given** yedek alınamıyor (ör. disk dolu, izin yok)
**When** `trust` sınıflandırma yapar
**Then** üzerine yazma Kademe 2 olarak kalır ve güvenlik kelimesi gerekir

**Given** başarılı bir yedekli yazma
**When** alfred rapor verir
**Then** yedeğin yerini söyler
**And** yedek de işlem kaydına geçer

### Story 2.4: Güvenlik kelimesi (CLI) ve Kademe 2

As a Emre,
I want geri alınamaz bir işlemden önce neyin, ne kadar etkileneceğini görmeyi ve yalnızca rastgele bir kelimeyi yazdığımda işlemin yapılmasını,
So that ne yanlışlıkla verilmiş bir "evet" ne de modelin kendi kararı bir şeyi geri dönüşsüz silebilsin.

**Acceptance Criteria:**

**Given** bir Kademe 2 Eylem (ör. `docker volume rm redis-data`, `rm`, `git push --force`, `git reset --hard`, branch silme, `sudo`, paket kurma ya da kaldırma, Emre adına mesaj gönderme, tanınmayan bir komut)
**When** istenir
**Then** `trust` rastgele ve tek kullanımlık bir kelime üretir ve bu kelimeyi yalnızca bekleyen Eylem'e bağlar (FR15, AD-5)

**Given** onay istemi
**When** gösterilir
**Then** metni `trust` ayrıştırılmış `Action`'dan kurar: birebir komut, hedef, etki ve yapılan doğrulama (ör. `redis-data → 2.4 GB → kalıcı olarak silinecek`)
**And** metni `persona` render eder; LLM'in açıklaması komutun yerine geçmez
**And** istem korkutucu değildir ve uyarı yağdırmaz

**Given** kelime bekleniyor
**When** Emre "evet", "biliyorum, sil" ya da yanlış bir kelime yazar
**Then** Eylem yürütülmez, yeni bir kelime üretilir ve yeniden sorulur
**And** 3 başarısız denemeden sonra Eylem iptal edilir

**Given** Emre doğru kelimeyi birebir yazar
**When** `trust` karşılaştırma yapar
**Then** Eylem yürütülür
**And** kelime ikinci kez kullanılamaz

**Given** kelime ve kullanıcının girdiği metin
**When** işlenir
**Then** karşılaştırma `trust`'ta yapılır ve LLM'den geçmez
**And** ne kelime ne de girdi `LlmPort`'a, `memory`'ye ya da log'a gider
**And** işlem kaydında yalnızca onay durumu (onaylandı, reddedildi, iptal) görünür

**Given** kelime bekleniyor
**When** `alfred.trust.safety-word-timeout` süresi (varsayılan 120 sn) dolar
**Then** kelime geçersiz olur, Eylem iptal edilir ve alfred bunu kısaca söyler

**Given** kelime bekleniyor
**When** girdi farklı bir kanaldan gelir
**Then** girdi, `taskId` eşleştiği sürece kabul edilir (AD-23)

**Given** bir test suite'i
**When** çalıştırılır
**Then** her Kademe 2 kategorisi için, güvenlik kelimesi olmadan yürütmenin imkânsız olduğunu gösteren bir test vardır (NFR10, SM-5)

### Story 2.5: Acil durdurma

As a Emre,
I want `Super+Esc`'ye bastığımda alfred'in yaptığı her şeyi anında durdurmasını,
So that yanlış giden bir şeyi, internet olmasa bile, hemen kesebileyim.

**Acceptance Criteria:**

**Given** aktif bir Task ve çalışan bir Eylem
**When** Emre `alfred stop` komutunu çalıştırır
**Then** Task iptal edilir ve çalışan Eylem'in process group'u sonlandırılır
**And** sonraki Tur'lar kesilir (FR16, AD-14)

**Given** onaylanmış ama henüz başlamamış bir Eylem
**When** tam bu anda durdurma gelir
**Then** `capability` süreci başlatmadan hemen önce iptal durumunu kontrol eder ve Eylem'i yürütmez

**Given** bekleyen bir güvenlik kelimesi
**When** durdurma gelir
**Then** kelime geçersiz olur ve sonradan doğru yazılsa bile Eylem yürütülmez

**Given** ağ bağlantısı ve Claude erişimi yok
**When** `alfred stop` çalıştırılır
**Then** durdurma yine çalışır

**Given** durdurma
**When** tamamlanır
**Then** otomatik geri alma yapılmaz
**And** alfred neyin durdurulduğunu ve sistemin hangi durumda kaldığını kısaca söyler

**Given** bir Sway config parçası
**When** Emre bu parçayı ekler
**Then** `bindsym $mod+Escape exec alfred stop` kısayolu çalışır ve kısayol tuşu yeniden atanabilir
**And** `systemctl --user import-environment WAYLAND_DISPLAY SWAYSOCK` satırı belgelenmiştir

**Given** durdurma
**When** gerçekleşir
**Then** `TaskCancelled` olayı işlem kaydına geçer

**Given** aktif bir Task yok
**When** `alfred stop` çalıştırılır
**Then** alfred "aktif bir iş yok, efendim" der ve başka hiçbir şey yapmaz

**Given** art arda iki durdurma komutu geldi
**When** işlenir
**Then** ikincisi hata üretmez; durdurma idempotent'tir

### Story 2.6: UJ-1 provası (CLI)

As a Emre,
I want demo senaryosunun mantık kısmını CLI'dan uçtan uca çalıştırabilmeyi,
So that ses eklenmeden önce teşhis, düzeltme, doğrulama ve onaylı silme zincirinin birlikte sorunsuz çalıştığını bileyim.

**Acceptance Criteria:**

**Given** portları yanlış yapılandırıldığı için çalışmayan bir Redis içeren bir demo Compose projesi (alfred'in kendi projesinden ayrı)
**When** Emre CLI'dan "Docker'da ne çalışıyor?" diye sorar
**Then** alfred çalışan container'ları sayar ve Redis'in durduğunu söyler

**Given** aynı ortam
**When** Emre "Redis loglarını kontrol et, olası hataları tespit et" der
**Then** alfred kanıtla birlikte port hatasını bulur
**And** düzeltmeyi uygulamadan "Düzeltmemi ister misiniz?" diye sorar

**Given** Emre düzeltmeyi onaylar
**When** alfred config'i düzeltir ve Redis'i yeniden başlatır
**Then** config yazımı yedekli (2.3) ya da git'te temiz olduğu için Kademe 1 olarak çalışır
**And** alfred sonucu doğrulayıp "Her şey sorunsuz çalışıyor" der

**Given** Emre "Eski Redis volume'ünü temizle" der
**When** alfred onay ister
**Then** volume'ün boyutunu ve etkisini söyleyip bir güvenlik kelimesi ister
**And** "evet" yazılırsa silmez, doğru kelime yazılırsa siler ve "Tamamlandı" der

**Given** prova tamamlandı
**When** işlem kaydı incelenir
**Then** her adım doğru Tier ve onay durumuyla görünür
**And** alfred'in kendi Postgres volume'ü hiçbir aşamada hedef alınmamıştır

## Epic 3: Emre, alfred'le konuşur

Emre bas-konuş tuşuyla Türkçe konuşur ve Alfred sesle yanıt verir. Kısa komutlarda yanıt yaklaşık 3 saniye içinde başlar. Güvenlik kelimesi sesle söylenebilir. alfred'in kendi sesi ya da arka plandaki bir ses onay sayılmaz. Motorlar önce Emre'nin makinesinde ölçülür, sonra seçilir.

### Story 3.1: Ses motoru benchmark'ı

As a Emre,
I want aday Türkçe STT ve TTS motorlarını kendi CPU'mda ölçmeyi ve dinlemeyi,
So that motor seçimini başka makinelerin rakamlarına değil, kendi gözlemime dayandırayım.

**Acceptance Criteria:**

**Given** bir Türkçe test seti hazırlanmış
**When** test setinin içeriği incelenir
**Then** en az 20 kısa kayıt içerir: demo komutları ("Docker'da ne çalışıyor?" gibi), güvenlik kelimesi örnekleri ve arka plan gürültülü örnekler
**And** her kaydın referans metni yazılıdır

**Given** STT adayları: seda-v0.1 (sherpa-onnx), faster-whisper small int8 ve Groq whisper-large-v3-turbo
**When** benchmark betiği test setinde çalıştırılır
**Then** her aday için şunlar raporlanır: konuşmanın bitişinden metnin hazır olmasına kadar geçen medyan ve p90 süre, kelime hata oranı (WER), güvenlik kelimesi doğruluğu ve tahmini aylık maliyet

**Given** TTS adayları: Piper `tr_TR-dfki-medium` ve Azure `tr-TR-AhmetNeural`
**When** benchmark çalıştırılır
**Then** her aday için 2–3 cümlelik bir yanıtın ilk sesinin çıkma süresi ve tahmini aylık maliyet raporlanır
**And** her aday aynı örnek Alfred cümlelerini seslendirir ve Emre her birini dinleyip "kulağa hoş geliyor mu?" sorusuna 1–5 arası bir puan verir

**Given** benchmark sonuçları
**When** karar verilir
**Then** STT ve TTS motoru şu ölçütlere göre seçilir: kısa komutlarda ~3 sn toplam hedefi (FR3), ses karakteri (erkek, sakin, olgun), Emre'nin dinleme puanı ve SM-6 bütçesi
**And** seçim gerekçesiyle birlikte mimari memlog'a ve omurganın Deferred bölümüne yazılır

**Given** hiçbir aday ~3 sn hedefini karşılamıyor
**When** karar verilir
**Then** en iyi aday seçilir ve sapma gerekçesiyle kayda yazılır
**And** demo için ara yanıt stratejisi kullanılır ("Bakıyorum, efendim."); Epic 3 bu yüzden durmaz

**Given** benchmark betiği ve test seti
**When** repoya eklenir
**Then** motor değişikliği gündeme geldiğinde tekrar çalıştırılabilir durumdadır

### Story 3.2: Konuşmayı anlamak: STT servisi

As a Emre,
I want söylediğim Türkçe cümlenin metne dönüşmesini,
So that alfred sesle verilen istekleri anlayabilsin.

**Acceptance Criteria:**

**Given** 3.1'de seçilen STT motoru
**When** `docker compose up` çalıştırılır
**Then** motor, Compose'ta bir Python HTTP servisi olarak ayağa kalkar
**And** servis bir ses dosyasını alıp Türkçe metin döndürür

**Given** `voice` modülü
**When** public API'si incelenir
**Then** dışarıya bir `SpeechToText` port'u açılır
**And** Python servisine düz HTTP request/response ile bağlanan bir adapter vardır; MVP'de streaming kullanılmaz (AD-13)

**Given** bir yerel motor seçildi
**When** buluta giden yedek bir adapter da tanımlanır
**Then** yedek adapter'a config ile geçilebilir ve `agent`'ta hiçbir değişiklik gerekmez

**Given** STT servisi çalışmıyor
**When** sesli bir istek gelir
**Then** alfred sesi anlayamadığını söyler, CLI'ın çalışmaya devam ettiğini belirtir ve hiçbir Eylem yapmaz

**Given** STT çıktısı
**When** log'a yazılır
**Then** yalnızca `taskId` ve uzunluk gibi meta bilgiler yazılır; ham ses kalıcı olarak saklanmaz

### Story 3.3: Konuşmak: TTS servisi

As a Emre,
I want Alfred'in yanıtlarını sakin ve olgun bir Türkçe sesle duymayı,
So that bir insanla konuşuyormuş hissini yaşayayım.

**Acceptance Criteria:**

**Given** 3.1'de seçilen TTS motoru
**When** Compose'ta bir servis olarak (ya da seçilen bulut adapter'ıyla) çalışır
**Then** `voice` modülündeki `TextToSpeech` port'u metni seslendirir ve ses hoparlörden çalar

**Given** Alfred'in bir yanıtı
**When** ses kanalı için render edilir
**Then** render'ı `persona` yapar
**And** basit yanıtlar en fazla 3 cümledir; teşhis, Kademe 2 onayı ve başarısızlık mesajları bu sınırın dışındadır (NFR13)
**And** ses kanalı için render edilen metinde markdown, kod bloğu ya da uzun komut satırı yoktur

**Given** TTS çalışmıyor
**When** bir yanıt seslendirilecek
**Then** yanıt yine CLI'da yazılı olarak görünür ve alfred TTS'in çalışmadığını bir kez belirtir

**Given** motor değiştirilir
**When** yeni adapter config ile seçilir
**Then** `agent` ve `persona` kodunda değişiklik gerekmez

### Story 3.4: Bas-konuş

As a Emre,
I want bir tuşa basılı tutarak alfred'le konuşmayı,
So that elim klavyedeyken ya da başka bir iş yaparken terminale geçmeden ona bir şey sorabileyim.

**Acceptance Criteria:**

**Given** Sway config'inde bas-konuş için bir tuşa basılma ve bırakılma (`--release`) bağlantıları var
**When** Emre tuşa basar
**Then** `alfred` client'ı daemon'a kaydı başlatmasını söyler
**And** daemon mikrofondan kayda başlar ve alfred "Buyurun?" der (FR3)

**Given** kayıt sürüyor
**When** Emre tuşu bırakır
**Then** ses tam hâliyle STT'ye gönderilir, metin `agent`'a ulaşır ve yanıt TTS ile okunur

**Given** sesli bir istek
**When** işlenir
**Then** CLI ile aynı Conversation, aynı `agent`, aynı `trust` ve aynı işlem kaydı kullanılır (AD-9)
**And** sesli istek ve yanıt CLI'da da görünür

**Given** kısa bir komut ("Docker'da ne çalışıyor?")
**When** 10 tekrarla ölçülür
**Then** tuşun bırakılmasından yanıtın sesli başlamasına kadar geçen süre raporlanır
**And** medyan süre ~3 sn hedefini karşılar; karşılamıyorsa hangi aşamanın (STT, LLM, TTS) aştığı raporlanır (NFR1)

**Given** çok adımlı bir Task
**When** ilk Tur sürerken
**Then** "Bakıyorum, efendim." gibi bir ara yanıt ~3 sn içinde sesli olarak verilir

**Given** alfred konuşuyor
**When** Emre bas-konuş tuşuna basar
**Then** alfred'in konuşması kesilir ve yeni kayıt başlar

**Given** tuşa çok kısa basıldı ya da kayıt boş veya anlaşılmaz
**When** STT sonucu boş ya da anlamsız döner
**Then** alfred "Sizi duyamadım, efendim" der ve Task başlatmaz

### Story 3.5: Sesli güvenlik kelimesi ve yankı reddi

As a Emre,
I want güvenlik kelimesini sesle söyleyebilmeyi ve başka hiçbir sesin onay sayılmamasını,
So that sesli kullanımda da geri alınamaz işlemler yalnızca benim bilinçli onayımla yapılsın.

**Acceptance Criteria:**

**Given** sesli modda bir Kademe 2 Eylem
**When** alfred onay ister
**Then** etkiyi ve güvenlik kelimesini sesle söyler; kelime aynı anda CLI'da da görünür (FR15)

**Given** kelime bekleniyor
**When** ses girdisi geliyor
**Then** kelime yalnızca bas-konuş tuşu basılıyken alınan kayıttan kabul edilir
**And** alfred konuşurken mikrofon kayıt almaz

**Given** tuşa basılmamışken arka planda (ör. bir videoda) "yeşil" kelimesi geçer
**When** `trust` bekliyor
**Then** bu ses onay sayılmaz

**Given** Emre kelimeyi söyler
**When** STT çıktısı `trust`'a gelir
**Then** Türkçe büyük/küçük harf ve noktalama farkları normalize edilir ve kelime birebir karşılaştırılır
**And** "evet" ya da yanlış duyulan bir kelime onay sayılmaz; Story 2.4'teki yeni kelime ve 3 deneme kuralı uygulanır

**Given** sesli onay akışı
**When** çalışır
**Then** kelime ve STT çıktısı `LlmPort`'a, `memory`'ye ya da log'a gitmez (AD-5)

**Given** bir test suite'i
**When** çalıştırılır
**Then** şu durumların her birinin onay sayılmadığı testlerle gösterilir: TTS çalarken gelen ses, tuşa basılmadan gelen ses ve normalize edilmiş yanlış bir kelime

## Epic 4: Demo günü: müzik ve tek seferde 60 saniye

alfred YouTube'da şarkı açar ve bilgisayar açıldığında arka planda hazır bekler. UJ-1'deki 60 saniyelik demo kesintisiz, kurgusuz ve tek seferde çalışır. README, projeyi portfolyoda anlatacak hâle gelir.

### Story 4.1: "Bana AC/DC'den bir şarkı aç"

As a Emre,
I want alfred'den bir sanatçının ya da şarkının çalınmasını isteyebilmeyi,
So that alfred yalnızca geliştirme işlerinde değil, gündelik işlerde de yanımda olsun.

**Acceptance Criteria:**

**Given** video bulma yöntemi henüz seçilmedi
**When** story'ye başlanır
**Then** YouTube Data API ile `yt-dlp` arama şu ölçütlerle karşılaştırılır: API anahtarı gereksinimi, maliyet ve kota, "en popüler" sonucun belirlenebilmesi, kırılganlık
**And** seçim gerekçesiyle birlikte mimari memlog'a ve omurganın Deferred bölümüne yazılır

**Given** Emre "Bana AC/DC'den bir şarkı aç" der
**When** alfred isteği işler
**Then** sanatçının en popüler videosunu bulur ve videonun izleme URL'sini `xdg-open` ile tarayıcıda açar (FR11)
**And** arama sonuç listesi açılmaz
**And** alfred "Peki." gibi kısa bir yanıt verir

**Given** Emre belirli bir şarkı adı verir ("AC/DC'den Thunderstruck aç")
**When** alfred videoyu arar
**Then** o şarkının videosunu açar

**Given** YouTube Tool'u
**When** sınıflandırılır
**Then** Kademe 0 olur, first-party bir Tool'dur ve `ApprovedAction` ile yürütülür (AD-3, AD-21)
**And** komut ayrıştırılmış argv ile çalıştırılır; `sh -c` kullanılmaz

**Given** seçilen yöntem bir API anahtarı gerektiriyor
**When** anahtar yapılandırılır
**Then** anahtar ortam değişkeninde ya da 0600 izinli bir dosyada durur
**And** anahtar `alfred.trust.protected-resources` listesine eklenir (AD-15, AD-20)

**Given** video bulunamadı ya da ağ yok
**When** alfred yanıt verir
**Then** FR9 biçiminde, ne denediğini ve neyin başarısız olduğunu kısaca söyler

### Story 4.2: Her zaman orada: systemd servisi

As a Emre,
I want alfred'in bilgisayarı açtığımda arka planda kendiliğinden hazır olmasını,
So that her seferinde elle başlatmadan bas-konuş tuşuna basıp konuşabileyim.

**Acceptance Criteria:**

**Given** bir systemd user unit dosyası
**When** `systemctl --user enable --now alfred` çalıştırılır
**Then** daemon oturum açılınca başlar ve çökerse yeniden başlatılır (AD-9)

**Given** Sway config'i
**When** oturum başlar
**Then** `systemctl --user import-environment WAYLAND_DISPLAY SWAYSOCK` ile ortam değişkenleri aktarılır
**And** daemon içinden `xdg-open` ve `swaymsg` çalışır

**Given** Compose servisleri (Postgres, STT, TTS) henüz hazır değil
**When** daemon başlamaya çalışır
**Then** Postgres hazır olana kadar başlamaz; bekleme ve yeniden deneme davranışı unit dosyasında ve uygulamada tanımlıdır (AD-7)

**Given** `alfred` thin client'ı
**When** kurulur
**Then** PATH'te bulunur
**And** her daemon başlangıcında yenilenen token dosyasını otomatik okur

**Given** README
**When** kurulum bölümü okunur
**Then** sıfırdan kurulum adımları eksiksiz yazılıdır: Compose, API anahtarı, unit dosyası, Sway kısayolları ve thin client
**And** API anahtarının repoya girmemesi gerektiği açıkça belirtilir

### Story 4.3: 60 saniyelik demo

As a Emre,
I want UJ-1 senaryosunun sesle, kesintisiz ve kurgusuz tek seferde çalışmasını,
So that MVP'nin bittiğini kanıtlayan bir video kaydedebileyim.

**Acceptance Criteria:**

**Given** demo için hazırlanmış Compose projesi (Story 2.6) ve çalışan alfred servisi
**When** bir prova kontrol listesi hazırlanır
**Then** liste ortamın başlangıç durumunu, mikrofon ve hoparlör ayarlarını, bütçe durumunu ve senaryonun 6 adımını içerir

**Given** prova kontrol listesindeki ortam hazır
**When** Emre senaryoyu sesle yürütür
**Then** şu adımlar sırayla gerçekleşir:
- "Buyurun?"
- "Docker'da ne çalışıyor?" sorusuna Redis'in durduğu yanıtı
- Log teşhisi ve "Düzeltmemi ister misiniz?" sorusu
- Onaydan sonra düzeltme, doğrulama ve "Her şey sorunsuz çalışıyor"
- Volume silme için güvenlik kelimesi ve "Tamamlandı"
- AC/DC şarkısının çalması
**And** bunların hepsi 60 saniye içinde, tek seferde ve kurgusuz tamamlanır (SM-1)

**Given** demo tamamlandı
**When** işlem kaydı incelenir
**Then** her adım doğru Tier ve onay durumuyla görünür
**And** alfred'in kendi kaynaklarına hiçbir Eylem yapılmamıştır

**Given** demo başarısız oldu
**When** sebep incelenir
**Then** sebep ve düzeltmesi kaydedilir ve demo yeniden denenir; kurgu ile birleştirme yapılmaz

**Given** başarılı bir çekim
**When** video kaydedilir
**Then** video README'de kullanılmak üzere repoya ya da bağlantılı bir yere konur

### Story 4.4: Portfolyo vitrini: README

As a Emre,
I want README'nin projeyi ilk bakışta anlatmasını,
So that portfolyomu inceleyen biri alfred'in ne yaptığını ve nasıl kurulduğunu bir dakikada anlasın.

**Acceptance Criteria:**

**Given** README
**When** açılır
**Then** en üstte demo videosu (Story 4.3) ve mimari diyagram yer alır (SM-4)

**Given** mimari diyagram
**When** README'de gösterilir
**Then** omurgadaki modül bağımlılık diyagramından türetilir ve core modülleri (`agent`, `trust`, `persona`) belirgin biçimde gösterir

**Given** README
**When** "Nasıl çalışır?" bölümü okunur
**Then** şu konular kısaca anlatılır: hexagonal + Spring Modulith yapısı, `ApprovedAction` güven kapısı, kademe ve güvenlik kelimesi modeli, maliyet sınırları
**And** ayrıntı için mimari omurgaya bağlantı verilir

**Given** Medium yazısı yayımlandı
**When** README güncellenir
**Then** yazının bağlantısı README'de yer alır
