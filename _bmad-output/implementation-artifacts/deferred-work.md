# Deferred Work

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-proje-iskeleti-ve-modul-sinirlari.md`
  summary: HTTP sunucusunun yalnızca 127.0.0.1'e bağlandığını gerçek port üzerinde doğrulayan bir test eklenmeli.
  evidence: Story 1.1 `server.address: 127.0.0.1` ayarladı ama hiçbir test bağlanan adresi okumuyor; ayar silinse testler geçer. Story 1.3'ün "yalnızca 127.0.0.1'e bind eder" AC'si bu testi kapsamalı (`@SpringBootTest(webEnvironment = RANDOM_PORT)` ile bağlı adres kontrolü).

- source_spec: `_bmad-output/implementation-artifacts/spec-1-3-guvenli-cli-kanali.md`
  summary: (kapandı) 127.0.0.1 bind testi Story 1.3 ile eklendi.
  evidence: `ChannelTests.bindsTheConnectorToLoopbackOnly` Tomcat connector'ının bağlandığı adresin loopback olduğunu doğruluyor. Dış IP'ye soketle bağlanma denemesi Windows güvenlik duvarında zaman aşımına düştüğü için kanıt sayılmadı.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-maliyet-butce-tur-siniri.md`
  summary: "Bu ay ne kadar harcadık?" sorgusu, Tool altyapısıyla (Story 1.8) bir Kademe 0 okuma Tool'u olarak eklenmeli.
  evidence: Emre'nin kararı (2026-10-28). Modelin harcamayı okuyabilmesi bir Tool gerektiriyor; Tool döngüsü ve SPI 1.8'de geliyor, ve kapsam daraltma sırası bu sorguyu ertelenebilir sayıyor. Ay toplamı zaten `CostLedger.monthToDate()` ile hazır. Yanıt, toplamı ve bunun bütçeye oranını söylemeli.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-maliyet-butce-tur-siniri.md`
  summary: Çok adımlı Task'larda (1.9) `LlmResponse.budgetWarning` ara bir `TOOL_USE` yanıtında gelirse, karar döngüsü onu Task'ın son yanıtına taşımalı.
  evidence: Uyarı ay başına bir kez veriliyor ve bayrak yalnızca eşiği geçiren yanıtın kendisinde. 1.3'teki tek turlu `Agent` bunu doğrudan yanıta ekliyor, ama Tool döngüsünde ara yanıtlar kullanıcıya gösterilmeyecek. 1.9'da karar döngüsü Task boyunca bayrağı hatırlayıp son `Reply`'a eklemeli ve bunun için test yazılmalı.
