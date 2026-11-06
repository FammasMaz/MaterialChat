# Bounded Gemini speech backend

## Integration

`GeminiTtsBackend(player = yourPlayer).speak(text, settings, key): Flow<TtsEvent>` generates one utterance per collection. Supply the existing `AssistantVoiceSettings` with explicit `geminiModel` and `geminiVoice`. Retrieve/decrypt the dedicated encrypted key immediately before collection. The backend does not persist or log it. A JVM String cannot be reliably erased; do not retain the returned cold Flow longer than needed.

`GeminiPcmPlayer.play(pcm, sampleRate)` is the injectable playback boundary, not an installed Android player. Implementations must play signed little-endian 16-bit mono PCM, suspend until audible completion, and stop/release playback before returning on cancellation. The buffer is borrowed and zeroed when playback returns. Serialize collections externally if sharing a physical player. No Android manager, ViewModel, overlay or preference integration is changed here.

Collecting emits Started immediately before calling the player and Completed after it returns. Cancel the collecting Job to stop; CancellationException propagates (no Stopped event after cancellation). Errors emit one fixed, sanitized Error. HTTP and playback timeouts also propagate as cancellation. A cancellable transport and player can be injected for tests.

Limits: 4,000 UTF-16 text characters, 512 key characters, 64 voice-name characters, 4,000,000 response bytes, 2,880,000 decoded PCM bytes (60 seconds at 24 kHz), 60-second generation and 70-second playback deadlines. Exactly one STOP candidate, one inline audio part, valid base64, an even nonempty sample buffer, and explicit 24-kHz audio/l16 MIME are required. No WAV guessing, resampling, redirects, retries, logging interceptors or disk cache. The dedicated OkHttp client cancels its owned Call when collection is cancelled. Limits are application safety limits, not provider quotas.

## Official sources and supported scope

Google's [speech generation guide](https://ai.google.dev/gemini-api/docs/speech-generation), now served at [AI Studio speech generation](https://aistudio.google.com/docs/speech-generation), lists `gemini-3.1-flash-tts-preview` and `gemini-2.5-pro-preview-tts` alongside newer models. Google's [pricing page](https://aistudio.google.com/docs/pricing#gemini-2.5-flash-preview-tts) additionally lists `gemini-2.5-flash-preview-tts`. This backend explicitly accepts those three preview generateContent models; there is no automatic model default.

The same guide's audio-format/migration section documents older preview output as headerless `audio/l16` PCM, contrasting newer unary WAV output. The [generateContent REST reference](https://ai.google.dev/api/generate-content) describes contents, generationConfig, responseModalities, speechConfig/voiceConfig/prebuiltVoiceConfig, candidates and inlineData. Requests use `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, with the key in `x-goog-api-key`, never a URL query.

The fetched guide currently advertises Gemini 3.8 models and a different Interactions schema with unary WAV defaults. Those identifiers are deliberately **not accepted** here: their raw-PCM generateContent request/response compatibility has not been independently established. This implementation does not assume a Gemini 3.8 API or silently switch schema. Revalidate these moving preview identifiers before deployment.

Google's [pricing table](https://aistudio.google.com/docs/pricing#gemini-3.1-flash-tts-preview) lists free input/output for 3.1 Flash TTS Preview; the [2.5 Flash section](https://aistudio.google.com/docs/pricing#gemini-2.5-flash-preview-tts) also lists free input/output. The [2.5 Pro section](https://aistudio.google.com/docs/pricing#gemini-2.5-pro-preview-tts) says free tier **not available**. Free-tier requests are marked as used to improve Google's products; paid-tier requests are marked No. Free does not mean unlimited or a guarantee of account access. [Rate limits](https://ai.google.dev/gemini-api/docs/rate-limits) vary by model/project/tier and should be checked in AI Studio; no universal numeric quota is baked into this backend. Send text only with informed user consent.

## Verification

Six JVM JUnit tests exercise injected request/player behavior, validated PCM and buffer erasure, malformed/oversized/unsupported audio rejection, input/model/key rejection, transport cancellation, player cancellation and sanitized errors. They require no live credential. Standalone Kotlin compiler/JUnit verification passed all six tests using cached dependencies, without Gradle/Android build or emulator. These are boundary tests, not proof of live Google availability, native AudioTrack behavior, or production UI integration.
