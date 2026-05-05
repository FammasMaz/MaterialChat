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
- **Encrypted Backups** - Password-protected backup/restore files via Android's system picker, including Google Drive
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
│   ├── backup/          # Manual encrypted chat backup/restore
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

## Monetization

The Play Store build can show ads to support development:

- Banner ads on top-level screens
- One-time Google Play Billing purchase to remove ads permanently
- Rewarded ad option for 24-hour premium/ad-free access
- GitHub release APKs are built with `ADS_ENABLED=false` and are intended to remain ad-free for users who prefer the open-source build

Play Store release builds use the `play` distribution variant and should provide real AdMob/Billing values, for example:

```bash
./gradlew bundlePlayRelease \
  -PADS_ENABLED=true \
  -PADMOB_APP_ID=ca-app-pub-xxx~yyy \
  -PADMOB_BANNER_AD_UNIT_ID=ca-app-pub-xxx/banner \
  -PADMOB_REWARDED_AD_UNIT_ID=ca-app-pub-xxx/rewarded \
  -PREMOVE_ADS_PRODUCT_ID=remove_ads \
  -PRELEASE_STORE_FILE=release.keystore \
  -PRELEASE_STORE_PASSWORD=... \
  -PRELEASE_KEY_ALIAS=... \
  -PRELEASE_KEY_PASSWORD=...
```

See `docs/PLAY_STORE_TESTING_CHECKLIST.md` for the full Play Console/testing checklist.

## Security

API keys are encrypted at rest using industry-standard practices:

- **Algorithm**: AES-256-GCM via Google Tink
- **Key Storage**: Android Keystore (hardware-backed when available)
- **Network**: TLS 1.2+ for all HTTPS traffic
- **Local Ollama**: HTTP allowed for localhost and private IP ranges

Chat history is stored locally and never transmitted to external servers beyond your configured AI providers. Android backup is disabled so local chat history and provider metadata are not copied to cloud/device backups.

Manual backup files are encrypted with a user-entered password and AES-GCM/PBKDF2 before being written through Android's system file picker. Backups include non-ephemeral conversations, messages, bookmarks, custom personas, and provider metadata, but they intentionally do not include provider API keys.

## Requirements

| Requirement | Value |
|-------------|-------|
| Android Version | 8.0 Oreo (API 26) or higher |
| Target SDK | 36 |
| Dynamic Color | Android 12+ (optional) |
| Assistant Overlay | Supported on all versions |

## Building from Source

### Prerequisites

- JDK 17 or higher
- Android Studio Ladybug (2024.2.1) or higher
- Android SDK with API 36

### Build Commands

```bash
# Clone the repository
git clone https://github.com/yourusername/MaterialChat.git
cd MaterialChat

# Build Play debug APK
./gradlew assemblePlayDebug

# Build GitHub debug APK
./gradlew assembleGithubDebug

# Build Play release AAB for Play Console
./gradlew bundlePlayRelease

# Build GitHub release APK
./gradlew assembleGithubRelease

# Install Play debug on connected device
./gradlew installPlayDebug
```

### APK Locations

- Play debug: `app/build/outputs/apk/play/debug/app-play-debug.apk`
- GitHub debug: `app/build/outputs/apk/github/debug/app-github-debug.apk`
- Play release AAB: `app/build/outputs/bundle/playRelease/app-play-release.aab`
- GitHub release APK: `app/build/outputs/apk/github/release/app-github-release.apk`

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
