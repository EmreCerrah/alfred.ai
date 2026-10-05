# Kaynak: JARVIS (→ alfred.ai) Brainstorm Roadmap

> Kullanıcının 2026-10-05'te paylaştığı orijinal brain dump. Diyagramlar özetlenmiştir; faz içerikleri korunmuştur.

**Amaç:** Bilgisayarı, geliştirme ortamını ve günlük araçları doğal dil ile kullanabilen, zamanla hafıza ve otonomi kazanabilen kişisel AI Agent.

**Vizyon:** Understand (LLM) · Act (Tools) · Remember (Memory) → User Experience (Chat / Voice / Desktop)

**Temel döngü:** User → Intent → Reasoning → Planning → Tool Selection → Action → Observation → Reasoning → Response

## Fazlar

1. **Foundation — "konuşabiliyor":** LLM, conversation, prompt management, tool calling, basic agent loop, context management. Arayüz: CLI, REST API, basit Web UI. İlk araçlar: Shell, File System, Git, Docker. Hedef senaryo: "Gateway projesinin durumunu kontrol et" → Git/Docker/FS → "Gateway çalışıyor, son commit…, container…, working tree clean."
2. **Developer Assistant — "dev ortamımı biliyor":** Git, GitHub, Docker, Maven, Gradle, Java, Spring Boot, PostgreSQL, Redis, Linux. Project detection, project context, log/build/error/dependency analysis. Örnek: "Gateway'i çalıştır" → projeyi bul → Java version → Docker dependency → environment → başlat → log izle → health check → bildir.
3. **Tool Ecosystem — "elleri":** Tool Registry; Developer (Git, GitHub, Docker, Maven, Gradle, Database), Computer (Files, Terminal, Keyboard, Mouse, Screen), Internet (Browser, Search, APIs). Kategoriler: Developer, Computer, Browser, System, Communication, Database, Cloud.
4. **MCP Layer:** MCP Client → harici MCP server'lar (GitHub, Docker, Filesystem); kendi tool'larının yanında.
5. **Memory — "beni tanıyor":** Short-term (conversation, task, context, tools). Long-term (preferences, projects, technologies, common commands, past decisions, useful info). Project memory (ör. Gateway: Java 21/Spring Boot/Docker/Redis/PostgreSQL; CaskKeeper: Next.js/MongoDB/TypeScript). PostgreSQL + Vector DB.
6. **Planning:** "Redis'i kontrol et" yerine "Gateway neden çalışmıyor?" → analyze → plan → Docker/Redis/Logs paralel → analiz → kök neden → cevap.
7. **Observation:** Action → Observation → Analysis → Next Action. Örn: başlat → log → fail → Redis eksik → Redis durmuş → Redis başlat → tekrar dene.
8. **Computer Control:** Screen (screenshot, vision), Input (keyboard, mouse, terminal), Apps (IntelliJ, Browser, Docker). Örn: "IntelliJ'de Gateway'i aç."
9. **Voice:** Wake word, STT, TTS, conversation, interruption. "Hey Jarvis, Docker'da ne çalışıyor?"
10. **Proactive:** Event → analyze → decide → notify/act. Kaynaklar: GitHub, Docker, Server, Database, Calendar, Email, CI/CD, Monitoring, System. Örn: PR açıldı → analiz → test → rapor.
11. **Automation:** Scheduler (daily, hourly, event-based, conditional). Örn: her sabah dev ortamı kontrolü → günlük rapor.
12. **Multi-Agent:** Coding, DevOps, Research; sonra Security, Database, Testing, Documentation, Travel, Personal Assistant.
13. **Advanced Memory:** Experience → Outcome → Store → Retrieve → better decision. Örn: `./gradlew bootRun` başarılı → project memory → bir dahakine bilinen workflow.
14. **Safety & Permissions:** READ otomatik (git status, docker ps, logs); WRITE onaylı (edit/delete file); EXECUTE onaylı (docker restart, git push), `rm` bloklu.
15. **Personal Ecosystem:** Computer (Linux, IntelliJ, Docker, Files), Internet (GitHub, Gmail, Calendar, APIs), Cloud (AWS, Docker, Servers).
16. **Possible Final Architecture:** User → Voice/Chat → AI Core → Planning/Memory/Agent → Tool Registry → Computer/GitHub/Docker/Browser/Database; Events (Scheduler, Webhook, Monitor).

**Evrim:** Chat Agent → Tool Calling → Developer Agent → MCP → Memory → Planning → Observation → Computer Control → Voice → Proactive → Automation → Multi-Agent → Personal AI OS.

## Olası teknoloji haritası
- Core: Java 21, Spring Boot, Spring AI, MCP
- AI: OpenAI, Ollama, Local LLM, Embeddings, Whisper, TTS
- Data: PostgreSQL, Redis, Vector DB
- Infra: Docker, Docker Compose, Linux
- External: GitHub, Browser, REST APIs, MCP Servers
- UI: Next.js, React, WebSocket
- Future: Computer Vision, Voice, Wake Word, Multi-Agent, Event System, Scheduler

## Kullanıcının açık soruları
Hangi işleri yapabilir / sadece önerebilir / onay istemeli / tamamen otomatik yapabilir? Neleri hatırlamalı, neleri kesinlikle hatırlamamalı? Bilgisayarı ne kadar kontrol etmeli? Hangi araçlara erişmeli? Görevi nasıl planlamalı? Başarısızlığı nasıl anlamalı, tekrar deneyebilir mi? Ne zaman başka agent'a devretmeli, ne zaman kullanıcıya sormalı, ne zaman kendi karar vermeli? Local mı cloud mu? Privacy? Tool permission sistemi? Audit?

## Uzun vadeli vizyon
Cevap veren sistem → işlem yapan agent → bilgisayarı kullanan agent → projeleri/ortamı anlayan agent → deneyimlerini hatırlayan agent → söylemeden problemleri fark eden agent.
