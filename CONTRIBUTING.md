# Contributing to Wishy Launcher

Thank you for your interest in contributing to Wishy Launcher! We welcome bug reports, feature proposals, theme submissions, and pull requests.

## How to Contribute

### 1. Reporting Bugs
- Search existing issues to ensure the bug hasn't already been reported.
- Open a new issue using the **Bug Report** template.
- Include details such as device model, Android version, TV screen resolution, theme used, and step-by-step instructions to reproduce the issue.

### 2. Suggesting Features & Enhancements
- Open a new issue using the **Feature Request** template.
- Clearly describe the proposed feature and why it would be beneficial for Android TV / Google TV users.

### 3. Submitting Themes
- Custom themes are data-only (JSON + assets) and validated against the schema defined in [`schema/wishy-theme.schema.v1.json`](schema/wishy-theme.schema.v1.json).
- Refer to [`docs/MAKING_THEMES.md`](docs/MAKING_THEMES.md) and [`THEME_SCHEMA.md`](THEME_SCHEMA.md) for detailed guidelines on building and testing themes.

### 4. Code Contributions
1. Fork the repository and create a feature branch off `main`.
2. Ensure your code follows Kotlin official style guidelines (`./gradlew :app:lintDebug`).
3. Run local unit tests to make sure everything passes:
   ```bash
   ./gradlew :themecore:test
   ./gradlew :app:assembleDebug
   ```
4. Submit a Pull Request describing your changes clearly.

## Development Requirements
- JDK 17
- Android Studio Koala (2024.1) or newer
- Android SDK Platform 34 & Build-Tools 34

## License
By contributing, you agree that your contributions will be licensed under the project's [GPL-3.0 License](LICENSE).
