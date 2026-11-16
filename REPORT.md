# Pass 1 (chat) — report

Branch `feat/p1-chat`, based on main `476349a`. Six commits, one per part, all compiled with
`:app:compileGithubDebugKotlin` + `:app:compileGithubDebugAndroidTestKotlin`; unit suite
`:app:testGithubDebugUnitTest` = 517 tests, 0 failures, 0 errors (75 result files).
No emulator, no push, no release workflow. `app/src/main/java/com/materialchat/assistant/**` untouched.
`SmoothStreamingText.kt` untouched. No italics added.

## Commits

| Commit | Part |
|---|---|
| `f987b3a` | 1 — tap-through bug in long chats |
| `781ec09` | 2 — action row as one connected M3E group, compresses when stats expand |
| `4258d75` | 3 — composer send button shape-morph |
| `64de2f2` | 6 — header keeps one tone while scrolling, no corner artifact |
| `140a0b2` | 7 — native predictive back |
| (this)    | REPORT.md |

## Part 1 — memory chips / tables / thinking header dead in long chats

**Root cause (read from Compose foundation 1.11.0-alpha06 source, not guessed).**
`DragGestureNode.processInitialDownState` (foundation `gestures/Draggable.kt`) consumes the
pointer-down in the **Initial** pass whenever `startDragImmediately()` is true; for the
LazyColumn scrollable that is `ScrollingLogic.shouldScrollImmediately()` ==
`scrollableState.isScrollInProgress || overscroll.isInProgress`. Every `clickable` /
`combinedClickable` / clickable `Surface` under the list requires an *unconsumed* down
(`Clickable.kt`: `isChangedToDown(requireUnconsumed = true)`), so while the list counts as
scrolling every control inside the bubbles is dead and the touch only "stops the fling". The
inner table `horizontalScroll` loses too: the outer scrollable consumes its moves and the inner
one cancels in the Final pass.

Why only long chats: a short chat cannot scroll, so `isScrollInProgress` is never true. A long
chat is "in progress" (a) through the whole sub-pixel tail of the default spline fling (seconds
of a visibly still list), (b) during overscroll spring-back, and (c) during the chat's own
`animateScrollBy` after streaming ends — which was called even when `canScrollForward` was
false, holding the session open for the full spring duration with zero movement.

**Fix (three layers):**
- `ui/components/TapThroughScroll.kt` (new): `Modifier.tapThroughScroll(onClick)`. Watches for a
  down that arrives already consumed, tracks it inside touch slop, fires `onClick` on the up.
  Unconsumed downs are left to the normal click path, so nothing double-fires. Applied to
  `MemoryIndicatorChip` (`MessageBubble.kt` ~1245), the thinking header row (~1001) and the
  action-row containers (`MessageActions.kt`).
- `ui/screens/chat/components/ChatListFling.kt` (new): `SettlingFlingBehavior` ends the decay
  once |velocity| < 24dp/s (sub-pixel per frame at any density) instead of trailing off, so the
  scroll session closes while the list already looks still. Hitting the end of the list still
  returns the remaining velocity so stretch overscroll is unchanged. Wired in `MessageList`
  (`ChatScreen.kt` ~976).
- `ChatScreen.kt` ~778: the post-stream `animateScrollBy` only runs when
  `listState.canScrollForward`.

**Tests:**
- `androidTest/.../MessageBubbleTapThroughScrollTest.kt` (new): 60-item LazyColumn of assistant
  bubbles with memory metadata; a slow programmatic `animateScrollBy` keeps the session open
  (asserts `isScrollInProgress` as a precondition), clicks "Memory saved", asserts the disclosure
  expanded. Fails before the fix (the Surface's clickable never sees the down), passes after. A
  second case covers the still list. **Not executed here** — device tests run on GitHub.
- `test/.../ChatListFlingTest.kt` (new, JVM, 3 tests): settle rule and threshold.

## Part 2 — action row (`MessageActions.kt`, rewritten)

- `ButtonGroup`/`ButtonGroupDefaults` exist in material3 1.5.0-alpha15 (verified in the AAR), but
  the row needs mixed tones, a long-press bookmark and an expanding stats pill, so the connected
  group is built directly: one `Row`, 2dp joins, per-position corners (outer 20dp, inner 6dp;
  `messageActionCornerRadii(index, count)`), pressed corner morph + 0.92 scale on
  `ExpressiveMotion.Spatial.shapeMorph()/scale()`.
- One tone family: standard = `surfaceContainerHigh`, selected = `secondaryContainer`,
  regenerate = `primaryContainer` *inside the same group* (deliberate single emphasis, closes the
  group) — no stray different-coloured button.
- Stats expansion: `Row` with the stats pill at `weight(1f, fill = false)` and ellipsis; sibling
  icon controls shrink from 40dp to `MessageActionCompactWidth` (32dp) while expanded
  (`messageActionSegmentWidth`). Touch height stays 48dp (`MessageActionRowTouchHeight`). No
  `FlowRow`.
- All content descriptions kept ("Copy message", "Branch conversation", "Redo with different
  model", "Regenerate response", "Response speed"/"Hide response speed", "Bookmark message"/
  "Remove bookmark").
- Tests: `MessageActionsLayoutTest.kt` (new, 7) — corner rules, compact width, 360dp budget with
  the longest stats string (`"123 tok/s · ttft 1.2s · 12.3s · 45 tok"` style) leaves ≥ 40dp for
  the pill; existing `MessageActionRowTest` (5) still green.

## Part 3 — send button (`ComposerSendButton.kt`, rewritten)

- Shape track via `androidx.graphics.shapes.Morph` on `MaterialShapes` polygons (verified in the
  AAR and on the classpath): `cookie4Sided` idle → `circle` ready → rounded `square` streaming,
  animated on `ExpressiveMotion.Spatial.shapeMorph()`; press scale/morph on the spatial spring;
  48dp, aligned with the 48dp field. Calm stop glyph kept; content descriptions unchanged
  (`chat_send_message`, `chat_send_message_disabled`, `chat_stop_generating`).
- Test: `ComposerSendShapeTest.kt` (new, 3) — state → shape-track mapping.

## Part 6 — header colour shift + corner artifact (`ChatTopBar.kt`, `ChatHeaderStatus.kt`)

**What was changing and why:** `ChatTopBar.kt` ~156 computed
`contentScrolledUnder = scrollBehavior.state.contentOffset < -1f` and retargeted
`containerColor` to `surfaceContainerHigh` (rest: `surfaceContainer`). The header `Surface` is
cut with `CustomShapes.TopAppBar` (0/0/24/24dp) over a `Scaffold` whose container is the same
`surfaceContainer`; the moment the two tones diverged the bar's 24dp bottom corners became
visible — that is the "rounded corners appearing" the user saw, and the tonal lift is the colour
shift. The fold itself (`ChatHeaderCollapseMachine`) never touched colour.

**Fix:** `resolveChatHeaderTone(isEphemeral, contentScrolledUnder, isCollapsed)` returns one tone
per conversation kind and ignores scroll/fold; the bar stays `surfaceContainer` (or
`tertiaryContainer` for temporary chats) in every state, same as the scaffold behind it. Fold
animation unchanged. Test: `ChatHeaderToneTest.kt` (new, 2) asserts the tone is invariant across
all four scrolled/collapsed combinations.

## Part 7 — predictive back (`ChatScreen.kt`, `ChatBackHandling.kt`)

- Manifest: `android:enableOnBackInvokedCallback="true"` already on `<application>`; neither
  activity overrides it. Confirmed.
- The chat had an always-enabled `BackHandler { viewModel.navigateBack() }`. Any always-enabled
  app callback suppresses the system predictive animation, and `navigateBack()` for a normal
  chat only emits `NavigateBack` → `popBackStack()` — it intercepted for nothing.
- Now: `resolveChatBackHandling(isEphemeral)` → `System` for normal chats (no interception; the
  NavHost pops with the native predictive animation) and `InterceptForEphemeralCleanup` for
  temporary chats, which use `PredictiveBackHandler`: gesture progress drives a scale/translate/
  alpha transform on the Scaffold (`chatBackTransform`, matched to the NavHost pop-exit), the
  cleanup (`navigateBack()`: delete, or promote to a normal chat if streaming) runs on commit,
  cancel springs back on `Spatial.default()`.
- Tests: `ChatBackHandlingTest.kt` (new, 6) — decision rule, transform endpoints, clamping/NaN.
- **What still intercepts back and why:** only temporary (ephemeral) chats, for the cleanup above,
  and they do so with `PredictiveBackHandler` animating progress. The three `ModalBottomSheet`s
  (export, redo-model picker, fusion selector) and `AddBookmarkSheet` are material3 modal sheets
  that own their back handling internally (predictive dismiss in 1.5.0-alpha15); the chat adds no
  `BackHandler` for them. `ConversationsScreen` keeps `BackHandler(enabled = isSearchActive)` —
  out of scope for this pass and only enabled while search is open.

## What I could NOT verify without a device

- Part 1: `MessageBubbleTapThroughScrollTest` was compiled but not run; the behaviour chain
  (down consumed in Initial pass → tap-through fires) is confirmed from foundation source, not
  from a device. Table horizontal drag during a fling tail is reasoned (session closes earlier),
  not observed.
- Part 2/3: visual result of the connected group corners/joins and the cookie→circle→square
  morph (including on API 26, where `Morph.toPath` runs on the CPU path — no shader/blur used,
  so no API gate expected).
- Part 6: the absence of the colour shift/corner is derived from the colour path; not screenshot
  verified.
- Part 7: the system predictive-back animation actually playing on an Android 14+ device, and
  the ephemeral-chat gesture-driven transform feel.
