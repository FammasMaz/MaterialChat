# AI Providers Feature Specification

## Overview

Implement a comprehensive provider system for MaterialChat that supports multiple AI providers with different authentication mechanisms, similar to how opencode handles providers. This includes OAuth-based authentication, API key authentication, and provider-specific system prompts.

## Requirements

### 1. Extended Provider Types

Expand the `ProviderType` enum to support:
- `OPENAI_COMPATIBLE` - Standard OpenAI-compatible API (existing)
- `OLLAMA_NATIVE` - Native Ollama API (existing)
- `ANTHROPIC` - Anthropic Claude API with specific headers
- `GOOGLE_GEMINI` - Google Gemini API with OAuth or API key
- `GITHUB_COPILOT` - GitHub Copilot with OAuth
- `ANTIGRAVITY` - Google Antigravity (Gemini/Claude via Google OAuth)

### 2. Authentication Types

Create `AuthType` enum:
- `API_KEY` - Simple API key authentication
- `OAUTH` - OAuth 2.0 with PKCE flow
- `NONE` - No authentication required (local providers)

### 3. Extended Provider Model

Update the `Provider` data class:

```kotlin
data class Provider(
    val id: String,
    val name: String,
    val type: ProviderType,
    val authType: AuthType,
    val baseUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean,
    val isActive: Boolean,
    val systemPrompt: String? = null,       // Provider-specific system prompt
    val supportsStreaming: Boolean = true,
    val supportsImages: Boolean = false,
    val supportsPdf: Boolean = false,
    val supportsReasoning: Boolean = false,
    val headers: Map<String, String> = emptyMap(),  // Custom headers
    val options: Map<String, Any> = emptyMap()      // Provider-specific options
)
```

### 4. OAuth Authentication Flow

Implement OAuth 2.0 with PKCE for providers that require it:

#### 4.1 OAuth Data Models
```kotlin
data class OAuthConfig(
    val clientId: String,
    val clientSecret: String?,
    val authUrl: String,
    val tokenUrl: String,
    val redirectUri: String,
    val scopes: List<String>
)

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val projectId: String? = null  // For Antigravity
)
```

#### 4.2 OAuth Manager
Create `OAuthManager` class to handle:
- PKCE code generation
- Authorization URL building
- Token exchange
- Token refresh
- Secure token storage

### 5. Provider-Specific Implementations

#### 5.1 Antigravity Provider
Reference: opencode-antigravity-auth plugin

OAuth Configuration:
- Client ID: Use Google OAuth credentials
- Scopes: cloud-platform, userinfo.email, userinfo.profile, cclog, experimentsandconfigs
- Redirect URI: http://localhost:PORT/oauth-callback
- Endpoints: cloudcode-pa.googleapis.com

System Prompt:
```
You are Antigravity, a powerful agentic AI coding assistant designed by the Google DeepMind team...
```

Features:
- Multi-account support
- Account rotation on rate limits
- Thinking budget configuration for Claude models

#### 5.2 GitHub Copilot Provider
OAuth Configuration:
- Use GitHub OAuth App credentials
- Scopes: copilot
- Endpoint: api.github.com

#### 5.3 Anthropic Provider
API Key Configuration:
- Base URL: https://api.anthropic.com
- Headers: anthropic-beta headers for features
- System prompt handling

#### 5.4 Google Gemini Provider
Dual auth support:
- API Key mode
- OAuth mode (via same flow as Antigravity)

### 6. UI Requirements

#### 6.1 Add Provider Sheet
Update the existing AddProviderSheet to support:
- Provider type selection with auth type indication
- API key input (for API_KEY auth)
- OAuth login button (for OAUTH auth)
- System prompt configuration field
- Provider-specific options

#### 6.2 OAuth Login Flow
Create in-app browser or custom tab flow:
- Open auth URL in browser/custom tab
- Handle redirect with deep link or local server
- Exchange code for tokens
- Store tokens securely

#### 6.3 Provider Settings
Show:
- Connection status
- Account email (for OAuth)
- Token expiry
- Logout/re-auth button

### 7. Token Management

#### 7.1 Secure Storage
- Store OAuth tokens encrypted with Tink
- Store alongside API keys in EncryptedPreferences
- Include token expiry tracking

#### 7.2 Token Refresh
- Auto-refresh tokens before expiry
- Handle refresh failures gracefully
- Prompt re-authentication when needed

### 8. System Prompt Handling

#### 8.1 Provider System Prompts
- Store provider-specific system prompts
- Inject automatically when making API calls
- Allow user override in settings

#### 8.2 Global System Prompt
- User's global system prompt from settings
- Combine with provider system prompt
- Order: Provider prompt first, then user prompt

## Acceptance Criteria

- [ ] Extended ProviderType enum with all new provider types
- [ ] AuthType enum created
- [ ] Provider model extended with new fields
- [ ] OAuth flow implemented with PKCE support
- [ ] OAuthManager created for token management
- [ ] EncryptedPreferences extended for OAuth tokens
- [ ] Antigravity provider fully implemented
- [ ] GitHub Copilot provider skeleton implemented
- [ ] Google Gemini provider with dual auth
- [ ] Anthropic provider with proper headers
- [ ] AddProviderSheet updated for OAuth providers
- [ ] OAuth browser flow working on Android
- [ ] System prompt injection in API calls
- [ ] Token auto-refresh working
- [ ] UI shows OAuth connection status
- [ ] Logout/re-auth functionality working

## Technical Notes

### Android OAuth Deep Linking
Use Android Custom Tabs or WebView for OAuth:
```kotlin
// In AndroidManifest.xml
<intent-filter>
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="materialchat" android:host="oauth"/>
</intent-filter>
```

### Token Storage Schema
```kotlin
// Key format for OAuth tokens
"oauth_access_{providerId}" -> encrypted access token
"oauth_refresh_{providerId}" -> encrypted refresh token
"oauth_expires_{providerId}" -> expiry timestamp
"oauth_email_{providerId}" -> account email
"oauth_project_{providerId}" -> project ID (for Antigravity)
```

### API Request Header Injection
```kotlin
// In ChatApiClient
fun buildRequest(provider: Provider, apiKey: String?, oauthToken: String?): Request {
    val builder = Request.Builder()

    // Add auth header
    when (provider.authType) {
        AuthType.API_KEY -> builder.header("Authorization", "Bearer $apiKey")
        AuthType.OAUTH -> builder.header("Authorization", "Bearer $oauthToken")
        AuthType.NONE -> { /* no auth */ }
    }

    // Add provider-specific headers
    provider.headers.forEach { (key, value) ->
        builder.header(key, value)
    }

    return builder.build()
}
```
