package com.materialchat.assistant.service

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
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
import com.materialchat.MainActivity
import com.materialchat.assistant.di.AssistantEntryPoint
import com.materialchat.assistant.ui.AssistantOverlay
import com.materialchat.assistant.ui.AssistantViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    private var composeView: ComposeView? = null
    private var assistantViewModel: AssistantViewModel? = null

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

        // Notify ViewModel that overlay is being shown (for timeout check and animation reset)
        assistantViewModel?.onOverlayShown()
    }

    override fun onHide() {
        assistantViewModel?.clearConversation()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onHide()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStoreInstance.clear()
        composeView = null
        assistantViewModel = null
        super.onDestroy()
    }

    override fun onCreateContentView(): View {
        // VoiceInteractionSession already configures its root/window for edge-to-edge.
        // Do not change decor fitting here: it is framework-owned, not an Activity.
        // The framework may create content before attaching its dialog decor. Calling
        // setSoftInputMode here dispatches a layout update to an unattached window
        // on Android 15. Set the initial attributes without dispatching that update;
        // the framework applies them when it attaches the session window.
        window.window?.attributes?.softInputMode =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

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
            applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ) } })[AssistantViewModel::class.java]

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
                val error = ClockHandoff.launch(event.command) { startAssistantActivity(it) }
                viewModel.onClockHandoff(event.token, error)
                if (error == null) hide()
            }
        )
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
        startAssistantActivity(intent)
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

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        // Could analyze the screenshot for context-aware assistance
    }
}
