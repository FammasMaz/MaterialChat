# MaterialChat

A native Android AI chat application built with Jetpack Compose and Material 3 Expressive design. Connect to any OpenAI-compatible API or run local models with Ollama.

## Features

### Multi-Provider AI Chat

- **OpenAI-Compatible APIs** - OpenAI, OpenRouter, Groq, Together, Anthropic proxies, and more
- **Ollama Integration** - Run local LLMs privately on your device or network
- **Real-Time Streaming** - SSE for OpenAI-compatible endpoints, NDJSON for Ollama native
- **Model Switching** - Change AI models mid-conversation
- **Reasoning Effort Controls** - Adjust reasoning depth for "thinking" models

### Conversation Management

- **Persistent History** - All chats stored locally with Room database
- **Conversation Branching** - Create branches to explore alternative responses
- **Full-Text Search** - Search across all conversations and messages
- **Export Options** - Export conversations to JSON or Markdown
- **Swipe to Delete** - Quick gesture-based conversation removal with undo

### Rich Text and Code

- **Markdown Rendering** - Bold, italic, headers, lists, links, and blockquotes
- **Syntax Highlighting** - Kotlin, Java, Python, JavaScript, TypeScript, Rust, Go, Swift, Bash
- **Code Blocks** - Styled code with copy functionality
- **Inline Code** - Monospace formatting for inline snippets

### Voice and Media

- **Voice Input** - Speech-to-text with waveform visualizer
- **Text-to-Speech** - Listen to AI responses
- **Image Attachments** - Send images to vision-capable models

### System Integration

- **Android Assistant Overlay** - Set MaterialChat as your default assistant (replaces Google Gemini on Pixel)
- **Floating Bubble** - Quick-access bubble for instant chat
- **Predictive Back Gestures** - Native Android 13+ back gesture support

### Customization

- **Dynamic Color** - Wallpaper-based theming on Android 12+
- **Theme Options** - Light, Dark, or follow System
- **Global System Prompt** - Set a default personality for all conversations
- **Haptic Feedback** - Tactile response for interactions

## Design Philosophy

MaterialChat follows **Material 3 Expressive** design guidelines, featuring:

| Element | Implementation |
|---------|----------------|
| Color | Dynamic color (Android 12+) with branded fallback palette |
| Motion | Spring-based physics (damping 0.6, stiffness 500) |
| Shapes | Rounded corners with shape-morphing on interaction |
| Messages | Role-specific styling (User: Primary Container, Assistant: Surface Variant) |

All animations target 60fps for smooth, expressive interactions.

## Supported Providers

| Provider | Type | Notes |
|----------|------|-------|
| OpenAI | OpenAI-Compatible | Requires API key |
| OpenRouter | OpenAI-Compatible | Access to 100+ models |
| Groq | OpenAI-Compatible | Ultra-fast inference |
| Together AI | OpenAI-Compatible | Open-source models |
| Anthropic Proxies | OpenAI-Compatible | Claude via compatible endpoints |
| Ollama | Native | Local inference, no API key required |

### Adding a Provider

1. Navigate to Settings
2. Tap "Add Provider"
3. Enter provider details:
   - **Name** - Display name
   - **Type** - OpenAI-Compatible or Ollama Native
   - **Base URL** - API endpoint (e.g., `https://api.openai.com`)
   - **Default Model** - Model ID (e.g., `gpt-4o`)
   - **API Key** - Required for cloud providers (stored encrypted)

## Architecture

MaterialChat uses **Clean Architecture** with **MVVM** pattern:

```
com.materialchat/
├── data/
│   ├── local/           # Room database, DataStore, encrypted preferences
│   ├── remote/          # OkHttp clients, SSE parser, DTOs
│   ├── repository/      # Repository implementations
│   └── mapper/          # Entity <-> Domain mappers
├── domain/
│   ├── model/           # Domain models (Message, Conversation, Provider)
│   ├── repository/      # Repository interfaces
│   └── usecase/         # Business logic
├── ui/
│   ├── theme/           # Material 3 Expressive theme
│   ├── navigation/      # Navigation host and routes
│   ├── screens/         # Chat, Conversations, Settings, Search
│   └── components/      # Shared UI components
├── assistant/           # Voice interaction service
└── di/                  # Hilt dependency modules
```

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.1+ |
| UI | Jetpack Compose |
| Design | Material 3 (Expressive) |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore |
| Networking | OkHttp + SSE |
| Encryption | Google Tink (AES-256-GCM) |
| Serialization | Kotlin Serialization |
| Async | Coroutines + Flow |
| Markdown | RichText Commonmark |
| Images | Coil |

## Security

API keys are encrypted at rest using industry-standard practices:

- **Algorithm**: AES-256-GCM via Google Tink
- **Key Storage**: Android Keystore (hardware-backed when available)
- **Network**: TLS 1.2+ for all HTTPS traffic
- **Local Ollama**: HTTP allowed for localhost and private IP ranges

Chat history is stored locally and never transmitted to external servers beyond your configured AI providers.

## Requirements

| Requirement | Value |
|-------------|-------|
| Android Version | 8.0 Oreo (API 26) or higher |
| Target SDK | 35 (Android 15) |
| Dynamic Color | Android 12+ (optional) |
| Assistant Overlay | Supported on all versions |

## Building from Source

### Prerequisites

- JDK 17 or higher
- Android Studio Ladybug (2024.2.1) or higher
- Android SDK with API 35

### Build Commands

```bash
# Clone the repository
git clone https://github.com/yourusername/MaterialChat.git
cd MaterialChat

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

### APK Locations

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

## Usage Tips

### Setting as Default Assistant

1. Open device Settings
2. Navigate to Apps > Default apps > Digital assistant app
3. Select MaterialChat
4. Long-press the home button or use your assistant gesture to invoke

### Using with Ollama

1. Install Ollama on your computer or server
2. Ensure Ollama is accessible from your Android device (same network)
3. Add Ollama as a provider with the base URL (e.g., `http://192.168.1.100:11434`)
4. No API key required for Ollama

### Conversation Branching

1. Long-press on any message in a conversation
2. Select "Branch from here"
3. Continue the conversation in a new direction
4. View branches from the conversation list

## Performance

| Metric | Target |
|--------|--------|
| Cold Start | < 2 seconds |
| First Token | < 500ms (network dependent) |
| Animations | 60 fps |
| Memory | < 150 MB |
| APK Size | < 30 MB |

## License

This project is provided as-is for personal use.

## Acknowledgments

- [Material Design 3](https://m3.material.io/) - Design system
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI framework
- [Ollama](https://ollama.ai/) - Local LLM inference
- [Google Tink](https://developers.google.com/tink) - Cryptography
- [RichText](https://github.com/halilozercan/compose-richtext) - Markdown rendering
