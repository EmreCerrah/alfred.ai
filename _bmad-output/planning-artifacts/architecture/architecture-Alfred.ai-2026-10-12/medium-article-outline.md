---
title: "Medium yazısı iskeleti: alfred ile DDD ve ajan mimarisi"
created: 2026-10-15
sources: [ARCHITECTURE-SPINE.md, .memlog.md, prds/prd-Alfred.ai-2026-10-07/prd.md, prds/prd-Alfred.ai-2026-10-07/addendum.md]
---

# Medium yazısı iskeleti

> Bu dosya yazının kendisi değil, hammaddesidir. Yazıyı Emre kendi sesiyle yazar. Her bölümde şu dört başlık var:
> - Anlatılacak karar
> - Elenen alternatifler
> - "Ne öğrendim?" sorusu
> - Kanıtın nerede olduğu
>
> Sorular senin cevaplaman için. Cevaplar yazının en kişisel ve en değerli kısmı olacak.

**Çalışma başlığı önerileri**
- "Kendi JARVIS'imi değil, kendi Alfred'imi yazdım: DDD ile bir yapay zekâ ajanı"
- "Modele güvenmeyen bir ajan: Spring Modulith, hexagonal mimari ve tip sistemiyle korunan güvenlik"
- "OpenClaw'u anlamak için kendi ajanımı yazdım"

**Hedef okuyucu:** Java/Spring bilen, ama yapay zekâ ajanı yazmamış geliştiriciler.

---

## 1. Giriş: neden kendim yazdım?

- **Anlatılacak:** alfred bir rakip ürün değil, bir öğrenme projesi. OpenClaw gibi ajanların nasıl çalıştığını onları kullanarak değil, yazarak anlamak istedim. DDD de bu projenin ikinci öğrenme hedefi.
- **Kanca:** "Proaktiflik daha fazla yetki değil, daha fazla dikkat demektir."
- **Ne öğrendim?** Başta neyi bilmiyordum? Hangi soruyla başladım?
- **Kanıt:** PRD §1 Vizyon.

## 2. Domain'i bulmak: yapbozun parçaları

- **Anlatılacak:** İlk listen şuydu: kişilik, ses, karar döngüsü, yetkinlik, yardımcı olmak. Bu liste iki şeyi karıştırıyordu:
  - **Nitelikler:** "yardımcı olmak" gibi, alfred'in nasıl biri olduğunu anlatan şeyler.
  - **Parçalar:** kendi verisi, kuralı ve kelimeleri olan bölümler. Bounded context bunlardan biri olmalı.
- **Kullandığın test:** "Bu parçanın kendine ait verisi, kuralları ve kelimeleri var mı? Başka parçalara dokunmadan değişebilir mi?"
- **Sonuç:**
  - Core: `agent`, `trust`, `persona`.
  - Supporting ve generic: `capability`, `llm`, `voice`, `memory`, `audit`.
  - Adapter: `cli`.
- **İlginç an:** İkinci turda karar döngüsü listeden düşmüştü. Oysa asıl core domain oydu.
- **İlginç an:** Konuşma hafızası ile işlem kaydı ilk bakışta aynı şey gibi görünüyordu, ikisi de "kayıt tutuyor". Ama biri geçici çalışma belleği, diğeri kalıcı güvenlik kanıtı. Yaşam döngüleri zıt olduğu için ayrıldılar.
- **Ne öğrendim?** Bounded context'i bir kavram olarak değil, bir test olarak düşünmek neyi değiştirdi?
- **Kanıt:** memlog, "DDD round 1/2" ve "Module map approved" kayıtları.

## 3. Ubiquitous language iki dilde

- **Anlatılacak:** PRD Türkçe, kod İngilizce. Örnekler: Görev → `Task`, Eylem → `Action`, Kademe → `Tier`, Güvenlik kelimesi → `SafetyWord`. Bu çeviri katmanı bir sözlük tablosuyla bağlayıcı hâle getirildi.
- **Elenen alternatif:** Domain'i Türkçe, altyapıyı İngilizce yazmak.
- **Ne öğrendim?** Ortak dil iki dile yayıldığında kayma nasıl önlenir?
- **Kanıt:** Omurga, Consistency Conventions ve AD-17.

## 4. Paradigma: neden hexagonal ve Spring Modulith?

- **Elenen alternatifler:**
  - **Katmanlı mimari:** Tanıdık, ama bağımlılıklar sızıyor ve güvenliği atlayan bir "kestirme" yazmak çok kolay.
  - **Pipes-and-filters:** Ses hattına uyuyor, ama kendi üstüne dönen karar döngüsüne uymuyor.
- **Seçilen:** Hexagonal mimari ile Modulith modülleri. Modül sınırları `ApplicationModules.verify()` testiyle her build'de korunuyor.
- **Görsel:** Omurgadaki bağımlılık diyagramı.
- **Ne öğrendim?** Modüler monolit ile mikroservis arasındaki farkı artık nasıl anlatırsın?
- **Kanıt:** AD-1 ve AD-2.

## 5. Yazının kalbi: modele güvenmeyen güvenlik

- **Problem:** Bir LLM'e shell erişimi vermek. "Kademeyi model değil, kod belirler."
- **Dört alternatif:**
  - AOP/proxy: Çalışma anında kontrol eder, atlanabilir.
  - "Güvenlik işi kendisi yapsın": Sorumluluğu şişirir.
  - Olay tabanlı kontrol: Senkron döngüyü bozar.
  - **`ApprovedAction`:** Tip sistemiyle korunan bir değişmez kural.
- **Seçilen:** `ApprovedAction` yalnızca `trust` içinde üretilebilir. Tam komutu taşır ve tek kullanımlıktır. Güvenlik kapısını atlayan kod **derlenmez**.
- **Saldırgan incelemenin bulduğu açık:** "`cat x` sınıflandırılıp `sh -c` ile başka bir şey çalıştırılabilir." Çözüm: komut gerçek bir shell gramerine göre ayrıştırılır ve ayrıştırılmış hâliyle çalıştırılır, `sh -c` kullanılmaz.
- **Güvenlik kelimesi:**
  - Neden "evet" yetmiyor?
  - Neden kelimeyi Claude hiç görmüyor?
  - Neden onay metnini model değil `trust` üretiyor?
- **Korunan kaynaklar:** alfred kendi API anahtarını okuyamaz, kendi veritabanını silemez.
- **Ne öğrendim?** "Make illegal states unrepresentable" ilkesini somut olarak nerede gördün?
- **Kanıt:** AD-3, AD-4, AD-5, AD-19, AD-20 ve `reviews/review-adversarial.md`.

## 6. İşlem kaydı: önce kaydet, sonra çalıştır

- **Anlatılacak:** Kontrol yolu senkron, kayıt yolu olay tabanlı.
- **İncelemenin bulduğu tuzak:** Modulith olayları yalnızca bir transaction içinde yayımlanırsa kalıcı oluyor.
- **Çözüm:** Write-ahead kayıt. Ayrıca veritabanı rolü kayıt tablosunda yalnızca `INSERT` ve `SELECT` yetkisine sahip, yani alfred kendi kaydını değiştiremez. Postgres yoksa hiçbir Eylem yapılmaz.
- **Ne öğrendim?** "Olay yayımladım, demek ki kaydedildi" varsayımı neden yanlış?
- **Kanıt:** AD-6 ve AD-7.

## 7. Claude'a bağlanmak: port, karma istemci ve 5 dolar

- **Port fikri:** `LlmPort` hiçbir sağlayıcı tipini dışarı sızdırmıyor. Model değiştirmek yalnızca adapter değiştirmek.
- **Karma yaklaşım:** Ana yol Spring AI, eksik kalanları Anthropic SDK tamamlıyor. Üç kural var:
  - İkisi de yalnızca adapter'ın içinde.
  - Her işlemin tek bir yolu var.
  - SDK sürümü yönetimi belli.
- **Bütçe matematiği:** Görev başına yaklaşık 0,005 dolar ile Haiku 5.5 kullanılabilir, Sonnet ile ayda yaklaşık 90 dolar tutardı. Bütçe ve tur sınırı her çağrıdan önce kontrol ediliyor ve kontrol başarısız olursa çağrı yapılmıyor.
- **Sürpriz:** Haiku 5.5'in "preserved thinking" özelliği yüzünden konuşma geçmişi yalnızca eklenebilir. Tool çıktısı geçmişe eklenmeden önce kırpılmalı.
- **Ne öğrendim?** Framework'ün "tool'ları benim yerime çalıştırırım" kolaylığı neden bir güvenlik açığı?
- **Kanıt:** AD-11 ve AD-12.

## 8. GPU olmadan Türkçe ses

- **Anlatılacak:** Motor bir port arkasında, seçim ölçümle yapılıyor.
- **Adaylar:** seda, faster-whisper ve Groq. "Önce ücretsiz ve yerel" ilkesi.
- **Ne öğrendim?** Kararı ertelemek ile kararsız kalmak arasındaki fark nedir? Port, ertelemeyi nasıl güvenli kılıyor?
- **Kanıt:** AD-13 ve `research-stt-stack.md`. M3'teki benchmark sonuçları buraya eklenecek.

## 9. Güvenli bir yerel kanal

- **Anlatılacak:** Unix socket önerisini reddettin, çünkü işletim sistemine fazla bağlıydı. Onun yerine localhost HTTP seçildi, güvenlik kuralları da ilk günden konuldu:
  - Yalnızca `127.0.0.1` dinleniyor.
  - Her istekte token var.
  - `Origin` ve `Host` başlıkları doğrulanıyor.

  Bu kurallar, tarayıcıdaki bir sayfanın alfred'e komut göndermesini engelliyor.
- **Ne öğrendim?** CSRF ve DNS rebinding'in yerel servislerle ilişkisi.
- **Kanıt:** AD-10.

## 10. Geleceğe açık kapı: çoklu ajan

- **Anlatılacak:** "Tek aktif Görev" bir yapı değil, bir politika olarak tasarlandı. Her şey bir `taskId` taşıyor, kayıt olayları ayrıca `parentTaskId` taşıyor. Alt ajanların yetkisi kademe 0–1 ile sınırlı olacak.
- **Ne öğrendim?** Bugün ucuz olan hangi karar yarın pahalı bir yeniden tasarımı önlüyor?
- **Kanıt:** AD-9, AD-12, AD-23 ve Deferred bölümü.

## 11. Süreç üzerine: bağımsız incelemeler

- **Anlatılacak:** Omurga yazıldıktan sonra dört bağımsız inceleme yapıldı: PRD karşılaştırması, kalite rubriği, sürüm doğrulaması ve saldırgan inceleme. Toplam 3 kritik ve yaklaşık 10 yüksek önemde açık buldular.
- **Ne öğrendim?** Kendi tasarımına saldırtmak neyi gösterdi?
- **Kanıt:** `reviews/` klasörü.

## 12. Kapanış

- **Anlatılacak:** MVP'nin bitiş ölçütü 60 saniyelik demo. Sonrasında alfred proaktif bildirimlere ve kalıcı hafızaya geçecek.
- **Medium yazısının kendisi**, PRD'deki SM-2 başarı ölçütü.
- **Ne öğrendim?** Projenin başına dönsen neyi farklı yapardın?

---

**Görseller (omurgadan alınabilir):** bağımlılık yönü diyagramı, çalışma zamanı ve dağıtım diyagramı, güven kademeleri tablosu.

**Not:** Kod yazıldıkça her bölüme gerçek bir kod parçası eklenmeli. Örnekler: `ApprovedAction`'ın paket-özel constructor'ı, `verify()` testi, `TierFactProbe`.
