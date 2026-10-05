---
title: "Product Brief: alfred.ai"
status: draft
created: 2026-10-05
updated: 2026-10-05
---

# Product Brief: alfred.ai

## Özet

alfred, Emre'nin kişisel Linux bilgisayarında yaşayan, Türkçe konuşan bir **dijital uşak ve teknik danışman**. Sesle ya da yazıyla verilen isteği anlar. Docker'ı, Git'i, dosyaları ve tarayıcıyı kullanarak işi yapar, sonucu doğrular ve kısaca söyler. Bir sorun fark ettiğinde danışır. Geri alınamaz bir şey yapmadan önce mutlaka onay ister. Beyni Claude'dur. Ama karar döngüsü, tool'lar ve sınırlar alfred'in kendi kodudur.

Proje bir pazar boşluğundan değil, bir öğrenme hedefinden doğdu. Emre'nin ilk yapay zekâ projesi. Amaç önce bir agent'ın nasıl karar verip uyguladığını içeriden öğrenmek, sonra bunu portfolyoda Spring ekosistemine hakimiyetin kanıtı olarak göstermek, en sonunda da evde gerçekten kullanmak.

alfred'i benzerlerinden ayıran üç şey var. Komut çalıştıran bir araç değil, karakteri olan bir danışman olması. Türkçe olması. Ve sınırlarının modele değil koda emanet edilmesi.

## Neden alfred?

alfred bir pazar boşluğundan değil, bir öğrenme isteğinden doğdu. Emre daha önce yapay zekâ ile hiç çalışmadı. Hem kendini geliştirecek hem de portfolyosunda gösterebileceği bir proje arıyordu. Iron Man'deki JARVIS akla geldi. Benzerlerinin var olduğunu görünce soru şu oldu: "Bunu neden ben de yapmıyorum?"

Ama hedef JARVIS değil. Batman'in Alfred'i gibi daha insansı, daha yardımcı bir **dijital uşak**: bilgisayardaki her şeye erişebilen ve istenenleri yerine getiren biri.

alfred üç şeye hizmet eder, önem sırasıyla:
1. **Öğrenmek:** Bir agent'ın nasıl karar verdiğini ve o kararı nasıl uyguladığını içeriden anlamak. Bu yüzden agent döngüsü, tool calling ve planlama sıfırdan yazılır. Ses tanıma, konuşma sentezi ve wake word ise hazır bileşenlerle çözülür; öğrenme hedefi orada değil.
2. **Göstermek:** Portfolyoda iki mesaj, önem sırasıyla: *Spring ekosistemine hakim* ve *bir AI agent'ını uçtan uca kurabiliyor*.
3. **Kullanmak:** Evde, kendi Linux bilgisayarında gerçekten işe yarayan bir asistan.

İlk eylemler bilinçli olarak basit tutuldu — geliştirme ortamı ve tarayıcı kontrolü — ki MVP hızla ortaya çıksın.

## Alfred kimdir?

alfred "bilgisayarını kullanabilen bir yapay zekâ" değil, **dijital bir uşak ve teknik danışmandır**. Bu fark tasarımın tamamını etkiler.

> *Alfred yalnızca komut çalıştırmaz. Niyeti anlar, danışır, yetkisi varsa uygular ve sonucu doğrular.*

**Karakter:** Sakin, saygılı ama aşırı resmi değil, kısa konuşan, analitik, ketum, ölçülü esprili, proaktif ama asla ukala değil. Panik yapmaz ("Anlaşıldı. Redis yanıt vermiyor gibi görünüyor. Bir göz atayım."). Mizahı kısa ve seyrektir ("Görünen o ki build bugün de işbirliği yapmamaya karar vermiş.").

**Danışman tavrı:** alfred işini yapar, sonra söyler; her komutta araya girmez. Danışmanlığı yalnızca bir şey fark ettiğinde devreye girer: "Gateway'i başlatabilirim, ama son üç çalıştırmada Redis bağlantısı başarısız olmuş. Önce Redis'i kontrol edeyim mi?"

**Dil:** alfred Türkçe konuşur ve Türkçe komut alır. İngilizce ileride eklenecek; bu yüzden dil, kodun içine gömülmez. Karakterli bir Türkçe ses bulunamazsa ses motoru sonradan değiştirilebilir olmalı.

**Dürüstlük:** Bir işlem başarısız olduysa asla başarılı olmuş gibi davranmaz. Her eylemden sonra sonucu doğrular.

**Karakter yalnızca konuşmada değil, sistemin her katmanındadır:** ne söylediğinde (kişilik), neye izin verdiğinde (güven modeli) ve nasıl konuştuğunda (ses: sakin, olgun, sıcak, net, hafif resmi bir erkek sesi).

Tam karakter tanımı, örnek cümleler ve taslak system prompt: `addendum.md`.

## alfred'i farklı kılan ne?

OpenClaw ve Goose gibi kişisel agent'lar alfred'in yapacaklarının büyük kısmını zaten yapıyor. alfred onlarla özellik yarışına girmiyor. Farkı üç yerde:

1. **Karakter ve danışman tavrı — projenin kimliği.** Benzer araçlar komut çalıştırır. alfred niyeti anlar, bir şey fark ettiğinde danışır ve yaptığını doğrular. Karakter yalnızca bir system prompt değil: konuşma tonunu, neye izin verildiğini ve sesi birlikte belirler.
2. **Modele güvenmeyen bir güven modeli — teknik olarak en değerli parça.** Bir işlemin geri alınıp alınamayacağına Claude değil, alfred'in deterministik Java kodu karar verir. Sesli onay kodu, arka planda çalan bir videonun ya da yanlış duyulmuş bir cümlenin onay sayılmasını engeller. Bu alandaki güvenlik olaylarının çoğu tam bu boşluktan çıktı: sınırı modelin kendi muhakemesine bırakmak.
3. **Türkçe.** Türkçe konuşan, karakteri olan bir agent. İngilizce sonra gelecek.

Bunların altında bir de yapı taşı var: agent döngüsü hazır bir framework'ün kara kutusuna bırakılmadan, Spring üzerinde sıfırdan yazılır. Güven modelinin araya girebilmesi de buna bağlı.

## Kapsam

**Hedef ortam:** Tek kullanıcı (Emre), tek makine: Fedora + Sway (Wayland). alfred'in çekirdeği host'ta yerel bir servis olarak çalışır; yardımcı servisler Docker Compose'da çalışabilir. Windows yalnızca geliştirme makinesidir; çoklu işletim sistemi desteği kapsam dışıdır.

**Beyin:** Claude API. Claude yalnızca düşünür ve önerir. Agent döngüsü, tool'lar, güven modeli ve ses hattı alfred'in kendi kodudur.

### MVP — ilk çalışan alfred

- **Ses:** Push-to-talk ile konuşma → ses tanıma (STT) → alfred → Türkçe sesli cevap (TTS)
- **Metin:** Aynı agent'a metinle erişim (CLI veya basit web arayüzü) — hata ayıklama ve yedek yol
- **Çok adımlı akıl yürütme — MVP'nin kalbi:** "Redis loglarını incele, hatayı bul" gibi bir isteği kanıt toplayarak, hipotez kurarak ve sonucu doğrulayarak birden çok adımda çözer
- **Tool'lar:** Shell, dosya (okuma/yazma), Git, Docker, tarayıcıda içerik açma (YouTube)
- **Güven modeli:** Geri alınamaz işlemler için sesli onay kodu, klavyeyle acil çıkış
- **Konuşma içi hafıza:** Aynı sohbette önceki söyleneni hatırlar

### MVP'nin bittiğini nereden anlarız: 60 saniyelik demo

1. Emre push-to-talk tuşuna basar → "Buyurun?"
2. "Docker'da ne çalışıyor?" → "3 container çalışıyor. Redis durmuş görünüyor."
3. "Redis loglarını kontrol et, olası hataları tespit et." → "Hatayı tespit ettim: Redis'in bağlı olduğu projede portlar yanlış verilmiş. Düzeltmemi ister misiniz?"
4. "Evet, config'lerdeki portları değiştir ve tekrar çalıştır." → alfred düzeltir, yeniden başlatır, sonucu doğrular → "Her şey sorunsuz çalışıyor."
5. "Eski Redis volume'unu da temizle." → "Bu işlem geri alınamaz. Onaylamak için 'yeşil' deyin." → "Yeşil." → "Tamamlandı."
6. "Şimdi bana AC/DC'den bir şarkı aç." → "Peki." → En popüler AC/DC şarkısı YouTube'da çalmaya başlar.

Bu video tek çekimde, kurgusuz çekilebildiğinde MVP tamamdır.

### MVP yol haritası

Sabit bir tarih yok. MVP, her biri kendi başına çalışan dört kilometre taşına bölünür:

| # | Kilometre taşı | Sonunda elde olan |
|---|---|---|
| **M1** | Metinle çalışan agent döngüsü + shell ve Docker tool'ları | Yazılı soruya cevap verir, logları inceleyip hatayı bulur. Öğrenmenin büyük kısmı burada. |
| **M2** | Güven modeli | Geri alınamaz işlemler onay kodu ister, `Super+Esc` süren işi durdurur. |
| **M3** | Ses | Push-to-talk ile Türkçe konuşma ve Türkçe sesli cevap. |
| **M4** | Git, dosya, YouTube + demo | 60 saniyelik demo tek çekimde çalışır. |

Haftada yaklaşık 8 saatle kaba tahmin **8–10 hafta**. Bu bir tahmin, söz değil. M1 ses yerine metinle başlar: öğrenme hedefi agent döngüsü, ve ses katmanı sonradan önüne takılır.

**Kontrol noktası:** M1 dört haftada bitmezse kapsam küçültülür.

### Sonraki sürümler

| Sürüm | İçerik |
|---|---|
| **V0.2** | Wake word ("Alfred") ve sesli acil çıkış kod kelimesi · Kalıcı hafıza (PostgreSQL + vektör arama) · MCP desteği |
| **V1** | Proaktif bildirimler · Zamanlanmış görevler |
| **Daha sonra** | İngilizce · Production sistemleri için güçlü onay · Bilgisayar kontrolü (ekran/fare) · Çok agent'lı yapı · Çoklu işletim sistemi |

## Başarı Ölçütleri

| Hedef | alfred başarılıysa |
|---|---|
| **MVP** | 60 saniyelik demo tek çekimde, kurgusuz çalışır. |
| **Öğrenmek** | Emre agent döngüsünü bir başkasına beyaz tahtada anlatabilir; bunun üzerine bir blog yazısı yazmıştır. |
| **Göstermek** | README'nin en üstünde demo videosu ve mimari diyagramı vardır. |
| **Kullanmak** | MVP'den sonra iki hafta boyunca alfred gerçekten her gün kullanılmıştır. |

## Sınırlar ve Güven Modeli

alfred, Emre'nin kişisel Linux bilgisayarında geniş yetkiyle çalışır. Varsayılan tutum güvendir: alfred iş yapsın diye var. Sınır tek bir kurala dayanır:

> **Geri alınamayan her şey onay ister.**

**Otomatik (sormadan yapar, sonra söyler)**
- Dosya ve klasörleri okumak, oluşturmak, düzenlemek — erişim kısıtı yok
- Git ve Docker komutları (geri alınamayanlar hariç)
- Uygulama başlatmak, tarayıcıda içerik açmak

**Onaylı (sorar, onaylanırsa yapar)**
- Geri alınamayan her işlem: silme, `git reset --hard`, `git push --force`, volume silen `docker compose down -v`, `docker system prune` ve benzerleri
- `sudo` gerektiren komutlar
- Bilgisayarı kapatmak veya yeniden başlatmak
- Emre adına mesaj veya e-posta göndermek

**Bir işlemin geri alınıp alınamayacağına model değil alfred'in kodu karar verir.** Claude bir işlemi önerir. Onu sınıflandıran şey alfred'in deterministik kurallarıdır. Böylece model yanlış değerlendirse bile sınır delinmez.

**Sesli onay kodu.** Onay gereken bir işlemde alfred her seferinde farklı, rastgele bir onay kelimesi söyler: "Redis volume'unu silmek için 'yeşil' deyin." İşlem yalnızca o kelime duyulursa yürür. Sıradan bir "evet" — yanlış duyulmuş bir cümle, arka planda çalan bir video, alfred'in kendi sesi — onay sayılmaz.

**Acil çıkış.** Süren her işlem anında durdurulabilir:
- Değiştirilebilir bir klavye kısayolu (varsayılan `Super+Esc`)
- Emre'nin seçtiği benzersiz bir sesli kod kelime (ör. "kırmızı elma"). Lokal olarak tanınır, Claude'a gitmez. Ağ yokken de çalışır. Sürekli dinleme gerektirdiği için wake word ile birlikte V0.2'de gelir; MVP'de acil çıkış klavyeden yapılır.
- Acil çıkış işlemi yalnızca **durdurur**. Bir şeyin geri alınması gerekiyorsa Emre bunu ayrı bir komutla ister.

**Bilinçli kabul edilen risk.** alfred'in okuduğu her dosyanın içeriği Claude API'ye gider. Bilgisayarda hassas veya production bilgisi tutulmadığı için yasak klasör listesi konmadı.

## Vizyon

alfred'in büyümesi bir yetenek listesi değil, bir ilişkinin derinleşmesi. MVP'deki alfred söyleneni yapar ve fark ettiğini söyler. V0.2'de **hatırlamaya** başlar: Gateway'in `./gradlew bootRun` ile çalıştığını, Redis'in hangi projeye bağlı olduğunu, Emre'nin hangi işleri nasıl yapmayı sevdiğini. Her başarılı iş bir sonrakini kolaylaştırır. MCP ile de kendi yazdığı tool'ların ötesine, hazır bir ekosisteme açılır.

V1'de alfred **beklemeyi bırakır**. Sabah geliştirme ortamını kontrol eder, bir container düştüğünde ya da bir PR açıldığında Emre sormadan haber verir. Ama değişmeyen bir ilke var: alfred fark eder ve danışır, sınırları aşmaz. Proaktiflik daha fazla yetki değil, daha fazla dikkat demektir.

Uzun vadede alfred, Emre'nin bütün dijital çalışma ortamının sakin ve güvenilir bir katmanına dönüşür: bilgisayarı ekrandan da kullanabilen, gerektiğinde işi uzman alt agent'lara dağıtan, Türkçe ve İngilizce konuşan bir uşak. Ölçüt hep aynı kalır: Emre'nin "Alfred, şuna bir bakar mısın?" demesi yetmeli.
