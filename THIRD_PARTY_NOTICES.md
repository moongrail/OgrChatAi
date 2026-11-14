# Third-party notices

OgrChatAi is proprietary software. See [LICENSE](LICENSE). This file lists the
third-party components used by the app. Each component remains under its own
license; the OgrChatAi license does not replace or restrict those rights.

## Runtime libraries

| Component | Version / artifact | License |
|---|---|---|
| AndroidX Core, Activity, Lifecycle, Navigation, Room, DataStore, Security Crypto, DocumentFile, Hilt | see `gradle/libs.versions.toml` | Apache License 2.0 |
| Jetpack Compose (UI, Foundation, Material 3, Material Icons) | Compose BOM 2026.09.00 | Apache License 2.0 |
| Kotlin standard library and kotlinx.coroutines / serialization | see `gradle/libs.versions.toml` | Apache License 2.0 |
| OkHttp and Retrofit | 5.5.1 / 3.0.0 | Apache License 2.0 |
| Gson | 2.15.0 | Apache License 2.0 |
| Coil 3 | 3.6.3 | Apache License 2.0 |
| Apache Commons Compress | 1.28.0 | Apache License 2.0 |
| sherpa-onnx | 2.0.1 (trimmed AAR in `app/libs`) | Apache License 2.0 |
| ONNX Runtime | bundled in sherpa-onnx AAR | MIT License |
| llama.cpp | bundled by android-ai-kit | MIT License |
| android-ai-kit-chat | 1.0.0 | MIT License |

## Neural voice model (not bundled)

The Supertonic 3 int8 model is downloaded on demand and is not included in the
APK. It is distributed by Supertone Inc. / the sherpa-onnx project under the
OpenRAIL-M License. By downloading the model, the user accepts that license.

- Model source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
- Model license: https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE
- Model card / README: https://huggingface.co/Supertone/supertonic-3

The Supertonic sample code is MIT-licensed; the model weights are OpenRAIL-M.

## Android system components

Text-to-speech, speech recognition, and other platform APIs are provided by the
user's Android device and are not redistributed with this app.

## Full license texts

Full license texts are available from the upstream projects and from the
dependency artifacts resolved by Gradle. If you redistribute a build of this
app, keep the upstream license and attribution notices that apply to the
bundled components.
