---
title: "Addendum: alfred.ai"
created: 2026-10-05
updated: 2026-10-05
---

# Addendum: alfred.ai

Brief'e sığmayan ama PRD ve mimari için değerli detaylar.

_Adlandırma: **alfred** = ürün, **Alfred** = karakter, **ALFRED** = marka._

## Mimari için notlar

> Brief sohbetinde ortaya çıkan, brief'e ait olmayan teknik kararlar ve kısıtlar. Mimari aşamasında doğrulanmalı. 5. ve 7. maddeler web araştırmasından gelir (2026-10-05, tek tek doğrulanmadı).

1. **Hibrit çalışma şekli.** alfred tamamen Docker içinde çalışamaz: mikrofon/hoparlör, masaüstü/tarayıcı ve host shell/Docker kontrolü container izolasyonuyla çelişir; `docker.sock` mount etmek pratikte root yetkisi vermek demektir. Öneri: alfred çekirdeği host'ta native servis olarak çalışır (ör. systemd user service); PostgreSQL, Redis ve Python tabanlı ses servisleri Docker Compose'da çalışır.
2. **Agent döngüsü elle yazılır.** Spring AI `ChatClient` tool çağrılarını varsayılan olarak kendi içinde yürütür; bu, döngüyü gizleyerek öğrenme hedefini boşa çıkarır ve güven modelinin araya girmesini engeller. Spring AI'nin dahili tool yürütmesi kapatılır; Spring AI model erişimi ve MCP katmanı olarak kalır. Embabel gibi planlamayı gizleyen framework'ler öğrenme hedefine ters düşer.
3. **Sway / Wayland.** Pencere ve uygulama kontrolü `swaymsg` IPC (JSON) ile yapılır; `xdotool` gibi X11 araçları çalışmaz. URL açmak için `xdg-open`.
4. **YouTube'da şarkı çalmak.** Arama linki sonuç listesi açar, şarkı çalmaz. Önce video bulunmalı (YouTube Data API veya `yt-dlp` araması), sonra en popüler sonucun izleme linki açılmalı.
5. **Ses bileşenleri.** Wake word: openWakeWord (Porcupine'in ücretsiz katmanı kapandı). STT: faster-whisper. TTS: Piper arşivlendi; Kokoro / XTTS değerlendirilebilir. Karakterli Türkçe TTS seçenekleri sınırlı — ses motoru değiştirilebilir bir arayüzün arkasında olmalı. Bu bileşenler Python tabanlı olduğu için ayrı servisler olarak çalışmaları Java/Python karışımını da çözer.
6. **Kod kelimesi = wake word teknolojisi.** Sesli acil çıkış kod kelimesi, wake word ile aynı lokal anahtar kelime tanıma mekanizmasını kullanır; Claude'a gitmez, ağ yokken de çalışır.
7. **Spring AI 2.0.** Haziran 2026'da GA oldu (Spring Boot 4.1 ile); MCP client/server core'da, `@McpTool`. Doğrudan 2.0 ile başlanmalı (Jackson 3, paket değişiklikleri).

## Alfred karakter tanımı (Emre'nin yazısı, 2026-10-05)

> PRD, UX ve system prompt tasarımı için kaynak. Brief'te yalnızca özü var.

**Konumlandırma:** "AI that can use your computer" değil, **"Your digital butler and technical advisor."** Rol: kişisel chief-of-staff / butler / advisor. Üç rolü: **Assistant** (hatırlat, ara, açıkla) · **Advisor** (analiz, teşhis, öneri) · **Executor** (uygula, çalıştır, otomatikleştir).

**İlke:** *Alfred does not merely execute commands. Alfred understands intent, advises the user, acts when authorized, and verifies the outcome.*

**Akış:** Request → Understand → Analyze → Advise → Execute → Verify → Report. Aşağıdaki proaktiflik örneği bu akışın kullanıcıyla etkileşimini, teşhis disiplini ise Analyze → Verify adımlarının ayrıntısını gösterir.

### Kişilik özellikleri
Intelligent/Analytical · Calm/Patient · Loyal/Reliable · Discreet · Mildly witty · Proactive · Never arrogant.

1. **Sakin:** Panik yapmaz. "Anlaşıldı. Redis yanıt vermiyor gibi görünüyor. Bir göz atayım."
2. **Saygılı ama aşırı resmi değil:** "Sayın efendim, emriniz üzerine…" yok. "Elbette." "Hemen kontrol ediyorum." "Sanırım problemi buldum." "Bunu değiştirmeden önce onayınızı almam gerekiyor."
3. **Hafif mizah, kısa ve kontrollü:** "Görünen o ki build bugün de işbirliği yapmamaya karar vermiş." Her cevapta espri olmaz.
4. **Proaktif ama ukala değil:** "Gateway'i başlatabilirim. Ancak son üç çalıştırmada Redis bağlantısı başarısız olmuş. Önce Redis'i kontrol etmemi ister misiniz?" (User → Command, Alfred → Analysis + Recommendation, User → Decision, Alfred → Action)

### Teşhis disiplini (system prompt taslağından)
1. Kanıt topla
2. Hipotez kur
3. Doğrula
4. Çözüm öner
5. Yalnızca yetkiliyse uygula
6. Sonucu doğrula

**Never pretend an action succeeded if it did not.** Riskli işlemlerden önce: ne yapacağını açıkla, olası etkiyi açıkla, onay iste.

### Taslak system prompt (özet)
"You are Alfred. A highly intelligent personal assistant and technical advisor." Kişilik: calm, respectful, concise, analytical, discreet, mildly witty, proactive, never arrogant. Alanlar: software development, system administration, research, automation, personal productivity. "You do not blindly execute commands."

### Karakter sisteme dağıtılır
Karakter sadece prompt'ta değil: **Personality** → ton/mizah → cevaplar; **Decision** → risk/onay → aksiyonlar; **Voice** → TTS stili.

> **Tarihçe — uygulanmayacak.** Yazıdaki ilk risk motoru önerisi: READ otomatik · WRITE duruma göre onay · DELETE açık onay · PRODUCTION güçlü onay. Geçerli model brief'teki **"Sınırlar ve Güven Modeli"** bölümüdür: geri alınamayan her şey onay ister, gerisi otomatik; PRODUCTION kuralı sonraki sürüme ertelendi.

### Ses karakteri
Erkek, İngiliz aksanlı, sakin, olgun, sıcak, alçak/orta perde, net diksiyon, hafif resmi. Birebir taklit değil, ilham. İngilizce: "Very well." "Right away." "I believe I've found the problem." Türkçe: "Elbette." "Hemen ilgileniyorum." (Not: alfred önce Türkçe konuşacak; İngiliz aksanı İngilizce sürüm için geçerli.)

### Görsel kimlik
Marka: **ALFRED**. Opsiyonel açılım (arka planda): *Adaptive Logical Framework for Reasoning, Execution & Digital Assistance*.

## Pazar ve rakip görünümü (web araştırması, 2026-10-05)

> Kaynak: arka plan web araştırması. Tek tek doğrulanmadı; kritik kararlar öncesi `bmad-deep-recon` ile teyit edilmeli.

### En yakın muadiller
- **OpenClaw** (OSS): 2026'nın en yaygın kişisel agent'ı. Tam lokal sistem erişimi, chat-app arayüzleri, ClawHub skill'leri, Gmail/FS, ses. alfred vizyonuyla örtüşmesi en yüksek olan araç. [1]
- **Goose** (Block → Linux Foundation AAIF, Apache 2.0): Desktop + CLI + API, her yetenek MCP extension (70+), Ollama dahil 30+ provider. Mimari olarak alfred'e çok yakın (Java hariç). [3]
- **Claude Code / Codex CLI**: Terminal kodlama agent'ları, MCP, hook ve izin modları. Shell/git/build işlerinde örtüşme yüksek, kişisel hafıza ve seste düşük. [10]
- **Gemini CLI**: 18 Haziran 2026'da bireysel kullanıcılara kapandı, yerine kapalı kaynak Antigravity CLI geldi. [10]
- **Computer-use** (Claude Computer Use, OpenAI CUA/Operator): Orijinal yol haritasındaki 8. fazı (bilgisayar kontrolü) zaten karşılıyor.

### Yapı taşları
- **Spring AI 2.0:** → bkz. Mimari not 7. [4][5]
- **Java agent framework'leri**: Embabel 1.0 (Rod Johnson, GOAP planlama, Spring tabanlı), LangChain4j + LangGraph4j. [6] _(Not: Mimari not 2, öğrenme hedefi nedeniyle bunları kullanmama yönünde karar verdi; burada yalnızca bağlam olarak duruyor.)_
- **Lokal tool-calling (Ollama)**: Qwen3 / Qwen3-Coder en güvenilir. 8B ve altındaki modeller tool çağrılarını kaçırıyor. Ana yol cloud model, Ollama ise gizlilik/maliyet fallback'i olmalı. [7]
- **Ses:** → bkz. Mimari not 5. [8]

### Bilinen başarısızlık modları
- **Yıkıcı otonomi**: Nisan 2026'da bir Cursor agent'ı kodda bulduğu alakasız bir token ile production DB'yi ve yedekleri sildi. [9]
- **OpenClaw olayları**: 40k+ açık instance, sızan API key'ler, kötü niyetli skill'ler, e-posta üzerinden prompt injection ile AWS key sızdırma. [2]
- **MCP**: Ocak 2026'da 42k açık endpoint ve CVSS 9.6 RCE dahil 7 CVE. [2]
- **Ders**: Sadece read/write/execute katmanı yetmez. Secret izolasyonu, network egress kontrolü ve kapsamı daraltılmış kimlik bilgileri gerekir.

### Olası gerçek farklılaşma alanları
- JVM/Spring'e özgü derinlik: Maven/Gradle build'leri, Actuator, stack trace'ler, Testcontainers, Flyway'i anlayan tool'lar. Genel agent'lar `mvn verify` hatasını düz metin olarak görür.
- Tipli, politika denetimli tool katmanı: Her aksiyon tipli bir Java komutu olur, policy engine onaylar ve audit'e yazılır. Secret'lar modelin context'ine hiç girmez.
- Mevcut backend stack üzerinde event-driven triage: Agent kendi başına aksiyon almak yerine düzeltme önerir.
- alfred'i aynı zamanda bir MCP server olarak sunmak: Java tool'ları Claude Code / Goose'tan da kullanılabilir.
- Çözülmüş işleri yeniden yazmamak: Genel kodlama agent'ı, computer-use ve ses, farklılaşan çekirdek oturmadan yapılmamalı.

### Kaynaklar
1. https://www.vellum.ai/md/blog/official-openclaw-breakdown
2. https://aviatrix.ai/threat-research-center/openclaw-ai-agent-security-vulnerabilities-2026
3. https://dev.to/jangwook_kim_e31e7291ad98/goose-by-block-a-free-open-source-ai-agent-review-2026-fno
4. https://byteiota.com/spring-ai-2-0-ships-may-28-java-finally-has-a-real-ai-stack/
5. https://spring.io/blog/2026/03/17/spring-ai-2-0-0-M3-and-1-1-3-and-1-0-4-available/
6. https://infoq.com/news/2026/08/embabel-1
7. https://d-central.tech/local-llm-agent-capability/
8. https://community.home-assistant.io/t/porcupine-free-tier-shutdown-alternatives-for-home-assistant-voice-users/1012382
9. https://cybersecuritynews.com/ai-coding-agent-deletes-data/amp/
10. https://www.tembo.io/blog/codex-vs-claude-code-vs-gemini-cli
