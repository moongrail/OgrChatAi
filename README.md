<div align="center">

# ⚡ OgrChatAi

### ИИ-чат на устройстве — Приватно, Быстро, Бесплатно

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-2024.12-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-8%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen?style=flat-square)](https://developer.android.com/studio/releases/platforms)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square)](LICENSE)

**Общайтесь с ИИ прямо на телефоне. Без облака. Без сбора данных. Только вы и модель.**

</div>

---

## 📱 О проекте

OgrChatAi — нативное Android-приложение для скачивания и запуска open-source LLM моделей из [HuggingFace](https://huggingface.co) прямо на устройстве. Общайтесь с ИИ приватно, отправляйте файлы и картинки — всё без интернета после скачивания модели.

### Почему OgrChatAi?

| | Облачные ИИ-приложения | OgrChatAi |
|---|:---:|:---:|
| **Приватность** | ❌ Данные уходят на серверы | ✅ 100% на устройстве |
| **Офлайн** | ❌ Нужен интернет | ✅ Работает без интернета |
| **Бесплатно** | ❌ Нужна подписка | ✅ Всегда бесплатно |
| **Скорость** | ⚡ Зависит от сети | 🚀 Локальная инференция |
| **Файлы** | ⚠️ Ограниченно | ✅ Картинки и документы |

---

## ✨ Возможности

- 🤖 **Локальная инференция** — запуск GGUF моделей через оптимизированный движок
- 📦 **Интеграция с HuggingFace** — поиск и скачивание тысяч моделей
- 💬 **Чат в реальном времени** — стриминг ответов с анимацией набора
- 📎 **Файлы и картинки** — отправка изображений и документов ИИ
- 🌍 **Двуязычный интерфейс** — русский и английский
- 🎨 **Красивый дизайн** — Material3, тёмная/светлая тема
- ⚙️ **Настраиваемый** — температура, top-p, макс. токенов
- 🔒 **Приватно** — без телеметрии, без аккаунтов
- 🚀 **Быстро** — оптимизировано для Pixel 8 Pro
- 📱 **Нативно** — 100% Kotlin + Jetpack Compose

---

## 🏗️ Архитектура

```
com.ogrchatai.app/
├── data/          # Слой данных
│   ├── local/     # Room БД, DAO, сущности
│   ├── remote/    # HuggingFace API
│   ├── ml/        # Движок инференции
│   └── preferences/ # DataStore настройки
├── domain/        # Доменный слой
│   ├── model/     # Модели данных
│   ├── repository/# Интерфейсы репозиториев
│   └── usecase/   # Бизнес-логика
├── ui/            # Слой представления
│   ├── screen/    # Compose экраны
│   ├── viewmodel/ # ViewModel
│   └── theme/     # Material3 тема
└── di/            # Hilt модули
```

**Стек:** Kotlin 2.1 · Jetpack Compose · Material3 · MVVM · Clean Architecture · Hilt · Room · Retrofit · Coil · DataStore

---

## 🚀 Запуск

### Требования

- Android Studio Ladybug (2024.2+)
- JDK 17
- Android SDK 35

### Сборка

```bash
git clone https://github.com/moongrail/OgrChatAi.git
cd OgrChatAi
./gradlew assembleDebug
```

### Установка

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📋 Поддерживаемые модели

Любая GGUF модель с HuggingFace:

| Модель | Размер | Скорость | Качество |
|--------|--------|----------|----------|
| [Phi-3 Mini](https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf) | 2.3 GB | ⚡⚡⚡ | ⭐⭐⭐ |
| [Llama 3.2 3B](https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF) | 2 GB | ⚡⚡⚡ | ⭐⭐⭐ |
| [Gemma 2 2B](https://huggingface.co/bartowski/gemma-2-2b-it-GGUF) | 1.5 GB | ⚡⚡⚡⚡ | ⭐⭐ |
| [Mistral 7B](https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF) | 4 GB | ⚡⚡ | ⭐⭐⭐⭐ |
| [Qwen2 1.5B](https://huggingface.co/Qwen/Qwen2-1.5B-Instruct-GGUF) | 1 GB | ⚡⚡⚡⚡ | ⭐⭐ |

---

## 🛣️ Дорожная карта

- [x] Чат со стримингом
- [x] Браузер моделей HuggingFace
- [x] Локальная GGUF инференция
- [x] Файлы и картинки
- [x] Русский/английский
- [x] Тёмная/светлая тема
- [ ] Голосовой ввод
- [ ] Экспорт чатов
- [ ] Виджет для домашнего экрана
- [ ] Оптимизация для планшетов

## 📄 Лицензия

[Apache License 2.0](LICENSE)
