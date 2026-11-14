# Contributing to OgrChatAi

Thank you for your interest in contributing to OgrChatAi! This document provides
guidelines and information about contributing to this project.

## Getting started

1. Fork the repository.
2. Clone your fork:
   `git clone https://github.com/your-username/OgrChatAi.git`
3. Create a feature branch: `git checkout -b feature/amazing-feature`
4. Make your changes.
5. Commit your changes: `git commit -m 'Add amazing feature'`
6. Push to your fork: `git push origin feature/amazing-feature`
7. Open a Pull Request.

For significant changes, please open an issue first so the approach can be
discussed before implementation.

## Development setup

- Android Studio with support for the project's Android Gradle Plugin
- JDK 17
- Android SDK 37 (`compileSdk = 37`)
- Android 11+ device or emulator (`minSdk = 30`)

Build:

```bash
./gradlew :app:assembleDebug
```

Run JVM unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Instrumentation tests require a connected device:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Code style

- Follow the Kotlin coding conventions.
- Keep UI in Compose and business logic in ViewModels/use cases.
- Prefer small, focused functions and explicit names.
- Add KDoc for public APIs.
- Do not add telemetry, analytics, advertising, or cloud dependencies.
- Do not commit API keys, signing keys, model files, or generated APKs.

## Commit messages

Use conventional commits:

- `feat:` new feature
- `fix:` bug fix
- `docs:` documentation
- `style:` formatting
- `refactor:` code restructuring
- `test:` tests
- `chore:` maintenance

Examples:

```text
feat: add voice input support
fix: resolve crash on model download
docs: update installation instructions
```

## Reporting issues

Use GitHub Issues with:

- **Bug:** steps to reproduce, expected vs actual behaviour;
- **Feature:** description, use case, alternatives considered;
- **Question:** a clear description of the question.

## Security

Please report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
Do not open a public issue with exploit details.

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE).

Third-party components remain under their own licenses; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
