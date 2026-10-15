# Review — Verification lens (ARCHITECTURE-SPINE.md, alfred.ai)

- **Reviewed:** 2026-10-12
- **Lens:** Was each committed decision checked against the web, the project, or the live starter, rather than stated from memory? This covers current versions, whether each named technology exists and fits, and the starter's live defaults.
- **Inputs:** `ARCHITECTURE-SPINE.md`, `research-stt-stack.md` (used as evidence, with key claims spot-checked live).

## Verdict

**PASS WITH FIXES.** The headline versions are confirmed live: Java 25 LTS, Boot 4.1.1, Spring AI 2.0.1, Modulith 2.1.1, and `claude-haiku-5-5`. Initializr also offers them. Two committed rules rest on assumptions that turned out wrong:

- **AD-11 / Stack:** the claim about how the Anthropic SDK version is managed is false.
- **AD-12:** "tool-output trimming" can conflict with Haiku 5.5's append-only thinking-block rule.

Two more need a reality check before scaffolding:

- **AD-6 / AD-7:** Modulith's registry depends on transactions.
- **AD-11 / AD-12:** Spring AI 2.0.1 was released before Haiku 5.5.

## Spot-check results

| Claim (spine / research) | Live check | Result |
|---|---|---|
| Spring Boot 4.1.1 is latest GA | GitHub releases: v4.1.1 (20 Aug) marked Latest; 4.2.0-M2 preview. Initializr default bootVersion = 4.1.1.RELEASE | Confirmed |
| Spring AI 2.0.1 is latest GA | GitHub releases: 2.0.1 (21 Aug) marked Latest; 2.1.0-M1 preview | Confirmed |
| Spring Modulith 2.1.1 aligned with Boot 4.1.1 | GitHub releases: 2.1.1 (26 Aug) "Spring Boot 4.1.1" | Confirmed |
| Java 25 is the current LTS | Research cites SD Times / InfoQ. Initializr offers 27, 25, 21, 17 | Confirmed. **Note:** Initializr's default javaVersion is **17**, so pick 25 explicitly at scaffold time. |
| Initializr offers Gradle Kotlin DSL | Initializr metadata: `gradle-project-kotlin` present. Default is Groovy `gradle-project` | Confirmed. Pick it explicitly. |
| Initializr deps: Modulith, Web, JDBC, Flyway, Testcontainers | All present. `modulith` and `spring-ai-anthropic` range [4.0.0, 4.3.0.M1) | Confirmed. The spine's Starter row leaves out `postgresql` (driver) and `spring-ai-anthropic`, which are also needed. |
| Spring AI Anthropic provider is built on the official Anthropic Java SDK | Spring AI 2.0.1 Anthropic docs: "through the official Anthropic Java SDK" | Confirmed |
| Framework tool execution off: `spring.ai.chat.client.tool-calling.enabled=false` / `AdvisorParams.toolCallingAdvisorAutoRegister(false)` / direct `ChatModel` | Spring AI 2.0.1 tools page: all three confirmed. Direct `ChatModel` calls do **not** execute tool calls. 2.0.0-RC1 removed `internalToolExecutionEnabled` | Confirmed. This closes research open item 4. |
| **Anthropic SDK version "from the Spring AI 2.0.1 BOM (2.71.0)"** | `spring-ai-bom-2.0.1.pom` does **not** manage `com.anthropic:*`. `spring-ai-anthropic-2.0.1.pom` hard-codes **`anthropic-java-core` 2.52.0**. Latest SDK is 2.71.0 (09 Oct). The `claude-haiku-5-5` constant arrived in 2.69.0 (07 Oct) | **Refuted** (see F1) |
| Model id `claude-haiku-5-5` | Anthropic models overview + Haiku 5.5 page: `claude-haiku-5-5`, released 2026-10-07, dateless pinned id, no separate alias | Confirmed. See F2 and F3 for consequences. |
| Modulith event publication registry, JDBC, Postgres | Modulith 2.1.1 events docs: `spring-modulith-starter-jdbc`. Entries are written "as part of the original business transaction". By default only `@TransactionalEventListener` listeners are recorded | Exists, with a transaction precondition the spine doesn't address (F4) |
| `ApplicationModules.of(...).verify()` | Modulith docs/blog (2.0+ also adds startup verification) | Confirmed |
| Per-module Flyway (`db/migration/<module>/`) | Modulith 2.0+ supports module-specific Flyway, **opt-in** via `spring.modulith.runtime.flyway-enabled=true` (default false). Newer docs move root migrations to `db/migration/__root` and use per-module history tables `flyway_schema_history_<module>` | Exists. The spine doesn't mention the opt-in flag or the `__root` layout (F5) |

## Findings

### F1 — HIGH — The Anthropic SDK version source is stated wrongly (Stack, AD-11)

The spine says the SDK version "comes from the Spring AI 2.0.1 BOM (current at research time: 2.71.0); not pinned by hand". Live check:

- The Spring AI BOM does not manage `com.anthropic` at all.
- `spring-ai-anthropic:2.0.1` hard-codes `anthropic-java-core:2.52.0`.

So the effective SDK is 2.52.0, about 19 minor versions behind 2.71.0. It is older than 2.69.0, the release that added `claude-haiku-5-5`. AD-11 also lets `llm` use the SDK directly for gaps like caching and token counting. Code written that way will be on 2.52.0 unless someone adds a direct dependency, and a direct 2.7x dependency could silently override what Spring AI was tested with.

**Fix:**

- Correct the Stack row: "transitively `anthropic-java-core` 2.52.0 via `spring-ai-anthropic` 2.0.1".
- Decide explicitly whether to accept 2.52.0 or align upward, and record that decision in AD-11.
- Have Gradle `dependencyInsight` confirm the version at scaffold time.

### F2 — HIGH — AD-12 "tool-output trimming" can collide with Haiku 5.5's append-only rule

The Haiku 5.5 migration guide says a request that sends a thinking block back after any change to `system`, `tools` or earlier `messages` returns **400**. That applies to accounts created after 2026-08-31. Adaptive thinking is on by default, and thinking blocks must go back with tool results.

If AD-12's "tool-output trimming" or prompt-caching layout edits *earlier* turns, the agent loop will fail. The model page also notes:

- Thinking blocks from earlier turns stay in context and count as input.
- The tokenizer uses about 30% more tokens than Haiku 4.5.

Both affect the USD 5/month budget (FR-17/18, SM-6), and neither was rechecked.

**Fix:** Add to AD-12:

- Trim tool output *before* it first enters the conversation, never retroactively.
- Keep conversations append-only within a Task.
- Set `effort` explicitly (default is `medium`).
- Handle `stop_reason: refusal` (FR-9).
- Never send `temperature`, `top_p` or `top_k`.
- Never send assistant prefill.

### F3 — MEDIUM — Spring AI 2.0.1 predates Haiku 5.5; compatibility is unconfirmed (AD-11, AD-12)

Spring AI 2.0.1 was released 2026-08-21. Haiku 5.5 was released 2026-10-07. The 2.0.1 Anthropic docs never mention Haiku 5.5. Their Runtime Options table lists a `temperature` default of `1.0`, and they don't say whether it's sent.

On Haiku 5.5, `temperature` must be absent or exactly 1, and sending `temperature` together with `top_p` returns 400. The docs do cover `thinkingAdaptive()`, `effort`, and automatic thinking-block replay across tool turns, which is promising. Even so, nothing confirms that 2.0.1 with SDK 2.52.0 handles the 5.5 wire format, including thinking display, `refusal`, and block binding.

**Fix:** Add a scaffold-time smoke test that runs one tool-call round trip against `claude-haiku-5-5` through `ChatModel`, as an M1 exit criterion. Fall back to the SDK-only adapter if it fails.

### F4 — MEDIUM — The audit guarantee rests on a transaction precondition (AD-6, AD-7)

Modulith records publications only for `@TransactionalEventListener`-style listeners, inside the publishing transaction. AD-6 defines the control path (`agent`, `trust`, `capability`) as plain synchronous calls. Those modules mostly don't touch Postgres. If they publish `ActionExecuted` and similar events outside a transaction, the following happens:

- By default, `@ApplicationModuleListener` / `@TransactionalEventListener` never fires.
- Nothing is written to the registry.

That silently breaks FR-19 and SM-5. AD-7's "when Postgres is unreachable, `trust` does not produce an `ApprovedAction`" also has no stated mechanism. The registry write would fail only inside a transaction.

**Fix:** State in AD-6/AD-7 that every audit-relevant publish happens inside a `@Transactional` boundary, so the registry write commits with it. Add a test that publishes from each module and asserts the registry row exists.

### F5 — LOW — Per-module Flyway is opt-in and its layout is version-dependent (AD-8, Structural Seed)

`db/migration/<module>/` works only with `spring.modulith.runtime.flyway-enabled=true`. The newer layout puts root migrations in `db/migration/__root`, with per-module history tables. Module folders map to *folders*, not *schemas*. The schema-per-module rule in AD-8 needs its own configuration.

The event publication table also needs an owning schema. It isn't owned by a module and isn't covered by AD-8.

**Fix:** Name the property, the `__root` folder, and the schema of the `event_publication` table.

### F6 — LOW — Starter defaults to check at scaffold time

- Initializr defaults are Java 17 and Groovy DSL. Select Java 25 and `gradle-project-kotlin`.
- Add the `postgresql` driver and `spring-ai-anthropic` to the Starter row.
- `spring.ai.anthropic.chat.model` defaults to `claude-haiku-4-5`. The docs are internally inconsistent here: one table says `claude-sonnet-4-20250514`. AD-12 already says to set the model in config, so this only needs a test that asserts the configured id is used.

### F7 — INFO — Spine items with no web evidence (none of them version-critical)

- Docker Compose PostgreSQL image version: explicitly deferred. Fine.
- Testcontainers on the Windows dev machine (Docker Desktop/WSL2): assumed, not checked against the project.
- systemd user service plus Sway `bindsym` for PushToTalk/EmergencyStop: assumed. These are standard and stable, so the risk is low.
- RFC 9457 `ProblemDetail`: Spring Framework has supported it since 6.0, so the risk is low.
- STT/TTS engines: deferred to the M3 benchmark. The research is thorough and correctly marks its figures as estimates.

## Sources

- https://github.com/spring-projects/spring-boot/releases
- https://github.com/spring-projects/spring-ai/releases
- https://github.com/spring-projects/spring-modulith/releases
- https://docs.spring.io/spring-ai/reference/api/tools.html (2.0.1)
- https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html (2.0.1)
- https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-bom/2.0.1/spring-ai-bom-2.0.1.pom
- https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-anthropic/2.0.1/spring-ai-anthropic-2.0.1.pom
- https://github.com/anthropics/anthropic-sdk-java/releases
- https://platform.claude.com/docs/en/about-claude/models/overview
- https://platform.claude.com/docs/en/models/haiku-5-5/overview
- https://platform.claude.com/docs/en/models/haiku-5-5/migration-guide
- https://docs.spring.io/spring-modulith/reference/events.html (2.1.1)
- https://docs.spring.io/spring-modulith/reference/runtime.html
- https://start.spring.io/metadata/client
