# Changelog

All notable changes to OgrChatAi will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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