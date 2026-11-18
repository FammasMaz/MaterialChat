# Long-chat disclosure tap diagnosis

## Verdict

The real-phone failure is not proven fixed. No production gesture replacement was made: the deliverable is an opt-in debug recorder, six additional full-MessageList touch-input acceptance tests, and this evidence report. No device or emulator was started and nothing was pushed.

Worktree: `/Users/fammasmaz/Downloads/MaterialChat/.worktrees/diag-sol`, branch `diag/diag-sol`, based on `6686844` (v2.40.0). The local SDK configuration is `sdk.dir=/opt/homebrew/share/android-commandlinetools`.

## Completed and verified

- `db403a3` — isolate the opt-in bubble pointer recorder from release input. All three requested compilation targets passed together: `:app:compileGithubDebugKotlin`, `:app:compileGithubReleaseKotlin`, and `:app:compileGithubDebugAndroidTestKotlin`.
- `6e68e65` — add six explicit root-injected touch tests through the production MessageList. `:app:compileGithubDebugAndroidTestKotlin` passed after the addition. Bytecode inspection confirms all six test methods return `void`.
- Full `:app:testGithubDebugUnitTest` passed. The XML contains **82 suites, 548 tests, 0 failures, 0 errors, 0 skipped**; suite timestamps are 2026-11-18T14:48:37 through 2026-11-18T14:48:41 as recorded by the runner. No unit-test fixes were needed, so this step required no additional code commit.

Compilation/test logs are local, ignored build outputs: `build/diag-step1-compile.log`, `build/diag-step2-compile.log`, and `build/diag-step3-unit-tests.log`. XML evidence is under `app/build/test-results/testGithubDebugUnitTest/`. These results establish compilation and JVM behavior, not successful phone interaction.

## Ranked causes

The ranking below is investigative priority, not a confirmed root-cause finding. Source-backed mechanisms are separated from the unverified connection to the reported phone failure.

### 1. Descendant hit-path loss or cancellation, leaving the bubble's combinedClickable eligible

**Top hypothesis; the phone-specific trigger is unproven.** The reported whole-bubble ripple is consistent with the bubble receiving a press while a disclosure does not successfully claim the gesture. It is not itself proof of a bounds defect, a particular consumer, or even a successful parent click.

Application evidence:

- `app/src/main/java/com/materialchat/ui/screens/chat/components/MessageBubble.kt:411-450`: arrival/settle graphics layer, animated content size, and assistant bookmark `combinedClickable` surround the controls.
- `MessageBubble.kt:456-480`: the fallback router sits inside that surface and can route only an up that reaches it, remains unconsumed, and stays within slop. It cannot repair a missing hit path or a cancelled event stream.
- `MessageBubble.kt:1146-1167` and `1430-1449`: thinking and memory controls have their own clickable/fallback layers.
- `app/src/main/java/com/materialchat/ui/screens/chat/ChatScreen.kt:1052-1053,1132`: the list uses stable message-ID keys, role content types, and animateItem.
- `app/src/main/java/com/materialchat/ui/screens/chat/components/BubbleTapRouter.kt:32-43,62-64`: routing uses registered root-space rectangles populated by global-position callbacks.

Pinned framework evidence:

- Foundation 1.11.0-alpha06 `Clickable.kt`, `ClickableNode.onPointerEvent` around lines 880-1000: an unconsumed Main-pass down starts click tracking; the up is consumed before onClick; consumption or bounds exit can cancel tracking.
- The same file, `CombinedClickableNode.createPointerInputNodeIfNeeded` / `onPointerEvent` around lines 1111-1179: combinedClickable has flag-selected suspending and non-suspending implementations. Both require a qualifying down; the latter explicitly rejects a consumed down. The suspending implementation uses detectTapGestures.
- Foundation `TapGestureDetector.kt:131-175,311-314`: detectTapGestures consumes the first accepted down and waits for up or cancellation; awaitFirstDown normally requires an unconsumed Main-pass down.
- UI 1.11.0-alpha06 `NodeCoordinator.kt:676-729,917-929,1201-1213,1325-1342`: hit-testing checks clipping and local bounds and converts through inverse layer transforms. A graphicsLayer alone does not establish that drawing and hit-testing disagree.
- Animation 1.11.0-alpha06 `AnimationModifier.kt:103-113,195-229`: animateContentSize adds clipToBounds, reports an animated size, and places its measured child within that size. Its existence is a concrete clipping mechanism, not evidence that a visible settled chip is outside its hit bounds.

Why long chats/phone only is plausible: long items and scrolling exercise more clipping, asynchronous WebView measurement, item placement, and frame timing than the short-text fixtures; the phone's rendering/input schedule differs. None of these differences has been measured on the failing phone.

**Confirm/refute:** capture one failed tap. Compare the same pointer ID/time at `list-before-scrollable`, `item-after-animateItem`, `surface-before-layers`, `inside-surface`, and the control stages. A parent down with no control down narrows the failure to hit-path eligibility, placement, or overlap; it does not uniquely identify which. Control down followed by cancellation or newly consumed changes instead points to gesture arbitration. A complete control click/toggle refutes failure-to-deliver for that tap. Retain the parent combinedClickable as a live suspect: emulator-only removal was never a phone A/B test.

### 2. Scroll or overscroll consumes the down before the child

**Mechanism source-backed; insufficient as the sole explanation after the shipped mitigations.**

- `ChatScreen.kt:775-790,810-829` contains streaming and post-stream scroll paths; `676-681` observes nested scrolling without consuming it.
- `app/src/main/java/com/materialchat/ui/screens/chat/components/ChatListFling.kt:20-68` already cuts off a slow fling tail.
- `app/src/main/java/com/materialchat/ui/components/TapThroughScroll.kt` already observes a consumed down in Initial, uses a stable pointer handler and current callback, and consumes a qualifying up to avoid double delivery.
- Foundation `Scrollable.kt:406-407,906-907`: immediate drag can be requested by either isScrollInProgress or overscrollEffect.isInProgress. Thus a false list scrolling flag alone does not exclude overscroll.
- Foundation `Draggable.kt:747-800`, processInitialDownState: when immediate drag is selected, the down is consumed in Initial before descendants process Main.

**Confirm/refute:** correlate LIST_SCROLL with control Initial/Main/Final consumed flags and router events. A consumed down seen by the control establishes upstream consumption, but the existing fallback should still be examined through up. An unconsumed control down during failure refutes this particular down-consumption explanation. The recorder does not directly expose overscroll's private state or identify every framework consumer.

### 3. Disclosure state is reset by disposal/reuse or a thinking-state key change

**Possible lifecycle failure, not established by ordinary list re-emission.**

- `MessageBubble.kt:1302`: memory expanded state uses unkeyed remember, so disposal/recreation can reset it.
- `MessageBubble.kt:1099-1108`: thinking expanded state is keyed by thinking phase, streaming, and alwaysShowThinking; changing those keys deliberately recreates its state.
- `app/src/main/java/com/materialchat/ui/screens/chat/ChatViewModel.kt:270` rebuilds MessageUiItem values from observed messages. This is not proof of composition disposal.
- `ChatScreen.kt:1052-1053` uses message.id keys rather than MessageUiItem object identity.
- Foundation `lazy/layout/LazyLayoutItemContentFactory.kt`, getContent/createContentLambda/SkippableItem: content is cached by key/index/contentType and saveable state is keyed by item key. `LazyLayout.kt:116-137,165-166` wires subcomposition, prefetch, and reuse compatibility by content type. Re-emitting equivalent objects does not by itself imply a state reset.
- `app/src/main/java/com/materialchat/ui/screens/chat/components/MessageArrival.kt:25-43` tracks fresh IDs and hands arrival eligibility out once; mere recomposition is not supposed to restart arrival.

**Confirm/refute:** look for MEMORY_TOGGLE/THINKING_TOGGLE true followed by DISCLOSURE_COMMIT false, DISCLOSURE_DISPOSE/ENTER, COMPOSITION_DISPOSE/ENTER, or node detach/attach. Repeated RECOMPOSITION_COMMIT with stable disclosure state is not evidence of a reset. A key change can reset thinking without disposal. Session-local bubble ordinals do not identify the same persisted message across disposal, so correlate the visible location, timings, and list movement; the trace cannot conclusively establish persisted identity continuity.

### 4. Math WebView interop captures a gesture or changes measurement

**Interop mechanism source-backed; attribution to unrelated chip taps remains hypothetical.**

- `app/src/main/java/com/materialchat/ui/components/MarkdownText.kt:1021-1058`: math WebViews claim only a sufficiently horizontal drag that they can scroll, request disallow-intercept when claimed, and release it on up/cancel. The new trace call is at 1033.
- UI `AndroidViewHolder.android.kt:297-299` forwards requestDisallowInterceptTouchEvent to its interop hook and parent.
- UI `PointerInteropFilter.android.kt:120-122,148-167,231-281,298-302` links that hook, changes dispatch/consumption order, and resets disallow-intercept for a new stream. It does not establish permanent interception of later unrelated taps.
- UI `AndroidComposeView.android.kt:2602-2630,2847-2865` sends MotionEvents into Compose, requests parent non-interception when movement is consumed, and has cancellation paths.

**Confirm/refute:** reproduce separately in a long plain-text conversation and a long reply containing math/tables. Correlate WebView MOTION timestamps and coordinates with the failed tap and Compose consumption/cancellation. A failed plain-text tap with no interop event weakens direct WebView interception. `claimed` is logged before the listener handles the current event, so the move that first claims a drag is reflected by subsequent records; the log does not directly record every parent interception request or WebView height update.

### 5. Haptic work prevents the toggle, or multiple delivery paths toggle twice

**Real code ordering; no observed failure evidence.**

- `MessageBubble.kt:1148-1165,1330-1351` records click entry, performs haptics, then changes expanded state and records the toggle.
- `app/src/main/java/com/materialchat/ui/components/HapticFeedbackManager.kt:281-319` checks vibrator support and invokes platform haptics before returning. Phone hardware exercises this path; the touch fixtures disable haptics. No throw or block has been observed, and long-chat specificity is unexplained.
- Multiple fallback paths exist, but their consumed-up guards are intended to prevent double toggles. Their presence alone is not proof of duplication.

**Confirm/refute:** repeated CLICK without its following TOGGLE narrows the interval to haptics/callback interruption, but cannot alone prove an exception. Two TOGGLE records for one pointer sequence, true then false, support duplicate delivery. Repeat once with Chat controls haptics disabled; a consistent change would justify a focused haptic investigation, not an immediate diagnosis. SmoothStreamingText.kt and word-boundary haptics were not edited.

## Negative findings and limits of earlier claims

- The earlier claim that Android performClick bypasses touch dispatch was wrong. Pinned `ui-test` `Actions.android.kt:22-23` implements it as `performTouchInput { click() }`. Existing emulator results remain legitimate touch results; they still do not reproduce this phone failure.
- ExpressiveFastScrollBar references were found in ConversationsScreen, not MessageList/ChatScreen. No long-chat-only overlay from that component was established.
- Stable ID keys and the arrival tracker argue against treating every Room emission as disposal or arrival. Lifecycle evidence is needed.
- No test, testTag, contentDescription, scripts content, or workflow content was weakened or deleted.

## What changed

### Recorder and release isolation — db403a3

- `app/src/debug/java/com/materialchat/ui/diagnostics/BubbleInputDiagnostics.kt:50-190`: settings-gated, session-only recorder; bubble/list lifecycle, three-pass pointer probes, local/root positions, clipped bounds, state commits, animation values, WebView events, and share action.
- `app/src/debug/java/com/materialchat/ui/diagnostics/TapTraceBuffer.kt:4-42`: synchronized bounded buffer, default 1,048,576 characters, row eviction with an explicit dropped_rows count, and field escaping. Header overhead is additional; this is a character budget, not an exact byte/RAM budget.
- `app/src/testDebug/java/com/materialchat/ui/diagnostics/TapTraceBufferTest.kt`: three JVM cases cover eviction, clearing/field escaping, and oversize rows.
- `app/src/release/java/com/materialchat/ui/diagnostics/BubbleInputDiagnostics.kt:10-26`: tracked no-op API bridge only; no recorder, mutable recorder state, pointer node, file writer, or share UI. The pre-existing ignored stub would not have survived a clean checkout; this commit tracks it explicitly. Compiled release diagnostic output contains only BubbleInputTrace and the bridge's top-level class, not the debug recorder/probe/buffer classes.
- `ChatScreen.kt:1006-1036`: list lifecycle/scroll probes, attached only when tracing is enabled.
- `MessageBubble.kt:202-275,316-478,1109-1167,1304-1449`: optional lifecycle/layer/pointer/action probes. Existing gesture eligibility, consumption, callback order, and toggles are retained; the router's equivalent boolean result is additionally recorded.
- `MarkdownText.kt:1033`: optional WebView listener entry trace.
- `app/src/main/java/com/materialchat/ui/screens/settings/InteractionSettingsScreen.kt:88-90`: debug-build-only diagnostics settings item.

When disabled, there are no diagnostic pointer nodes, diagnostic collection coroutines, trace file writes, or event-detail formatting on null-safe record calls. The release modifier bridge returns its receiver unchanged, so it adds no hit-testing nodes. Shared-code no-op calls and a nullable rememberUpdatedState remain; this is not a claim of literally zero CPU instructions or allocations. When enabled, probes are non-consuming pointer nodes, but their timing/participation could mask a race; enabled-versus-disabled behavior must be compared on the phone.

### Full-list touch coverage — 6e68e65

`app/src/androidTest/java/com/materialchat/ui/screens/chat/MessageListDisclosureTouchTest.kt:48-212` adds six tests: memory chip and thinking header, each still, after a real swipe/fling settles, and during a deliberately slow programmatic scroll. All compose the production MessageList with 60 keyed messages, bookmark callbacks, swipe-to-quote callbacks, action rows, animateItem, and chat fling behavior. Semantics locates/asserts a fully visible control; down/up events are injected at root coordinates, not by invoking a semantics click action. Tests assert the exact tapped node's expanded description; the memory cases also assert displayed panel content.

The tests compiled and their six public methods were checked as void. They were not executed. They do not reproduce Room flow, full ChatContent auto-scroll/nested-scroll wiring, real long markdown/WebViews, attachments, phone haptics, or the user's display configuration, and are not claimed to fail on the current phone-only mechanism.

## Exact phone capture procedure

1. Use a debug build containing db403a3 or later. The normal release has no recorder UI. This work did not package, distribute, or install a debug APK; obtaining that build remains necessary. Do not uninstall the existing app or discard chat data to install it; signing/install compatibility was not tested here.
2. In Settings, open **Interaction & Motion** (the entry in the appearance controls), then **Bubble tap diagnostics**.
3. Turn **Record bubble input** on. It starts a fresh in-memory log; leave it on for this capture. No adb is needed.
4. Immediately return to the affected long conversation. First let the list stop, then tap one visible memory chip once and one thinking header once. Note which failed. If possible, repeat immediately after a fling, then compare one short conversation. Keep this short because the bounded log evicts its oldest rows.
5. Return to **Settings → Interaction & Motion → Bubble tap diagnostics** and press **Share input trace**. Choose the app/contact used to return the text attachment, `bubble-input-trace.txt`. Share before turning recording off or killing the process; disabling removes probes and can itself add detach events. Re-enabling clears the old log.
6. State the order of taps, whether the list was visibly moving, whether the affected reply contained math/tables, whether recording changed the symptom, and whether this means many messages, one very long reply, or both. Include phone Android version if known; version/API/model are also in the trace header.
7. Turn recording off afterward. The trace contains touch coordinates, message-content lengths, list positions, UI state and device metadata, but no chat text, memory labels, raw persisted message IDs, HTML, or URLs. Review it before sharing. Sharing writes a cache file which can remain until overwritten or cache cleanup.

## Reading the trace

| Observed outcome for the failed tap | Supported conclusion | What it does not prove |
| --- | --- | --- |
| List and surface see down, but no inside-surface/control down | Descendant hit-path or placement/overlap problem is favored | Which clip/layer/node caused it; a recording alone does not show drawn pixels |
| Control sees down, then POINTER_CANCEL/detach before up | Gesture/lifecycle interruption occurred | The exact canceller without surrounding consumption/lifecycle records |
| Control down is already consumed in Initial | Upstream Initial-pass consumption occurred | That scrolling, rather than another upstream handler, was uniquely responsible |
| Router gets up with consumed=true | Router deliberately cannot deliver that up | Which consumer won; compare the control probes and click records |
| ROUTER_RESULT routed=false with targets present | Root position matched no registered target at routing time | Whether registration or coordinate conversion was stale without matching bounds |
| CLICK followed by TOGGLE true and DISCLOSURE_COMMIT true | Click delivery and expanded state succeeded | That expanded content was visibly drawn and unclipped |
| CLICK without the following TOGGLE | Execution did not reach the post-toggle trace | That haptics threw, rather than stalled/interrupted execution or incomplete evidence |
| Two opposite TOGGLE records for one tap | Duplicate action delivery is favored | Which fallback duplicated it unless the path labels identify it |
| TOGGLE true then disposal/re-entry/reset | State loss is favored | Stable persisted identity continuity; IDs are deliberately omitted |
| WebView MOTION events overlap the failed interaction | Interop participated in that event stream | That it intercepted the chip or that the prior event's claim persisted forever |
| Only RECOMPOSITION_COMMIT repeats, no reset | Recomposition happened | That recomposition caused the failure |

Use pointer IDs, event time/uptime and stages together, not just the presence of one event name. A nonzero dropped_rows count means missing earlier evidence may have been evicted. No down at any probe can also mean the relevant item was not traced, recording was off, or the event never reached this Compose tree. The probes show selected instrumented stages, not a complete framework hit-test tree; animateItem's private animation state is not directly logged.

## Pinned source provenance

Actual extracted sources were read under `build/diag-compose/`, not inferred from newer documentation. Cached resolved versions checked: foundation-android, ui-android and animation-android **1.11.0-alpha06**; material3-android **1.5.0-alpha15**. Source jars are ignored local evidence, not committed dependencies.

Canonical retrieval pattern: `https://dl.google.com/dl/android/maven2/<group path>/<artifact>/<version>/<artifact>-<version>-sources.jar`.

| Local source jar | SHA-256 |
| --- | --- |
| foundation-android-sources.jar | 00882350885108902471da94bd6fe0d7bcb9d39b4d9a65fb96a4bfca80523f26 |
| ui-android-sources.jar | ca8d0996c8703b4e67ee94673aa8f971df444b9b9f6d8eb0a517e5f509f9f8dd |
| animation-android-sources.jar | 323c8a5206cce6f576d01bee2bc3bf95835c8a54ec31dc3d061339a0968ea8d7 |
| material3-sources.jar | 62437af7753b5fb158a3ed2f60014d3b6b913144d43cd26c2163cf292cde3263 |
| ui-test-android-sources.jar | ade5c97630e82f3e943e2af3433fb7e4c6b52be9f24939428d1eec406d043a3d |

Material3 `Surface.kt:198-236` appends clickable after the caller's modifier; `98-130` shows the non-clickable surface's empty pointerInput node. Foundation `lazy/layout/LazyLayoutItemAnimation.kt:120-192` implements placement delta animation and layer-alpha appearance. These mechanisms were inspected, but no defect in them was demonstrated.

## Not verified

- The original failing phone gesture, rendered ripple, chip hit path, table drag, visual expansion, or a root-cause fix.
- Execution of the six new Android tests, or enabled-recorder transparency under actual touch input.
- Debug APK packaging/install/signing compatibility, navigation and share-sheet delivery on the phone.
- A release APK build/minification or device behavior: release Kotlin compilation and the no-recorder class inspection passed, which is narrower.
- The wider device/release regression matrix for chat, models, memory, streaming termination, titles, equations and long messages. Production gesture behavior and SmoothStreamingText.kt were preserved rather than a speculative behavioral fix being shipped.

Follow-up should start from one failed-phone trace, not another emulator-only gesture replacement. The discriminating questions are: many messages versus one long reply, failure after complete rest versus only around scrolling, presence of math/WebViews, and whether enabled tracing or disabled Chat controls haptics changes the result.
