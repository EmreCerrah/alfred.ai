# Deferred Work

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-proje-iskeleti-ve-modul-sinirlari.md`
  summary: HTTP sunucusunun yalnızca 127.0.0.1'e bağlandığını gerçek port üzerinde doğrulayan bir test eklenmeli.
  evidence: Story 1.1 `server.address: 127.0.0.1` ayarladı ama hiçbir test bağlanan adresi okumuyor; ayar silinse testler geçer. Story 1.3'ün "yalnızca 127.0.0.1'e bind eder" AC'si bu testi kapsamalı (`@SpringBootTest(webEnvironment = RANDOM_PORT)` ile bağlı adres kontrolü).
