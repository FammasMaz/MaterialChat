# Implementation Plan - Multiple Providers Feature

## Status
- **Phase**: 5 - API Layer (next)
- **Last Updated**: 2026-01-30
- **Branch**: feature-providers
- **Progress**: ~40% (Phases 1-4 complete)

## Overview

This plan implements a multi-provider system for MaterialChat, enabling support for 6 provider types (OpenAI-compatible, Ollama, Anthropic, Google Gemini, GitHub Copilot, Antigravity) with 3 authentication methods (API Key, OAuth, None). The primary target is Antigravity integration with full OAuth/PKCE support.

---

## Critical Path Order

The phases must be implemented in this order due to dependencies:

1. **Phase 1** - Domain models (foundation for everything) ✅ COMPLETE
2. **Phase 2** - Database layer (persistence) ✅ COMPLETE
3. **Phase 3** - Security infrastructure (PKCE, encrypted storage) ✅ COMPLETE
4. **Phase 4** - OAuth Manager (OAuth flow orchestration) ✅ COMPLETE
5. **Phase 5** - API layer (Antigravity chat)
6. **Phase 6** - Repository updates (bridge to UI)
7. **Phase 7** - UI layer (user-facing OAuth flow)
8. **Phase 8** - DI updates (wire everything together)
9. **Phase 9** - Built-in providers (polish)
10. **Phase 10** - Testing (quality assurance)

---

---

## Phase 5: API Layer

### 5.1 Antigravity DTOs
- [ ] **Create Antigravity request DTOs** - Gemini-style format
- [ ] **Create Antigravity response DTOs** - Streaming and non-streaming

### 5.2 Anthropic DTOs (Lower Priority)
- [ ] **Create Anthropic request/response DTOs** - Native Claude format

### 5.3 Antigravity API Client
- [ ] **Create AntigravityApiClient** - Antigravity-specific API calls

### 5.4 ChatApiClient Updates
- [x] **Extend ChatApiClient** - Route by provider type ✅
  - Added placeholder cases for ANTIGRAVITY, ANTHROPIC, GOOGLE_GEMINI, GITHUB_COPILOT
- [x] **Extend ModelListApiClient** - Support new provider types ✅
  - Added static model lists for new provider types

### 5.5 Streaming Parser Updates
- [ ] **Create AntigravityStreamParser** - Gemini-style SSE parsing
- [ ] **Extend SseEventParser** - Route by provider type

---

## Phase 6: Repository Layer

- [ ] **Extend ProviderRepository interface** - OAuth operations
- [ ] **Update ProviderRepositoryImpl** - Implement OAuth operations

---

## Phase 7: UI Layer - OAuth

- [ ] **Update AndroidManifest.xml** - OAuth callback deep link
- [ ] **Create OAuthCallbackActivity** - Handle OAuth deep link callback
- [ ] **Create OAuthViewModel** - OAuth flow state management
- [ ] **Create OAuthLoginButton** - OAuth sign-in button
- [ ] **Create OAuthStatusIndicator** - OAuth status chip/badge
- [x] **Update AddProviderSheet** - Support OAuth providers ✅
  - Added all provider types to when expressions
- [x] **Update ProviderCard** - Show OAuth status ✅
  - Added all provider types to when expressions

---

## Phase 8: Dependency Injection

- [ ] **Add androidx.browser dependency** - Custom Tabs for OAuth
- [ ] **Create AuthModule** - OAuth-related bindings
- [ ] **Update NetworkModule** - Antigravity client

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

// Need to add:
implementation(libs.androidx.browser)               // Custom Tabs for OAuth (add browser = "1.8.0" to libs.versions.toml)
```

### Migration Strategy
- Database migration is additive (new columns with defaults)
- Existing providers continue working (default authType = API_KEY)
- No breaking changes to existing UI until OAuth features are stable
