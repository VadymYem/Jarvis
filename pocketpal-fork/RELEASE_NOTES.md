# Jarvis | AuthorAi 0.1.0 — PocketPal test release

This arm64 APK is built from the **pocketpal-fork** branch, pinned to PocketPal AI 1.18.0. It is the full React Native/PocketPal app, not the smaller Kotlin/Compose prototype on `main`. No GGUF model is bundled; download or import a model in the app.

## Available to test

- Local GGUF and OpenAI-compatible remote models, chats, Pals, model downloads, benchmarks and upstream TTS engines.
- The PocketPal agent loop with calculator, date/time, HTML rendering, URL reading and web search. Brave, Tavily, Exa, Parallel and custom HTTP search require their respective configuration or keys.
- Jarvis `device_status` and `device_action`: open app or system settings, toggle flashlight, set media volume, start the Android alarm/timer flow, and play `was-wollen-wir-trinken.mp3` from the device media library.
- One-shot Android speech input from the microphone button. The selected Android speech provider may need network access; offline mode is only a preference.
- Launcher artwork with a small mint spark badge. Android locale and the default Jarvis persona are configured for Ukrainian.

## Current limits

- Continuous hands-free Live voice with interruptions is **not implemented**. Dictation and TTS are separate upstream flows.
- The larger agent toolset from `feat/jarvis-voice-mode` is a separate Kotlin/Compose prototype and is **not included** in this PocketPal APK: Accessibility UI actions, Shizuku, Termux, mail and notification automation are pending a native bridge port.
- There is no bundled, free offline web index. Local inference works offline with a downloaded model; web search needs a network provider.
- Xiaomi 14T Pro performance and all hardware actions need device testing. CI checks code, tests, payload and signature, not real-device behavior.

## Installing

Package ID: `top.authorche.agent`. The previous 0.3.1 compact APK uses a different signing certificate. Android cannot install this APK as an update over that build: back up any data you need, uninstall the old app, then install this APK. Uninstalling erases the old app's local chats and files. Future PocketPal test builds should use this release certificate.

This is a private **prerelease for device testing**, not a final feature-complete release.
