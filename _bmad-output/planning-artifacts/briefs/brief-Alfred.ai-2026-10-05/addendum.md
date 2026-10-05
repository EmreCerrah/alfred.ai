---
title: "Addendum: alfred.ai"
created: 2026-10-05
updated: 2026-10-05
---

# Addendum: alfred.ai

Brief'e sığmayan ama PRD / mimari için değerli detaylar.

## Alfred karakter tanımı (Emre'nin yazısı, 2026-10-05)

> PRD, UX ve system prompt tasarımı için kaynak. Brief'te yalnızca özü var.

**Konumlandırma:** "AI that can use your computer" değil, **"Your digital butler and technical advisor."** Rol: kişisel chief-of-staff / butler / advisor. Üç yüz: **Assistant** (hatırlat, ara, açıkla) · **Advisor** (analiz, teşhis, öneri) · **Executor** (uygula, çalıştır, otomatikleştir).

**İlke:** *Alfred does not merely execute commands. Alfred understands intent, advises the user, acts when authorized, and verifies the outcome.*

**Akış:** Request → Understand → Analyze → Advise → Execute → Verify → Report

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
Karakter sadece prompt'ta değil: **Personality** → ton/mizah → cevaplar; **Decision** → risk/onay → aksiyonlar; **Voice** → TTS stili. Önerilen risk motoru: READ otomatik · WRITE belki onay · DELETE açık onay · PRODUCTION güçlü onay. (Not: brief'teki güven modeli bununla uzlaştırıldı, bkz. brief.)

### Ses karakteri
Erkek, İngiliz aksanlı, sakin, olgun, sıcak, alçak/orta perde, net diksiyon, hafif resmi. Birebir taklit değil, ilham. İngilizce: "Very well." "Right away." "I believe I've found the problem." Türkçe: "Elbette." "Hemen ilgileniyorum."

### Görsel kimlik
Marka: **ALFRED**. Opsiyonel açılım (arka planda): *Adaptive Logical Framework for Reasoning, Execution & Digital Assistance*.

## Pazar ve rakip görünümü (web araştırması, 2026-10-05)

> Kaynak: arka plan web araştırması. Tek tek doğrulanmadı; kritik kararlar öncesi `bmad-deep-recon` ile teyit edilmeli.

### En yakın muadiller
- **OpenClaw** (OSS): 2026'nın en yaygın kişisel agent'ı. Tam lokal sistem erişimi, chat-app arayüzleri, ClawHub skill'leri, Gmail/FS, ses. alfred vizyonuyla örtüşmesi en yüksek olan araç.
- **Goose** (Block → Linux Foundation AAIF, Apache 2.0): Desktop + CLI + API, her yetenek MCP extension (70+), Ollama dahil 30+ provider. Mimari olarak alfred'e çok yakın (Java hariç).
- **Claude Code / Codex CLI**: Terminal kodlama agent'ları, MCP, hook ve izin modları. Shell/git/build işlerinde örtüşme yüksek, kişisel hafıza ve seste düşük.
- **Gemini CLI**: 2026-06-18'de bireysel kullanıcılara kapandı, yerine kapalı kaynak Antigravity CLI geldi.
- **Computer-use** (Claude Computer Use, OpenAI CUA/Operator): Faz 8'i zaten karşılıyor.

### Yapı taşları
- **Spring AI 2.0** 2026 Haziran'da GA oldu (Spring Boot 4.1 ile). MCP client/server core'da, `@McpTool`. 2.0'dan başlanmalı (Jackson 3, paket değişiklikleri).
- **Java agent framework'leri**: Embabel 1.0 (Rod Johnson, GOAP planlama, Spring tabanlı), LangChain4j + LangGraph4j. Kendi planner'ını yazmadan önce bunlar değerlendirilmeli.
- **Lokal tool-calling (Ollama)**: Qwen3 / Qwen3-Coder en güvenilir. 8B ve altı modeller çağrıları kaçırıyor. Ana yol cloud model, Ollama ise gizlilik/maliyet fallback'i olmalı.
- **Ses**: Porcupine ücretsiz katmanı kapandı → openWakeWord. STT için faster-whisper. Piper arşivlendi → Kokoro / XTTS.

### Bilinen başarısızlık modları
- **Yıkıcı otonomi**: 2026 Nisan'da bir Cursor agent'ı kodda bulduğu alakasız bir token ile production DB'yi ve yedekleri sildi.
- **OpenClaw olayları**: 40k+ açık instance, sızan API key'ler, kötü niyetli skill'ler, e-posta üzerinden prompt injection ile AWS key sızdırma.
- **MCP**: 2026 Ocak'ta 42k açık endpoint ve CVSS 9.6 RCE dahil 7 CVE.
- **Ders**: Sadece read/write/execute katmanı yetmez. Secret izolasyonu, network egress kontrolü ve kapsamı daraltılmış kimlik bilgileri gerekir.

### Olası gerçek farklılaşma alanları
- JVM/Spring'e özgü derinlik: Maven/Gradle build'leri, Actuator, stack trace'ler, Testcontainers, Flyway'i anlayan tool'lar. Genel agent'lar `mvn verify` hatasını düz metin olarak görür.
- Tipli, politika denetimli tool katmanı: Her aksiyon tipli bir Java komutu olur, policy engine onaylar ve audit'e yazılır. Secret'lar modelin context'ine hiç girmez.
- Mevcut backend stack üzerinde event-driven triage: Agent kendi başına aksiyon almak yerine düzeltme önerir.
- alfred'i aynı zamanda bir MCP server olarak sunmak: Java tool'ları Claude Code / Goose'tan da kullanılabilir.
- Çözülmüş işleri yeniden yazmamak: Genel kodlama agent'ı, computer-use ve ses, farklılaşan çekirdek oturmadan yapılmamalı.

### Kaynaklar
vellum.ai/md/blog/official-openclaw-breakdown · aviatrix.ai/threat-research-center/openclaw-ai-agent-security-vulnerabilities-2026 · dev.to (Goose review 2026) · byteiota.com (Spring AI 2.0) · spring.io/blog/2026/03/17 · infoq.com/news/2026/08/embabel-1 · d-central.tech/local-llm-agent-capability · community.home-assistant.io (Porcupine shutdown) · cybersecuritynews.com/ai-coding-agent-deletes-data · tembo.io/blog/codex-vs-claude-code-vs-gemini-cli
