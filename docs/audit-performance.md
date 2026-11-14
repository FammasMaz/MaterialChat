# MaterialChat performance audit (read-only, code reading)

Branch `main`, 2026-11-14. All findings are from reading source; nothing was run or profiled. "Proven" means the code path and thread are established by reading; costs are reasoned unless stated.

---

## 1. Streaming fans out into a full-conversation re-query, re-map and token re-estimate every 64 ms

- `domain/usecase/StreamingMessageContentUpdater.kt:5,25-35` — `updateContent` Room write every 64 ms with the entire accumulated message string.
- `data/repository/ConversationRepositoryImpl.kt:224-226` + `data/local/database/dao/MessageDao.kt` (`getMessagesForConversation`, `SELECT *`) — each write invalidates the `messages` table; Room re-runs the full `SELECT *` for the conversation (content, thinking_content, all metadata JSON for every message).
- `data/mapper/EntityMappers.kt:158-175,208` — every row goes through `deserializeAttachments` → `json.decodeFromString` per message, per emission.
- `ui/screens/chat/ChatViewModel.kt:189-293` — the collector then rebuilds `MessageUiItem` for every message (`mapIndexed` L258, `resolveGroupPosition` L270 for each), filters/averages stats over all messages, and runs `ContextWindowEstimator.estimate` over the whole history (L285-293, the code itself comments it is expensive) — all ~15×/s during a stream.

**Why slow:** cost per tick is O(conversation size) on three axes (SQLite read + JSON decode + Kotlin remap + token estimate) while the only thing that changed is one row's `content`. For a 100-message conversation with attachments this is the dominant streaming CPU cost, and because `messages = messageItems` is a fresh list each time, `MessageList` recomposes each time (LazyColumn item keys limit the damage to equality checks on every `MessageUiItem`).

**Fix:** keep the streaming message's live text out of Room until completion (hold it in `ChatUiState.streamingState` / a `MutableStateFlow<String>` keyed by messageId, write Room at ~1 s or on completion); or at minimum (a) observe `messages` with `distinctUntilChanged` per row and only remap changed items, (b) compute `historyTokens` only when `messages.size` or completed-message content changes, (c) cache attachment deserialization by `image_attachments` string.

**Severity:** high. **Confidence:** proven path; cost reasoned.

---

## 2. `SmoothStreamingText` re-parses the whole markdown every 18 ms and defeats its own cache

- `ui/screens/chat/components/SmoothStreamingText.kt:56-77` — reveal loop: every `STREAMING_TEXT_FRAME_MS = 18L` (L209) it sets `visibleText = target.substring(0, nextEnd)` (new String each tick) and recomposes `MarkdownText(markdown = visibleText)`.
- `ui/components/MarkdownText.kt:113-118` — `remember(markdown, …)` → `cachedParseMarkdown`; `MarkdownText.kt:276-304` keys the LruCache on the full markdown string. Each tick has a longer string → guaranteed cache miss → `parseMarkdown` over the entire accumulated text (all lines, regex, tables, inline parsing) up to ~55×/s.
- While waiting for tokens (`visibleText == target`) the loop still wakes every 48 ms (`STREAMING_IDLE_POLL_MS`, L212) for the whole stream duration.

**Why slow:** O(n) parse per tick → O(n²) over a response; a 20 KB reply ≈ 1000 ticks × full parse on the main thread (the `remember` block runs in composition). This, not the network, bounds streaming smoothness on long replies.

**Fix:** parse incrementally — parse only the trailing unfinished block and reuse the already-settled element list (prefix elements are immutable once a blank line/closing fence has passed); or run the parse off-main with `produceState` + `Dispatchers.Default` and render the last good result; drop the 48 ms idle poll by suspending on a `snapshotFlow { latestRawText }` instead.

**Severity:** high. **Confidence:** proven path; cost reasoned.

---

## 3. A vibrator IPC per reveal tick

- `SmoothStreamingText.kt:~68` and `~128` — `haptics.perform(CONTENT_TICK/THINKING_TICK)` on every reveal step (every 18/28 ms).
- `ui/components/HapticFeedbackManager.kt:663-677` — `vibrator?.vibrate(contentTickComposition)` (binder call to the vibrator service) on each.

**Why slow:** up to ~55 binder calls/s on the main thread for the whole stream, plus battery from continuous haptic compositions. Also a UX smell when the user has haptics on but is not touching the device.

**Fix:** rate-limit content ticks (e.g. ≥100 ms apart, or one per word boundary), and make streaming haptics a separate opt-in preference.

**Severity:** medium. **Confidence:** proven.

---

## 4. Android Keystore / Tink initialisation runs on the main thread during `MainActivity` creation

- `data/local/preferences/EncryptedPreferences.kt:31-43` — constructor does `AeadConfig.register()`, `AndroidKeysetManager.Builder()…withMasterKeyUri().build()` (Keystore round-trip; on first launch key generation), and `getPrimitive(Aead)`.
- `di/AppModule.kt:85-90` provides it as a `@Singleton`; `data/repository/ProviderRepositoryImpl.kt:24` injects it; `di/AppInitializer.kt:23` injects `ProviderRepository`; `MainActivity.kt:78-84` field-injects `AppInitializer`, so Hilt constructs the whole chain synchronously in `MainActivity.onCreate` (before `setContent`).

**Why slow:** Keystore operations are commonly 50-300 ms (seconds on first launch with key generation, and known to be pathological on some OEM keymaster implementations). This sits on the cold-start critical path before any frame.

**Fix:** make the AEAD lazy (`by lazy` inside `EncryptedPreferences`, initialised on first `getApiKey`/`setApiKey`, which are already `withContext(IO)`), or wrap in `Lazy<EncryptedPreferences>` at injection sites.

**Severity:** high (startup). **Confidence:** proven path; cost reasoned.

---

## 5. The Conversations (home) screen never lets the display idle

- `ui/components/DeviceShake.kt:88-96` — `while (true) { withFrameNanos { } … }` drain loop requests a frame on every vsync forever, even when `energy == 0f`; used via `rememberShakeEnergy()` at `ui/screens/conversations/ConversationsScreen.kt:479`.
- `DeviceShake.kt:64-66` — accelerometer at `SENSOR_DELAY_GAME` (~50 Hz) whenever resumed.
- `ConversationsScreen.kt:478` + `ui/components/DeviceTilt.kt:128` — a second accelerometer/rotation-vector listener at `SENSOR_DELAY_UI`; each event writes `pose.value`, driving recomposition of the banner at sensor rate.
- `ConversationsScreen.kt:504-530` — `rememberInfiniteTransition` with ≥4 `animateFloat`s (16 s, 9.5 s, 30 s gears, breath) while `decorAlpha > 0f`, i.e. the default expanded state.
- Same pattern on Explore: `ui/screens/explore/ExploreScreen.kt:224` (tilt sensor) and `227-235` (48 s infinite rotation), and on Onboarding (`OnboardingScreen.kt:104`).

**Why slow:** with the app foregrounded on its home tab, Compose renders every frame (60-120 fps) indefinitely and two sensors stream events; this is pure battery drain while the user is reading a list. The `withFrameNanos` drain loop alone forces continuous frames regardless of the gears.

**Fix:** in `DeviceShake`, only run the drain loop while `energy > 0` (suspend on a `snapshotFlow`/channel otherwise); stop the gear transition when the list is still for N seconds or respect `Settings.Global.ANIMATOR_DURATION_SCALE`/power-save like `NeuralParticleField` already does (`shouldRequestParticleFrames`); register sensors at `SENSOR_DELAY_UI` only and unregister after an idle timeout; share one sensor listener between tilt and shake.

**Severity:** high (battery). **Confidence:** proven.

---

## 6. Per-token O(n) copy of the accumulated response (O(n²) over a reply)

- `data/repository/ChatRepositoryImpl.kt:76-83` — `normalizedAccumulation()` calls `accumulatedContent.toString()` and `accumulatedThinking.toString()` (full copies) and `normalizeStreamingTextBoundary` (`domain/util/StreamingTextNormalizer.kt:15-33`, scans from the start).
- `ChatRepositoryImpl.kt:~111-120` — called on every `StreamingEvent.Content`, and the result is boxed into a new `StreamingState.Streaming` carrying the full string, consumed by `SendMessageUseCase.kt:298-315` on each token.

**Why slow:** each token allocates two strings the size of the whole reply so far. 1000 tokens × 20 KB ≈ 20 MB of transient garbage per response on the IO thread, plus GC pressure that competes with UI threads.

**Fix:** emit deltas (`StreamingState.Delta(content, thinking)`) and let the single consumer accumulate; or throttle `toString()` to the 64 ms cadence the updater already uses.

**Severity:** medium. **Confidence:** proven.

---

## 7. Global markdown parse cache keyed by full text retains up to 160 message copies

- `ui/components/MarkdownText.kt:176-181` — process-global `LruCache<MarkdownCacheKey, List<MarkdownElement>>(160)`, key contains the entire markdown `String`.

**Why slow (memory):** during one stream (finding 2) the cache fills with 160 successive prefixes of the same reply plus their parsed trees — for a 30 KB reply that is several MB held after the stream ends, never trimmed, and with near-zero hit rate on streaming keys. Size is entry-count, not bytes.

**Fix:** size the cache by bytes (`sizeOf = markdown.length`), skip caching while `isStreaming`, or key on a hash and keep only settled messages.

**Severity:** medium. **Confidence:** proven.

---

## 8. One `WebView` per KaTeX expression inside the chat `LazyColumn`

- `MarkdownText.kt:703-722` (`InlineMathView`) and `725-800` (`MathBlockView`) — `AndroidView(factory = { createMathWebView(context) … })`; `createMathWebView` at `879-883` instantiates `WebView(context)` with JS enabled per expression. Batching only kicks in at ≥2 block equations (`BATCHED_MATH_BLOCK_THRESHOLD = 2`, L177).
- `MathBlockView` height state is `remember(expression, isStreaming)` (L762-763), so the WebView is re-measured (and the fallback height reapplied) when streaming flips to settled.

**Why slow:** first WebView creation ≈ 100-200 ms (renderer process start), subsequent ≈ 10-30 ms each plus several MB per instance; inside a lazy list they are destroyed/recreated on scroll, so scrolling a math-heavy conversation janks and churns memory.

**Fix:** render inline/simple math natively (the `NativeMathBlockView` path exists), pool a single WebView per screen and draw results to bitmaps, or batch all math of a message into one document unconditionally (threshold 1).

**Severity:** medium. **Confidence:** proven path; cost reasoned.

---

## 9. Thirteen separate DataStore collectors at the composition root

- `MainActivity.kt:125-163` — 13 × `appPreferences.X.collectAsState(initial = …)`; each is an independent `dataStore.data.map {}` (`data/local/preferences/AppPreferences.kt`).

**Why slow:** every preference write anywhere in the app (including unrelated keys) emits on all 13 flows, runs 13 `map`s and recomposes the theme root; `collectAsState` is not lifecycle-aware so this continues in the background. At startup each collector independently delivers its initial value a frame later, so the first frames render with defaults and then re-theme.

**Fix:** expose one `Flow<UiPrefs>` (single `dataStore.data.map` producing a data class) and `collectAsStateWithLifecycle` it once; read it synchronously-once before `setContent` (`runBlocking` is not needed — read it inside the existing `LaunchedEffect` gate and only show content after).

**Severity:** medium-low. **Confidence:** proven.

---

## 10. `MobileAds.initialize` on the main thread in `Application.onCreate` (play flavor)

- `MaterialChatApplication.kt:28-30`.

**Why slow:** Google's own guidance is to initialise on a background thread; it typically costs 100-500 ms on cold start and loads adapter classes eagerly.

**Fix:** `applicationScope.launch { MobileAds.initialize(this@MaterialChatApplication) }` (the banner composable already tolerates late init), or defer until a top-level route is shown.

**Severity:** medium (play flavor only). **Confidence:** proven thread; cost reasoned.

---

## 11. Splash held for serial DB round-trips on every launch

- `MainActivity.kt:98-121` — `setKeepOnScreenCondition { !isInitialized }` until `appInitializer.initializeIfNeeded()` returns.
- `di/AppInitializer.kt:35-49,66-86` — every launch: `firstLaunchComplete.first()`, `onboardingPreferenceSet.first()`, `deleteEphemeralConversations()`, `getBuiltinPersonaCount()`, `getProviders()`, then `onboardingComplete.first()` — six sequential suspend calls, the Room ones paying the first database open (and any pending migration) on the critical path; then `updateManager.checkForUpdates` (network) is launched in the same effect.

**Why slow:** none of these except the onboarding flag decide the start destination, yet all gate the first frame.

**Fix:** gate the splash only on `onboardingComplete`; move seeding/ephemeral cleanup to `applicationScope` (the Application already does this for `clearInterruptedStreamingFlags`), and parallelise with `async`.

**Severity:** medium-low. **Confidence:** proven.

---

## 12. ConversationsViewModel recomputes the whole home list on every `messages` invalidation while a chat streams

- `ui/screens/conversations/ConversationsViewModel.kt:78-120` — `combine(... conversationRepository.observeStreamingConversationIds() ...)` collected in `viewModelScope` (alive while the Conversations entry sits on the back stack under the Chat screen).
- `ConversationRepositoryImpl.kt:234-236` — `observeStreamingConversationIds` is a Room flow on `messages`; finding 1 invalidates it every 64 ms.

**Why slow:** each invalidation re-runs `SELECT DISTINCT conversation_id … WHERE is_streaming = 1`, re-runs the combine lambda, builds a `ConversationsData`, and only then `distinctUntilChanged()` suppresses it. Whether Room's generated Flow deduplicates identical results before this is version-dependent; the re-query itself is unconditional.

**Fix:** `observeStreamingConversationIds().distinctUntilChanged()` at the repository, and make the Conversations collector lifecycle-aware (`repeatOnLifecycle`/`stateIn(WhileSubscribed)`) so it is idle while the chat screen is on top.

**Severity:** medium. **Confidence:** proven re-query; dedupe behaviour suspected.

---

## 13. Mind-map tree build is N+1 and loads every message of every node

- `domain/usecase/GetConversationTreeUseCase.kt:20-45` — walks the parent chain with one `getConversation` per ancestor, then `buildNode` recursively does `getConversation` + `getMessages` (full rows) + `getBranches` per node.

**Why slow:** a tree with B branches issues ~3B queries and deserialises every message in the tree to draw nodes that need only id/title/icon/count.

**Fix:** one recursive CTE (or `getRootConversationsWithBranches`-style query) returning id/parent/title/icon plus a `COUNT(*)` per conversation; avoid `getMessages` entirely.

**Severity:** medium (mind-map screen). **Confidence:** proven.

---

## 14. Foreground service start/stop per chat send

- `domain/usecase/SendMessageUseCase.kt:~284` — `ImageGenerationForegroundService.startChat(context, …)` before every stream; `notifications/ImageGenerationForegroundService.kt:117-160` starts a FGS (notification post, `startForegroundService`) for each owner and stops when done.

**Why slow:** a service bind/notification/channel round-trip per message (tens of ms, main thread for the notification), a visible notification flicker for short replies, and FGS start is throttled/denied on Android 12+ from background (already caught, but then streaming has no protection anyway).

**Fix:** start the FGS only when the activity goes to background mid-stream (observe `ProcessLifecycleOwner`), or keep one service alive across consecutive sends with a short linger.

**Severity:** medium-low. **Confidence:** proven.

---

## 15. ChatViewModel init: 11 independent preference collectors plus a redundant full-message cleanup

- `ChatViewModel.kt:132-145` — eleven `load*()` launches, each an independent `appPreferences.*` collector updating `_uiState` separately (≥11 state updates on open; every pref write wakes all).
- `ChatViewModel.kt:153-167` — `cleanUpStuckStreamingMessages` loads all messages for the conversation and issues one `setMessageStreaming` per stale row; `MaterialChatApplication.kt:38-42` already clears interrupted flags once per process, making this a redundant full-table-for-conversation read on every chat open.

**Fix:** one combined prefs flow; drop the per-open cleanup (or replace with a single `UPDATE … WHERE conversation_id = ? AND is_streaming = 1 AND created_at < ?`).

**Severity:** low. **Confidence:** proven.

---

## 16. Generated-image base64 decoded synchronously in composition, full size

- `ui/screens/chat/components/MessageBubble.kt:1438-1447` — `remember(attachment.base64Data) { Base64.decode(...); BitmapFactory.decodeByteArray(...) }` on the main thread, no `inSampleSize`, for images that are displayed at 120 dp thumbnails.

**Why slow:** a 1024² PNG decode is 20-80 ms on main thread per first composition; the base64 string itself (megabytes) is also held in the `Message` list. Mitigated by the mapper never persisting base64 (`EntityMappers.kt:193`), so this only hits freshly generated images.

**Fix:** write the generated image to disk in `persistGeneratedImage` (already exists, `ChatRepositoryImpl.kt:256`) and load via the existing URI path with a downsampled loader.

**Severity:** low. **Confidence:** proven path; frequency limited.

---

## 17. Message search is a full-table `LIKE '%q%'` scan

- `MessageDao.kt` (`searchMessageContent`) — `content LIKE '%' || :query || '%' COLLATE NOCASE` over all messages, no FTS table; `searchConversationsByTitle` likewise.

**Why slow:** indices on `conversation_id`/`created_at` cannot serve a leading-wildcard LIKE; cost grows linearly with total message bytes, runs per keystroke if the search box is debounced loosely.

**Fix:** add an FTS4/5 virtual table on `content` (with a migration per project rule), or at least debounce ≥300 ms and cap scanned rows by recency.

**Severity:** low-medium (scales with history). **Confidence:** proven.

---

## Not examined

- `SettingsScreen.kt` (4210 lines), `SettingsViewModel.kt`, `ModelAssignmentsScreen`, `OnDeviceModelsScreen`, `BackupRepository`, `LocalModelRepositoryImpl` / LiteRT inference path, `WorkflowExecutionScreen`, `ArenaViewModel`, `SeaGlassMaterialComponents.kt` (1317 lines of custom rendering — likely worth its own pass), `ExpressiveFastScrollBar`, `MemoryRepositoryImpl` retrieval scoring (full `SELECT * FROM memories` loads were seen at `MemoryDao.kt:20,97` but the call frequency was not traced), `NativeAuthManager` loopback servers (blocking `ServerSocket.accept` loops at L445/516 — thread not verified), the Room migration bodies in `MaterialChatDatabase.kt`, `UpdateManager` download path, Hilt component/graph size (no `@EntryPoint`/module count analysis), ProGuard/R8 and baseline-profile configuration.

## Could not verify without running the app

- Actual per-tick cost of findings 1, 2, 6 (needs systrace/Perfetto on a long reply); whether the 64 ms Room cadence or the 18 ms reveal loop is the bottleneck in practice.
- Keystore latency in finding 4 on the user's devices.
- Whether Room's generated `Flow` deduplicates unchanged results before `combine` in finding 12.
- Frame rate / power draw of the idle home screen in finding 5 (needs `dumpsys gfxinfo` / Battery Historian).
- WebView instance count and memory in finding 8 under scroll.

## Negative findings (checked, no issue found)

- No `runBlocking` anywhere in `app/src/main`.
- The assistant session does **not** retain the assist screenshot (`MaterialChatVoiceInteractionSession.kt:302-307` logs and drops it; `releaseScreenshot` recycles any held bitmap on hide/destroy).
- `NeuralParticleField` and `EdgeGlow` gate their frame loops on visibility/presence/power-save/animator-scale and have an idle timeout; `EdgeGlow` unregisters its power-save receiver on dispose.
- `DeviceShake`/`DeviceTilt` unregister sensors on pause and dispose.
- `ChatViewModel.handleStreamingState` throttles UI state to 120 ms and strips the content string from the emitted state.
- Streaming cancellation is owner-scoped via `callbackFlow`/`awaitClose`.
- `EncryptedPreferences.getApiKey/setApiKey` run on `Dispatchers.IO`; `SpeechRecognitionManager` correctly uses `Dispatchers.Main.immediate` (API requirement).
