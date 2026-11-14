# Contributing to OgrChatAi

OgrChatAi is a proprietary, source-available project. The repository is public
for viewing and evaluation only; it is not an open-source project. Forking,
modifying, distributing, or using the app requires prior written permission
from the copyright holder. See [LICENSE](LICENSE).

## Before you contribute

Do not open a pull request or publish a fork without prior written permission.
If you have received permission to contribute:

1. Agree with the maintainer on the scope and the terms in writing.
2. Create a branch from the branch specified by the maintainer.
3. Keep the change focused and add tests where practical.
4. Update `README.md` and `CHANGELOG.md` when behaviour changes.
5. Submit the change for review; the maintainer decides whether and where it
   is published.

By submitting a contribution, you grant the copyright holder a perpetual,
worldwide, royalty-free right to use, modify, relicense, and distribute the
contribution as part of OgrChatAi. Do not submit code you cannot license on
those terms.

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

## Security

Please report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
Do not open a public issue with exploit details.

## License

OgrChatAi is distributed under the proprietary terms in [LICENSE](LICENSE).
Third-party components remain under their own licenses; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
