# Implemented capabilities

This document describes only functionality implemented in the portfolio snapshot. It intentionally does not act as a product roadmap.

## Agent core

- Accepts a natural-language task.
- Runs a bounded agent loop of up to 16 steps.
- Requests one tool action or final answer per step.
- Records visible plan/action/result trace entries.
- Applies a risk policy before every tool execution.
- Requires user confirmation for medium- and high-risk operations.
- Rejects critical operations.

## Model layer

- Local GGUF inference through llama.cpp/JNI.
- Gemini REST provider.
- OpenAI-compatible REST provider.
- Presets for OpenAI and Groq.
- Runtime provider switching.
- Per-provider endpoint, model, and credential configuration.
- HTTPS requirement for cloud endpoints.
- Local GGUF operation without an API key.

## Credential handling

- Per-provider API credentials.
- AES-GCM encryption before storage.
- Android Keystore-backed secret key material.
- Encrypted values stored in private SharedPreferences.
- Secrets excluded from Android backup/device transfer.
- Password-style masking in the settings UI.

## Android interaction

- Launch an installed application by name/package.
- Read the current Accessibility semantic tree.
- Mask password fields.
- Find and click visible elements.
- Enter text through Accessibility.
- Scroll supported nodes.
- Trigger Back and Home system actions.

## Shizuku / privileged operations

- Detect Shizuku availability and authorization state.
- Request Shizuku permission.
- Bind a private UserService.
- Execute only application-defined privileged operations.
- Force-stop an application after confirmation.
- Open selected Android Settings pages.
- Send selected key events.
- Grant/revoke runtime permissions after confirmation.
- Modify selected Android settings after confirmation.
- Read active-activity state through dumpsys.
- Reject unknown privileged operations.
- Limit command execution time and returned output.

## Automation

- Create local reminders.
- Schedule background reminder work with WorkManager.
- Deliver reminders through an Android notification channel.

## UI and engineering

- Native Jetpack Compose UI.
- Material 3 components.
- Light/dark theme.
- Runtime capability status for Accessibility and Shizuku.
- Confirmation dialog with visible risk level.
- Unit tests for policy/command boundaries.
- CI build and unit-test verification.

For architecture and security rationale, see [ARCHITECTURE.md](ARCHITECTURE.md), [CASE_STUDY.md](CASE_STUDY.md), and [../SECURITY.md](../SECURITY.md).
