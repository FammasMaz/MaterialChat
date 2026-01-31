# Implementation Plan - Multiple Providers Feature

## Status
- **Phase**: 9 - Default Providers & Onboarding (next)
- **Last Updated**: 2026-01-31
- **Branch**: feature-providers
- **Progress**: ~80% (Phases 1-8 complete)

## Overview

This plan implements a multi-provider system for MaterialChat, enabling support for 6 provider types (OpenAI-compatible, Ollama, Anthropic, Google Gemini, GitHub Copilot, Antigravity) with 3 authentication methods (API Key, OAuth, None). The primary target is Antigravity integration with full OAuth/PKCE support.

---

## Critical Path Order

The phases must be implemented in this order due to dependencies:

1. **Phase 1** - Domain models (foundation for everything) ✅ COMPLETE
2. **Phase 2** - Database layer (persistence) ✅ COMPLETE
3. **Phase 3** - Security infrastructure (PKCE, encrypted storage) ✅ COMPLETE
4. **Phase 4** - OAuth Manager (OAuth flow orchestration) ✅ COMPLETE
5. **Phase 5** - API layer (Antigravity chat) ✅ COMPLETE
6. **Phase 6** - Repository updates (bridge to UI) ✅ COMPLETE
7. **Phase 7** - UI layer (user-facing OAuth flow) ✅ COMPLETE
8. **Phase 8** - DI updates (wire everything together) ✅ COMPLETE
9. **Phase 9** - Built-in providers (polish)
10. **Phase 10** - Testing (quality assurance)

---

---

## Phase 9: Default Providers & Onboarding

- [ ] **Create BuiltInProviders object** - Predefined provider configurations
- [ ] **Update seedDefaultProviders** - Include Antigravity option

---

## Phase 10: Testing

- [ ] Unit tests for domain models
- [ ] Unit tests for PKCE
- [ ] Unit tests for OAuth
- [ ] Unit tests for API layer
- [ ] Integration tests
- [ ] UI tests

---

## Completed

### Phase 8: Dependency Injection ✅ COMPLETE

- [x] **Add androidx.browser dependency** - Custom Tabs for OAuth ✅
  - Added browser = "1.8.0" to libs.versions.toml
- [x] **Create AuthModule** - OAuth-related bindings ✅ (pulled forward for Phase 6)
  - File: `app/src/main/java/com/materialchat/di/AuthModule.kt`
  - Provides: OAuthManager, AntigravityOAuth
  - Uses @StandardClient OkHttpClient for auth requests
- [x] **Update NetworkModule** - Antigravity client ✅
  - File: `app/src/main/java/com/materialchat/di/NetworkModule.kt`
  - Added AntigravityApiClient provider with StreamingClient
  - Updated ChatApiClient provider to include AntigravityApiClient

---

### Phase 7: UI Layer - OAuth ✅ COMPLETE

- [x] **Update AndroidManifest.xml** - OAuth callback deep link ✅
  - Added OAuthCallbackActivity with intent-filter for `materialchat://oauth/antigravity`
- [x] **Create OAuthCallbackActivity** - Handle OAuth deep link callback ✅
  - File: `app/src/main/java/com/materialchat/ui/oauth/OAuthCallbackActivity.kt`
- [x] **Create OAuthViewModel** - OAuth flow state management ✅
  - File: `app/src/main/java/com/materialchat/ui/oauth/OAuthViewModel.kt`
- [x] **Create OAuthLoginButton** - OAuth sign-in button ✅
  - File: `app/src/main/java/com/materialchat/ui/oauth/OAuthLoginButton.kt`
  - M3 Expressive button with spring-physics animations
- [x] **Create OAuthStatusIndicator** - OAuth status chip/badge ✅
  - File: `app/src/main/java/com/materialchat/ui/oauth/OAuthStatusIndicator.kt`
- [x] **Update AddProviderSheet** - Support OAuth providers ✅
  - Added all provider types to when expressions
- [x] **Update ProviderCard** - Show OAuth status ✅
  - Added all provider types to when expressions

---

### Phase 6: Repository Layer ✅ COMPLETE

#### 6.1 ProviderRepository Interface Extensions
- [x] **Extend ProviderRepository interface** - OAuth operations
  - File: `app/src/main/java/com/materialchat/domain/repository/ProviderRepository.kt`
  - Added: `OAuthAuthorizationRequest` data class
  - Added: `buildOAuthAuthorizationUrl()` - Build OAuth authorization URL
  - Added: `handleOAuthCallback()` - Handle OAuth callback
  - Added: `getOAuthState()`, `observeOAuthState()` - OAuth state management
  - Added: `getOAuthAccessToken()`, `hasValidOAuthTokens()` - Token access
  - Added: `logoutOAuth()` - Sign out of OAuth
  - Added: `getOAuthEmail()`, `getOAuthProjectId()` - OAuth metadata

#### 6.2 ProviderRepositoryImpl Updates
- [x] **Update ProviderRepositoryImpl** - Implement OAuth operations
  - File: `app/src/main/java/com/materialchat/data/repository/ProviderRepositoryImpl.kt`
  - Added: OAuthManager injection
  - Implemented all OAuth operations delegating to OAuthManager
  - Bridges domain layer to OAuth infrastructure

#### 6.3 DI Updates (Pulled from Phase 8)
- [x] **Create AuthModule** - OAuth-related DI bindings
  - File: `app/src/main/java/com/materialchat/di/AuthModule.kt`
  - Provides: OAuthManager singleton
  - Provides: AntigravityOAuth singleton
  - Uses @StandardClient OkHttpClient for auth requests
- [x] **Add @Inject constructor to PkceGenerator** - Enable DI
  - File: `app/src/main/java/com/materialchat/data/auth/PkceGenerator.kt`
  - Added: @Singleton and @Inject constructor annotations

---

### Phase 5: API Layer ✅ COMPLETE

#### 5.1 Antigravity DTOs
- [x] **Create AntigravityModels.kt** - Complete Gemini-style DTOs
  - File: `app/src/main/java/com/materialchat/data/remote/dto/AntigravityModels.kt`
  - Request: `AntigravityRequest`, `AntigravityContent`, `AntigravityPart`, `AntigravitySystemInstruction`, `AntigravityGenerationConfig`, `AntigravityThinkingConfig`, `AntigravitySafetySetting`, `AntigravityInlineData`
  - Response: `AntigravityResponse`, `AntigravityCandidate`, `AntigravityStreamChunk`, `AntigravitySafetyRating`, `AntigravityUsageMetadata`, `AntigravityPromptFeedback`
  - Errors: `AntigravityErrorResponse`, `AntigravityError`, `AntigravityErrorDetail`
  - Helpers: `AntigravityConverters` for OpenAI-to-Antigravity format conversion

#### 5.2 Antigravity API Client
- [x] **Create AntigravityApiClient** - Complete streaming and non-streaming support
  - File: `app/src/main/java/com/materialchat/data/remote/api/AntigravityApiClient.kt`
  - `streamChat()`: OAuth-authenticated streaming chat with thinking support
  - `generateSimpleCompletion()`: Non-streaming completions
  - Automatic token refresh via OAuthManager
  - Project info caching with endpoint fallback
  - Model ID mapping to Antigravity API format

#### 5.3 Streaming Parser Updates
- [x] **Extend SseEventParser** - Antigravity SSE parsing
  - File: `app/src/main/java/com/materialchat/data/remote/sse/SseEventParser.kt`
  - Added: `parseAntigravityEvent()`, `parseAntigravityChunk()`, `parseAntigravityEvents()`
  - Handles `candidates[].content.parts[]` with `thought` field for thinking content
  - Error handling: `AntigravityErrorWrapper`

#### 5.4 ChatApiClient Updates
- [x] **Update ChatApiClient** - Updated TODO comments
  - Provider routing prepared for Phase 8 DI wiring
  - AntigravityApiClient is ready but not yet injected

---

### Phase 2: Database Layer ✅ COMPLETE

#### 2.1 Entity Updates
- [x] **Extend ProviderEntity** - Add new columns
  - File: `app/src/main/java/com/materialchat/data/local/database/entity/ProviderEntity.kt`
  - Added: `authType`, `systemPrompt`, `headersJson`, `optionsJson`, `supportsStreaming`, `supportsImages`

#### 2.2 DAO Updates
- [x] **Extend ProviderDao** - Add auth-type queries
  - File: `app/src/main/java/com/materialchat/data/local/database/dao/ProviderDao.kt`
  - Added: `getProvidersByAuthType()`, `getOAuthProviders()`, `getApiKeyProviders()`

#### 2.3 Database Migration
- [x] **Create Migration_5_6** - Add new provider columns
  - File: `app/src/main/java/com/materialchat/data/local/database/MaterialChatDatabase.kt`
  - Database version updated to 6

#### 2.4 Mappers
- [x] **Update EntityMappers** - Map new Provider fields
  - File: `app/src/main/java/com/materialchat/data/mapper/EntityMappers.kt`
  - Added JSON serialization for headers and options

---

### Phase 3: Security Infrastructure ✅ COMPLETE

#### 3.1 PKCE Implementation
- [x] **Create PkceGenerator** - PKCE code verifier and challenge (RFC 7636)
  - File: `app/src/main/java/com/materialchat/data/auth/PkceGenerator.kt`
  - Uses SecureRandom for cryptographically secure values
  - Base64 URL-safe encoding without padding
- [x] **Create PkceState data class** - Track PKCE session
  - File: `app/src/main/java/com/materialchat/data/auth/PkceState.kt`
  - Includes 10-minute expiration for security

#### 3.2 Encrypted Storage Extensions
- [x] **Extend EncryptedPreferences** - OAuth token methods
  - File: `app/src/main/java/com/materialchat/data/local/preferences/EncryptedPreferences.kt`
  - Added: `setAccessToken`, `getAccessToken`, `setRefreshToken`, `getRefreshToken`
  - Added: `setTokenExpiry`, `getTokenExpiry`, `setOAuthEmail`, `getOAuthEmail`
  - Added: `setOAuthProjectId`, `getOAuthProjectId`, `clearOAuthTokens`, `hasValidTokens`

---

### Phase 4: OAuth Manager ✅ COMPLETE

#### 4.1 Core OAuth Manager
- [x] **Create OAuthManager** - Central OAuth orchestration
  - File: `app/src/main/java/com/materialchat/data/auth/OAuthManager.kt`
  - Thread-safe singleton with ConcurrentHashMap for active sessions
  - Handles: authorization URL building, callback processing, token exchange, refresh
  - Uses Mutex for synchronized token refresh
- [x] **Create OAuthException sealed class** - OAuth-specific errors
  - File: `app/src/main/java/com/materialchat/data/auth/OAuthException.kt`
  - Error types: InvalidState, TokenExchangeFailed, RefreshFailed, NetworkError, UserCancelled, InvalidCallback, UnsupportedProvider, UserInfoFailed
  - Includes `isRecoverable` property for UI handling

#### 4.2 Antigravity-Specific OAuth
- [x] **Create AntigravityOAuth** - Antigravity helper functions
  - File: `app/src/main/java/com/materialchat/data/auth/AntigravityOAuth.kt`
  - Fetches user info from Google userinfo API
  - Resolves project ID with endpoint fallback logic
  - Builds authenticated request headers
  - Constructs Antigravity API URLs

---

### Phase 1: Domain Foundation ✅ COMPLETE

#### 1.1 Core Enums
- [x] **Extend ProviderType enum** - Added ANTHROPIC, GOOGLE_GEMINI, GITHUB_COPILOT, ANTIGRAVITY
- [x] **Create AuthType enum** - NONE, API_KEY, OAUTH

#### 1.2 OAuth Domain Models
- [x] **Create OAuthConfig data class** - OAuth provider configuration
- [x] **Create OAuthTokens data class** - Token storage with isExpired(), needsRefresh()
- [x] **Create OAuthState sealed class** - Unauthenticated, Authenticating, Authenticated, Error

#### 1.3 Extended Provider Model
- [x] **Extend Provider data class** - Added authType, systemPrompt, headers, options, supportsStreaming, supportsImages, supportsPdf, supportsReasoning
- [x] **Added provider templates** - antigravityTemplate(), anthropicTemplate(), geminiTemplate()

#### 1.4 Extended Model Definition
- [x] **Extend AiModel data class** - Added contextWindow, maxOutputTokens, supportsThinking, maxThinkingTokens, supportsImages, supportsTools

#### 1.5 Antigravity Configuration
- [x] **Create AntigravityConfig object** - Full OAuth config, endpoints, headers, system instruction, predefined models

---

## Bug Fixes Applied

1. **Fixed regex pattern in GenerateConversationTitleUseCase** - Invalid `\U` escape sequences causing PatternSyntaxException
2. **Fixed SseEventParser test** - Test expected Done but implementation returns KeepAlive (by design, relies on [DONE] marker)
3. **Fixed SendMessageUseCaseTest** - Updated to match current use case signature (added appPreferences, generateConversationTitleUseCase, applicationScope, reasoningEffort)

---

## Notes

### Key Technical Decisions
1. **PKCE over implicit flow** - Required for mobile OAuth security (RFC 7636)
2. **Android Custom Tabs for OAuth** - Better UX than WebView
3. **Deep link scheme**: `materialchat://oauth/{provider}` - Clean callback handling
4. **Encrypted SharedPreferences for tokens** - Tink AES-256-GCM already in use
5. **Gemini-style API for Antigravity** - NOT OpenAI-compatible; needs dedicated client
6. **Provider system prompt injection order**: Provider prompt BEFORE user's global prompt

### Antigravity API Format (vs OpenAI)
| Aspect | OpenAI | Antigravity |
|--------|--------|-------------|
| Messages | `messages` array | `contents` array with `parts` |
| Roles | system/user/assistant | user/model (system separate) |
| System prompt | In messages | `systemInstruction` field |
| Endpoint | `/v1/chat/completions` | `/v1internal:generateContent` |
| Streaming | `data: {delta}` | `data: {candidates}` |

### Dependencies
```kotlin
// Already present:
implementation(libs.tink.android)                    // Encryption - ALREADY PRESENT
implementation(libs.kotlinx.serialization.json)     // DTO serialization - ALREADY PRESENT

// Added:
implementation(libs.androidx.browser)               // Custom Tabs for OAuth - ADDED (browser = "1.8.0" in libs.versions.toml)
```

### Migration Strategy
- Database migration is additive (new columns with defaults)
- Existing providers continue working (default authType = API_KEY)
- No breaking changes to existing UI until OAuth features are stable
