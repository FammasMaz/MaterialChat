package com.materialchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.di.AppInitializer
import com.materialchat.ui.navigation.MaterialChatNavHost
import com.materialchat.ui.theme.MaterialChatTheme
import com.materialchat.ui.theme.isDynamicColorSupported
import dagger.hilt.android.AndroidEntryPoint
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before calling super.onCreate()
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Keep splash screen visible while initializing
        var isInitialized = false
        splashScreen.setKeepOnScreenCondition { !isInitialized }

        // Enable edge-to-edge display
        enableEdgeToEdge()

        setContent {
            // Track initialization state
            var initComplete by remember { mutableStateOf(false) }

            // Perform first-launch initialization
            LaunchedEffect(Unit) {
                appInitializer.initializeIfNeeded()
                initComplete = true
                isInitialized = true
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
                    MaterialChatApp()
                }
            }
        }
    }
}

/**
 * Root composable for the MaterialChat application.
 * Sets up navigation with Material 3 Expressive transitions.
 */
@Composable
fun MaterialChatApp() {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        MaterialChatNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize()
        )
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
