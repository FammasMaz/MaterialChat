package com.materialchat.ui.oauth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.auth.OAuthException
import com.materialchat.data.auth.OAuthManager
import com.materialchat.domain.model.AuthType
import com.materialchat.domain.model.OAuthState
import com.materialchat.domain.model.Provider
import com.materialchat.domain.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing OAuth authentication flows.
 *
 * Coordinates between the UI and the OAuth infrastructure:
 * - Initiates OAuth flows by building authorization URLs
 * - Opens Custom Tabs for user authentication
 * - Observes and exposes OAuth state changes
 * - Handles logout operations
 *
 * Uses Hilt for dependency injection of OAuthManager and ProviderRepository.
 */
@HiltViewModel
class OAuthViewModel @Inject constructor(
    private val oauthManager: OAuthManager,
    private val providerRepository: ProviderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OAuthUiState>(OAuthUiState.Idle)
    val uiState: StateFlow<OAuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OAuthEvent>()
    val events: SharedFlow<OAuthEvent> = _events.asSharedFlow()

    /**
     * Initiates the OAuth login flow for a provider.
     *
     * Builds the authorization URL and opens it in a Custom Tab for user authentication.
     * The actual callback handling is done by OAuthCallbackActivity.
     *
     * @param context Android context for launching Custom Tabs
     * @param provider The OAuth provider to authenticate with
     * @param projectId Optional project ID for providers like Antigravity
     */
    fun startOAuthFlow(
        context: Context,
        provider: Provider,
        projectId: String? = null
    ) {
        if (provider.authType != AuthType.OAUTH) {
            viewModelScope.launch {
                _events.emit(OAuthEvent.Error("Provider does not support OAuth authentication"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = OAuthUiState.Loading

            try {
                val authRequest = oauthManager.buildAuthorizationUrl(provider, projectId)

                // Launch Custom Tab for OAuth authentication
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()

                customTabsIntent.launchUrl(context, Uri.parse(authRequest.url))

                _uiState.value = OAuthUiState.AwaitingCallback
            } catch (e: OAuthException) {
                _uiState.value = OAuthUiState.Error(e.message ?: "Failed to start OAuth flow")
                _events.emit(OAuthEvent.Error(e.message ?: "Failed to start OAuth flow"))
            } catch (e: Exception) {
                _uiState.value = OAuthUiState.Error(e.message ?: "Unknown error")
                _events.emit(OAuthEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Observes the OAuth state for a provider.
     *
     * @param providerId The ID of the provider to observe
     * @return Flow of OAuthState updates
     */
    fun observeOAuthState(providerId: String) = providerRepository.observeOAuthState(providerId)

    /**
     * Gets the current OAuth state for a provider.
     *
     * @param providerId The ID of the provider
     */
    fun getOAuthState(providerId: String) {
        viewModelScope.launch {
            val state = providerRepository.getOAuthState(providerId)
            when (state) {
                is OAuthState.Authenticated -> {
                    _uiState.value = OAuthUiState.Authenticated(
                        email = state.email,
                        expiresAt = state.expiresAt
                    )
                }
                is OAuthState.Authenticating -> {
                    _uiState.value = OAuthUiState.Loading
                }
                is OAuthState.Unauthenticated -> {
                    _uiState.value = OAuthUiState.Idle
                }
                is OAuthState.Error -> {
                    _uiState.value = OAuthUiState.Error(state.message)
                }
            }
        }
    }

    /**
     * Logs out of OAuth for a provider.
     *
     * Clears all OAuth tokens and resets the authentication state.
     *
     * @param providerId The ID of the provider to logout from
     */
    fun logout(providerId: String) {
        viewModelScope.launch {
            try {
                providerRepository.logoutOAuth(providerId)
                _uiState.value = OAuthUiState.Idle
                _events.emit(OAuthEvent.LoggedOut(providerId))
            } catch (e: Exception) {
                _events.emit(OAuthEvent.Error(e.message ?: "Failed to logout"))
            }
        }
    }

    /**
     * Handles the OAuth result from OAuthCallbackActivity.
     *
     * Called when the OAuth callback returns to the app (via MainActivity).
     *
     * @param success Whether the OAuth flow was successful
     * @param providerId The provider ID
     * @param email The authenticated user's email (if successful)
     * @param errorMessage The error message (if failed)
     */
    fun handleOAuthResult(
        success: Boolean,
        providerId: String?,
        email: String?,
        errorMessage: String?
    ) {
        viewModelScope.launch {
            if (success && providerId != null) {
                _uiState.value = OAuthUiState.Authenticated(
                    email = email ?: "Unknown",
                    expiresAt = null
                )
                _events.emit(OAuthEvent.Success(providerId, email))
            } else {
                _uiState.value = OAuthUiState.Error(errorMessage ?: "OAuth authentication failed")
                _events.emit(OAuthEvent.Error(errorMessage ?: "OAuth authentication failed"))
            }
        }
    }

    /**
     * Resets the UI state to idle.
     */
    fun resetState() {
        _uiState.value = OAuthUiState.Idle
    }
}

/**
 * UI state for OAuth operations.
 */
sealed class OAuthUiState {
    /** No OAuth operation in progress. */
    data object Idle : OAuthUiState()

    /** OAuth flow is loading or starting. */
    data object Loading : OAuthUiState()

    /** Waiting for OAuth callback from browser. */
    data object AwaitingCallback : OAuthUiState()

    /** Successfully authenticated. */
    data class Authenticated(
        val email: String,
        val expiresAt: Long?
    ) : OAuthUiState()

    /** OAuth error occurred. */
    data class Error(val message: String) : OAuthUiState()
}

/**
 * One-time events for OAuth operations.
 */
sealed class OAuthEvent {
    /** OAuth flow completed successfully. */
    data class Success(val providerId: String, val email: String?) : OAuthEvent()

    /** OAuth error occurred. */
    data class Error(val message: String) : OAuthEvent()

    /** User logged out. */
    data class LoggedOut(val providerId: String) : OAuthEvent()
}
