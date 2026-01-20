# MaterialChat - Project Plan

## Overview

Building a native Android AI chat application with Material 3 Expressive design. The app supports multiple AI providers (OpenAI-compatible and Ollama) with real-time streaming responses.

**Reference:** `PRD.md`

---

## Task List

```json
[
  {
    "id": "setup-01",
    "category": "setup",
    "description": "Create Gradle build configuration with all dependencies",
    "steps": [
      "Create settings.gradle.kts with project name",
      "Create gradle/libs.versions.toml with all dependency versions",
      "Create build.gradle.kts (project level) with plugin declarations",
      "Create app/build.gradle.kts with all dependencies",
      "Create gradle.properties with optimization settings",
      "Run ./gradlew tasks to verify Gradle sync"
    ],
    "passes": true
  },
  {
    "id": "setup-02",
    "category": "setup",
    "description": "Create Android manifest and application entry points",
    "steps": [
      "Create AndroidManifest.xml with INTERNET permission",
      "Create MaterialChatApplication.kt with @HiltAndroidApp",
      "Create MainActivity.kt with basic Compose setup",
      "Verify app compiles with ./gradlew assembleDebug"
    ],
    "passes": true
  },
  {
    "id": "domain-01",
    "category": "domain",
    "description": "Create domain layer models",
    "steps": [
      "Create domain/model/ProviderType.kt enum",
      "Create domain/model/Provider.kt data class",
      "Create domain/model/MessageRole.kt enum",
      "Create domain/model/Message.kt data class",
      "Create domain/model/Conversation.kt data class",
      "Create domain/model/AiModel.kt data class",
      "Create domain/model/StreamingState.kt sealed class"
    ],
    "passes": true
  },
  {
    "id": "domain-02",
    "category": "domain",
    "description": "Create domain layer repository interfaces",
    "steps": [
      "Create domain/repository/ChatRepository.kt interface",
      "Create domain/repository/ConversationRepository.kt interface",
      "Create domain/repository/ProviderRepository.kt interface"
    ],
    "passes": true
  },
  {
    "id": "data-local-01",
    "category": "data",
    "description": "Create Room database entities",
    "steps": [
      "Create data/local/database/entity/ProviderEntity.kt",
      "Create data/local/database/entity/ConversationEntity.kt",
      "Create data/local/database/entity/MessageEntity.kt"
    ],
    "passes": true
  },
  {
    "id": "data-local-02",
    "category": "data",
    "description": "Create Room DAOs",
    "steps": [
      "Create data/local/database/dao/ProviderDao.kt with CRUD operations",
      "Create data/local/database/dao/ConversationDao.kt with CRUD operations",
      "Create data/local/database/dao/MessageDao.kt with CRUD operations"
    ],
    "passes": true
  },
  {
    "id": "data-local-03",
    "category": "data",
    "description": "Create Room database and preferences",
    "steps": [
      "Create data/local/database/MaterialChatDatabase.kt",
      "Create data/local/preferences/AppPreferences.kt for non-sensitive data",
      "Create data/local/preferences/EncryptedPreferences.kt with Tink encryption"
    ],
    "passes": true
  },
  {
    "id": "data-remote-01",
    "category": "data",
    "description": "Create API DTOs",
    "steps": [
      "Create data/remote/dto/OpenAiModels.kt with request/response classes",
      "Create data/remote/dto/OllamaModels.kt with request/response classes",
      "Create data/remote/api/StreamingEvent.kt sealed class"
    ],
    "passes": true
  },
  {
    "id": "data-remote-02",
    "category": "data",
    "description": "Create SSE parser and streaming client",
    "steps": [
      "Create data/remote/sse/SseEventParser.kt for parsing SSE events",
      "Implement parseOpenAiEvent() for OpenAI SSE format",
      "Implement parseOllamaEvent() for Ollama NDJSON format"
    ],
    "passes": true
  },
  {
    "id": "data-remote-03",
    "category": "data",
    "description": "Create API clients",
    "steps": [
      "Create data/remote/api/ChatApiClient.kt with OkHttp",
      "Implement streamOpenAiChat() with callbackFlow",
      "Implement streamOllamaChat() with callbackFlow",
      "Create data/remote/api/ModelListApiClient.kt for fetching models"
    ],
    "passes": true
  },
  {
    "id": "data-repo-01",
    "category": "data",
    "description": "Create entity mappers",
    "steps": [
      "Create data/mapper/EntityMappers.kt",
      "Implement Provider entity/domain mappers",
      "Implement Conversation entity/domain mappers",
      "Implement Message entity/domain mappers"
    ],
    "passes": true
  },
  {
    "id": "data-repo-02",
    "category": "data",
    "description": "Create repository implementations",
    "steps": [
      "Create data/repository/ProviderRepositoryImpl.kt",
      "Create data/repository/ConversationRepositoryImpl.kt",
      "Create data/repository/ChatRepositoryImpl.kt"
    ],
    "passes": true
  },
  {
    "id": "di-01",
    "category": "di",
    "description": "Create Hilt dependency injection modules",
    "steps": [
      "Create di/AppModule.kt for app-wide dependencies",
      "Create di/DatabaseModule.kt for Room database and DAOs",
      "Create di/NetworkModule.kt for OkHttpClient and API clients",
      "Create di/RepositoryModule.kt for repository bindings"
    ],
    "passes": true
  },
  {
    "id": "domain-usecase-01",
    "category": "domain",
    "description": "Create use cases",
    "steps": [
      "Create domain/usecase/SendMessageUseCase.kt",
      "Create domain/usecase/RegenerateResponseUseCase.kt",
      "Create domain/usecase/GetConversationsUseCase.kt",
      "Create domain/usecase/CreateConversationUseCase.kt",
      "Create domain/usecase/ExportConversationUseCase.kt",
      "Create domain/usecase/ManageProvidersUseCase.kt"
    ],
    "passes": true
  },
  {
    "id": "ui-theme-01",
    "category": "ui",
    "description": "Create Material 3 Expressive theme",
    "steps": [
      "Create ui/theme/Color.kt with light/dark color schemes",
      "Create ui/theme/Typography.kt with expressive font scales",
      "Create ui/theme/Shapes.kt with M3 shape system",
      "Create ui/theme/Motion.kt with spring physics specs",
      "Create ui/theme/Theme.kt with MaterialChatTheme composable",
      "Implement dynamic color support for Android 12+"
    ],
    "passes": true
  },
  {
    "id": "ui-nav-01",
    "category": "ui",
    "description": "Create navigation structure",
    "steps": [
      "Create ui/navigation/Screen.kt with route definitions",
      "Create ui/navigation/MaterialChatNavHost.kt",
      "Update MainActivity to use MaterialChatTheme and NavHost"
    ],
    "passes": true
  },
  {
    "id": "ui-conversations-01",
    "category": "ui",
    "description": "Create conversations screen",
    "steps": [
      "Create ui/screens/conversations/ConversationsUiState.kt",
      "Create ui/screens/conversations/ConversationsViewModel.kt",
      "Create ui/screens/conversations/ConversationsScreen.kt",
      "Create ui/screens/conversations/components/ConversationItem.kt",
      "Create ui/screens/conversations/components/SwipeToDeleteBox.kt"
    ],
    "passes": true
  },
  {
    "id": "ui-conversations-02",
    "category": "ui",
    "description": "Implement conversations screen features",
    "steps": [
      "Implement conversation list with LazyColumn",
      "Implement empty state UI with illustration",
      "Implement swipe-to-delete with undo snackbar",
      "Implement Extended FAB for new chat",
      "Implement navigation to settings"
    ],
    "passes": true
  },
  {
    "id": "ui-chat-01",
    "category": "ui",
    "description": "Create chat screen structure",
    "steps": [
      "Create ui/screens/chat/ChatUiState.kt",
      "Create ui/screens/chat/ChatViewModel.kt",
      "Create ui/screens/chat/ChatScreen.kt",
      "Create ui/screens/chat/components/ChatTopBar.kt"
    ],
    "passes": true
  },
  {
    "id": "ui-chat-02",
    "category": "ui",
    "description": "Create chat message components",
    "steps": [
      "Create ui/screens/chat/components/MessageBubble.kt",
      "Create ui/screens/chat/components/MessageInput.kt",
      "Create ui/screens/chat/components/StreamingIndicator.kt",
      "Implement message list with proper styling"
    ],
    "passes": true
  },
  {
    "id": "ui-chat-03",
    "category": "ui",
    "description": "Implement chat functionality",
    "steps": [
      "Implement send message flow in ViewModel",
      "Implement streaming display with partial updates",
      "Implement auto-scroll to bottom on new message",
      "Implement input field disabled during streaming"
    ],
    "passes": true
  },
  {
    "id": "ui-markdown-01",
    "category": "ui",
    "description": "Implement Markdown rendering",
    "steps": [
      "Add compose-markdown library or implement custom parser",
      "Create ui/components/MarkdownText.kt composable",
      "Implement code block rendering with syntax highlighting",
      "Implement bold, italic, links, lists support",
      "Apply markdown rendering to assistant message bubbles"
    ],
    "passes": true
  },
  {
    "id": "ui-chat-04",
    "category": "ui",
    "description": "Implement chat actions",
    "steps": [
      "Create ui/screens/chat/components/MessageActions.kt",
      "Implement copy button with clipboard manager",
      "Implement regenerate button on last assistant message",
      "Add snackbar feedback for copy action"
    ],
    "passes": true
  },
  {
    "id": "ui-chat-05",
    "category": "ui",
    "description": "Implement model picker",
    "steps": [
      "Create ui/screens/chat/components/ModelPickerDropdown.kt",
      "Fetch models from provider on dropdown open",
      "Implement model switching in ChatViewModel",
      "Show confirmation snackbar on model change"
    ],
    "passes": true
  },
  {
    "id": "ui-chat-06",
    "category": "ui",
    "description": "Implement export functionality",
    "steps": [
      "Add export menu item to ChatTopBar",
      "Create export format selection bottom sheet",
      "Implement JSON export format",
      "Implement Markdown export format",
      "Implement share intent for exported file"
    ],
    "passes": true
  },
  {
    "id": "ui-settings-01",
    "category": "ui",
    "description": "Create settings screen structure",
    "steps": [
      "Create ui/screens/settings/SettingsUiState.kt",
      "Create ui/screens/settings/SettingsViewModel.kt",
      "Create ui/screens/settings/SettingsScreen.kt"
    ],
    "passes": false
  },
  {
    "id": "ui-settings-02",
    "category": "ui",
    "description": "Create settings components",
    "steps": [
      "Create ui/screens/settings/components/ProviderCard.kt",
      "Create ui/screens/settings/components/AddProviderSheet.kt",
      "Create ui/screens/settings/components/SystemPromptField.kt"
    ],
    "passes": false
  },
  {
    "id": "ui-settings-03",
    "category": "ui",
    "description": "Implement settings features",
    "steps": [
      "Implement provider list display",
      "Implement add provider bottom sheet with form validation",
      "Implement edit provider functionality",
      "Implement delete provider with confirmation",
      "Implement system prompt text field",
      "Implement theme selector (System/Light/Dark)",
      "Implement dynamic color toggle"
    ],
    "passes": false
  },
  {
    "id": "ui-shared-01",
    "category": "ui",
    "description": "Create shared expressive components",
    "steps": [
      "Create ui/components/ExpressiveButton.kt with shape morph",
      "Create ui/components/ExpressiveLoadingIndicator.kt with wavy animation"
    ],
    "passes": false
  },
  {
    "id": "integration-01",
    "category": "integration",
    "description": "Wire up complete chat flow",
    "steps": [
      "Connect ChatViewModel to SendMessageUseCase",
      "Test sending message and receiving streaming response",
      "Verify messages persist in Room database",
      "Verify conversation list updates after new message"
    ],
    "passes": false
  },
  {
    "id": "integration-02",
    "category": "integration",
    "description": "Wire up provider management",
    "steps": [
      "Connect SettingsViewModel to ProviderRepository",
      "Test adding new provider with API key",
      "Verify API key is encrypted in DataStore",
      "Test switching active provider"
    ],
    "passes": false
  },
  {
    "id": "polish-01",
    "category": "polish",
    "description": "Seed default data and polish",
    "steps": [
      "Seed OpenAI and Ollama provider templates on first launch",
      "Set default system prompt in preferences",
      "Add app icon and splash screen",
      "Review all animations for 60fps smoothness"
    ],
    "passes": false
  },
  {
    "id": "testing-01",
    "category": "testing",
    "description": "Create and run tests",
    "steps": [
      "Write unit tests for SseEventParser",
      "Write unit tests for SendMessageUseCase",
      "Write tests for entity mappers",
      "Run all tests with ./gradlew test"
    ],
    "passes": false
  },
  {
    "id": "build-01",
    "category": "build",
    "description": "Final build verification",
    "steps": [
      "Run ./gradlew assembleDebug and verify APK generated",
      "Install APK on device or emulator",
      "Test complete flow: add provider -> create chat -> send message -> receive response",
      "Verify no crashes during normal usage",
      "Take screenshot of working app"
    ],
    "passes": false
  }
]
```

---

## Agent Instructions

1. Read `activity.md` first to understand current state
2. Find next task with `"passes": false`
3. Complete all steps for that task
4. Verify the implementation compiles (./gradlew assembleDebug if applicable)
5. Update task to `"passes": true`
6. Log completion in `activity.md` with timestamp and what was done
7. Make one git commit for that task only with a clear message
8. Repeat until all tasks pass

**Important Rules:**
- Only modify the `passes` field in this plan
- Do not remove or rewrite tasks
- Do not git init, change remotes, or push
- Work on exactly ONE task per iteration
- Always verify compilation after code changes

---

## Completion Criteria

All tasks marked with `"passes": true`

When all tasks are passing, output:
```
<promise>COMPLETE</promise>
```

---

## File Reference

- **PRD.md** - Full product requirements
- **SPEC.md** - Technical specification
- **TODOs.md** - Detailed checkbox list
- **activity.md** - Session activity log

---

*Last updated: 2026-01-20*
