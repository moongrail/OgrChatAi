# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability within OgrChatAi, please send an email to the project maintainer. All security vulnerabilities will be promptly addressed.

**Please do NOT report security vulnerabilities through public GitHub issues.**

### What to include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### Response time

- Initial response: Within 48 hours
- Fix timeline: Within 7 days for critical issues

## Security Best Practices

OgrChatAi is designed with privacy in mind:

- **No telemetry** — We don't collect any usage data
- **No accounts** — No registration required
- **Local storage** — All data stays on your device
- **No network calls** — Except for model downloads from HuggingFace
- **Open source** — Code is fully auditable
- **AES-GCM encryption** — User messages encrypted with Android Keystore

## Data Handling

- Chat messages are stored locally in Room database (encrypted)
- Models are stored in app-specific storage
- Settings are stored in DataStore Preferences
- No data is sent to any server except HuggingFace for model downloads

## Permissions

OgrChatAi requests minimal permissions:

- `INTERNET` — For downloading models from HuggingFace
- `READ_MEDIA_*` — For attaching files and images to chats
- `POST_NOTIFICATIONS` — For download progress notifications

## Updates

Security updates will be released as patch versions and announced in the Releases section.