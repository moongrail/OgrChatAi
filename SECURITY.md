# Security Policy

## Supported versions

| Version | Supported |
| ------- | --------- |
| 1.1.x / `main` | Yes |
| `release/*` branches | Yes, when published |

## Reporting a vulnerability

If you discover a security vulnerability in OgrChatAi, report it privately to
the project maintainer. Do not publish exploit details in GitHub issues.

Please include:

- a description of the vulnerability;
- steps to reproduce;
- the affected version or branch;
- potential impact;
- a suggested fix, if available.

Initial response is normally within 48 hours. Critical issues are prioritised
for a patch release.

## Security model

OgrChatAi is designed to keep user data on the device:

- **No telemetry** — no usage analytics are collected.
- **No accounts** — no registration or cloud profile is required.
- **Local storage** — chats, attachments, and settings stay on the device.
- **Encrypted messages** — chat message bodies are encrypted with AES-GCM keys
  protected by Android Keystore.
- **Local inference** — prompts and model responses are processed on-device.
- **Local speech** — neural TTS audio is generated on-device and is never
  uploaded.
- **Network access only for downloads** — model files and the optional neural
  voice pack are fetched from HuggingFace or the sherpa-onnx GitHub releases.
  The app does not upload chats, prompts, or audio.
- **Open source for audit** — the code is available under the Apache License
  2.0 and can be inspected, modified, and redistributed under its terms. See
  [LICENSE](LICENSE).

Speech recognition is provided by the Android speech recognition service. Some
devices use an offline speech pack; others may send audio to the device's speech
provider. That behaviour is controlled by Android and the speech engine selected
on the device, not by OgrChatAi. Use an offline recognition pack when speech must
remain fully local.

## Data handling

- Chat messages and attachments are stored locally in the app's private storage.
- Downloaded GGUF models and the optional Supertonic 3 voice pack are stored in
  app-specific storage.
- Settings are stored in DataStore Preferences.
- No data is sent to an OgrChatAi server. The project has no backend.

## Permissions

OgrChatAi requests:

- `INTERNET` — downloading models and the optional neural voice pack.
- `ACCESS_NETWORK_STATE` — showing download connectivity state.
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, and legacy
  `READ_EXTERNAL_STORAGE` (Android 12 and below) — attaching user-selected files.
- `POST_NOTIFICATIONS` — download progress notifications.
- `RECORD_AUDIO` — voice input and voice calls.

The app requests microphone access only when the user starts voice input or a
voice call.

## Updates

Security fixes are released as patch versions. Users should install updates from
the official repository only.
