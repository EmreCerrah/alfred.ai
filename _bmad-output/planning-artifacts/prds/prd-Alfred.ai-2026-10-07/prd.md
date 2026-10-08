---
title: "PRD: alfred.ai"
status: final
created: 2026-10-07
updated: 2026-10-08
---

# PRD: alfred.ai

## 0. Belgenin amacı

Bu PRD, alfred'in MVP'sinde **ne** yapacağını tanımlar. **Nasıl** yapılacağı bu belgenin konusu değildir.

- **Okuyucular:** Emre ve sonraki aşamalar (`bmad-architecture`, `bmad-create-epics-and-stories`).
- **Dayandığı brief:** `briefs/brief-Alfred.ai-2026-10-05/brief.md`. Bu belge brief'in üzerine kuruludur ve onu tekrarlamaz.
- **Teknik tercihler, örnek diyaloglar ve araştırma notları:** `addendum.md`.
- **Varsayımlar:** Taslaktaki tüm varsayımlar Emre tarafından onaylanmış ve gereksinime dönüştürülmüştür.
- **Terimler:** §3'teki sözlükte tanımlıdır ve belge boyunca aynı anlamda kullanılır.

## 1. Vizyon

alfred, Emre'nin kişisel Linux bilgisayarında yaşayan, Türkçe konuşan bir **dijital uşak ve kıdemli teknik yardımcıdır**. Sesle ya da yazıyla verilen isteği anlar. Docker'ı, Git'i, dosyaları, shell'i ve tarayıcıyı kullanarak işi yapar. Sonucu doğrular ve kısaca raporlar. Bir sorun fark ettiğinde danışır. Geri alınamaz bir şeyi, kullanıcı güvenlik kelimesini söylemeden asla yapmaz. Beyni Claude'dur. Karar döngüsü, Tool'lar ve sınırlar ise alfred'in kendi kodudur.

alfred bir rakip ürün değil, bir **öğrenme projesidir**. Emre, OpenClaw gibi ajanların nasıl çalıştığını onları kullanarak değil, kendisi yazarak anlamak istiyor. Geliştirme Domain-Driven Design ile yürütülür; DDD'yi uygulayarak öğrenmek de projenin açık hedeflerinden biridir. Core domain dışındaki her şey (ses motorları, LLM istemcisi, tarayıcı açma vb.) hazır araçlarla çözülür.

alfred'i alfred yapan şey **kişiliğidir**. Batman'in Alfred'i gibi konuşur: sakin, kısa, kanıta dayalı ve arada kuru bir İngiliz mizahıyla. Kullanıcı bir insanla konuşuyormuş gibi hisseder. Ama hedef Turing testini geçmek değildir; biraz robotik kalması kabul edilebilir.

Uzun vadede alfred büyüdükçe yetkisi değil, dikkati artar. Önce hatırlamayı, sonra beklemeden fark etmeyi öğrenir. Ama her zaman fark eder ve danışır, sınırlarını aşmaz: **proaktiflik daha fazla yetki değil, daha fazla dikkat demektir.** Hedeflenen son durum, "Alfred, şuna bir bakar mısın?" demenin yeterli olmasıdır.

## 2. Hedef kullanıcı

### 2.1 Yapılacak işler (JTBD)

- **İşlevsel:** "Docker'da ne oluyor, Redis neden düştü?" gibi soruları sistemle tek tek uğraşmadan sorabilmek. alfred kanıt toplar ve teşhis koyar.
- **Güven:** alfred'e geniş yetki verebilmek ve geri alınamaz bir şeyin onaysız yapılmayacağından emin olmak.
- **Duygusal:** Bilgisayarla değil, onu yöneten sakin ve yetkin biriyle konuşuyormuş hissi.
- **Kurucu olarak:** Bir ajanın karar döngüsünü, tool çağırmayı ve DDD'yi uçtan uca kendi yazarak öğrenmek ve bunu portfolyoda göstermek.

### 2.2 Kullanıcı olmayanlar (MVP)

- Emre dışındaki herkes. Çok kullanıcılı veya ev içi kullanım yoktur.
- Fedora + Sway dışındaki ortamlar. Windows yalnızca geliştirme makinesidir.

### 2.3 Temel kullanıcı yolculukları

**UJ-1. Emre, gece Redis'in neden düştüğünü sesle buldurur (60 saniyelik demo).**
1. Emre bas-konuş tuşuna basar. alfred "Buyurun?" der.
2. "Docker'da ne çalışıyor?" diye sorar. alfred: "3 container çalışıyor. Redis durmuş görünüyor."
3. "Redis loglarını kontrol et." alfred kanıt toplar ve portların yanlış verildiğini bulur. "Düzeltmemi ister misiniz?" diye sorar.
4. Emre onaylar. alfred config'i düzeltir, Redis'i yeniden başlatır, durumu doğrular ve "Her şey sorunsuz çalışıyor" der.
5. "Eski Redis volume'ünü temizle." alfred volume'ün boyutunu ve etkisini söyler, sonra güvenlik kelimesini ister. Emre "Yeşil" der. alfred "Tamamlandı" der.
6. "Bana AC/DC'den bir şarkı aç." YouTube'da bir şarkı çalmaya başlar.

**Kenar durum (UJ-1):** Emre güvenlik kelimesi yerine "evet" derse alfred volume'ü silmez ve kelimeyi tekrar ister.

**UJ-2. Emre, alfred'i CLI'dan yazıyla kullanır.** Ses bileşenleri hazır değilken ya da sessiz bir ortamdayken Emre aynı istekleri terminalden yazar. Güvenlik kelimesi ekrana yazılır, Emre onu yazarak onaylar. Davranış sesli moddakiyle aynıdır.

**UJ-3. Emre gün sonunda "Alfred, bugün ne yaptın?" diye sorar.** alfred işlem kaydına bakar ve kısa bir özet verir: "Bugün iki teşhis yaptım, efendim. Bir container'ı yeniden başlattım; silme işlemi yapmadım. Bugünkü harcama 4 sent."

## 3. Sözlük

- **alfred:** Ürün ve çalışan uygulama.
- **Alfred:** Ürünün karakteri, yani kişiliği ve konuşma tarzı.
- **Konuşma:** Kullanıcıyla alfred arasındaki ardışık mesajlar. Kullanıcı "yeni konuşma" deyince ya da alfred yeniden başlatılınca biter. Hafıza yalnızca bir Konuşma içinde geçerlidir.
- **Görev:** Kullanıcının bir isteğini karşılamak için alfred'in yürüttüğü, bir ya da daha çok Tur'dan oluşan iş.
- **Tur:** Karar döngüsünün tek bir adımı: Claude'a bir çağrı ve varsa ardından gelen Eylem'ler.
- **Tool:** alfred'in sisteme dokunmak için kullandığı yetenek: shell, dosya, Git, Docker, tarayıcı.
- **Eylem:** Bir Tool'un belirli parametrelerle tek bir kullanımı, örneğin `docker volume rm redis-data`.
- **Kademe:** Bir Eylem'in risk sınıfı: 0, 1 ya da 2. Kademeyi alfred'in deterministik kodu belirler, model belirlemez.
- **Güvenlik kelimesi:** Kademe 2 bir Eylem için her seferinde rastgele üretilen tek kullanımlık onay kelimesi.
- **Acil durdurma:** Çalışan Görev'i anında durduran klavye kısayolu. Varsayılanı `Super+Esc`.
- **İşlem kaydı:** alfred'in yaptığı her Eylem'in yerel ve yalnızca eklenebilen kaydı.
- **Bas-konuş:** Kullanıcının tuşa basılı tutarak konuştuğu ses girişi.

## 4. Özellikler

### 4.1 Kişilik (Alfred)

**Açıklama:** Alfred, sistemi gerçekten yöneten kıdemli bir teknik yardımcıdır; "uşak gibi konuşan bir chatbot" değildir. Kullanıcıya "efendim" diye hitap eder. Sakin, kısa ve kanıta dayalı konuşur. Mizah kuru ve seyrektir, yanıtın kendisinin yerine geçmez. Kişilik yalnızca prompt'ta değil, sistemin genelinde görünür: ton yanıtlarda, risk anlayışı onaylarda, ses karakteri TTS'te. Kanonik örnek diyaloglar `addendum.md` içindedir. UJ-1, UJ-2 ve UJ-3'ü gerçekleştirir.

#### FR-1: Kişilik tutarlılığı
alfred, tüm yanıtlarını Alfred karakterinde ve Türkçe verir.
- Yanıtlar kullanıcıya "efendim" diye hitap eder ve aşırı resmî kalıplar içermez ("Sayın efendim, emriniz üzerine…" gibi).
- Basit sesli yanıtlar, kullanıcı ayrıntı istemedikçe en fazla 3 cümledir. Teşhis, Kademe 2 onayı ve başarısızlık mesajları bu sınırın dışındadır. Bunlar da gereksiz ayrıntı içermez.
- alfred panik yapmaz, ketumdur ve asla ukala değildir. Bir şey fark ettiğinde dayatmaz, önerir ve kararı kullanıcıya bırakır.
- Mizah en fazla bir kısa cümledir ve her yanıtta yer almaz.
- Addendum'daki örnek diyaloglar kabul testlerinde referans olarak kullanılır.

#### FR-2: Kanıta dayalı konuşma
alfred, bir teşhisi kanıtla desteklemeden kesin bir dille söylemez.
- Kanıt yetersizse bunu açıkça belirtir, örneğin "Henüz emin değilim, efendim".
- Hipotezle doğrulanmış sonucu birbirinden ayırır: "OOM ihtimali güçlü, fakat henüz sebep diyemem."

### 4.2 Etkileşim kanalları

**Açıklama:** Aynı ajan iki kanaldan kullanılır: bas-konuş ile ses ve CLI ile yazı. İki kanal aynı karar döngüsünü, aynı güven modelini ve aynı işlem kaydını paylaşır. UJ-1 ve UJ-2'yi gerçekleştirir.

#### FR-3: Bas-konuş ile ses
Kullanıcı bas-konuş tuşuna basılı tutarak Türkçe konuşur ve alfred Türkçe sesli yanıt verir.
- Tuşa basıldığında alfred dinlemeye hazır olduğunu belirtir ("Buyurun?").
- Söylenen istek metne çevrilir, işlenir ve yanıt sesli olarak okunur.
- Kısa bir komutta kullanıcının sözünü bitirmesinden alfred'in yanıta başlamasına kadar geçen süre yaklaşık 3 saniyedir. Çok adımlı bir Görev'de alfred bu süre içinde kısa bir ara yanıt verir ("Bakıyorum, efendim."), sonucu hazır olunca söyler.
- Makinede GPU yoktur. Ses motorları bu hız hedefini CPU üzerinde ya da bütçeye uygun bir bulut servisiyle karşılamalıdır.
- Ses motorları değiştirilebilir. Motor değişikliği karar döngüsünü etkilemez.

**Kapsam dışı:** uyandırma kelimesi, sürekli dinleme, Alfred'e özgü ses (bkz. §7.2).

#### FR-4: CLI ile yazı
Kullanıcı aynı istekleri terminalden yazarak verebilir ve yanıtları yazılı olarak alır.
- CLI'daki davranış, onay ve durdurma dâhil, sesli modla aynıdır.
- Ses bileşenleri çalışmıyorken CLI tek başına kullanılabilir.

#### FR-5: Konuşma içi hafıza
alfred, aynı Konuşma içindeki önceki mesajları hatırlar.
- "Onu yeniden başlat" gibi bir istekte "onu"nun neye karşılık geldiğini önceki mesajlardan çözer.
- Konuşma, kullanıcı "yeni konuşma" dediğinde (sesle ya da CLI'da) ya da alfred yeniden başlatıldığında biter. Süre dolması Konuşma'yı bitirmez.
- Konuşma bittiğinde hiçbir şey kalıcı olarak saklanmaz. İşlem kaydı bu kuralın dışındadır.

### 4.3 Teşhis ve çok adımlı akıl yürütme (MVP'nin kalbi)

**Açıklama:** alfred bir isteği tek bir komutla değil, birden çok Tur'da çözer: hipotez → kanıt toplama → teşhis → aksiyon → doğrulama. Kullanıcıyı gereksiz teknik ayrıntıya boğmaz. UJ-1'i gerçekleştirir.

#### FR-6: Çok adımlı teşhis
alfred, bir sorunu çözmek için birden çok Tool çağrısını sırayla planlar ve her adımın sonucuna göre bir sonrakine karar verir.
- "Redis loglarını incele, hatayı bul" gibi bir istekte alfred en az şu adımları kendisi yürütür: container durumu, loglar, ilgili config.
- Teşhisi söylerken dayandığı kanıtı kısaca belirtir.

#### FR-7: İstek kapsamında kalma
alfred, kullanıcının istediğinden fazlasını yapmaz.
- Kullanıcı yalnızca teşhis istediyse alfred düzeltmeyi uygulamaz, önerir ("Düzeltmemi ister misiniz?").
- "Hallet" gibi açık yetki veren istekler teşhis ve düzeltmeyi birlikte kapsar. Kademe kuralları yine geçerlidir.

#### FR-8: Sonucu doğrulama
alfred, durumu değiştiren her Eylem'den sonra sonucu doğrular ve doğrulanmamış bir sonucu başarı olarak bildirmez.
- Bir container'ı yeniden başlattıktan sonra çalıştığını kontrol eder.

#### FR-9: Dürüst başarısızlık
alfred bir Görev'i başaramadığında bunu açıkça kabul eder ve sistemi güvenli bırakır.
- Başarısızlık mesajında şunlar yer alır: durum, ne denendiği, neyin kesin olmadığı, sistemin hangi durumda bırakıldığı ve bir sonraki seçenek.
- Bir Tool hata verirse alfred sonucu varsaymaz. Mümkünse farklı bir yöntemle yeniden dener, o da başarısız olursa durur.
- Başarısızlıktan sonra tahmine dayanarak değişiklik yapmaya devam etmez.
- Claude API'ye ya da ağa erişilemezse alfred bunu açıkça söyler ve hiçbir Eylem yapmaz.

### 4.4 Tool'lar

**Açıklama:** MVP'deki Tool'lar şunlardır: shell, dosya okuma/yazma, Git, Docker ve tarayıcıda içerik açma. Her Tool kullanımı bir Eylem'dir ve önce güven modelinden (§4.5) geçer. UJ-1'i gerçekleştirir.

#### FR-10: Geliştirme ortamı Tool'ları
alfred shell komutları çalıştırabilir, dosya okuyup yazabilir, Git ve Docker işlemleri yapabilir.
- Her Eylem, çalıştırılmadan önce bir Kademe ile sınıflandırılır (FR-12).
- Tool çıktısı kullanıcıya ham hâliyle değil, özetlenerek aktarılır.

#### FR-11: YouTube'da içerik açma
Kullanıcı bir sanatçı ya da şarkı istediğinde alfred uygun bir videoyu bulur ve tarayıcıda oynatır.
- "AC/DC'den bir şarkı aç" isteğinde, arama sonuç listesi değil, bir video açılır.
- Belirli bir şarkı adı verilmediyse sanatçının en popüler videosu seçilir.

### 4.5 Güven modeli

**Açıklama:** Varsayılan duruş güvendir; alfred iş yapsın diye vardır. Tek kural şudur: **geri alınamayan her şey, güvenlik kelimesi olmadan yapılmaz.** Kademeyi model değil, alfred'in deterministik kodu belirler. Son söz her zaman kullanıcınındır. Ayrıntılı örnekler `addendum.md` içindedir. UJ-1 ve UJ-2'yi gerçekleştirir.

| Kademe | Davranış | Örnekler |
|---|---|---|
| **0** | Sormadan yapar | Log okumak, `ls`/`cat`, `git status/log/diff`, `docker ps/inspect/logs`, sistem metrikleri, uygulama başlatmak, YouTube açmak |
| **1** | Yapar ve raporlar. Yalnızca somut bir risk fark ederse bir kez uyarır | Container start/stop/restart, `git commit`, normal `git push`, yeni dosya oluşturmak, yedeği alınmış ya da Git'te temiz duran bir dosyayı düzenlemek, `docker pull`, `docker compose up/build`, container'ı yeniden oluşturmak |
| **2** | Güvenlik kelimesi ister | `rm`, volume/image silme, `git push --force`, `git reset --hard`, branch silme, yedeksiz üzerine yazma, `sudo`, paket kurma/kaldırma, kapatma/yeniden başlatma, Emre adına mesaj veya e-posta göndermek, **bilinmeyen her komut** |

#### FR-12: Deterministik sınıflandırma
alfred, her Eylem'i çalıştırmadan önce kendi kurallarıyla bir Kademe'ye atar.
- Model bir Eylem'i daha düşük bir Kademe'ye indiremez.
- Tanınmayan ya da sınıflandırılamayan her Eylem Kademe 2 sayılır (fail-closed).
- `sudo` içeren her Eylem, içeriğinden bağımsız olarak Kademe 2'dir.
- Birleşik komutlar (`|`, `&&`, `;`, `$(...)`) parçalarına ayrılır ve parçaların en yüksek Kademe'si tüm komutun Kademe'si olur. `>` ile yönlendirme bir dosya yazma işlemi sayılır. Ayrıştırılamayan bir komut Kademe 2'dir.

#### FR-13: Önce geri alınabilir yap
alfred, bir Eylem'i geri alınabilir hâle getirebiliyorsa önce bunu yapar.
- Git'te izlenmeyen bir dosyanın üzerine yazmadan önce bir yedek alır. Böylece Eylem Kademe 1 olarak ele alınır.

#### FR-14: Kademe 1'de uyarı ve ısrar
alfred, Kademe 1 bir Eylem'de somut bir risk fark ederse bir kez uyarır. Kullanıcı ısrar ederse Eylem'i uygular.
- Uyarı neyin riskli olduğunu söyler, örneğin "Aktif bağlantılar var".
- Kullanıcı "Biliyorum, yine de yap" dediğinde alfred tekrar sormadan uygular.

#### FR-15: Güvenlik kelimesi
alfred, Kademe 2 bir Eylem'den önce neyin etkileneceğini, etkisini ve yaptığı doğrulamayı söyler, sonra rastgele bir güvenlik kelimesi ister.
- Eylem yalnızca o kelime birebir söylendiğinde ya da yazıldığında çalışır. "Evet", "biliyorum, sil" ya da farklı bir kelime onay sayılmaz.
- Kelime her seferinde yeniden üretilir ve tek kullanımlıktır.
- Sesli modda kelime söylenir. CLI'da ekrana yazılır ve kullanıcı yazarak onaylar.
- alfred'in kendi sesi ya da arka plandaki bir ses onay sayılmaz.
- Onay istemi korkutucu değildir ve uyarı yağdırmaz. Örnek: `redis-data → 2.4 GB → kalıcı olarak silinecek`.

#### FR-16: Acil durdurma
Kullanıcı `Super+Esc` ile çalışan Görev'i anında durdurabilir.
- Durdurma, devam eden Eylem'i ve sonraki Tur'ları keser.
- Otomatik geri alma yapılmaz. Geri alma ayrı ve açık bir istektir.
- Kısayol yeniden atanabilir.
- Durdurma, Claude API'ye ya da ağa bağlı değildir. Bağlantı yokken de çalışır.

### 4.6 Maliyet kontrolü

**Açıklama:** alfred, Claude API'yi kullandıkça öde modeliyle kullanır. Aylık bütçe şimdilik 5 USD'dir. Döngüye giren tek bir Görev bütçeyi tüketemez.

#### FR-17: Görünür maliyet
alfred her Görev'in yaklaşık maliyetini ve ayın toplam harcamasını kaydeder ve istendiğinde söyler.

#### FR-18: Bütçe sınırları
alfred, aylık harcamayı ve tek bir Görev'in Tur sayısını sınırlar.
- Aylık harcama bütçenin %80'ine ulaştığında alfred kullanıcıyı uyarır.
- Bütçenin %100'ünde yeni Görev başlatmaz ve nedenini söyler.
- Tek bir Görev'in Tur sayısı sınırlıdır. Sınıra ulaşıldığında alfred durur ve FR-9'a göre raporlar. Başlangıç sınırı 15 Tur'dur ve ayarlanabilir.

### 4.7 İşlem kaydı

**Açıklama:** alfred'in yaptığı her şey sonradan okunabilir. Bu kayıt hem bir güvenlik aracıdır hem de Medium yazısı için malzemedir. UJ-3'ü gerçekleştirir.

#### FR-19: Yalnızca eklenebilen kayıt
alfred her Eylem için yerel bir kayda bir satır ekler: zaman, kullanıcının isteği, Tool ve komut, Kademe, onay durumu, sonuç ve maliyet.
- alfred kendi işlem kaydını değiştiremez ve silemez.
- Gizli bilgiler (şifre, token, API anahtarı) kayda girmeden önce maskelenir.

#### FR-20: Gün özeti
Kullanıcı "Bugün ne yaptın?" diye sorduğunda alfred işlem kaydından kısa bir özet verir.

## 5. Kısıtlar ve sınırlar

- **Ortam:** Tek makine, Fedora + Sway (Wayland). Tek kullanıcı.
- **Beyin:** Yalnızca Claude, API anahtarıyla. Claude Pro aboneliği kullanılmaz (gerekçesi addendum'da).
- **Teknoloji:** alfred Java ve Spring ile yazılır. Brief'teki portfolyo mesajlarından biri "Spring ekosistemine hâkim" olmaktır.
- **Kendi kodu:** Karar döngüsü, Tool katmanı ve güven modeli alfred'in kendi kodudur. Bunları gizleyen hazır ajan framework'leri kullanılmaz. LLM istemcisinin kendi başına tool çalıştırma özelliği kapalı tutulur, çünkü açık kalırsa güven modeli atlanabilir.
- **Hazır olanı kullan:** Core domain dışındaki her şey için hazır araçlar tercih edilir. Bağımlılıklar Docker'da çalışır; alfred ise host'ta modüler monolit olarak çalışır.
- **Dil:** Türkçe öncelikli, ama dil koda gömülmez. İngilizce sonradan eklenebilmelidir.
- **Gizlilik, bilinçli risk:** alfred'in okuduğu dosyaların içeriği Claude API'ye gider. Claude'a gönderilen içerikte yasak klasör ya da maskeleme yoktur, çünkü bilgisayarda hassas ya da üretim verisi tutulmaz. Bu kural yalnızca Claude'a gönderilen içerik içindir. İşlem kaydındaki maskeleme (FR-19) ayrıca geçerlidir.
- **Güvenlik:** Üçüncü taraf eklenti ya da "skill" pazaryeri yoktur. Web'den gelen içerik talimat olarak değil, veri olarak ele alınır.

## 6. Hedef olmayanlar

- alfred, OpenClaw ya da Goose'a rakip bir ürün değildir. Bir özellik yarışına girmez.
- Ticari bir ürün değildir.
- Turing testini geçmeye çalışmaz.
- Mikroservis mimarisi kullanmaz.

## 7. MVP kapsamı

### 7.1 Kapsamda

FR-1–FR-20. Kilometre taşları brief'ten gelir (haftada yaklaşık 8 saat, 8–10 hafta tahmini):

| # | Kilometre taşı | Sonuç |
|---|---|---|
| M1 | CLI karar döngüsü, shell ve Docker Tool'ları | Yazılı sorulara cevap verir, logları inceler, hatayı bulur |
| M2 | Güven modeli, maliyet kontrolü, işlem kaydı | Kademe 2 Eylem'ler güvenlik kelimesi ister, `Super+Esc` çalışır |
| M3 | Ses | Bas-konuş ile Türkçe ses girişi ve Türkçe sesli yanıt |
| M4 | Git, dosya, YouTube ve demo | UJ-1 tek seferde çalışır |

**Kontrol noktası:** M1 dört haftada bitmezse kapsam daraltılır.

### 7.2 MVP dışında

- **V0.2:** Uyandırma kelimesi ("Alfred") ve sesli durdurma kelimesi. Kalıcı hafıza. MCP desteği. Web arayüzü.
- **V1:** Proaktif bildirimler, zamanlanmış görevler.
- **Daha sonra:**
  - İngilizce.
  - Alfred'i çağrıştıran, İngiliz uşak tınısında bir ses. Bu, belirli bir oyuncunun sesinin kopyası olmayacak.
  - Üretim sistemleri için güçlü onay.
  - Ekran ve fare kontrolü.
  - Çok ajanlı yapı, çoklu işletim sistemi.

## 8. Başarı ölçütleri

**Birincil**
- **SM-1:** 60 saniyelik demo (UJ-1) tek seferde ve kurgusuz çalışır. FR-3, FR-6, FR-10, FR-11, FR-15'i doğrular.
- **SM-2:** Emre, alfred'de DDD'yi ve ajan karar döngüsünü nasıl uyguladığını anlatan bir Medium yazısı yayımlar.

**İkincil**
- **SM-3:** MVP'den sonra alfred iki hafta boyunca her gün gerçekten kullanılır.
- **SM-4:** README'nin en üstünde demo videosu ve mimari diyagram bulunur.
- **SM-5:** İşlem kaydında güvenlik kelimesi olmadan çalışmış tek bir Kademe 2 Eylem bile yoktur. FR-12 ve FR-15'i doğrular.
- **SM-6:** Aylık Claude API harcaması 5 USD'yi aşmaz. FR-17 ve FR-18'i doğrular.

**Karşı ölçütler (optimize edilmez)**
- **SM-C1:** Onay sayısını azaltmak hedef değildir. Kullanımı kolaylaştırmak (SM-3) için Kademe 2 kuralları gevşetilmez.
- **SM-C2:** Maliyeti düşürmek (SM-6) için teşhisin kalitesi feda edilmez. Kanıt toplamadan cevap vermek tasarruf sayılmaz.
- **SM-C3:** Mizah sıklığı bir hedef değildir. Kişilik, kısalık ve doğrulukla ölçülür.

## 9. Açık sorular

1. **Güvenlik kelimesinin sağlamlığı:** alfred'in kendi TTS sesi ve arka plandaki ses, onay olarak algılanmaktan teknik olarak nasıl dışlanacak? (mimari)
2. **`Super+Esc`:** Sway'de global kısayol alfred'e nasıl iletilecek? Durdurma, çalışan alt süreçleri nasıl sonlandıracak? (mimari)
3. **Türkçe ses motorları:** STT ve TTS motorları nasıl seçilip kurulacak? Adaylar ve ayrıntılar addendum'da. **Makinede GPU yok.** Whisper'ın büyük modelleri CPU'da yavaş kalıyor. Mimari aşamada iki yol karşılaştırılacak: küçük bir yerel model ya da bütçeye uygun bir bulut STT. Karşılaştırmada FR-3'teki yaklaşık 3 saniyelik hedef ve SM-6'daki bütçe ölçüt olacak.
4. **YouTube video bulma:** YouTube Data API mi, `yt-dlp` mi? (mimari)
5. **Bütçe yeterliliği:** 5 USD'nin yetip yetmediği ilk haftalarda ölçülecek, gerekirse bütçe ya da model stratejisi değişecek.
6. **Redis karışıklığı:** Demodaki Redis, Emre'nin projesine ait. alfred'in kendi bağımlılıkları arasında Redis olursa demodaki "eski volume'ü temizle" isteği alfred'inkini hedeflememeli.
