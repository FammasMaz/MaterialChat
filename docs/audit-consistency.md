# MaterialChat consistency audit (read-only)

Scope: `app/src/main/java/com/materialchat`, branch `main` @ 648c37c (v2.29.41). Method: read `AppPreferences.kt` key-by-key, grep every accessor, then read both sides of each suspicious usage in ChatViewModel, AssistantViewModel, SettingsViewModel, ModelAssignmentsViewModel, use cases, NetworkModule, ChatApiClient, AppNotificationManager. Line numbers marked `~` were derived by offset rather than grep.

Ordered by user impact.

---

## 1. "Native Title Generation" toggle in Settings is overridden by a second key with the opposite default

**Files**
- `data/local/preferences/AppPreferences.kt:59` (`PREFER_ON_DEVICE_TITLE_MODEL`), `:62` (`PREFER_ON_DEVICE_BACKGROUND_TASKS`), `:179` (`DEFAULT_PREFER_ON_DEVICE_TITLE_MODEL = false`), `:180` (`DEFAULT_PREFER_ON_DEVICE_BACKGROUND_TASKS = true`), `preferOnDeviceBackgroundTasks` flow (reads BACKGROUND_TASKS, falls back to TITLE_MODEL, then default `true`), `setPreferOnDeviceBackgroundTasks` (writes both keys), `setPreferOnDeviceTitleModel` (writes only TITLE_MODEL)
- `ui/screens/settings/SettingsViewModel.kt:306-312, 1083` reads/writes `preferOnDeviceTitleModel`
- `ui/screens/settings/SettingsScreen.kt:2656` `PreferOnDeviceTitleModelToggle` ("Use the smallest downloaded on-device model before cloud title generation")
- `ui/screens/settings/ModelAssignmentsViewModel.kt:39, 165` reads/writes `preferOnDeviceBackgroundTasks`
- Consumers: `domain/usecase/GenerateConversationTitleUseCase.kt:204`, `domain/usecase/SendMessageUseCase.kt:732`, `domain/usecase/ExtractMemoriesUseCase.kt:164` — all read **only** `preferOnDeviceBackgroundTasks`.

**What disagrees**: Two toggles control one behaviour. (a) Fresh install: Settings shows the title toggle **off** (`false` default), but the use cases evaluate `preferOnDeviceBackgroundTasks` → `true`, so on-device titles are attempted while the UI says disabled. (b) Once the Model Assignments toggle has been touched, `BACKGROUND_TASKS` is set and the Settings title toggle can never affect behaviour again (the fallback is only consulted when `BACKGROUND_TASKS` is absent) — it just flips a stored value nobody reads.

**Fix**: Delete `PREFER_ON_DEVICE_TITLE_MODEL` as an independent setting: make `preferOnDeviceTitleModel`/`setPreferOnDeviceTitleModel` aliases of the background-tasks accessors (or remove the Settings toggle and link to Model Assignments). Unify the default.

**Severity**: high · **Confidence**: proven

---

## 2. "Notifications" toggle is never read by the notification code

**Files**
- `AppPreferences.kt:54, 177, 409-418` (`notificationsEnabled`, default `false`)
- `ui/screens/settings/SettingsScreen.kt:1020` `NotificationsToggle`; `SettingsViewModel.kt:234, 1053`
- `notifications/AppNotificationManager.kt:52` `notifyChatResponseComplete` — gates only on `canPostNotifications()` (system permission) and `isAppInForeground()`; no `AppPreferences` reference anywhere in `notifications/` (grep: zero hits).
- Caller: `ui/screens/chat/ChatViewModel.kt` send path (`appNotificationManager.notifyChatResponseComplete`) — unconditional.

**What disagrees**: The preference is read only to render the switch. Chat-complete notifications are posted whenever the OS permission is granted, regardless of the switch — and the switch defaults to off, so every user sees "Notifications: off" while receiving them.

**Fix**: Inject `AppPreferences` into `AppNotificationManager` (or check it at the call sites) and early-return when `notificationsEnabled.first()` is false. Decide whether image-generation FGS notifications are covered by the same switch and document it in the label.

**Severity**: high · **Confidence**: proven

---

## 3. "System Assistant" switch ("Replace default assistant on your device") only hides a button

**Files**
- `AppPreferences.kt:85, 737-745` (`assistantEnabled`, default `true`)
- `ui/screens/settings/SettingsScreen.kt:3814-3821` — switch; `if (assistantEnabled)` only toggles visibility of the "Set as Default Assistant" card
- `SettingsViewModel.kt:245-248, 1205`
- `assistant/service/MaterialChatVoiceInteractionSession.kt:148` obtains `appPreferences` but never reads `assistantEnabled`; grep for `assistantEnabled` outside `ui/screens/settings` returns only the AppPreferences definition.

**What disagrees**: The label promises enabling/disabling the system-assistant role. Turning it off does not unbind the voice-interaction service, does not block the session from showing, does not change manifest-declared behaviour. If the user has already set MaterialChat as default, the assistant keeps working with the switch off.

**Fix**: Either gate `MaterialChatVoiceInteractionSession`/`AssistantActivity` launch on the flag (show a "disabled in settings" notice and finish), or relabel the switch to "Show assistant setup" / remove it.

**Severity**: high · **Confidence**: proven

---

## 4. Two implementations of "continue previous assistant chat", one dead, with contradictory clock-skew semantics

**Files**
- Live: `AppPreferences.kt:80` (`ASSISTANT_CONTINUE_CHAT_MINUTES`), `data/local/preferences/AssistantVoiceSettings.kt:21, 36, 48-52` (`continueChatMinutes`, `CONTINUE_CHAT_CHOICES`, `shouldContinueAssistantChat`), `assistant/ui/AssistantViewModel.kt:118-123, 173`, `ui/screens/settings/AssistantVoiceSettingsPanel.kt:91-93`
- Dead: `AppPreferences.kt:88` (`ASSISTANT_CONTINUATION` key), `~806` (`assistantContinuation` flow + setter), `data/local/preferences/AssistantContinuation.kt` (entire enum). Grep: zero readers/writers of `assistantContinuation` and zero references to `AssistantContinuation` outside its own file and AppPreferences.

**What disagrees**: Same policy coded twice. `AssistantContinuation.continues()` clamps negative elapsed to 0 (clock moved back ⇒ still continues; the KDoc says this is intentional). `shouldContinueAssistantChat` uses `in 0..window` (clock moved back ⇒ starts fresh). The documented behaviour is in the dead code.

**Fix**: Delete `AssistantContinuation.kt`, the `ASSISTANT_CONTINUATION` key and its accessors. Port the `coerceAtLeast(0)` semantics into `shouldContinueAssistantChat` if that behaviour is wanted.

**Severity**: medium · **Confidence**: proven

---

## 5. Fusion send and normal send diverge in five user-visible ways

**Files**: `ui/screens/chat/ChatViewModel.kt:698-800` (`sendCurrentInput`) vs `:1904-1990` (`sendFusionMessage`), `onCleared` (`:~1760-1770`)

| Aspect | Normal | Fusion |
|---|---|---|
| Scope | `applicationScope` — survives navigation | `viewModelScope` — cancelled in `onCleared()` ("fusion jobs are less critical") |
| Quoted reply | prepended as `> …` and cleared | ignored: not prepended, `quotedMessage` not cleared, so the quote chip survives the send |
| Image-generation guard | yes | none (`sendCurrentInput` returns before fusion only when `shouldGenerateImage`; an image model selected as a fusion member is not checked) |
| Error surfacing | `StreamingState.Error` only | `StreamingState.Error` **and** snackbar `e.message ?: "Fusion failed"` |
| Completion notification | `appNotificationManager.notifyChatResponseComplete` | none |

**Fix**: Route both through one `launchSend(scope, ...)` helper that handles quote prepending, clearing, notifications and error surfacing identically; move fusion to `applicationScope` or document why it is intentionally cancelled.

**Severity**: medium · **Confidence**: proven

---

## 6. Fusion state has two sources of truth: `isFusionRunning` and `streamingState`

**Files**: `ChatViewModel.kt:1920-1960` — every update sets `isFusionRunning = result.isSynthesizing || result.synthesizedResponse == null` *and* `streamingState = Idle/Starting` from the same expression; the error branch sets `streamingState = Error`, `isFusionRunning = false`. `ChatUiState.kt` carries both. `toggleFusionMode` resets `fusionResult` but not `isFusionRunning`.

**What disagrees**: Nothing forces the two to agree; a future edit to one branch desynchronises them. Disabling fusion mid-run leaves `isFusionRunning = true` until the job finishes.

**Fix**: Derive `isFusionRunning` as a computed property of `fusionResult`/`streamingState`, or drop it.

**Severity**: medium · **Confidence**: suspected (desync path not exercised)

---

## 7. Domain layer imports data, UI and notifications packages

**Files**
- `domain/usecase/SendMessageUseCase.kt:4, 26, 27` → `data.local.preferences.AppPreferences`, `ui.screens.chat.ContextWindowEstimator`, `notifications.ImageGenerationForegroundService`
- `domain/usecase/RegenerateResponseUseCase.kt:14, 15` → same two
- `domain/usecase/GetConversationInsightsUseCase.kt:3-6` → `ConversationDao`, `MessageDao`, Room entities directly (bypasses `domain/repository`)
- `domain/usecase/{CreateConversation,ExtractMemories,GenerateConversationTitle,RunFusion}UseCase.kt` → `AppPreferences`

**What disagrees**: `domain/repository` interfaces exist for exactly this purpose; six use cases skip them. `ContextWindowEstimator` lives under `ui/screens/chat` but is pure logic used by domain.

**Fix**: Add a `SettingsRepository`/`PreferencesRepository` interface in `domain/repository`; move `ContextWindowEstimator` to `domain/util`; give insights a repository method instead of DAOs; wrap the FGS start/stop behind an interface injected into the use cases.

**Severity**: medium · **Confidence**: proven

---

## 8. Streaming-cleanup failure is swallowed, leaving messages stuck in `isStreaming`

**Files**: `domain/usecase/SendMessageUseCase.kt:459-469` — `onCompletion { withContext(NonCancellable) { try { flush(); setMessageStreaming(false); FGS.stop() } catch (_: Exception) {} } }`

**What disagrees**: The comment says this exists "to prevent messages from getting stuck with isStreaming=true forever", but if `setMessageStreaming` throws (DB closed, disk full) the exception is dropped with no log; the badge then stays until the process-start sweep (`MaterialChatApplication.kt:42`). Deliberate best-effort, but un-logged.

**Fix**: `Log.w(TAG, "stream cleanup failed", e)` at minimum; split the three calls so one failure does not skip the FGS stop.

**Severity**: medium · **Confidence**: proven

---

## 9. Ollama ignores the temperature parameter

**Files**: `data/remote/api/ChatApiClient.kt:120, 203, 322` (`temperature: Double = 0.7` params threaded into requests at `:133, 144, 172, 339`) vs `:1126` `OllamaOptions(temperature = 0.7)` hard-coded.

**What disagrees**: Every other provider path forwards `temperature`; the Ollama request builder discards it.

**Fix**: Pass the function's `temperature` into `OllamaOptions`. Hoist `0.7` to one `DEFAULT_TEMPERATURE` constant.

**Severity**: medium · **Confidence**: suspected (did not trace whether the enclosing Ollama function receives `temperature`)

---

## 10. Insights chart bypasses "Beautiful model names"

**Files**: `ui/screens/insights/components/ModelUsageChart.kt:183, 205` (`prettyModelName` = `substringAfterLast('/')`) vs `ui/util/ModelNameParser.kt:152-170` (`formatModelName`, honours `beautifulModelNamesEnabled`) vs `domain/util/ModelIdentity.kt` (`canonicalKey`, alias mapping).

**What disagrees**: Three formatters. Insights shows raw ids (and treats `ox-alpha` and `glm-5.3-flash` as different names despite `ModelIdentity` merging their usage).

**Fix**: Use `ModelNameParser` in the chart, keyed by `ModelIdentity.canonicalKey`.

**Severity**: low · **Confidence**: proven

---

## 11. OkHttp timeout constants duplicated across five builders

**Files**
- `di/NetworkModule.kt:39-42` (`CONNECT/READ/WRITE = 30`, `STREAMING_READ = 120`) — the injected clients
- `data/remote/api/ChatApiClient.kt:1945, 2070-2074` `STREAMING_TIMEOUT_SECONDS = 120`, `defaultClient()` — constructor default, unused under Hilt (`NetworkModule.kt` injects `@StreamingClient`)
- `data/remote/api/ModelListApiClient.kt:337, 395-398` `REQUEST_TIMEOUT_SECONDS = 30`, `defaultClient()` — same, unused under Hilt
- `ChatApiClient.kt:854-857` and `:937-940` — identical 30s/6m/60s/7m image-gen client built inline twice
- `NetworkModule.kt:186-188` DoH client, `data/auth/NativeAuthManager.kt:691-694` — literal 10s

**What disagrees**: Values currently agree (30/120) but are maintained in two places; the `defaultClient()` fallbacks are dead in production and will silently drift.

**Fix**: Remove `defaultClient()` defaults (require injection), extract one `imageGenerationClient` in `ChatApiClient`, and move all timeout constants to `NetworkModule`.

**Severity**: low · **Confidence**: proven

---

## 12. Web-search provider stored as free string with the default literal in four places

**Files**: `AppPreferences.kt:966` (`?: "EXA"`), `ui/screens/settings/SettingsUiState.kt:75`, `SettingsViewModel.kt:1480`, `SettingsScreen.kt:4040-4075` (string compares), while `domain/model/WebSearchMetadata.kt:33` already defines `enum class WebSearchProvider { EXA, SEARXNG, NATIVE }`.

**Fix**: Store/read `WebSearchProvider` via `entries.firstOrNull` like every other enum pref; one `DEFAULT_WEB_SEARCH_PROVIDER`.

**Severity**: low · **Confidence**: proven

---

## 13. Preference writes use three different failure policies

**Files**: `SettingsViewModel.kt:1035-1040, 1053, 1083-1087, 1205, 1220, 1235` — `try { set… } catch { snackbar "Failed to save …" }`; `ui/screens/settings/InteractionSettingsViewModel.kt:51-67` and `ModelAssignmentsViewModel.kt:163-167` — bare `launch { set… }` (uncaught → crash on DataStore IO error); `AssistantVoiceSettingsViewModel.kt:221` — `message.value = "Unable to save all voice settings…"`.

**Fix**: One `savePreference { }` helper on a base ViewModel that surfaces a uniform snackbar.

**Severity**: low · **Confidence**: proven

---

## 14. Toast and Snackbar both used for the same class of error

**Files**: Toasts: `ui/screens/chat/components/MessageBubble.kt:1484, 1498`, `ui/screens/canvas/SmartCanvasScreen.kt:86, 88`, `ui/screens/generatedimages/GeneratedImagesScreen.kt:214, 224`, `ui/components/ImageViewerDialog.kt:124, 133` ("Could not share image", save failures). Everything else (137 sites) uses `*Event.ShowSnackbar`.

**Fix**: Route share/save results through the host screen's snackbar.

**Severity**: low · **Confidence**: proven

---

## 15. Five user-facing error prefixes

`grep -c`: "Failed to" 88, "Could not" 29, "Unable to" 7, "Couldn't" 5, "Error:" 4. No style rule. **Fix**: pick one ("Couldn't …" per Material writing guidance) and sweep. **Severity**: low · **Confidence**: proven

---

## 16. `DEFAULT_FONT_SIZE_SCALE = "Default"` is a leftover from a prior enum representation

**Files**: `AppPreferences.kt:192-193` — `DEFAULT_FONT_SIZE_SCALE = "Default"` is unused; `fontSizeScale` parses the stored string with `toFloatOrNull() ?: 1.0f`. Any install that still holds `"Default"` (or an old enum name) silently resets to 1.0.

**Fix**: Delete the constant; store as `floatPreferencesKey`.

**Severity**: low · **Confidence**: proven

---

## 17. Two first-run flags

**Files**: `AppPreferences.kt:56-57` `FIRST_LAUNCH_COMPLETE` (read/written only in `di/AppInitializer.kt:36, 65`) and `ONBOARDING_COMPLETE` + `onboardingPreferenceSet` (three writers). Both mean "setup done" but neither consults the other, so the initializer can run its first-launch seeding on an install that already finished onboarding (or vice versa after a backup restore).

**Fix**: Document the distinction in KDoc or merge.

**Severity**: low · **Confidence**: suspected

---

## 18. Enum-from-string parsing done three ways

`ReasoningEffort.fromStoredValue` (`domain/model/ReasoningEffort.kt:21-27`, try/`valueOf`), `AppPreferences` theme/palette/bubble (`try { valueOf } catch IllegalArgumentException`), `AssistantVoiceSettings`/`AssistantContinuation` (`entries.firstOrNull`). Same semantics, three idioms. **Fix**: one `inline fun <reified E: Enum<E>> String?.toEnumOrNull()`. **Severity**: low · **Confidence**: proven

---

## Not examined

- `ArenaViewModel`, `RegenerateResponseUseCase` body, `RunFusionUseCase` body, repository implementations beyond `UpdateManager`/`WebSearchRepositoryImpl`/`MonetizationManager`, `EncryptedPreferences`, backup/restore, mind-map/canvas/workflows screens, `SettingsViewModel` lines 400-1000.
- Whether `rememberLastModel` actually gates `lastUsedModel` in `CreateConversationUseCase.kt:99`.
- `AppNotificationManager.canPostNotifications()` body (only confirmed it does not read `AppPreferences`).
- Haptics: the global ∧ per-category combination is done in `MainActivity.kt:173-174` and again in `SwipeToDeleteBox.kt:90`; harmless double-AND, not listed.

## Could not verify without running the app

- Finding 1(a): that the first-install UI really displays the title toggle as off while on-device titles run (follows from the defaults, not observed).
- Finding 2: that notifications are visibly posted with the switch off on a device with POST_NOTIFICATIONS granted.
- Finding 3: that the voice session still launches with the switch off.
- Finding 6: an actual desynchronised fusion state.
- Finding 9: whether `temperature` reaches the Ollama builder's enclosing function at all.
