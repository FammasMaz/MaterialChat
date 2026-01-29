package com.materialchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.repository.UpdateManager
import com.materialchat.di.AppInitializer
import com.materialchat.domain.model.UpdateState
import com.materialchat.ui.components.UpdateBanner
import com.materialchat.ui.navigation.MaterialChatNavHost
import com.materialchat.ui.navigation.Screen
import com.materialchat.ui.theme.MaterialChatTheme
import com.materialchat.ui.theme.isDynamicColorSupported
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main entry point Activity for MaterialChat.
 * Uses Jetpack Compose with Material 3 Expressive for the UI.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var appInitializer: AppInitializer

    @Inject
    lateinit var updateManager: UpdateManager

    /** Pending conversation ID from assistant intent, to be navigated after init */
    private var pendingConversationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before calling super.onCreate()
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Handle assistant navigation intent
        handleAssistantIntent(intent)

        // Keep splash screen visible while initializing
        var isInitialized = false
        splashScreen.setKeepOnScreenCondition { !isInitialized }

        // Enable edge-to-edge display
        enableEdgeToEdge()

        setContent {
            // Track initialization state
            var initComplete by remember { mutableStateOf(false) }

            // Perform first-launch initialization and check for updates
            LaunchedEffect(Unit) {
                appInitializer.initializeIfNeeded()
                initComplete = true
                isInitialized = true

                // Check for updates in background (respects auto-check preference)
                updateManager.checkForUpdates(force = false)
            }

            val themeMode by appPreferences.themeMode.collectAsState(
                initial = AppPreferences.ThemeMode.SYSTEM
            )
            val dynamicColorEnabled by appPreferences.dynamicColorEnabled.collectAsState(
                initial = isDynamicColorSupported()
            )

            MaterialChatTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColorEnabled && isDynamicColorSupported()
            ) {
                // Only show the app after initialization is complete
                if (initComplete) {
                    MaterialChatApp(
                        updateManager = updateManager,
                        initialConversationId = pendingConversationId
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAssistantIntent(intent)
    }

    /**
     * Handles the OPEN_CHAT intent action from the assistant overlay.
     */
    private fun handleAssistantIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_CHAT) {
            pendingConversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        }
    }

    companion object {
        const val ACTION_OPEN_CHAT = "com.materialchat.OPEN_CHAT"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}

/**
 * Root composable for the MaterialChat application.
 * Sets up navigation with Material 3 Expressive transitions.
 * Includes update banner overlay for non-blocking update notifications.
 *
 * @param updateManager Manager for app updates
 * @param initialConversationId Optional conversation ID to navigate to on launch (from assistant)
 */
@Composable
fun MaterialChatApp(
    updateManager: UpdateManager? = null,
    initialConversationId: String? = null
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // Navigate to chat if launched from assistant with a conversation ID
    LaunchedEffect(initialConversationId) {
        if (initialConversationId != null) {
            navController.navigate(Screen.Chat.createRoute(initialConversationId)) {
                // Pop up to conversations so back button goes there
                popUpTo(Screen.Conversations.route) {
                    inclusive = false
                }
            }
        }
    }

    // Collect update state if updateManager is provided
    val updateState by updateManager?.updateState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(UpdateState.Idle) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main app content
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MaterialChatNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Update banner overlay (top)
        if (updateManager != null) {
            UpdateBanner(
                state = updateState,
                onDownload = { update ->
                    scope.launch {
                        updateManager.downloadUpdate(update)
                    }
                },
                onInstall = {
                    scope.launch {
                        updateManager.installUpdate()
                    }
                },
                onCancel = {
                    updateManager.cancelDownload()
                },
                onDismiss = {
                    updateManager.dismissBanner()
                },
                onSkipVersion = {
                    scope.launch {
                        updateManager.skipVersion()
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MaterialChatAppPreview() {
    MaterialChatTheme(
        themeMode = AppPreferences.ThemeMode.LIGHT,
        dynamicColor = false
    ) {
        MaterialChatApp()
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MaterialChatAppDarkPreview() {
    MaterialChatTheme(
        themeMode = AppPreferences.ThemeMode.DARK,
        dynamicColor = false
    ) {
        MaterialChatApp()
    }
}
