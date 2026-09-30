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
- Read/write/clear clipboard content through a typed tool.
- Open constrained system intents and selected device controls.
- Open the mail client and prepare a visible draft without silently sending it.
- Read/dismiss supported notifications and send a notification quick-reply only after risk-policy confirmation.
- Run allowlisted Termux/ADB operations through `RUN_COMMAND`; arbitrary model-generated shell text is rejected.

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

## Search and voice (v0.3)

- Offline search across installed launchable applications and files in one folder explicitly selected with Android's document picker (up to four subfolder levels; bounded scan).
- Wikipedia article search and GitHub repository search without an API key.
- Optional general web search through a user configured HTTPS SearXNG instance or Brave Search API key.
- A foreground voice screen with partial speech transcription, turn taking, Android TextToSpeech, manual interruption, and Ukrainian/English/German language selection.
- Prefer Android's on-device recognition service where available. System recognition and TTS availability and language packs vary by device; this version does not bundle Whisper/FUTO or neural voice models.
- A small deterministic offline router for app search, web search, app launch, and relative reminders. Other tasks require a configured GGUF or cloud model.
- Explicit stop control for an executing agent task.
- Read device battery/storage status and open selected Android settings pages. These simple commands can also run through the offline router.
- Play a user-imported MP3 for up to 45 seconds with the `find_phone` tool while the agent is running. The app does not passively listen for a wake word.

## Current boundaries

There is no true full-duplex Live audio, acoustic echo cancellation/barge-in pipeline, background wake-word listening, production voice cloning, global filesystem search, or bundled general offline web index in this version. Search results are external untrusted data. A SearXNG server must enable JSON output. Users must verify actions on a real device, including recognition language availability and Accessibility permissions.

## UI and engineering

- Native Jetpack Compose UI.
- Material 3 components.
- Light/dark theme.
- Runtime capability status for Accessibility and Shizuku.
- Confirmation dialog with visible risk level.
- Unit tests for policy/command boundaries.
- CI build and unit-test verification.

For architecture and security rationale, see [ARCHITECTURE.md](ARCHITECTURE.md), [CASE_STUDY.md](CASE_STUDY.md), and [../SECURITY.md](../SECURITY.md).
