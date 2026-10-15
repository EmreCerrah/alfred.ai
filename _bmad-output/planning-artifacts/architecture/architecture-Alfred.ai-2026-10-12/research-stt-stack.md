# Research: STT / TTS / Platform Versions for alfred

- **Date of research:** 2026-10-12
- **Context:** Turkish-speaking personal desktop agent. Fedora + Sway (Wayland), CPU only (no GPU), Java/Spring modular monolith on the host, Python STT/TTS engines may run as Docker Compose services. Push-to-talk. Target about 3 s from end of speech to start of spoken reply. Total Claude API budget USD 5/month. Single user, 20-50 short commands/day (about 30 min of audio/month at most).
- **Method:** Web search plus direct fetches of primary sources (GitHub release pages, official docs, model cards, the Whisper paper PDF). Each claim is marked **[verified]** (read in a primary source), **[secondary]** (third-party source only) or **[estimate]** (my inference, must be measured).

---

## 0. Bottom line

| Concern | Recommendation | Why |
|---|---|---|
| STT (primary) | **seda-v0.1** (Turkish streaming Zipformer2 transducer, int8 ONNX, sherpa-onnx) in a Docker service | Turkish-only and made for CPU. About 30x faster than real time on **one** thread. Final text arrives about 40 ms after audio ends. FLEURS-TR WER 11.5% (with LM), level with Whisper small. Apache-2.0. |
| STT (alt 1, local) | **faster-whisper `small` int8** (or `medium` int8 if the CPU allows) | Mature, widely used. Turkish FLEURS WER 15.9% (small) / 10.4% (medium) zero-shot. About 1 s per utterance for small on a modern CPU, several seconds for medium/turbo. |
| STT (alt 2, cloud) | **Groq `whisper-large-v3-turbo`** | USD 0.04 per audio hour, 10 s billed minimum per request, so about **USD 0.17/month** at 50 commands/day. Best accuracy of the options here (large-class Whisper). Adds a network hop and sends audio off the machine. |
| TTS (primary) | **Piper (OHF-Voice/piper1-gpl) + `tr_TR-dfki-medium`** | Only official Piper Turkish voice left. Fast on CPU. Voice data is CC BY-NC-SA 4.0 (fine for personal use). Gender not stated on the model card. The MaryTTS "dfki-ot" source voice is believed to be male, but listen to samples to confirm. |
| TTS (fallback / quality) | **Azure Neural `tr-TR-AhmetNeural`** (male) on the F0 free tier, 0.5 M chars/month; or Google Cloud TTS (WaveNet 4 M chars/month free) | Free at alfred's volume (about 150 k chars/month). Cloud dependency. |
| Java | **Java 25 (LTS)** | Latest LTS. JDK 27 (Sep 2026) is non-LTS. |
| Spring Boot | **4.1.1** | Latest GA. |
| Spring AI | **2.0.1** | Latest GA. Requires Boot 4.0/4.1 and Framework 7. Anthropic provider is built on the official Anthropic Java SDK. |
| Anthropic Java SDK | **2.71.0** | Latest, 2026-10-09. |
| Spring Modulith | **2.1.1** | Latest GA, aligned with Boot 4.1.1. |

Design implication: put STT and TTS behind ports (`SpeechToText`, `TextToSpeech`) with adapters (sherpa-onnx/seda, faster-whisper, Groq; Piper, Azure). Then the engine choice can change after a spike on the real hardware without rework. **Run a benchmark spike on the target CPU before committing.** Every CPU latency figure below comes from other hardware.

---

## 1. Speech-to-text (Turkish, CPU only)

### 1.1 Whisper family: Turkish accuracy by model size [verified]

Source: Radford et al., *Robust Speech Recognition via Large-Scale Weak Supervision*, Appendix D, Table 11 (Common Voice 9) and Table 13 (FLEURS). https://arxiv.org/pdf/2212.04356 (I extracted the PDF text and read the Turkish column directly.)

| Model | Params | Turkish WER, FLEURS | Turkish WER, Common Voice 9 |
|---|---|---|---|
| tiny | 39 M | 42.5 % | 53.6 % |
| base | 74 M | 27.5 % | 38.6 % |
| small | 244 M | 15.9 % | 23.7 % |
| medium | 769 M | 10.4 % | 17.7 % |
| large (v1) | 1550 M | 9.4 % | 16.6 % |
| large-v2 | 1550 M | 8.4 % | 14.5 % |

- **large-v3**: Turkish WER is not in the paper. BuzzASR reports zero-shot large-v3 **CER** of 2.42 on FLEURS-TR and 5.15 on CV25-TR (https://huggingface.co/BuzzASR/turkish) [secondary].
- **large-v3-turbo** (809 M, 4 decoder layers): no published Turkish number found. OpenAI's card names Thai and Cantonese as the languages that degrade most. Third-party summaries say turbo tracks large-v2 in most languages. One estimate puts it 2-4 WER points behind large-v3 for Turkish (https://vexascribe.com/whisper-large-v3-vs-turbo) [secondary, unmeasured]. **Unconfirmed.**
- **Distil-Whisper**: the official distil-large-v2/v3 models are English-only. No Turkish distil model of note found. Not suitable.
- Takeaway: **tiny and base are unusable for Turkish.** **small is the minimum.** medium or turbo is needed for about 10 % WER.

### 1.2 Whisper CPU latency

- faster-whisper project benchmark (via https://www.ssdnodes.com/learn/lang/vi/self-hosted-speech-to-text-and-tts, which cites SYSTRAN's numbers): `small` on i7-12700K, 8 threads, 13 min audio: **int8 102 s** (about RTF 0.13), fp32 157 s [secondary, citing the project README].
- Per-utterance measurement on Apple M3 CPU (runtime not stated): **small about 1.0 s, large-v3-turbo about 3.8 s** per utterance (cited in the search summary from a French voice-agent write-up) [secondary].
- Whisper always encodes a 30 s window. Short (2-5 s) clips therefore cost roughly a fixed encoder pass. With push-to-talk, decoding cannot start before the user releases the key.
- **Estimate for a modern 8-16-core desktop CPU, int8, 2-5 s utterance** [estimate, must be measured]:
  - small: about 0.6-1.2 s
  - medium: about 2-3.5 s
  - large-v3-turbo: about 2-4 s (same 32-layer encoder as large, small decoder)
  - Within a 3 s end-to-end budget (STT + Claude first token + TTS first audio), only **small** or a streaming model fits comfortably. medium and turbo would use up most of the budget.
- whisper.cpp: no trustworthy x86 AVX2 numbers found. The project ships `whisper-bench` / `bench-all.sh` for measuring locally (https://github.com/ggerganov/whisper.cpp). Some reports show whisper.cpp fp32 beating faster-whisper on CPU [secondary]. Both are viable. faster-whisper is simpler to wrap in a Python HTTP service.

### 1.3 seda-v0.1: Turkish streaming transducer [verified from model card]

https://huggingface.co/atasoglu/seda-v0.1 (release date not stated on the card)

- Causal streaming Zipformer2 RNN-T, **66.1 M params**, trained with icefall, exported to **int8 ONNX**, runs on **sherpa-onnx** (CPU).
- **1 CPU thread: RTF 0.027 (greedy) / 0.029 (beam 4) / 0.034 (beam 4 + LM)**, peak RAM about 170 MB.
- Final result about **40 ms after end of audio**. Median first-word latency 0.70 s (streaming partials).
- WER (streaming, int8, CPU):

| Test set | Greedy | Beam 4 | Beam 4 + LM |
|---|---|---|---|
| FLEURS-TR | 15.14 | 14.24 | **11.52** |
| Common Voice 27 TR | 16.32 | 15.59 | 13.61 |
| ISSAI TSC | 16.00 | 15.72 | 15.23 |

- On the card's normalization, Whisper small (offline, beam 5) scores 11.90 on FLEURS-TR, which the card calls within the confidence interval.
- Output is lowercase, unpunctuated, numbers as words. That is fine as LLM input. Claude can normalize it.
- License: acoustic model Apache-2.0. Optional LM CC BY-SA 4.0.
- **Risks:** v0.1 from a single community author. No published track record. Vocabulary for commands, app names and English loanwords is untested. Mitigate with sherpa-onnx hotwords/contextual biasing, or keep Whisper as a fallback adapter.

### 1.4 Other local engines: Turkish support check

| Engine | Turkish? | Notes | Source |
|---|---|---|---|
| Vosk | Yes (`vosk-model-small-tr`, about 50 MB class) | Kaldi-era. No published Turkish WER found. Expect clearly worse than Whisper small. Not recommended except as a toy. | https://alphacephei.com/vosk/models [Turkish WER unconfirmed] |
| Moonshine | **No** | "Flavors" release covers ar, zh, ja, ko, uk, vi only. | https://arxiv.org/abs/2509.02523 |
| NVIDIA Parakeet-TDT-0.6B-v3 | **No** | 25 European languages, Turkish not included. | https://build.nvidia.com/nvidia/parakeet-tdt-0_6b/modelcard |
| NVIDIA Canary-1B-v2 | **No** | Same 25-language Granary set. | https://arxiv.org/pdf/2509.14128 |
| Kyutai STT | **No** | English/French only (known; not re-verified in this pass). | n/a [unconfirmed this pass] |
| SenseVoice-Small | **No (not native)** | Native: zh, yue, en, ja, ko. | https://github.com/FunAudioLLM/SenseVoice |
| Qwen3-ASR-0.6B | **Yes** | About 0.9 B params, BF16, examples are GPU/CUDA. No Turkish-specific WER (FLEURS "extra 8 languages" average 10.37). CPU speed unknown. Possibly interesting later. | https://huggingface.co/Qwen/Qwen3-ASR-0.6B |
| Meta Omnilingual ASR (300M-7B) | Yes (1600+ languages) | Turkish WER not checked. 300M variant could run on CPU. | https://ai.meta.com/research/publications/omnilingual-asr-open-source-multilingual-speech-recognition-for-1600-languages/ |
| Voxtral (Mistral) | Not in published FLEURS columns | 3B, too heavy for the CPU target. | openrouter comparisons [secondary] |

### 1.5 Cloud STT with Turkish: price at alfred's volume

Volume assumption: 50 commands/day × 30 days = 1500 requests, about 3 s each, so about 75 min of raw audio (task brief says about 30 min; both shown).

| Service | Turkish | Price | Monthly cost (30-75 min, 1500 req) | Source |
|---|---|---|---|---|
| **Groq whisper-large-v3-turbo** | Yes (Whisper, 99+ langs) | **USD 0.04/hour**, 10 s minimum billed per request | 1500 × 10 s = 4.2 h → **about USD 0.17** | https://console.groq.com/docs/model/whisper-large-v3-turbo [verified]; min-bill from Groq ASR guide [secondary]. Free plan exists with rate limits (ASH/ASD), exact values per account: https://console.groq.com/docs/rate-limits |
| Groq whisper-large-v3 | Yes | USD 0.111/hour | about USD 0.46 | Groq ASR guide (dated Oct 2024) [secondary] |
| **Deepgram Nova-3** | Yes, monolingual `tr` (not in `multi`) | about USD 0.0043/min batch, 0.0048-0.0077/min streaming (sources vary) | **about USD 0.15-0.60** (per-second billing assumed); **USD 200 signup credit** covers years | Languages: https://developers.deepgram.com/docs/models-languages-overview [verified]; price: https://convertaudiototext.com/blog/deepgram-nova-3-explained, https://www.happyrobot.ai/hub/deepgram-pricing [secondary] |
| OpenAI gpt-4o-mini-transcribe | Yes | about USD 0.003/min (whisper-1 USD 0.006/min) | about USD 0.10-0.25 | https://costgoat.com/pricing/openai-transcription [secondary] |
| Google Cloud STT | Yes | 60 min/month free (possibly V1 only); V2 USD 0.016/min | **USD 0** within 60 min, otherwise about USD 0.50-1.20 (15 s billing increments on V1 make it worse) | https://cloud.google.com/speech-to-text/priv/pricing [verified 60 min free; V1/V2 scope unconfirmed] |
| Azure Speech (F0) | Yes | 5 audio hours/month free (real-time) | **USD 0** | https://azure.microsoft.com/pricing/details/cognitive-services/speech-services [verified via search snippet] |

All cloud options cost **well under USD 1/month**, so they do not threaten the USD 5 Claude budget. The real trade-offs are network latency, an external dependency, and sending microphone audio to a third party.

---

## 2. Text-to-speech (Turkish, CPU)

### 2.1 Piper [verified]

- Project moved to **OHF-Voice/piper1-gpl** (GPL-3.0, `pip install piper-tts`, HTTP server doc `docs/API_HTTP.md`). README says the Open Home Foundation **"is looking for maintainers for Piper"**, a maintenance-risk signal. https://github.com/OHF-Voice/piper1-gpl
- Turkish voices in `rhasspy/piper-voices`: **only `tr_TR-dfki-medium` remains.** `fahrettin` and `fettah` were **removed on 2025-12-30** "at request of contributors" (commit b145f2a). https://huggingface.co/rhasspy/piper-voices/tree/main/tr/tr_TR
- `dfki-medium` model card: 22.05 kHz, single speaker, fine-tuned from en_US lessac. Dataset is MaryTTS **dfki-ot**, license **CC BY-NC-SA 4.0**. **Gender not stated.** The MaryTTS dfki-ot voice is understood to be male [unconfirmed, listen to samples]. https://huggingface.co/rhasspy/piper-voices/blob/main/tr/tr_TR/dfki/medium/MODEL_CARD
- Community voice: **Piper "Cem"** Turkish (24 kHz, INT8 30.8 MB, recommended for CPU), gated download, license and gender not verified. https://huggingface.co/dcx514ai/piper_tts_turkish_high
- Piper CPU speed: VITS, typically far faster than real time on desktop CPUs. Time to first audio for a short sentence is on the order of 100-300 ms [estimate, widely reported but not re-benchmarked here].

### 2.2 Others

| Option | Turkish | CPU suitability | Notes / source |
|---|---|---|---|
| **Kokoro** | **No** | Fast | Languages: en, ja, zh, fr, it, pt, es, hi. https://tts.ai/voices/kokoro/?lang=tr [secondary] |
| **XTTS-v2** (Coqui) | Yes | **Poor**: autoregressive GPT + HiFi-GAN. No CPU benchmark found. Generally reported slower than real time on CPU [unconfirmed]. GPU RTF 0.08 (RTX 5090). | Coqui shut down (2024). Model under CPML (non-commercial). https://huggingface.co/AMD-PAVS-AI/XTTS-v2 |
| **Chatterbox Multilingual** (Resemble) | Yes (23 langs incl. Turkish) | About 500 M params. No CPU benchmark. Likely too slow for 3 s on CPU [estimate]. | https://build.nvidia.com/resembleai/chatterbox-multilingual-tts/modelcard |
| **FreyaTTS** | Turkish-first | 183 M params, Apache-2.0, weights + code released. Claims real time on a laptop CPU. GPU RTF 0.11. Speaker gender not stated. Very new (arXiv 2026-07-10). | https://arxiv.org/abs/2607.09530. **Worth a spike if Piper dfki quality is not good enough.** |
| Meta MMS-TTS `tur` | Yes | VITS, fast | CC BY-NC 4.0, gender unknown, robotic quality. https://huggingface.co/facebook/mms-tts-tur |
| **Azure Neural TTS** | Yes, `tr-TR-AhmetNeural` (male), `tr-TR-EmelNeural` (female) | Cloud | F0: **0.5 M chars/month free** (prebuilt non-HD neural). At about 100 chars × 1500 replies = 150 k chars/month, **USD 0**. https://azure.microsoft.com/pricing/details/cognitive-services/speech-services |
| Google Cloud TTS | Yes (Turkish voice list not re-verified this pass) | Cloud | Free/month: Standard & WaveNet **4 M chars**, Neural2 1 M, Chirp 3 HD 1 M (then USD 30/M). https://cloud.google.com/text-to-speech/pricing |

**TTS recommendation:** Piper with `tr_TR-dfki-medium` locally. Validate the voice gender and quality by ear in a spike. If the voice is unacceptable, spike FreyaTTS (local, Apache-2.0) or use Azure `tr-TR-AhmetNeural` (guaranteed male, free at this volume, adds a cloud hop of roughly 200-500 ms [estimate]).

---

## 3. Platform versions (as of 2026-10-12)

| Component | Latest GA | Release date | Notes | Source |
|---|---|---|---|---|
| **Java (LTS)** | **25** | 2025-09-16 | JDK 27 (non-LTS) GA **2026-09-15**, supported until Mar 2027. Next LTS expected JDK 29 (Sep 2027, by cadence). | https://sdtimes.com/softwaredev/java-25-lts-is-now-available-with-features-like-module-import-declarations-compact-source-files/ ; https://infoq.com/news/2026/08/java-27-so-far ; https://endoflife.ai/oracle-jdk/27 |
| **Spring Boot** | **4.1.1** | 2026-08-20 | 4.0.8 (2026-08-21) maintenance; 4.2.0-M2 preview (2026-09-24). 3.5.x OSS support ended 2026-06-30 [secondary]. Boot 4 minimum Java 17. | https://github.com/spring-projects/spring-boot/releases |
| **Spring AI** | **2.0.1** | 2026-08-21 | 2.0.0 GA 2026-06-12. 2.1.0-M1 preview 2026-09-25. **Designed for Spring Boot 4.0 / 4.1 and Spring Framework 7.0** (does not run on Boot 3.x). Jackson 3, JSpecify. | https://github.com/spring-projects/spring-ai/releases ; https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/ |
| **Anthropic Java SDK** (`com.anthropic:anthropic-java`) | **2.71.0** | 2026-10-09 | Very frequent releases (several per week). | https://github.com/anthropics/anthropic-sdk-java/releases |
| **Spring Modulith** | **2.1.1** | 2026-08-26 | Targets Boot 4.1.1. 2.0.8 for Boot 4.0.8. 2.2-M1 for Boot 4.2-M1. | https://github.com/spring-projects/spring-modulith/releases ; https://spring.io/blog/2026/08/26/spring-modulith-2-2-m1-2-1-1-2-0-8-and-1-4-13-released/ |

Compatible set: **Java 25 + Spring Boot 4.1.1 + Spring AI 2.0.1 + Spring Modulith 2.1.1**. The Anthropic SDK version is managed transitively by Spring AI. Pin the standalone SDK only if it is used directly, and check that it matches the version Spring AI's BOM brings in.

### 3.1 Spring AI 2.0: Anthropic support [verified]

- Starter: `org.springframework.ai:spring-ai-starter-model-anthropic` (manual config: `spring-ai-anthropic`). Properties: `spring.ai.anthropic.*`, chat options `spring.ai.anthropic.chat.*`.
- 2.0 **merged the two Anthropic variants (HTTP and SDK) into one SDK-based implementation** on the official Anthropic Java SDK (https://github.com/anthropics/anthropic-sdk-java).
- Docs: https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html (2.0.1).

### 3.2 Spring AI 2.0: disabling framework tool execution [verified in 2.0.1 docs]

https://docs.spring.io/spring-ai/reference/api/tools.html

- **Changed from 1.x:** in 1.x, each `ChatModel` ran its own loop, switched off with `ToolCallingChatOptions.internalToolExecutionEnabled(false)` / `spring.ai.<provider>.chat.options.internal-tool-execution-enabled=false`. In **2.0 those per-model loops were removed.** Tool execution is now done by `ToolCallingAdvisor`, which `ChatClient` registers automatically. The 2.0.1 tools page and the Anthropic page **do not mention `internalToolExecutionEnabled`**. Treat it as gone or deprecated in 2.0 [verify against the 2.0 upgrade notes before coding].
- Ways to keep tool execution under alfred's own control:
  1. **Per call:** `.advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))` on `chatClient.prompt()`.
  2. **Globally:** `spring.ai.chat.client.tool-calling.enabled=false`.
  3. **Use `ChatModel` directly:** per the docs, tool definitions are sent but **tool calls in the response are not executed automatically**. Read `chatResponse.hasToolCalls()` and dispatch them yourself.
  4. **Manual loop helper:** `ToolCallingManager.builder().build().executeToolCalls(prompt, chatResponse)` returns a `ToolExecutionResult` with `conversationHistory()`. For limit breaches use `onLimitExceeded(ToolCallLimitBehavior.RETURN_ERROR_RESPONSE)`.
  - Docs caveat: a hand-driven loop bypasses advisor observability and composition. The docs suggest a custom advisor inside the loop as the more composable alternative.
- Alfred implication: if every tool call must pass through alfred's own permission/confirmation gate, use option 2 or 3 and run the loop in the alfred core module. Use `ToolCallingManager` only as a helper, or not at all.

### 3.3 Spring Modulith for module-boundary verification

- `ApplicationModules.of(App.class).verify()` in tests, plus **startup verification of module structure** (added in 2.0), event publication registry (JDBC/JPA), and per-module Flyway migrations (2.0). https://spring.io/blog/2025/11/21/spring-modulith-2-0-ga-1-4-5-and-1-3-11-released/
- Use 2.1.1 with Boot 4.1.1.

---

## 4. Latency budget sketch (3 s target, push-to-talk) [estimate]

| Stage | seda (local) | faster-whisper small (local) | Groq turbo (cloud) |
|---|---|---|---|
| STT final after key release | about 0.05-0.1 s | about 0.6-1.2 s | about 0.3-0.8 s (upload + inference) |
| Claude first tokens (short reply, streaming) | about 0.6-1.5 s | same | same |
| TTS first audio (Piper, first sentence) | about 0.1-0.3 s | same | same |
| **Total** | **about 0.8-1.9 s** | about 1.3-3.0 s | about 1.0-2.6 s |

Stream Claude output and start Piper on the first full sentence. Do not wait for the whole reply.

---

## 5. Open items / unconfirmed

1. Real CPU latency for seda, faster-whisper small/medium/turbo on the user's actual CPU (spike required).
2. Turkish WER for large-v3-turbo (no published number).
3. Gender and quality of the Piper `tr_TR-dfki-medium` voice (listen to samples). License and gender of the community "Cem" voice.
4. Whether Spring AI 2.0 still exposes `internalToolExecutionEnabled` anywhere (2.0.1 docs don't mention it; check the upgrade notes).
5. Groq free-plan audio quotas (account-specific). Deepgram's current Turkish per-minute price (monolingual tier assumed).
6. Kyutai STT language list (not re-verified this pass; believed en/fr only).
