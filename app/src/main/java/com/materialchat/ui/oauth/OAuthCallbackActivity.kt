package com.materialchat.ui.oauth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.materialchat.MainActivity
import com.materialchat.data.auth.OAuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity that handles OAuth deep link callbacks.
 *
 * This activity is registered in AndroidManifest.xml to handle the OAuth redirect URI:
 * `materialchat://oauth/{provider}`
 *
 * When the OAuth provider redirects back to our app after user authentication,
 * this activity receives the callback URI containing the authorization code and state.
 * It delegates to OAuthManager to complete the token exchange.
 *
 * Uses a translucent theme to avoid disrupting the user experience during the brief
 * callback handling process.
 */
@AndroidEntryPoint
class OAuthCallbackActivity : ComponentActivity() {

    @Inject
    lateinit var oauthManager: OAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleOAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthCallback(intent)
    }

    /**
     * Handles the OAuth callback by extracting the URI and delegating to OAuthManager.
     */
    private fun handleOAuthCallback(intent: Intent?) {
        val uri = intent?.data

        if (uri == null) {
            // No URI provided, just finish and return to app
            navigateToMain()
            return
        }

        lifecycleScope.launch {
            val result = oauthManager.handleCallback(uri)

            result.fold(
                onSuccess = { tokens ->
                    // Successfully authenticated
                    // Navigate back to the app with success indication
                    navigateToMain(
                        providerId = uri.lastPathSegment,
                        email = tokens.email,
                        success = true
                    )
                },
                onFailure = { error ->
                    // Authentication failed
                    navigateToMain(
                        providerId = uri.lastPathSegment,
                        errorMessage = error.message,
                        success = false
                    )
                }
            )
        }
    }

    /**
     * Navigates back to the main activity with OAuth result.
     */
    private fun navigateToMain(
        providerId: String? = null,
        email: String? = null,
        errorMessage: String? = null,
        success: Boolean = false
    ) {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = ACTION_OAUTH_RESULT
            providerId?.let { putExtra(EXTRA_PROVIDER_ID, it) }
            email?.let { putExtra(EXTRA_EMAIL, it) }
            errorMessage?.let { putExtra(EXTRA_ERROR_MESSAGE, it) }
            putExtra(EXTRA_SUCCESS, success)
        }
        startActivity(mainIntent)
        finish()
    }

    companion object {
        const val ACTION_OAUTH_RESULT = "com.materialchat.OAUTH_RESULT"
        const val EXTRA_PROVIDER_ID = "provider_id"
        const val EXTRA_EMAIL = "email"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_SUCCESS = "success"
    }
}
