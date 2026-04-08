package com.materialchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.repository.UpdateManager
import com.materialchat.di.AppInitializer
import com.materialchat.domain.model.UpdateState
import com.materialchat.ui.MainViewModel
import com.materialchat.ui.components.UpdateBanner
import com.materialchat.ui.navigation.LocalAnimatedVisibilityScope
import com.materialchat.ui.navigation.LocalSharedTransitionScope
import com.materialchat.ui.navigation.MaterialChatNavBar
import com.materialchat.ui.navigation.MaterialChatNavHost
import com.materialchat.ui.navigation.Screen
import com.materialchat.ui.navigation.TopLevelTab
import com.materialchat.ui.screens.personas.components.PersonaPickerSheet
import com.materialchat.ui.theme.MaterialChatTheme
import com.materialchat.ui.theme.LocalChatFontSizeScale
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
            val chatFontSizeScale by appPreferences.fontSizeScale.collectAsState(
                initial = AppPreferences.DEFAULT_FONT_SIZE_SCALE_VALUE
            )

            MaterialChatTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColorEnabled && isDynamicColorSupported()
            ) {
                // Provide chat-specific font size via CompositionLocal
                CompositionLocalProvider(
                    LocalChatFontSizeScale provides chatFontSizeScale
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
 * Routes where the bottom Navigation Bar should be visible.
 * Only top-level screens show the nav bar; detail screens hide it.
 */
private val topLevelRoutes = setOf(
    "conversations",
    "explore",
    "settings"
)

/**
 * Root composable for the MaterialChat application.
 * Sets up navigation with Material 3 Expressive transitions.
 * Uses a Box overlay layout with HorizontalFloatingToolbar for navigation
 * (replaces deprecated Scaffold + bottomBar pattern per M3 Expressive).
 * Includes update banner overlay for non-blocking update notifications.
 *
 * @param updateManager Manager for app updates
 * @param initialConversationId Optional conversation ID to navigate to on launch (from assistant)
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MaterialChatApp(
    updateManager: UpdateManager? = null,
    initialConversationId: String? = null
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val mainViewModel: MainViewModel = hiltViewModel()

    // M3 Expressive: Collapse toolbar to FAB-only on scroll down, expand on scroll up
    var isToolbarExpanded by remember { mutableStateOf(true) }
    val toolbarNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10f) isToolbarExpanded = false  // Scrolling down → collapse
                else if (available.y > 10f) isToolbarExpanded = true  // Scrolling up → expand
                return Offset.Zero  // Don't consume scroll
            }
        }
    }

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

    // Handle MainViewModel navigation events (New Chat FAB in floating toolbar)
    LaunchedEffect(Unit) {
        mainViewModel.navigateToChat.collect { conversationId ->
            navController.navigate(Screen.Chat.createRoute(conversationId)) {
                popUpTo(Screen.Conversations.route) {
                    inclusive = false
                }
            }
        }
    }

    // Track current route for nav bar visibility and tab highlighting
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute by remember {
        derivedStateOf { navBackStackEntry?.destination?.route }
    }

    // Determine whether the floating toolbar should be visible
    val showBottomBar by remember {
        derivedStateOf { currentRoute in topLevelRoutes }
    }

    // Persona picker state
    val showPersonaPicker by mainViewModel.showPersonaPicker.collectAsStateWithLifecycle()
    val personas by mainViewModel.personas.collectAsStateWithLifecycle(initialValue = emptyList())

    // Collect update state if updateManager is provided
    val updateState by updateManager?.updateState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(UpdateState.Idle) }

    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Main content - each screen handles its own Scaffold and insets
        MaterialChatNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(toolbarNestedScrollConnection)
        )

        // M3 Expressive: Floating toolbar navigation overlay
        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            enter = slideInVertically(
                animationSpec = spring(
                    stiffness = 500f,
                    dampingRatio = 0.7f  // Spatial - subtle bounce
                ),
                initialOffsetY = { it }  // Slide up from bottom
            ),
            exit = slideOutVertically(
                animationSpec = spring(
                    stiffness = 500f,
                    dampingRatio = 1.0f  // Effects - smooth exit
                ),
                targetOffsetY = { it }  // Slide down off screen
            )
        ) {
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaterialChatNavBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        // Avoid re-navigating to the current tab
                        if (currentRoute != tab.route) {
                            navController.navigate(tab.route) {
                                // Pop up to the start destination to avoid building
                                // up a large back stack of top-level destinations
                                popUpTo(Screen.startDestination) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination
                                launchSingleTop = true
                                // Restore state when re-selecting a previously selected tab
                                restoreState = true
                            }
                        }
                    },
                    onNewChat = { mainViewModel.createNewConversation() },
                    onNewChatLongPress = { mainViewModel.showPersonaPicker() },
                    expanded = isToolbarExpanded
                )
            }
            }
        }

        // Persona picker bottom sheet
        if (showPersonaPicker) {
            PersonaPickerSheet(
                personas = personas,
                isVisible = true,
                onDismiss = {
                    mainViewModel.hidePersonaPicker()
                },
                onPersonaSelected = { persona ->
                    mainViewModel.createNewConversationWithPersona(persona.id)
                }
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
