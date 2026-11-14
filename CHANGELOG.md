# Changelog

All notable changes to OgrChatAi will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-11-14

### Added

- On-device neural speech with Supertonic 3 through sherpa-onnx and transparent
  fallback to the Android system TTS.
- Ten expressive neural voices (F1–F5, M1–M5) with descriptions in a compact
  voice selector.
- Automatic speech-language detection for English, Russian, Spanish, French and
  German, applied per sentence so mixed replies sound natural.
- Full voice-call flow: speech recognition, local model answer and spoken reply.
- Per-model reasoning mode (`Auto`, `On`, `Off`) with Qwen3 `/think` and
  `/no_think` support.
- Smart context compression with recent verbatim turns, relevance-ranked older
  messages, important-fact notes, noise filtering and recency decay.
- “Clear memory” action that keeps messages visible while excluding them from
  future prompts.
- Context window choices of 2048, 4096 and 8192 tokens, with an enforced
  8192-token engine limit.
- JVM tests for speech-language detection and context building.

### Changed

- The engine now keeps a KV-cache conversation and sends only the latest user
  turn; context is rebuilt only after a reset or near the context limit.
- Streaming UI updates are batched to reduce recompositions during generation.
- AndroidX, Kotlin, Compose, Room, OkHttp, Retrofit and Coil updated to current
  stable releases; image loading migrated to Coil 3.
- New adaptive dog-constellation launcher icon with a themed monochrome layer.

### Fixed

- Reduced repeated assistant replies when a conversation is reloaded from a
  stale compressed context.
- Missing model/voice settings after migration and model removal.
- Context meter now respects the memory-clear cutoff.

## [1.0.0] - 2026-08-29

### Added

- 💬 Real-time chat with AI models
- 📦 HuggingFace model browser with search and filters
- 🚀 On-device GGUF model inference
- 📎 File and image attachment support
- 🌍 Russian and English localization
- 🎨 Material3 dark/light theme support
- ⚙️ Configurable generation parameters (temperature, top-p, max tokens)
- 💾 Room database for chat history
- 📱 Edge-to-edge display support
- 🔒 Privacy-first architecture (no telemetry, no accounts)
- 🚀 Optimized for Pixel 8 Pro and modern Android devices
- 🔐 AES-GCM encryption for user messages (Android Keystore)
- 📁 Folder system for organizing chats
- 🏷️ Auto-naming chats by topic (LM Studio style)

### Architecture

- Clean Architecture with MVVM pattern
- Hilt dependency injection
- Jetpack Compose UI
- Room database
- Retrofit networking
- DataStore preferences
- Coil image loading

### Security

- No data collection or telemetry
- All data stored locally on device
- Open source codebase
- Minimal permission requests
- AES-GCM encryption using Android Keystore