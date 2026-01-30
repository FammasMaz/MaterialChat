# OAuth Authentication Specification

## Overview

Implement OAuth 2.0 authentication flow with PKCE support for MaterialChat providers. This enables authentication with providers like Antigravity (Google), GitHub Copilot, and other OAuth-based services.

## Requirements

### 1. PKCE (Proof Key for Code Exchange)

Implement PKCE for secure OAuth flows on mobile:

```kotlin
data class PkcePair(
    val verifier: String,   // 64-byte random string (base64url)
    val challenge: String   // SHA-256 hash of verifier (base64url)
)

class PkceGenerator {
    private val secureRandom = SecureRandom()

    fun generate(): PkcePair {
        val verifier = generateVerifier()
        val challenge = generateChallenge(verifier)
        return PkcePair(verifier, challenge)
    }

    private fun generateVerifier(): String {
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
```

### 2. OAuth Configuration Model

```kotlin
data class OAuthConfig(
    val providerId: String,
    val clientId: String,
    val clientSecret: String?,      // Some providers don't need this for PKCE
    val authUrl: String,
    val tokenUrl: String,
    val redirectUri: String,
    val scopes: List<String>,
    val additionalParams: Map<String, String> = emptyMap()
)

// Pre-defined configurations
object OAuthConfigs {
    val ANTIGRAVITY = OAuthConfig(
        providerId = "antigravity",
        clientId = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com",
        clientSecret = "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",
        authUrl = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenUrl = "https://oauth2.googleapis.com/token",
        redirectUri = "materialchat://oauth/antigravity",
        scopes = listOf(
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/cclog",
            "https://www.googleapis.com/auth/experimentsandconfigs"
        ),
        additionalParams = mapOf(
            "access_type" to "offline",
            "prompt" to "consent"
        )
    )

    val GITHUB = OAuthConfig(
        providerId = "github-copilot",
        clientId = "YOUR_GITHUB_CLIENT_ID",
        clientSecret = "YOUR_GITHUB_CLIENT_SECRET",
        authUrl = "https://github.com/login/oauth/authorize",
        tokenUrl = "https://github.com/login/oauth/access_token",
        redirectUri = "materialchat://oauth/github",
        scopes = listOf("copilot")
    )
}
```

### 3. OAuth Token Model

```kotlin
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,              // Unix timestamp in milliseconds
    val tokenType: String = "Bearer",
    val scope: String? = null,
    // Provider-specific fields
    val email: String? = null,        // User's email
    val projectId: String? = null     // For Antigravity
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt - 60_000 // 1 min buffer
    fun needsRefresh(): Boolean = isExpired() && refreshToken != null
}
```

### 4. OAuth State Management

Encode state for OAuth callback verification:

```kotlin
data class OAuthState(
    val verifier: String,
    val providerId: String,
    val projectId: String = "",
    val nonce: String = UUID.randomUUID().toString()
)

object OAuthStateEncoder {
    fun encode(state: OAuthState): String {
        val json = Json.encodeToString(state)
        return Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun decode(encoded: String): OAuthState {
        val json = String(Base64.decode(encoded, Base64.URL_SAFE))
        return Json.decodeFromString(json)
    }
}
```

### 5. OAuth Manager

```kotlin
interface OAuthManager {
    suspend fun startAuthFlow(config: OAuthConfig): OAuthAuthIntent
    suspend fun handleCallback(uri: Uri): Result<OAuthTokens>
    suspend fun refreshTokens(config: OAuthConfig, refreshToken: String): Result<OAuthTokens>
    suspend fun getValidTokens(providerId: String): OAuthTokens?
    suspend fun logout(providerId: String)
}

data class OAuthAuthIntent(
    val authUrl: Uri,
    val state: OAuthState
)

class OAuthManagerImpl @Inject constructor(
    private val httpClient: OkHttpClient,
    private val encryptedPreferences: EncryptedPreferences,
    private val pkceGenerator: PkceGenerator
) : OAuthManager {

    // In-memory state storage during auth flow
    private val pendingStates = mutableMapOf<String, OAuthState>()

    override suspend fun startAuthFlow(config: OAuthConfig): OAuthAuthIntent {
        val pkce = pkceGenerator.generate()
        val state = OAuthState(
            verifier = pkce.verifier,
            providerId = config.providerId
        )

        val authUrl = Uri.parse(config.authUrl)
            .buildUpon()
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("scope", config.scopes.joinToString(" "))
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", OAuthStateEncoder.encode(state))
            .apply {
                config.additionalParams.forEach { (key, value) ->
                    appendQueryParameter(key, value)
                }
            }
            .build()

        pendingStates[state.nonce] = state

        return OAuthAuthIntent(authUrl, state)
    }

    override suspend fun handleCallback(uri: Uri): Result<OAuthTokens> {
        val code = uri.getQueryParameter("code")
            ?: return Result.failure(OAuthException("Missing authorization code"))
        val stateParam = uri.getQueryParameter("state")
            ?: return Result.failure(OAuthException("Missing state parameter"))

        val state = try {
            OAuthStateEncoder.decode(stateParam)
        } catch (e: Exception) {
            return Result.failure(OAuthException("Invalid state parameter"))
        }

        // Verify state
        if (!pendingStates.containsKey(state.nonce)) {
            return Result.failure(OAuthException("Unknown state - possible CSRF attack"))
        }
        pendingStates.remove(state.nonce)

        // Exchange code for tokens
        return exchangeCodeForTokens(state, code)
    }

    private suspend fun exchangeCodeForTokens(state: OAuthState, code: String): Result<OAuthTokens> {
        val config = getConfigForProvider(state.providerId)
            ?: return Result.failure(OAuthException("Unknown provider"))

        val requestBody = FormBody.Builder()
            .add("client_id", config.clientId)
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", config.redirectUri)
            .add("code_verifier", state.verifier)
            .apply {
                config.clientSecret?.let { add("client_secret", it) }
            }
            .build()

        val request = Request.Builder()
            .url(config.tokenUrl)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        OAuthException("Token exchange failed: ${response.code}")
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(OAuthException("Empty response"))

                val tokens = parseTokenResponse(body, state.providerId)
                saveTokens(state.providerId, tokens)

                Result.success(tokens)
            } catch (e: Exception) {
                Result.failure(OAuthException("Token exchange error: ${e.message}", e))
            }
        }
    }

    // ... other methods
}
```

### 6. Android Deep Link Handling

#### Manifest Configuration
```xml
<activity
    android:name=".ui.oauth.OAuthCallbackActivity"
    android:exported="true"
    android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <!-- Antigravity callback -->
        <data android:scheme="materialchat"
              android:host="oauth"
              android:path="/antigravity"/>
        <!-- GitHub callback -->
        <data android:scheme="materialchat"
              android:host="oauth"
              android:path="/github"/>
    </intent-filter>
</activity>
```

#### Callback Activity
```kotlin
@AndroidEntryPoint
class OAuthCallbackActivity : ComponentActivity() {

    @Inject
    lateinit var oauthManager: OAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val uri = intent.data
            if (uri != null) {
                val result = oauthManager.handleCallback(uri)
                result.fold(
                    onSuccess = { tokens ->
                        // Navigate back to settings with success
                        setResult(RESULT_OK, Intent().apply {
                            putExtra("provider_id", uri.lastPathSegment)
                            putExtra("email", tokens.email)
                        })
                    },
                    onFailure = { error ->
                        setResult(RESULT_CANCELED, Intent().apply {
                            putExtra("error", error.message)
                        })
                    }
                )
            }
            finish()
        }
    }
}
```

### 7. Encrypted Token Storage

Extend EncryptedPreferences for OAuth tokens:

```kotlin
// Key patterns
private fun oauthAccessKey(providerId: String) = "oauth_access_$providerId"
private fun oauthRefreshKey(providerId: String) = "oauth_refresh_$providerId"
private fun oauthExpiresKey(providerId: String) = "oauth_expires_$providerId"
private fun oauthEmailKey(providerId: String) = "oauth_email_$providerId"
private fun oauthProjectKey(providerId: String) = "oauth_project_$providerId"

suspend fun saveOAuthTokens(providerId: String, tokens: OAuthTokens) {
    setSecure(oauthAccessKey(providerId), tokens.accessToken)
    tokens.refreshToken?.let { setSecure(oauthRefreshKey(providerId), it) }
    setLong(oauthExpiresKey(providerId), tokens.expiresAt)
    tokens.email?.let { setString(oauthEmailKey(providerId), it) }
    tokens.projectId?.let { setString(oauthProjectKey(providerId), it) }
}

suspend fun getOAuthTokens(providerId: String): OAuthTokens? {
    val accessToken = getSecure(oauthAccessKey(providerId)) ?: return null
    return OAuthTokens(
        accessToken = accessToken,
        refreshToken = getSecure(oauthRefreshKey(providerId)),
        expiresAt = getLong(oauthExpiresKey(providerId), 0),
        email = getString(oauthEmailKey(providerId)),
        projectId = getString(oauthProjectKey(providerId))
    )
}

suspend fun deleteOAuthTokens(providerId: String) {
    delete(oauthAccessKey(providerId))
    delete(oauthRefreshKey(providerId))
    delete(oauthExpiresKey(providerId))
    delete(oauthEmailKey(providerId))
    delete(oauthProjectKey(providerId))
}
```

### 8. Token Refresh

```kotlin
suspend fun refreshTokens(config: OAuthConfig, refreshToken: String): Result<OAuthTokens> {
    val requestBody = FormBody.Builder()
        .add("client_id", config.clientId)
        .add("refresh_token", refreshToken)
        .add("grant_type", "refresh_token")
        .apply {
            config.clientSecret?.let { add("client_secret", it) }
        }
        .build()

    val request = Request.Builder()
        .url(config.tokenUrl)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Accept", "application/json")
        .post(requestBody)
        .build()

    return withContext(Dispatchers.IO) {
        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    OAuthRefreshException("Token refresh failed: ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(OAuthRefreshException("Empty response"))

            val tokens = parseTokenResponse(body, config.providerId)

            // Keep old refresh token if not returned
            val finalTokens = if (tokens.refreshToken == null) {
                tokens.copy(refreshToken = refreshToken)
            } else {
                tokens
            }

            saveTokens(config.providerId, finalTokens)
            Result.success(finalTokens)
        } catch (e: Exception) {
            Result.failure(OAuthRefreshException("Refresh error: ${e.message}", e))
        }
    }
}
```

## Acceptance Criteria

- [ ] PkceGenerator implemented with SHA-256 challenge
- [ ] OAuthConfig data class created
- [ ] Pre-defined configs for Antigravity and GitHub
- [ ] OAuthTokens data class with expiry checking
- [ ] OAuthState encoding/decoding for CSRF protection
- [ ] OAuthManager interface and implementation
- [ ] Android deep link manifest configuration
- [ ] OAuthCallbackActivity handling callbacks
- [ ] EncryptedPreferences extended for OAuth tokens
- [ ] Token refresh flow implemented
- [ ] Error handling for all OAuth failures

## Technical Notes

### Using Android Custom Tabs
```kotlin
// Prefer Custom Tabs over WebView for better security
val customTabsIntent = CustomTabsIntent.Builder()
    .setShowTitle(true)
    .build()
customTabsIntent.launchUrl(context, authUrl)
```

### Thread Safety
- Use mutex or synchronized blocks when accessing pendingStates
- All token operations should be on IO dispatcher

### Error Handling
```kotlin
sealed class OAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
class OAuthCanceledException : OAuthException("User cancelled OAuth flow")
class OAuthRefreshException(message: String, cause: Throwable? = null) : OAuthException(message, cause)
class OAuthNetworkException(message: String, cause: Throwable? = null) : OAuthException(message, cause)
```
