# Diagnosis: taps inside assistant bubbles hit the bubble, not the chip (long chats, real phone only)

Worktree: `.worktrees/diag-fable`, branch `diag/diag-fable`, based on main at v2.40.0 (`6686844`).
Pinned sources read (downloaded from Google Maven, extracted to `/tmp/compose-src`): compose foundation 1.11.0-alpha06 (`Clickable.kt`, `GestureNode.kt`, `ComposeFoundationFlags.kt`, `gestures/Draggable.kt`, `gestures/Scrollable.kt`, `lazy/LazyListMeasuredItem.kt`, `lazy/layout/LazyLayoutItemAnimation.kt`, `lazy/layout/LazyLayoutPrefetchState.kt`), ui 1.11.0-alpha06 (`node/NodeCoordinator.kt`, `node/HitTestResult.kt`, `node/LayoutNode.kt`, `node/NodeChain.kt`, `input/pointer/HitPathTracker.kt`, `input/pointer/PointerInputEventProcessor.kt`, `input/pointer/SuspendingPointerInputFilter.kt`, `input/pointer/PointerInteropFilter.android.kt`, `platform/AndroidComposeView.android.kt`), animation 1.11.0-alpha06 (`AnimationModifier.kt`), material3 1.5.0-alpha15 `Surface.kt`.

## 0. The one hard fact the device gives us

The user sees the WHOLE bubble ripple. Under the pinned sources that is a strong constraint, not a vague symptom:

- The assistant bubble's ripple belongs to `Modifier.combinedClickable(onClick = {}, onDoubleClick = bookmark)` on the bubble `Surface` (`MessageBubble.kt:418` before this change). With `ComposeFoundationFlags.isNonSuspendingPointerInputInCombinedClickableEnabled = true` (`ComposeFoundationFlags.kt:159`) that is `CombinedClickableNode.onPointerEvent` (`Clickable.kt:1146`), which only starts a press when `pointerEvent.isChangedToDown(requireUnconsumed = true)` in the Main pass (`Clickable.kt:1156`), then consumes the down (`:1214`).
- The Main pass is child-first (`HitPathTracker.dispatchMainEventPass`, `HitPathTracker.kt:408-450`: Initial on self, then children, then Main on self). The chip's own `ClickableNode` (`Surface(onClick)` -> `Modifier.clickable`, `Surface.kt:229`; `ClickableNode.onPointerEvent` `Clickable.kt:880-901`) consumes the down at `:929` when it is on the hit path and in its idle state.
- Therefore: bubble ripples => the bubble's node received an UNCONSUMED down in Main => the chip's node either was not on the hit path for that gesture, or was on it but in a state where it does not consume the down. Either way every handler the previous four fixes added below the bubble node (chip `clickable`, `tapThroughScroll`, the 2.39.3 router on the inner Column) sits on the same losing side of that fact. Only a handler on the bubble node itself is guaranteed to see the gesture the bubble is rippling for. That is what this branch changes.

## 1. Ranked hypotheses

### H1 (strongest; confident about the mechanism, cannot prove it is the one firing on the phone): stale press state inside the chip's own `ClickableNode` after LazyColumn item deactivation/reuse

Mechanism, with citations:
- `ClickableNode` keeps `downEvent` (`Clickable.kt:875`). It is cleared only in `handleUpEvent` (`:959`) and `cancelInput` (`:1028`). `CombinedClickableNode` likewise (`:1323`, `:1444`).
- `AbstractClickableNode.onDetach` (`Clickable.kt:1820-1833`) disposes interactions, indication and the gesture node; it does NOT clear `downEvent`. `CombinedClickableNode.onReset` (`:1630`) only resets key-press state. `ClickableNode` has no `onReset`.
- When a LazyColumn item scrolls out it is deactivated for reuse: `LayoutNode.onDeactivate` (`LayoutNode.kt:1486`) -> `resetModifierState` (`:966`) -> `NodeChain.resetState` (`NodeChain.kt:214`: `onReset`, detach lifecycle, mark detached). On reuse `onReuse` (`LayoutNode.kt:1456`) re-attaches the SAME `Modifier.Node` instances. So a `ClickableNode` that was mid-gesture when its item left the viewport comes back with `downEvent != null`.
- A detached node receives no cancel from the pointer pipeline: `HitPathTracker.Node.dispatchIfNeeded` (`HitPathTracker.kt:644-660`) silently no-ops for a detached or unplaced node, and `removeInvalidPointerIdsAndChanges` (`:394`) prunes it from the hit path on the next event. `dispatchCancel` (`:669`) only runs on `processCancel` (ACTION_CANCEL / device change, `AndroidComposeView.android.kt:2700`, `:2862`) or `removePointerInputModifierNode` (`:315`). NOT traced in this session: whether the owner calls `removePointerInputModifierNode` for every node that detaches; this is the one unverified link in H1's chain.
- What a stale `downEvent` does on the next real tap on that chip: Main pass, `downEvent != null` -> else branch (`:891`), not all-up -> `handleNonUpEventIfNeeded` (`:971`): the down is unconsumed and in bounds, so nothing happens and, crucially, the chip does NOT consume the down. The bubble's `CombinedClickableNode` then takes the unconsumed down (`:1156`) and consumes it -> press -> ripple on the bubble. In the Final pass the chip runs `checkForCancellation` (`:993`): the down is now consumed and `it != downEvent` (the stale one is a different object) -> `cancelInput` -> `downEvent = null`, no click. On the up only the bubble fires (`onClick = {}` after `doubleTapTimeoutMillis`, `:1310-1318`). Net: bubble ripples, chip does nothing, and the chip is healthy again for the NEXT tap (which matches the user never saying it is permanently dead, only that taps are lost).
- Why long chats only: the precondition is a gesture that started on a chip and ended while that item was deactivated (finger down on a chip while a fling carries it off-screen, or the auto-scroll at stream end moving the item out from under a touch). A short chat never scrolls an item out of the viewport, so it cannot reach this state.
- Why the emulators are clean: `MessageListMemoryChipTest` taps a displayed chip whose node has never been mid-gesture while deactivated; it never creates the precondition.
- What H1 does NOT explain on its own: the 2.39.3 router on the inner Column should have delivered the tap under H1 (it observed an unconsumed up in Main before the bubble consumed it). So if H1 is the whole story, 2.39.3 should have worked. Either its `boundsInRoot()` (clipped, `LayoutCoordinates.kt:190` -> `localBoundingBoxOf(clipBounds = true)`) did not contain the tap (see H2), or the user was still on a build without it. This is the first thing to ask the user (section 5).
- Falsify on device: with the tracer-style logging described in section 4, a lost tap under H1 shows the chip node receiving the down in Main with `downEvent != null`, no consumption, then cancel in Final. Falsify on emulator: an androidTest that presses a chip, scrolls it out of the viewport with `scrollToItem` while the pointer is still down, releases, scrolls back and taps the chip; H1 predicts the first tap after that is lost.

### H2 (plausible; guessing at the specific trigger): the chip's node is not on the hit path at all because its layout bounds do not match where it is drawn

- Hit testing is pure layout: `NodeCoordinator.hitTest` (`NodeCoordinator.kt:676-734`) uses `withinLayerBounds` (`:1325`, only clipping layers) and `isPointerInBounds` (`:1337`, measured size). `animateContentSize` is `clipToBounds() then SizeAnimationModifierElement` (`AnimationModifier.kt:112-113`), so the bubble has a clipping layer whose size lags the content while the spring (stiffness 520) is running.
- Production differs from the hosted test in one structural way that touches this: `MainActivity.kt:301` wraps the NavHost in `SharedTransitionLayout` (a `LookaheadScope`). Inside a LookaheadScope `SizeAnimationModifierNode.measure` (`AnimationModifier.kt:195-220`) measures with lookahead constraints and animates to `lookaheadSize`, and `LazyListMeasuredItem.place` (`LazyListMeasuredItem.kt:193-251`) places items at `lookaheadOffset + placementDelta`. The hosted `MessageListMemoryChipTest` composes `MessageList` with no LookaheadScope, so none of these branches run there. I could not show a concrete bounds mismatch from the sources alone; this is the strongest untested structural difference between phone and emulator.
- Why long chats: more placement animations in flight (`animateItem` placementSpec on every non-streaming item, `ChatScreen.kt:1116`), bigger `animateContentSize` retargets when the list re-emits.
- Falsify on emulator cheaply: wrap the existing `MessageListMemoryChipTest` content in `SharedTransitionLayout` and rerun on API 26/35. If it goes red, H2 is live and the fix in section 2 (unclipped bounds, routing from the bubble node) is exactly what covers it.

### H3 (possible; moderately confident it contributes to table scrolling, less to chips): the list consumes the down in the Initial pass while a scroll session is open

- `DragGestureNode.processInitialDownState` (`Draggable.kt:747-800`) consumes the down in Initial when `startDragImmediately()` (`Scrollable.kt:406` -> `shouldScrollImmediately`, `:907`: `isScrollInProgress || overscroll.isInProgress`). `tapThroughScroll` (`TapThroughScroll.kt`) already covers this path and the hosted tests prove it works. The settling fling (`ChatListFling.kt`) shrinks the window but overscroll (`isInProgress` = any edge effect distance != 0, `AndroidOverscroll.android.kt:758`) and the stream-end `animateScrollBy` (`ChatScreen.kt:821`) still open it. This is the mechanism already shipped against; it cannot by itself produce the ripple on the bubble (the bubble would see a consumed down too), so it is not the ripple case.

### H4 (low; checked and mostly ruled out): toggle fires but state is lost

- `expanded` is `remember { mutableStateOf(false) }` in `MemoryDisclosure` (`MessageBubble.kt:1249`), unkeyed. It is reset only when the item is deactivated/reused (scrolled out) or when `MemoryDisclosure` leaves the composition. The list is keyed on `message.id` (`ChatScreen.kt:1036`) and `ChatViewModel` re-emits the same ids, so a UI interaction does not re-key the item. `AnimatedContent(targetState = working)` (`MessageBubble.kt:487`) is false for a finished message and does not flip on tap. I found nothing that re-creates a visible bubble on tap. The arrival animation runs once per id and only within 1.5 s of sync (`MessageArrival.kt`), so it is not the 'blink'. The blink the user describes is the bubble ripple from H1/H2.

### H5 (ruled out for chips): Markdown math WebView interop

- `createMathWebView` (`MarkdownText.kt:1030-1054`) only calls `requestDisallowInterceptTouchEvent(true)` for a real horizontal drag the WebView can scroll; the interop filter then consumes in Initial (`PointerInteropFilter.android.kt:233-255`). That affects drags that start on a math cell, not a tap on a chip outside the WebView. It stays a suspect for 'tables not scrollable' only when the table contains math.

### Also checked, not implicated
- `ExpressiveFastScrollBar` is only mounted in `ConversationsScreen.kt` (`:1183`, `:1267`), never over the chat list.
- `BookmarkBurstAnimation` overlay (`MessageBubble.kt`, after the Surface) has no pointer-input modifiers (`BookmarkBurstAnimation.kt:105` only `size(48.dp)`), so hit-testing falls through it (`LayoutNodeDrawScope`/`hitTestChild`, `NodeCoordinator.kt:185-236`: a child without hits does not stop the sibling loop).
- `HapticFeedbackManager.perform` (`HapticFeedbackManager.kt:281`) returns early when disabled or no vibrator and otherwise calls `Vibrator.vibrate`; nothing there can block or throw before the toggle in a way the chevron would show.
- `pointerInput(Unit)` restart: `SuspendPointerInputElement.update` (`SuspendingPointerInputFilter.kt:619-670`) only resets on key change or handler class change, so the `rememberUpdatedState` lambdas in `tapThroughScroll`/the router do not restart mid-gesture. The old bubble router was keyed on `tapRouter` (stable); fine too.
- `isPausableCompositionInPrefetchEnabled = true` (`ComposeFoundationFlags.kt:83`) was temporarily disabled upstream for a deactivated-node crash (`fb2dac3`), then re-enabled; prefetched-but-unplaced items are skipped by hit testing (`child.isPlaced`, `NodeCoordinator.kt:214`) and by dispatch (`dispatchIfNeeded`). Not a tap-loss path on its own.

## 2. What I changed (commit `9ca6251`)

Minimal change: last-resort routing now runs on the one node proven to receive the gesture, ignores consumption, and dedupes explicitly.

- `app/src/main/java/com/materialchat/ui/screens/chat/components/BubbleTapRouter.kt` (rewritten, 1-146)
  - `BubbleTapRouter.beginGesture()` / `markDelivered()` / `delivering(action)` (`:62-76`): a control's own click path wraps its action so the router knows the current gesture was delivered; `route()` (`:81-90`) skips a gesture that was marked, so a working chip never toggles twice.
  - `Modifier.bubbleTapTarget` (`:99-115`) registers UNCLIPPED root bounds (`findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)`) and only while attached. The old `boundsInRoot()` was clipped by `animateContentSize`'s `clipToBounds` and the list viewport, so a chip half inside an animating clip registered a wrong rect.
  - `Modifier.bubbleTapRouting(router)` (`:124-146`): new. Observes the down in the Initial pass (`awaitFirstDown(requireUnconsumed = false, pass = Initial)`), follows it in Main, and on an up within touch slop routes it whether or not anything consumed it (`changedToUpIgnoreConsumed`). Consumes the up when a control was routed so the bubble stops rippling for a tap that belonged to a chip.
- `app/src/main/java/com/materialchat/ui/screens/chat/components/MessageBubble.kt`
  - `:410` `.bubbleTapRouting(tapRouter)` on the bubble Surface's own modifier chain, immediately before the `combinedClickable` (`:412-424`). Removed the 2.39.3 router `pointerInput` and `onGloballyPositioned` from the inner Column (old `:430-456`) and the now-unused `LayoutCoordinates` state/imports.
  - ThinkingSection `:1081` `val toggle = tapRouter.delivering { ... }`, used by `clickable` (`:1090`) and `tapThroughScroll` (`:1092`); `bubbleTapTarget` (`:1094`) keeps the raw action.
  - MemoryIndicatorChip `:1347` `val deliver = tapRouter.delivering(onClick)`, used by `Surface(onClick = deliver)` (`:1350`) and `tapThroughScroll(onClick = deliver)` (`:1359`); `bubbleTapTarget` (`:1361`) keeps the raw action.
- Tests
  - `app/src/test/java/com/materialchat/ui/screens/chat/components/BubbleTapRouterTest.kt`: existing four tests kept; added `aGestureTheControlDeliveredItselfIsNotRoutedAgain` (`:85`) and `aNewGestureClearsAStaleDeliveryMark` (`:102`). Both fail without the dedupe.
  - `app/src/androidTest/java/com/materialchat/ui/screens/chat/components/MessageBubbleTapRoutingTest.kt` (new, void test methods): `memoryChipTogglesExactlyOncePerTap` (`:72`) and `thinkingHeaderTogglesExactlyOncePerTap` (`:87`). They compose real `MessageBubble`s in a `LazyColumn` with the bookmark `combinedClickable` present and assert the chevron contentDescription flips exactly once per tap. Without the dedupe the new consumption-agnostic router would fire a second time on a working chip and the chevron would end where it started, so these fail without the change.

What the change covers and does not cover:
- Covers H1 (chip cancelled/stale: the bubble node still sees the up), H2 (chip not on the hit path: bounds come from layout, unclipped, and the bubble node is on the path whenever it ripples), and the H3 residue (a consumed up is routed anyway).
- Does not cover H4 (if the toggle fired and state was lost, routing again changes nothing).
- Does not remove the bubble's `combinedClickable` (it still ripples on a routed tap until the up is consumed; the ripple release and the routed toggle happen on the same up).

## 3. How verified

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2 :app:compileGithubDebugKotlin` -> BUILD SUCCESSFUL.
- `:app:testGithubDebugUnitTest --tests 'com.materialchat.ui.screens.chat.components.BubbleTapRouterTest'` -> 6/6 green (4 existing + 2 new).
- `:app:compileGithubDebugAndroidTestKotlin` -> BUILD SUCCESSFUL (new androidTest compiles; not executed: no emulator allowed on this machine).
- No test, testTag, contentDescription, script or workflow was weakened or removed. `SmoothStreamingText.kt` untouched. Nothing pushed.
- `./gradlew --stop` run at the end.

## 4. What I could NOT verify without the device

- Which of H1/H2 is actually firing on the phone. Both are consistent with the ripple fact; the sources cannot decide it.
- The unverified link in H1: whether the owner dispatches a pointer cancel to a `ClickableNode` whose LayoutNode is deactivated for reuse (I traced `onReset`/`onDetach` and `HitPathTracker`; I did not trace the owner side of `removePointerInputModifierNode`).
- That the new androidTest passes on API 26/35 (it compiles; run it on the hosted runners).
- The settings-gated tap tracer asked for in the brief was NOT built in this pass; the time went into the source reading and the fix. What it needs to record to discriminate H1 from H2 on the phone: per gesture inside a bubble, (a) whether the chip's node was on the hit path at all (a `pointerInput` on the chip observing the Initial-pass down), (b) the chip's unclipped vs clipped root bounds and the tap position in root space, (c) whether the bubble's node saw the down unconsumed in Main, (d) `expanded` before/after, written to a file shareable from Settings. Zero cost when off means the modifiers must not be added at all unless the preference is on.

## 5. Questions for the user that discriminate the hypotheses

1. Which exact version is installed right now (Settings -> About, or the Play/GitHub release number)? If it is older than 2.39.3 the inner-Column router never ran on the phone and H1 alone explains everything; if it is 2.39.3 or 2.40.0, H1 alone is insufficient and H2 is live.
2. When a chip ignores a tap, does the SECOND tap on the same chip work? H1 predicts yes (the stale state is cleared by the first lost tap); H2 predicts no until something re-lays out the bubble.
3. Does it only happen after scrolling (a fling, or the list auto-scrolling when a reply finishes), or also on a chip that has been sitting still on screen since the conversation was opened? H1 needs a scroll that carried the chip out from under a touch; H2 needs an animation in flight.
4. Does the same happen to the thinking header ("Thought for ...") and to the action-row buttons under the bubble, or only to the chips? Chips and header share the router; the action row does not, so a difference there points at the chip subtree (H2) rather than list-wide state (H1).
