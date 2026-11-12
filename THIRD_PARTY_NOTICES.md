# Third-party notices

## PocketPal AI

- Repository: https://github.com/a-ghorbani/pocketpal-ai
- License: MIT
- Copyright: Copyright (c) 2024 Asghar Ghorbani
- Use: Upstream baseline for local mobile LLM inference and chat runtime. Extended in `pocketpal/` with structured JSON tool actions, offline ZIM archive search, grammar-prefix sampling fixes, and background work execution.
- Notice:
  ```text
  MIT License

  Copyright (c) 2024 Asghar Ghorbani

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  SOFTWARE.
  ```

## Capka

- Repository: https://github.com/LyoSU/capka
- License: GNU AGPL-3.0
- Use: reference architecture for durable agent tasks, tools, policies and auditability. No Capka UI or TypeScript source is embedded in the Android module.

## Shizuku API

- Repository: https://github.com/RikkaApps/Shizuku-API
- License: MIT
- Use: Maven dependencies `dev.rikka.shizuku:api` and `dev.rikka.shizuku:provider` plus the documented UserService integration.

## Termux RUN_COMMAND API

- Repository: https://github.com/termux/termux-app
- License: GPL-3.0
- Use: documented inter-app `RUN_COMMAND` Intent contract only. Termux source or binaries are not embedded in Jarvis | AuthorAi.

## Mobile-Agent

- Repository: https://github.com/X-PLUG/MobileAgent
- License: MIT
- Use: research reference for the mobile GUI-agent observe/act/reflect workflow. Its Python ADB implementation is not embedded.

## MobileRun

- Repository: https://github.com/mobilerun-ai/mobilerun
- License: MIT
- Use: research reference for combining accessibility trees, gestures and screenshot-aware mobile task execution. No MobileRun source is embedded.

## PokeClaw

- Repository: https://github.com/shubham0204/PokeClaw
- License: Apache-2.0
- Use: research reference for on-device Android agents, notification access and explicit background execution. No PokeClaw source or model is embedded.

## sherpa-onnx

- Repository: https://github.com/k2-fsa/sherpa-onnx
- License: Apache-2.0
- Use: evaluated as the future local Ukrainian STT/TTS, VAD and wake-word runtime. It is not bundled in version 0.4.0.

## llama.cpp

- Repository: https://github.com/ggml-org/llama.cpp
- Pinned commit: `ed7ac35e1ee49cb70e4dfa9f0a2ce39b0a5ec4ea`
- License: MIT
- Use: native arm64-v8a inference for user-provided GGUF models. The JNI layer is based on the official Android example from the same pinned revision.

## AndroidX, Kotlin, OkHttp

Dependency license metadata is preserved in the built artifacts. Release builds must generate a complete dependency license report before public distribution.
