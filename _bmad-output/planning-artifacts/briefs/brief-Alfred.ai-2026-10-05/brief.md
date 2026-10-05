---
title: "Product Brief: alfred.ai"
status: final
created: 2026-10-05
updated: 2026-10-05
---

# Product Brief: alfred.ai

## Özet

alfred, Emre'nin kişisel Linux bilgisayarında yaşayan, Türkçe konuşan bir **dijital uşak ve teknik danışman**. Sesle ya da yazıyla verilen isteği anlar. Docker'ı, Git'i, dosyaları ve tarayıcıyı kullanarak işi yapar, sonucu doğrular ve kısaca söyler. Bir sorun fark ettiğinde danışır. Geri alınamaz bir şey yapmadan önce mutlaka onay ister. Beyni Claude'dur; karar döngüsü, tool'lar ve sınırlar alfred'in kendi kodudur.

Öğrenme hedefiyle başlayan bir portfolyo projesi. Farkı karakteri, Türkçe olması ve kodla korunan sınırları.

## Neden alfred?

alfred bir pazar boşluğundan değil, bir öğrenme isteğinden doğdu. Emre daha önce yapay zekâ ile hiç çalışmadı. Hem kendini geliştirecek hem de portfolyosunda gösterebileceği bir proje arıyordu. Iron Man'deki JARVIS akla geldi. Benzerlerinin var olduğunu görünce soru şu oldu: "Bunu neden ben de yapmıyorum?"

Ama hedef JARVIS değil. Batman'in Alfred'i gibi daha insansı, daha yardımcı bir **dijital uşak**: bilgisayardaki her şeye erişebilen ve istenenleri yerine getiren biri.

alfred üç şeye hizmet eder, önem sırasıyla:
1. **Öğrenmek:** Bir agent'ın nasıl karar verdiğini ve o kararı nasıl uyguladığını içeriden anlamak. Bu yüzden agent döngüsü, tool calling ve planlama hazır bir framework'ün kara kutusuna bırakılmadan, Spring üzerinde sıfırdan yazılır. Ses tanıma, konuşma sentezi ve wake word ise hazır bileşenlerle çözülür; öğrenme hedefi orada değil.
2. **Göstermek:** Portfolyoda iki mesaj: önce *Spring ekosistemine hâkim*, sonra *bir AI agent'ını uçtan uca kurabiliyor*.
3. **Kullanmak:** Evde, kendi Linux bilgisayarında gerçekten işe yarayan bir asistan.

## alfred kimdir?

alfred "bilgisayarını kullanabilen bir yapay zekâ" değil, **dijital bir uşak ve teknik danışmandır**. Bu fark tasarımın tamamını etkiler.

> *alfred yalnızca komut çalıştırmaz. Niyeti anlar, danışır, yetkisi varsa uygular ve sonucu doğrular.*

**Karakter:** Sakin, saygılı ama aşırı resmi değil, kısa konuşan, analitik, ketum, ölçülü esprili, proaktif ama asla ukala değil. Panik yapmaz ("Anlaşıldı. Redis yanıt vermiyor gibi görünüyor. Bir göz atayım."). Mizahı kısa ve seyrektir ("Görünen o ki build bugün de işbirliği yapmamaya karar vermiş.").

**Danışman tavrı:** alfred işini yapar, sonra söyler; her komutta araya girmez. Danışmanlığı yalnızca bir şey fark ettiğinde devreye girer: "Gateway'i başlatabilirim, ama son üç çalıştırmada Redis bağlantısı başarısız olmuş. Önce Redis'i kontrol edeyim mi?"

**Dürüstlük:** Bir işlem başarısız olduysa asla başarılı olmuş gibi davranmaz. Her eylemden sonra sonucu doğrular.

**Dil ve ses:** alfred Türkçe konuşur ve Türkçe komut alır; İngilizce ileride eklenecek, bu yüzden dil koda gömülmez. Sesi sakin, olgun, sıcak, net ve hafif resmi bir erkek sesidir. Ses motoru değiştirilebilir olmalı; karakterli bir Türkçe ses bulunamazsa sonradan değiştirilebilsin.

Tam karakter tanımı, örnek cümleler ve taslak system prompt: `addendum.md`.

## alfred'i farklı kılan ne?

OpenClaw ve Goose gibi kişisel agent'lar alfred'in yapacaklarının büyük kısmını zaten yapıyor. alfred onlarla özellik yarışına girmiyor. Farkı üç yerde:

1. **Karakter ve danışman tavrı — projenin kimliği.** Benzer ürünler komut çalıştırır; alfred niyeti anlar, bir şey fark ettiğinde danışır ve yaptığını doğrular. Karakter yalnızca bir system prompt değil: konuşma tonunu, neye izin verildiğini ve sesi birlikte belirler.
2. **Modele güvenmeyen bir güven modeli — teknik olarak en değerli parça.** Sınırları Claude'un muhakemesi değil, alfred'in deterministik Java kodu korur (ayrıntı: "Sınırlar ve güven modeli"). Bu alandaki güvenlik olaylarının çoğu tam bu boşluktan çıktı: sınırı modelin kendi muhakemesine bırakmak.
3. **Türkçe.** Türkçe konuşan, karakteri olan bir agent. İngilizce sonra gelecek.

## Kapsam

**Hedef ortam:** Tek kullanıcı (Emre), tek makine: Fedora + Sway (Wayland). alfred'in çekirdeği host'ta yerel bir servis olarak çalışır; yardımcı servisler Docker Compose'da çalışabilir. Windows yalnızca geliştirme makinesidir; çoklu işletim sistemi desteği kapsam dışıdır.

İlk eylemler bilinçli olarak basit tutuldu — geliştirme ortamı ve tarayıcı kontrolü — ki MVP hızla ortaya çıksın.

### MVP — ilk çalışan alfred

- **Ses:** Push-to-talk ile konuşma → ses tanıma (STT) → alfred → Türkçe sesli cevap (TTS)
- **Metin:** Aynı agent'a metinle erişim (CLI veya basit web arayüzü) — hata ayıklama ve yedek yol
- **Çok adımlı akıl yürütme — MVP'nin kalbi:** "Redis loglarını incele, hatayı bul" gibi bir isteği kanıt toplayarak, hipotez kurarak ve sonucu doğrulayarak birden çok adımda çözer
- **Tool'lar:** Shell, dosya (okuma/yazma), Git, Docker, tarayıcıda içerik açma (YouTube)
- **Güven modeli:** Geri alınamaz işlemler için sesli onay kelimesi, klavyeyle acil çıkış
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
| **M2** | Güven modeli | Geri alınamaz işlemler onay kelimesi ister, `Super+Esc` süren işi durdurur. |
| **M3** | Ses | Push-to-talk ile Türkçe konuşma ve Türkçe sesli cevap. |
| **M4** | Git, dosya, YouTube + demo | 60 saniyelik demo tek çekimde çalışır. |

Haftada yaklaşık 8 saatle kaba tahmin **8–10 hafta**. Bu bir tahmin, söz değil. M1 ses yerine metinle başlar, çünkü öğrenme hedefi agent döngüsüdür; ses katmanı sonradan eklenir.

**Kontrol noktası:** M1 dört haftada bitmezse kapsam küçültülür.

### Sonraki sürümler

| Sürüm | İçerik |
|---|---|
| **V0.2** | Wake word ("Alfred") ve sesli acil çıkış kelimesi · Kalıcı hafıza (PostgreSQL + vektör arama) · MCP desteği |
| **V1** | Proaktif bildirimler · Zamanlanmış görevler |
| **Daha sonra** | İngilizce · Production sistemleri için güçlü onay · Bilgisayar kontrolü (ekran/fare) · Çok agent'lı yapı · Çoklu işletim sistemi |

## Sınırlar ve güven modeli

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

**Bir işlemin geri alınıp alınamayacağına model değil alfred'in kodu karar verir.** Claude bir işlemi önerir; onu sınıflandıran şey alfred'in deterministik kurallarıdır. Böylece model yanlış değerlendirse bile sınır delinmez.

**Sesli onay kelimesi.** Onay gereken bir işlemde alfred her seferinde farklı, rastgele bir onay kelimesi söyler: "Redis volume'unu silmek için 'yeşil' deyin." İşlem yalnızca o kelime duyulursa yürür. Sıradan bir "evet", yanlış duyulmuş bir cümle, arka planda çalan bir video ya da alfred'in kendi sesi onay sayılmaz.

**Acil çıkış.** Süren her işlem anında durdurulabilir:
- Değiştirilebilir bir klavye kısayolu (varsayılan `Super+Esc`)
- Emre'nin seçtiği benzersiz bir sesli acil çıkış kelimesi (ör. "kırmızı elma"). Yerel olarak tanınır, Claude'a gitmez, ağ yokken de çalışır. Sürekli dinleme gerektirdiği için wake word ile birlikte V0.2'de gelir; MVP'de acil çıkış klavyeden yapılır.
- Acil çıkış işlemi yalnızca **durdurur**. Bir şeyin geri alınması gerekiyorsa Emre bunu ayrı bir komutla ister.

**Bilinçli kabul edilen risk.** alfred'in okuduğu her dosyanın içeriği Claude API'ye gider. Bilgisayarda hassas veya production bilgisi tutulmadığı için yasak klasör listesi konmadı.

## Başarı ölçütleri

| Hedef | alfred başarılıysa |
|---|---|
| **MVP** | 60 saniyelik demo tek çekimde, kurgusuz çalışır. |
| **Öğrenmek** | Emre agent döngüsünü bir başkasına beyaz tahtada anlatabilir; bunun üzerine bir blog yazısı yazmıştır. |
| **Göstermek** | README'nin en üstünde demo videosu ve mimari diyagramı vardır. |
| **Kullanmak** | MVP'den sonra iki hafta boyunca alfred gerçekten her gün kullanılmıştır. |

## Vizyon

alfred'in büyümesi bir yetenek listesi değil, bir ilişkinin derinleşmesi. Önce **hatırlamayı** öğrenir: Gateway'in nasıl çalıştığını, Emre'nin işleri nasıl yapmayı sevdiğini; her başarılı iş bir sonrakini kolaylaştırır. Sonra **beklemeyi bırakır**: bir container düştüğünde ya da bir PR açıldığında Emre sormadan haber verir. Ama ilke değişmez: alfred fark eder ve danışır, sınırları aşmaz. Proaktiflik daha fazla yetki değil, daha fazla dikkat demektir.

Uzun vadede alfred, Emre'nin bütün dijital çalışma ortamının sakin ve güvenilir bir katmanına dönüşür. Ölçüt hep aynı kalır: "Alfred, şuna bir bakar mısın?" demek yetmeli.
