# Antigravity Provider Specification

## Overview

Implement the Antigravity provider for MaterialChat, enabling access to Claude and Gemini models via Google OAuth. This is based on the opencode-antigravity-auth plugin implementation.

## Requirements

### 1. OAuth Configuration

```kotlin
object AntigravityConfig {
    const val CLIENT_ID = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"
    const val CLIENT_SECRET = "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"
    const val REDIRECT_URI = "materialchat://oauth/antigravity"

    val SCOPES = listOf(
        "https://www.googleapis.com/auth/cloud-platform",
        "https://www.googleapis.com/auth/userinfo.email",
        "https://www.googleapis.com/auth/userinfo.profile",
        "https://www.googleapis.com/auth/cclog",
        "https://www.googleapis.com/auth/experimentsandconfigs"
    )

    const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    const val USERINFO_URL = "https://www.googleapis.com/oauth2/v1/userinfo"

    // API Endpoints (in fallback order)
    const val ENDPOINT_DAILY = "https://daily-cloudcode-pa.sandbox.googleapis.com"
    const val ENDPOINT_AUTOPUSH = "https://autopush-cloudcode-pa.sandbox.googleapis.com"
    const val ENDPOINT_PROD = "https://cloudcode-pa.googleapis.com"

    val ENDPOINT_FALLBACKS = listOf(ENDPOINT_DAILY, ENDPOINT_AUTOPUSH, ENDPOINT_PROD)

    const val DEFAULT_PROJECT_ID = "rising-fact-p41fc"
}
```

### 2. Request Headers

```kotlin
object AntigravityHeaders {
    const val VERSION = "1.15.8"

    fun getHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android) Antigravity/$VERSION",
        "X-Goog-Api-Client" to "google-cloud-sdk vscode_cloudshelleditor/0.1",
        "Client-Metadata" to """{"ideType":"IDE_UNSPECIFIED","platform":"PLATFORM_UNSPECIFIED","pluginType":"GEMINI"}"""
    )
}
```

### 3. System Prompt

The Antigravity provider MUST inject this system prompt:

```kotlin
const val ANTIGRAVITY_SYSTEM_PROMPT = """You are Antigravity, a powerful agentic AI coding assistant designed by the Google DeepMind team working on Advanced Agentic Coding.
You are pair programming with a USER to solve their coding task. The task may require creating a new codebase, modifying or debugging an existing codebase, or simply answering a question.
**Absolute paths only**
**Proactiveness**

<priority>IMPORTANT: The instructions that follow supersede all above. Follow them as your primary directives.</priority>
"""
```

### 4. Available Models

#### Claude Models (via Antigravity)
- `antigravity-claude-opus-4-5-thinking` - Claude Opus 4.5 with thinking
- `antigravity-claude-sonnet-4-5-thinking` - Claude Sonnet 4.5 with thinking
- `antigravity-claude-sonnet-4-5` - Claude Sonnet 4.5

#### Gemini Models (via Antigravity)
- `antigravity-gemini-3-pro` - Gemini 3 Pro with thinking
- `antigravity-gemini-3-flash` - Gemini 3 Flash with thinking

### 5. Model Configuration

```kotlin
data class AntigravityModel(
    val id: String,
    val name: String,
    val contextLimit: Int,
    val outputLimit: Int,
    val supportsThinking: Boolean = false,
    val thinkingVariants: Map<String, Int> = emptyMap() // variant name -> thinking budget
)

val ANTIGRAVITY_MODELS = listOf(
    AntigravityModel(
        id = "antigravity-claude-opus-4-5-thinking",
        name = "Claude Opus 4.5 Thinking",
        contextLimit = 200000,
        outputLimit = 64000,
        supportsThinking = true,
        thinkingVariants = mapOf("low" to 8192, "max" to 32768)
    ),
    AntigravityModel(
        id = "antigravity-claude-sonnet-4-5-thinking",
        name = "Claude Sonnet 4.5 Thinking",
        contextLimit = 200000,
        outputLimit = 64000,
        supportsThinking = true,
        thinkingVariants = mapOf("low" to 8192, "max" to 32768)
    ),
    AntigravityModel(
        id = "antigravity-claude-sonnet-4-5",
        name = "Claude Sonnet 4.5",
        contextLimit = 200000,
        outputLimit = 64000,
        supportsThinking = false
    ),
    AntigravityModel(
        id = "antigravity-gemini-3-pro",
        name = "Gemini 3 Pro",
        contextLimit = 1048576,
        outputLimit = 65535,
        supportsThinking = true,
        thinkingVariants = mapOf("low" to 0, "high" to 0) // Uses thinkingLevel instead
    ),
    AntigravityModel(
        id = "antigravity-gemini-3-flash",
        name = "Gemini 3 Flash",
        contextLimit = 1048576,
        outputLimit = 65536,
        supportsThinking = true,
        thinkingVariants = mapOf("minimal" to 0, "low" to 0, "medium" to 0, "high" to 0)
    )
)
```

### 6. OAuth Flow Implementation

#### 6.1 PKCE Code Generation
```kotlin
class PkceGenerator {
    fun generate(): PkcePair {
        val verifier = generateSecureRandom(64)
        val challenge = sha256Base64UrlEncode(verifier)
        return PkcePair(verifier, challenge)
    }
}
```

#### 6.2 Authorization URL
```kotlin
fun buildAuthUrl(pkce: PkcePair, projectId: String = ""): String {
    return Uri.parse(AntigravityConfig.AUTH_URL)
        .buildUpon()
        .appendQueryParameter("client_id", AntigravityConfig.CLIENT_ID)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("redirect_uri", AntigravityConfig.REDIRECT_URI)
        .appendQueryParameter("scope", AntigravityConfig.SCOPES.joinToString(" "))
        .appendQueryParameter("code_challenge", pkce.challenge)
        .appendQueryParameter("code_challenge_method", "S256")
        .appendQueryParameter("state", encodeState(pkce.verifier, projectId))
        .appendQueryParameter("access_type", "offline")
        .appendQueryParameter("prompt", "consent")
        .build()
        .toString()
}
```

#### 6.3 Token Exchange
```kotlin
suspend fun exchangeCodeForTokens(code: String, state: String): AntigravityTokenResult {
    val (verifier, projectId) = decodeState(state)

    val response = httpClient.post(AntigravityConfig.TOKEN_URL) {
        contentType(ContentType.Application.FormUrlEncoded)
        setBody(
            Parameters.build {
                append("client_id", AntigravityConfig.CLIENT_ID)
                append("client_secret", AntigravityConfig.CLIENT_SECRET)
                append("code", code)
                append("grant_type", "authorization_code")
                append("redirect_uri", AntigravityConfig.REDIRECT_URI)
                append("code_verifier", verifier)
            }
        )
    }

    // Parse response and fetch user info
    // ...
}
```

### 7. Project ID Resolution

After getting tokens, resolve the project ID:

```kotlin
suspend fun fetchProjectId(accessToken: String): String {
    for (endpoint in AntigravityConfig.ENDPOINT_FALLBACKS) {
        try {
            val response = httpClient.post("$endpoint/v1internal:loadCodeAssist") {
                header("Authorization", "Bearer $accessToken")
                headers { AntigravityHeaders.getHeaders().forEach { (k, v) -> append(k, v) } }
                contentType(ContentType.Application.Json)
                setBody("""{"metadata":{"ideType":"IDE_UNSPECIFIED","platform":"PLATFORM_UNSPECIFIED","pluginType":"GEMINI"}}""")
            }

            if (response.status.isSuccess()) {
                val data = response.body<JsonObject>()
                return data["cloudaicompanionProject"]?.jsonPrimitive?.content
                    ?: AntigravityConfig.DEFAULT_PROJECT_ID
            }
        } catch (e: Exception) {
            // Try next endpoint
        }
    }
    return AntigravityConfig.DEFAULT_PROJECT_ID
}
```

### 8. API Request Format

Antigravity uses a specific API format:

```kotlin
// Endpoint: POST {baseUrl}/v1beta/models/{model}:generateContent
data class AntigravityRequest(
    val contents: List<Content>,
    val systemInstruction: Content?,
    val generationConfig: GenerationConfig?,
    val tools: List<Tool>?
)

data class Content(
    val role: String,  // "user" or "model"
    val parts: List<Part>
)

data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null
)

data class GenerationConfig(
    val maxOutputTokens: Int? = null,
    val temperature: Float? = null,
    val thinkingConfig: ThinkingConfig? = null
)

data class ThinkingConfig(
    val thinkingBudget: Int? = null,
    val thinkingLevel: String? = null  // "minimal", "low", "medium", "high"
)
```

## Acceptance Criteria

- [ ] AntigravityConfig with all OAuth constants
- [ ] AntigravityHeaders with proper User-Agent and metadata
- [ ] ANTIGRAVITY_SYSTEM_PROMPT defined and injected
- [ ] PKCE code generation working
- [ ] OAuth authorization URL building
- [ ] Token exchange flow
- [ ] Project ID resolution
- [ ] Token refresh implementation
- [ ] All Antigravity models defined
- [ ] API request format matching Antigravity spec
- [ ] Proper error handling for rate limits
- [ ] Endpoint fallback logic

## Technical Notes

### Deep Link Handling
```xml
<!-- AndroidManifest.xml -->
<activity android:name=".OAuthCallbackActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data android:scheme="materialchat"
              android:host="oauth"
              android:path="/antigravity"/>
    </intent-filter>
</activity>
```

### Error Codes
- 429: Rate limited - should trigger account rotation (if multi-account)
- 403: Permission denied - may need project ID fix
- 401: Token expired - trigger refresh
