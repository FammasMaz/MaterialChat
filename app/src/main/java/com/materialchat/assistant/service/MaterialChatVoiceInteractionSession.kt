package com.materialchat.assistant.service

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.materialchat.assistant.actions.ClockHandoff
import com.materialchat.assistant.actions.AssistantActivityLauncher
import com.materialchat.assistant.actions.DeviceActionHandler
import com.materialchat.MainActivity
import com.materialchat.assistant.di.AssistantEntryPoint
import com.materialchat.assistant.ui.ASSISTANT_KEEP_AWAKE_GRACE_MILLIS
import com.materialchat.assistant.ui.AssistantOverlay
import com.materialchat.assistant.ui.AssistantViewModel
import com.materialchat.assistant.ui.shouldKeepScreenOn
import dagger.hilt.android.EntryPointAccessors
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Voice interaction session that handles individual assistant invocations.
 *
 * This session is created each time the user activates the assistant
 * (power button long-press, corner swipe, etc.).
 *
 * It manages:
 * - The overlay UI displayed on top of other apps
 * - Voice input/output
 * - AI query handling
 * - Navigation to the full app
 */
class MaterialChatVoiceInteractionSession(
    context: Context
) : VoiceInteractionSession(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreInstance = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreInstance
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    /** Outlives a single show/hide cycle; cancelled with the session. */
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlayShown = false
    private var pendingShow = false
    private var composeView: ComposeView? = null
    private var assistantViewModel: AssistantViewModel? = null

    /**
     * The assist screenshot of the screen the assistant was opened over, when the platform
     * delivers one (shown with SHOW_WITH_SCREENSHOT, "Use screenshot" enabled for the assistant
     * app, no secure window, keyguard not locked). The overlay paints it as a shrinking backdrop;
     * it is released on hide.
     */
    private val assistScreenshot = mutableStateOf<Bitmap?>(null)

    init {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        if (!overlayShown) {
            overlayShown = true
            val model = assistantViewModel
            if (model == null) pendingShow = true else model.onOverlayShown()
        }
        enforceEnabledSetting()
    }

    /**
     * The Settings switch used to only hide the setup card, so turning the assistant off did
     * nothing. Honour it: close the session with an explanation. A preference read failure is
     * treated as enabled so a transient error cannot silently disable the assistant.
     */
    private fun enforceEnabledSetting() {
        sessionScope.launch {
            val preferences = runCatching {
                EntryPointAccessors.fromApplication(context.applicationContext, AssistantEntryPoint::class.java)
                    .appPreferences()
            }.getOrNull() ?: return@launch
            val enabled = runCatching { preferences.assistantEnabled.first() }.getOrNull()
                ?: return@launch
            if (enabled || !overlayShown) return@launch
            Toast.makeText(
                context,
                "MaterialChat assistant is turned off in Settings",
                Toast.LENGTH_LONG
            ).show()
            hide()
        }
    }

    /**
     * Whether the session asked the platform to keep the display on.
     *
     * Exposed because the platform flag itself lives on the session window the system creates,
     * which only exists for a session the voice-interaction service attached — instrumentation
     * tests construct the session directly and have no window.
     */
    var keepScreenOn: Boolean = false
        private set

    /** Pending release of the display after the assistant stops working. */
    private var keepAwakeRelease: Job? = null

    /**
     * Holds the display on while the assistant listens, thinks, streams or speaks, and for
     * [ASSISTANT_KEEP_AWAKE_GRACE_MILLIS] afterwards so the answer stays readable. Timed here
     * rather than in the composition, where a long delay would keep the frame clock busy.
     */
    fun onAssistantBusyChanged(busy: Boolean) {
        Log.i(TAG, "keep-awake busy=$busy")
        keepAwakeRelease?.cancel()
        keepAwakeRelease = null
        setKeepAwake(shouldKeepScreenOn(busy = busy, idleForMillis = 0L))
        if (busy) return
        keepAwakeRelease = sessionScope.launch {
            delay(ASSISTANT_KEEP_AWAKE_GRACE_MILLIS)
            setKeepAwake(
                shouldKeepScreenOn(busy = false, idleForMillis = ASSISTANT_KEEP_AWAKE_GRACE_MILLIS)
            )
        }
    }

    override fun setKeepAwake(keepAwake: Boolean) {
        keepScreenOn = keepAwake
        // The platform throws "Can't call before onCreate()" when no session window was ever
        // attached. Production always has one (the system attaches it before showing the
        // session); the acceptance script asserts the real FLAG_KEEP_SCREEN_ON on that window.
        runCatching { super.setKeepAwake(keepAwake) }
            .onFailure { Log.w(TAG, "Session window unavailable for setKeepAwake($keepAwake)", it) }
    }

    override fun onHide() {
        // The session window is going away; never leave the display pinned on.
        keepAwakeRelease?.cancel()
        keepAwakeRelease = null
        setKeepAwake(false)
        overlayShown = false
        pendingShow = false
        assistantViewModel?.clearConversation()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onHide()
        releaseScreenshot()
    }

    /** Drops the backdrop first so no later frame can draw the recycled bitmap. */
    private fun releaseScreenshot() {
        val previous = assistScreenshot.value
        assistScreenshot.value = null
        previous?.recycle()
    }

    override fun onDestroy() {
        sessionScope.cancel()
        releaseScreenshot()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStoreInstance.clear()
        composeView = null
        assistantViewModel = null
        super.onDestroy()
    }

    override fun onCreateContentView(): View {
        // Keep framework decor ownership; apply IME policy only after attachment.

        // Get dependencies from Hilt EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AssistantEntryPoint::class.java
        )

        // Create ViewModel with dependencies
        assistantViewModel = ViewModelProvider(this, viewModelFactory { initializer { AssistantViewModel(
            speechRuntime = entryPoint.assistantSpeechRuntime(),
            speechRecognitionManager = entryPoint.speechRecognitionManager(),
            textToSpeechManager = entryPoint.textToSpeechManager(),
            chatRepository = entryPoint.chatRepository(),
            providerRepository = entryPoint.providerRepository(),
            appPreferences = entryPoint.appPreferences(),
            createConversationUseCase = entryPoint.createConversationUseCase(),
            conversationRepository = entryPoint.conversationRepository(),
            generateConversationTitleUseCase = entryPoint.generateConversationTitleUseCase(),
            applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            deviceActionHandler = DeviceActionHandler(context.applicationContext, assistantLauncher())
        ) } })[AssistantViewModel::class.java]
        if (pendingShow && overlayShown) {
            pendingShow = false
            assistantViewModel?.onOverlayShown()
        }

        return ComposeView(context).apply {
            composeView = this
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // Set up lifecycle ownership for Compose
            setViewTreeLifecycleOwner(this@MaterialChatVoiceInteractionSession)
            setViewTreeViewModelStoreOwner(this@MaterialChatVoiceInteractionSession)
            setViewTreeSavedStateRegistryOwner(this@MaterialChatVoiceInteractionSession)

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Apply after Dialog.show has installed its framework window policy.
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.post {
                        if (view.isAttachedToWindow) {
                            window.window?.let { attachedWindow ->
                                val policy = attachedWindow.attributes
                                // The session never resizes for the IME: Compose is the only
                                // IME-inset owner. A resized window would double-count on
                                // API 26-29, where the IME height is still reported as a
                                // system window inset after the window has shrunk.
                                // API 26-29 deliver no IME inset here; resize the window and
                                // let the overlay measure the remaining overlap instead.
                                policy.softInputMode =
                                    if (android.os.Build.VERSION.SDK_INT >= 30)
                                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                                    else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                                if (android.os.Build.VERSION.SDK_INT >= 30) policy.setFitInsetsTypes(0)
                                attachedWindow.attributes = policy
                                view.requestApplyInsets()
                            }
                        }
                    }
                }
                override fun onViewDetachedFromWindow(view: View) = Unit
            })
            setContent {
                AssistantOverlayContent()
            }
        }
    }

    @Composable
    private fun AssistantOverlayContent() {
        val viewModel = remember { assistantViewModel!! }

        AssistantOverlay(
            viewModel = viewModel,
            onDismiss = {
                hide()
            },
            onOpenInApp = { conversationId ->
                openInApp(conversationId)
            },
            onOpenClock = { event ->
                // startAssistantActivity only succeeds while this session is the active and
                // shown one, so fall back to a plain launch rather than failing whenever the
                // session state has moved on.
                val error = ClockHandoff.launchWithFallback(
                    command = event.command,
                    startAssistantActivity = { startAssistantActivity(it) },
                    startActivity = { context.startActivity(it) }
                )
                viewModel.onClockHandoff(event.token, error)
                if (error == null) hide()
            },
            assistScreenshot = assistScreenshot.value,
            onKeepScreenOnChanged = { busy -> onAssistantBusyChanged(busy) }
        )
    }

    /**
     * Device actions that open another app (calendar, settings, an installed app) go through
     * the same assistant-authorised start with plain-launch fallback as the clock handoff,
     * and the overlay gets out of the way afterwards just as it does for the clock.
     */
    private fun assistantLauncher(): AssistantActivityLauncher {
        val launcher = AssistantActivityLauncher.throughSession(
            startAssistantActivity = { startAssistantActivity(it) },
            startActivity = { context.startActivity(it) }
        )
        return AssistantActivityLauncher { intent ->
            launcher.launch(intent).also { error -> if (error == null) hide() }
        }
    }

    /**
     * Opens the full MaterialChat app, optionally with a specific conversation.
     */
    private fun openInApp(conversationId: String?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Use custom action to navigate directly to chat screen
            action = "com.materialchat.OPEN_CHAT"
            conversationId?.let {
                putExtra("conversation_id", it)
            }
        }
        // startAssistantActivity throws when the session is no longer the active/shown one.
        // Letting that escape would kill the overlay instead of just failing to open the app.
        runCatching { startAssistantActivity(intent) }
            .onFailure {
                // Fall back to a plain launch, which still works from a service context.
                runCatching { context.startActivity(intent) }
            }
        hide()
    }

    @Deprecated("Deprecated in API level 30")
    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        @Suppress("DEPRECATION")
        super.onHandleAssist(data, structure, content)
        // Handle assist data from the current app context
        // This provides context about what the user is looking at
    }

    @Deprecated("Deprecated in API level 30")
    override fun onHandleAssistSecondary(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?,
        index: Int,
        count: Int
    ) {
        @Suppress("DEPRECATION")
        super.onHandleAssistSecondary(data, structure, content, index, count)
    }

    /**
     * Delivered after show() when the invocation carried SHOW_WITH_SCREENSHOT (the system
     * assist gesture and power-button invocation do) and Settings.Secure.ASSIST_SCREENSHOT_ENABLED
     * is not 0 ("Use screenshot" under Default apps > Digital assistant app). The overlay shows
     * it as the shrinking backdrop; a null or absent screenshot leaves the plain scrim.
     */
    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        // The shrinking backdrop is disabled, so the screenshot is not held (it is a full-screen
        // bitmap). Kept as a hook for the planned press pulse.
        Log.i(TAG, "assist screenshot=" + (screenshot?.let { "${it.width}x${it.height}" } ?: "none") + " (unused)")
    }

    private companion object {
        const val TAG = "MaterialChatAssistant"
    }
}
