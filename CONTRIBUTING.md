# Contributing to OgrChatAi

Thank you for your interest in contributing to OgrChatAi! This document provides guidelines and information about contributing to this project.

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/your-username/OgrChatAi.git`
3. Create a feature branch: `git checkout -b feature/amazing-feature`
4. Make your changes
5. Commit: `git commit -m 'Add amazing feature'`
6. Push: `git push origin feature/amazing-feature`
7. Open a Pull Request

## Development Setup

### Prerequisites

- Android Studio Ladybug (2024.2+)
- JDK 17
- Android SDK 35

### Building

```bash
./gradlew assembleDebug
```

### Testing

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Code Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions small and focused
- Use coroutines for async operations

## Architecture

We follow Clean Architecture with MVVM:

- **data/** — Data sources, repositories implementations
- **domain/** — Business logic, models, repository interfaces
- **ui/** — Compose screens, ViewModels, components

## Pull Request Guidelines

1. **Title:** Clear, descriptive title
2. **Description:** What changes and why
3. **Tests:** Add tests for new features
4. **Documentation:** Update README if needed
5. **Code Review:** Be open to feedback

## Commit Messages

Use conventional commits:

- `feat:` new feature
- `fix:` bug fix
- `docs:` documentation
- `style:` formatting
- `refactor:` code restructuring
- `test:` adding tests
- `chore:` maintenance

Examples:
```
feat: add voice input support
fix: resolve crash on model download
docs: update installation instructions
```

## Reporting Issues

Use GitHub Issues with:

- **Bug:** Steps to reproduce, expected vs actual behavior
- **Feature:** Description, use case, alternatives considered
- **Question:** Clear description of your question

## Code of Conduct

- Be respectful and inclusive
- Focus on constructive feedback
- Help newcomers learn
- Celebrate contributions of all sizes

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.