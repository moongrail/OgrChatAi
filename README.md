<div align="center">

# ⚡ MindForge

### On-Device AI Chat — Private, Fast, Free

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen)](https://developer.android.com/studio/releases/platforms)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![PRs](https://img.shields.io/badge/PRs-Welcome-brightgreen.svg)](CONTRIBUTING.md)

<br/>

**Chat with AI models directly on your phone. No cloud. No data collection. Just you and the model.**

[Download APK](https://github.com/moongrail/MindForge/releases) · [Report Bug](https://github.com/moongrail/MindForge/issues) · [Request Feature](https://github.com/moongrail/MindForge/issues)

</div>

---

## 📱 About

MindForge is a native Android application that lets you download and run open-source LLM models from [HuggingFace](https://huggingface.co) directly on your device. Chat with AI privately, send files and images, and enjoy a beautiful Material3 interface — all without an internet connection after model download.

### Why MindForge?

| Feature | Cloud AI Apps | MindForge |
|---------|:------------:|:---------:|
| **Privacy** | ❌ Data sent to servers | ✅ 100% on-device |
| **Offline** | ❌ Requires internet | ✅ Works offline |
| **Free** | ❌ Subscription needed | ✅ Always free |
| **Speed** | ⚡ Network dependent | 🚀 Local inference |
| **File Support** | ⚠️ Limited | ✅ Images & documents |

---

## ✨ Features

- 🤖 **On-Device Inference** — Run GGUF models locally using optimized inference engine
- 📦 **HuggingFace Integration** — Search and download thousands of open-source models
- 💬 **Real-time Chat** — Streaming responses with typing animation
- 📎 **File & Image Support** — Send images and documents to the AI
- 🌍 **Bilingual Interface** — Full Russian and English localization
- 🎨 **Beautiful Design** — Material3 with dark/light theme support
- ⚙️ **Configurable** — Adjust temperature, top-p, max tokens
- 🔒 **Private** — No telemetry, no data collection, no accounts
- 🚀 **Performant** — Optimized for Pixel 8 Pro and modern devices
- 📱 **Native** — 100% Kotlin with Jetpack Compose

---

## 🏗️ Architecture

```
com.mindforge.app/
├── data/                    # Data layer
│   ├── local/              # Room database, DAOs, entities
│   ├── remote/             # HuggingFace API, DTOs
│   ├── repository/         # Repository implementations
│   ├── preferences/        # DataStore preferences
│   └── ml/                 # Inference engine
├── domain/                  # Domain layer
│   ├── model/              # Domain models
│   ├── repository/         # Repository interfaces
│   └── usecase/            # Business logic
├── ui/                      # Presentation layer
│   ├── screen/             # Compose screens
│   ├── viewmodel/          # ViewModels
│   ├── components/         # Reusable components
│   ├── navigation/         # Navigation graph
│   └── theme/              # Material3 theme
├── di/                      # Hilt modules
└── util/                    # Utilities
```

**Tech Stack:**
- **Language:** Kotlin 2.1
- **UI:** Jetpack Compose + Material3
- **Architecture:** MVVM + Clean Architecture
- **DI:** Hilt
- **Database:** Room
- **Network:** Retrofit + OkHttp + Kotlinx Serialization
- **Images:** Coil
- **Settings:** DataStore Preferences
- **Navigation:** Compose Navigation

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug (2024.2+)
- JDK 17
- Android SDK 35
- Pixel 8 Pro or equivalent (API 26+)

### Build

```bash
# Clone the repository
git clone https://github.com/moongrail/MindForge.git

# Open in Android Studio
# Or build from command line
./gradlew assembleDebug
```

### Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📋 Supported Models

MindForge supports any GGUF-format model from HuggingFace. Recommended models for Pixel 8 Pro:

| Model | Size | Speed | Quality |
|-------|------|-------|---------|
| [Phi-3 Mini](https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf) | 2.3 GB | ⚡⚡⚡ | ⭐⭐⭐ |
| [Llama 3.2 3B](https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF) | 2 GB | ⚡⚡⚡ | ⭐⭐⭐ |
| [Gemma 2 2B](https://huggingface.co/bartowski/gemma-2-2b-it-GGUF) | 1.5 GB | ⚡⚡⚡⚡ | ⭐⭐ |
| [Mistral 7B](https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF) | 4 GB | ⚡⚡ | ⭐⭐⭐⭐ |
| [Qwen2 1.5B](https://huggingface.co/Qwen/Qwen2-1.5B-Instruct-GGUF) | 1 GB | ⚡⚡⚡⚡ | ⭐⭐ |

---

## 📸 Screenshots

> Screenshots coming soon! Want to contribute? See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 🛣️ Roadmap

- [x] Chat interface with streaming
- [x] HuggingFace model browser
- [x] On-device GGUF inference
- [x] File & image attachments
- [x] Russian/English localization
- [x] Dark/Light theme
- [ ] Voice input
- [ ] Multiple model conversation
- [ ] Chat export (JSON/Markdown)
- [ ] Model fine-tuning support
- [ ] Widget for home screen
- [ ] Tablet-optimized layout

---

## 🤝 Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## 📄 License

This project is licensed under the Apache License 2.0 — see [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgments

- [HuggingFace](https://huggingface.co) — Model hub
- [llama.cpp](https://github.com/ggerganov/llama.cpp) — GGUF format
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — UI toolkit
- [Material Design 3](https://m3.material.io) — Design system

---

<div align="center">

**Made with ❤️ for the open-source AI community**

[![GitHub](https://img.shields.io/github/stars/moongrail/MindForge?style=social)](https://github.com/moongrail/MindForge)

</div>
