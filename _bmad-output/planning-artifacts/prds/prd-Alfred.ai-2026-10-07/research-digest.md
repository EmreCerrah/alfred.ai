# alfred: Research Digest (2026-10-07)

Web research for the alfred PRD: a Turkish-speaking personal desktop AI agent on a Linux PC, with Claude as the LLM, tool use (Docker, Git, files, browser), voice and text input, and confirmation before irreversible actions. Confidence notes: claims from vendor or marketing pages are marked *(vendor claim)*. Anything that drives a design decision should be checked again before implementation.

---

## 1. Comparable open-source personal/desktop agents

### OpenClaw (formerly Clawdbot/Moltbot)
- **Positioning:** a self-hosted "hyper-personal assistant" that you reach through messaging apps (WhatsApp, iMessage, etc.). It keeps memory between sessions, runs shell commands, controls a browser, and manages calendar and files. It went viral in Jan 2026 (145k+ GitHub stars within weeks).
- **Permissions model:** "exec approvals". Host command execution is allowed only when the policy, an allowlist, and an optional user approval all agree. The security levels are `deny` / `allowlist` / `full`, and the ask levels are `off` / `on-miss` / `always`. Allowlists are set per agent and are glob patterns on resolved binary paths. Execution can also be sandboxed in Docker.
- **Failure modes and incidents:**
  - The ClawHub skill marketplace was poisoned: about 341 malicious skills out of about 2,857 (~12%), some of which installed keyloggers or stealers.
  - CVE-2026-25253 allowed one-click RCE through a malicious link (patched in 2026.1.29).
  - 1,800+ exposed instances leaked API keys and chat history.
  - Infostealers took the config file, which holds every token the agent uses.
- **Thoughtworks Radar: "Caution".** Usefulness grows with the access you grant, so the pattern is "permission-hungry and high risk". Smaller-blast-radius variants such as NanoClaw and ZeroClaw are mentioned.
- Sources: https://www.thoughtworks.com/radar/tools/openclaw · https://docs.openclaw.ai/tools/exec-approvals · https://www.digitalocean.com/resources/articles/openclaw-security-challenges · https://www.techradar.com/pro/here-are-the-openclaw-security-risks-you-should-know-about · https://adversa.ai/blog/openclaw-security-101-vulnerabilities-hardening-2026/ · https://icml.cc/virtual/2026/67884

### Open Interpreter
- **Positioning:** "let LLMs run code locally". A terminal chat interface that writes and runs Python/shell on the user's machine. It works with any LLM, including Claude and local models.
- **Permissions model:** it asks the user to confirm before running any code by default; `--auto-run` turns this off. An experimental "safe mode" (`off` / `ask` / `auto`) scans generated code with semgrep. The docs say plainly that it offers no guarantees.
- **User complaints:** garbage output or random commands mid-run, hangs after code runs (reported with Claude 3 models), API cost with frontier models, and the inherent risk of running arbitrary generated code.
- Sources: https://docs.openinterpreter.com/safety/introduction · https://docs.openinterpreter.com/safety/safe-mode · https://github.com/openinterpreter/open-interpreter/issues/1056 · https://community.openai.com/t/open-interpreter-outputting-garbage-values-on-loop/786061

### Goose (Block, now under the Linux Foundation's Agentic AI Foundation)
- **Positioning:** an open-source, local, model-agnostic agent for coding and automation. Its whole tool surface is made of **MCP extensions**: built-in, stdio, or remote HTTP.
- **Permissions model:** tool calls prompt by default. Modes are `prompt` / `allow` / `deny`, and sensitive environment variables are filtered before they reach extensions.
- **Complaints:** token cost per action discourages side-project iteration, context grows with every loop, loops can run silently with no budget limit, and there was disruption from the 2026 repo/docs migration.
- Sources: https://mintlify.com/block/goose/concepts/extensions · https://mintlify.com/block/goose/guides/mcp-integration · https://rywalker.com/research/goose · https://aaif.io/blog/ai-agents-for-air-gapped-industrial-environments-why-goose

### Claude Code (as a reference agent)
- It has layered permissions (modes plus allow/ask/deny rules plus hooks) and **OS-level sandboxing** on Linux through bubblewrap (filesystem) and socat (network proxy). The docs argue that filesystem and network isolation are both needed: without network isolation a compromised agent can exfiltrate data, and without filesystem isolation it can plant backdoors.
- Source: https://code.claude.com/docs/en/sandboxing

### Local "JARVIS" hobby projects
- A recurring finding: most problems come from **system-level plumbing**, not from the AI: audio devices, file paths, packaging, and runtime differences.
- Source: https://stardance.hackclub.com/projects/19963 (hobby project log, anecdotal)

### Cross-cutting takeaways for alfred
1. **Confirmation gating is standard**, from "always ask" (Open Interpreter) to allowlist plus ask-on-miss (OpenClaw). Allowlist + ask-on-miss + hard deny-list is the most mature pattern.
2. **The biggest real-world risks are supply chain and secrets,** not the LLM itself: third-party skills/plugins, prompt injection through browsed web content, and plaintext config holding tokens. alfred should skip a plugin marketplace, keep secrets out of the agent's readable files, and treat web/page content as untrusted.
3. **Sandboxing is how prompt fatigue is reduced:** run risky commands in Docker or bubblewrap so fewer actions need a prompt.
4. **Cost and loop control** come up constantly as complaints. Plan for max turns/budget, visible token or cost tracking, and loop detection.
5. **The audio/OS plumbing** is where hobby voice assistants stall. Budget time for it.

---

## 2. Turkish speech: STT, TTS, wake word

### Speech-to-text (STT)
| Option | Turkish quality | Hardware / latency | Get it |
|---|---|---|---|
| **Whisper large-v3** (OpenAI, open weights) | Good. Studies report Turkish WER of about **4.3–14.2%** depending on dataset | Needs a GPU for interactive use. About 3 GB VRAM with int8 on faster-whisper | HF / pip |
| **Whisper large-v3-turbo** | Close to large-v3 (about 1–2 pp WER worse on benchmarks); ~4x faster | GPU recommended. On CPU, Whisper models are reported **slower than real time** (RTF ~1–6) *(vendor claim)* | HF |
| **faster-whisper** (CTranslate2) | Same models | ~2–4x faster than openai/whisper. int8 on GPU (large-v2 about 2.9 GB); small model int8 on CPU about 1.5 GB RAM. Built-in Silero VAD. GPU needs CUDA 12 + cuDNN 9 | `pip install faster-whisper` |
| **whisper.cpp** | Same models (GGML) | C/C++, runs on CPU with optional GPU. Good for small/medium on CPU; large is slow on CPU | Build from source |
| **Turkish fine-tunes** (e.g. LoRA fine-tunes of large-v3-turbo on HF) | Can beat base Whisper on Turkish | Same as the base model | HF |
| **Vosk small-tr 0.3** | WER not published ("TBD"); lightweight | 35 MB, real-time on CPU or RPi | Apache-2.0, alphacephei.com |
| **Cloud: Deepgram Nova-3 multilingual** | Supports Turkish | ~$0.005–0.006/min streaming, sub-300 ms *(vendor claim)* | API key |
| Cloud: OpenAI gpt-4o-transcribe / Google Chirp 3 | Support Turkish | Pricing not confirmed in this research | API key |

**Recommendation for the PRD:** with an NVIDIA GPU, faster-whisper with large-v3-turbo (or a Turkish fine-tune) plus VAD is the strongest local option. On CPU only, accept higher latency with small/medium models, or allow a cloud STT fallback. Since alfred records short push-to-talk commands rather than streaming long audio, batch transcription of short utterances is good enough.

Sources: https://github.com/SYSTRAN/faster-whisper · https://avesis.gazi.edu.tr/yayin/f370e789-f23c-4749-8823-65c2ce471199/implementation-of-a-whisper-architecture-based-turkish-automatic-speech-recognition-asr-system-and-evaluation-of-the-effect-of-fine-tuning-with-a-low-rank-adaptation-lora-adapter-on-its-performance · https://app.alphaneural.io/models/mihuai/turkish-stt (vendor page, CPU RTF claims) · https://alphacephei.com/vosk/models · https://huggingface.co/ysdede/whisper-khanacademy-large-v3-turbo-tr · https://www.happyrobot.ai/hub/deepgram-pricing

### Text-to-speech (TTS)
| Option | Turkish | Hardware / latency | License / notes |
|---|---|---|---|
| **Piper** | 3 voices: `tr_TR-dfki`, `tr_TR-fahrettin`, `tr_TR-fettah` (all *medium*) | Very fast on CPU, runs on RPi; ONNX | MIT voices/engine, but original repo **archived Oct 2025** (development moved to OHF-Voice/piper1-gpl, GPL). Voices on HF `rhasspy/piper-voices` |
| **Coqui XTTS-v2** | Supported, with voice cloning from a short sample | ~2 GB model; streaming at <200 ms on a consumer GPU; slow on CPU | **Coqui Public Model License, non-commercial.** Coqui shut down in Dec 2023, so no commercial license is available. Fine for a personal hobby project. Turkish WER in one eval ~11% |
| **FreyaTTS** (2026 paper, Turkish-first) | Built for Turkish; WER 8.0% vs XTTS-v2 11.1% in authors' eval | Compact flow-matching model | Research; check availability and license |
| **edge-tts** (unofficial) | Microsoft neural tr-TR voices (e.g. `tr-TR-EmelNeural`, `tr-TR-AhmetNeural`) | Cloud and streaming; no API key | Reverse-engineered Edge "Read Aloud" protocol. **Unofficial; could break or violate ToS.** Free |
| **Azure AI Speech** (official) | Same Emel/Ahmet neural voices, SSML | Cloud | Paid with a free tier; API key |

**Recommendation for the PRD:** Piper (fahrettin or dfki) as the offline default, since it is CPU-cheap and instant. A higher-quality option (Azure, edge-tts, or XTTS on GPU) can be pluggable. Keep TTS behind an interface.

Sources: https://github.com/rhasspy/piper/blob/master/VOICES.md · https://huggingface.co/rhasspy/piper-voices · https://huggingface.co/coqui/XTTS-v2 · https://www.promptquorum.com/power-local-llm/xtts-v2-review · https://arxiv.org/pdf/2607.09530 · https://github.com/rany2/edge-tts · https://json2video.com/ai-voices/azure/voices/tr-tr-ahmetneural/

### Wake word
| Option | Turkish | Notes |
|---|---|---|
| **openWakeWord** | **English only officially**: training data is synthesized with English TTS | Apache-2.0 code, CC BY-NC-SA pre-trained models. Very light (15–20 models on one RPi3 core). A custom model can be trained in a Colab notebook. A name like "alfred" may still work as an English-trained word (not verified) |
| **Porcupine (Picovoice)** | **Turkish not supported** (EN, FR, DE, IT, JA, KO, ZH, PT, ES, plus some others) | Custom words are trained in seconds in the console; free personal tier with an AccessKey; Linux x86_64 |
| **Alternative** | — | **Push-to-talk / global hotkey** avoids the problem entirely, or use Vosk with a small grammar to spot "alfred" in Turkish audio |

**Recommendation for the PRD:** make the wake word optional or a later phase. For MVP, use push-to-talk or a hotkey. "Alfred" is phonetically close in English and Turkish, so an English-trained openWakeWord or Porcupine model may be acceptable; test it.

Sources: https://github.com/dscripka/openWakeWord · https://picovoice.ai/docs/porcupine/ · https://picovoice.ai/blog/console-tutorial-custom-wake-word/

---

## 3. Claude agent tooling (capability shape)

- **Claude Agent SDK** (Python and TypeScript): "Claude Code as a library". It provides:
  - built-in tools (read/write/edit files, bash, web search)
  - the agent loop, context management, and sessions (resume/fork)
  - hooks, subagents, MCP, skills/memory, and plugins
- **Permission pipeline (directly relevant to alfred's confirmation feature):** hooks → deny rules → ask rules → permission mode → allow rules → `canUseTool` callback.
  - Modes are `default`, `dontAsk`, `acceptEdits`, `bypassPermissions`, `plan`, and `auto` (a model classifier approves or blocks).
  - Deny rules apply even in bypass mode.
  - `canUseTool` is the runtime hook where alfred would show a Turkish "Emin misin?" confirmation, by voice or UI.
  - A `PreToolUse` hook runs on every call. Use it for checks that must never be skipped.
  - MCP tools can be marked `requiresUserInteraction` so they always need approval.
- **Language note for a Java/Spring developer:** the Agent SDK is **Python/TS only**. From Java you can:
  - (a) drive the Claude Code CLI as a subprocess (`claude -p --output-format json`)
  - (b) use the official **Anthropic Java SDK** (`com.anthropic:anthropic-java`) with your own tool loop (it has a tool runner and an `anthropic-java-mcp` module)
  - (c) use **Spring AI** (Anthropic chat model plus MCP client)
  - (d) use the official **MCP Java SDK** to expose Docker/Git/file tools as MCP servers
  - Option (b) or (c) means alfred implements its own confirmation gate and agent loop.
- **Auth/licensing:** third-party apps on the Agent SDK must use **API-key auth**. Anthropic does not allow offering claude.ai subscription login or rate limits unless pre-approved, so plan for API cost. Branding: "alfred, powered by Claude" is fine; don't present it as "Claude Code".
- **Managed Agents** (Anthropic-hosted agent harness with a cloud or self-hosted sandbox) is another option, but it is less suited to controlling a local desktop.

Sources: https://code.claude.com/docs/en/agent-sdk/overview · https://code.claude.com/docs/en/agent-sdk/permissions · https://code.claude.com/docs/en/sandboxing · https://github.com/anthropics/anthropic-sdk-java · https://central.sonatype.com/artifact/com.anthropic/anthropic-java-mcp/2.48.0 · https://spring.io/blog/2025/02/14/mcp-java-sdk-released-2 · https://docs.spring.io/spring-ai/docs/current/api/

---

## PRD implications (summary)
- **Safety:** tiered actions (read-only auto, reversible with notice, irreversible needs explicit confirmation), a hard deny-list, Docker/bubblewrap sandboxing, no third-party skill marketplace, secrets kept out of what the agent can read, and web content treated as untrusted (prompt injection).
- **Cost:** per-session turn and budget caps with visible usage, since this is API-key billing.
- **Voice:** push-to-talk first, faster-whisper (GPU) for STT, Piper Turkish voice for TTS, wake word deferred. Speech engines go behind interfaces so cloud fallbacks can be added.
- **Stack choice:** if staying in Java, use the Anthropic Java SDK or Spring AI plus MCP Java SDK and own the agent loop and confirmation gate. Otherwise a Python/TS sidecar on the Agent SDK gives permission modes and `canUseTool` for free.
