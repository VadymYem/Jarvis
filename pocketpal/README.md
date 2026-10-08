# Jarvis | AuthorAi — PocketPal release layer

This directory contains the reproducible PocketPal-based Android release layer for the single canonical `main` branch.

The release is reconstructed from PocketPal AI commit `4ebab32771df7d9e2554cd88a376bbda7d833da1` and the ordered patches in `.github/workflows/jarvis-pocketpal-build.yml`. The same `main` branch also keeps the native Kotlin/Compose agent implementation used to develop and port Android-agent capabilities.

Included here:

- `jarvis-pocketpal.patch` — base Jarvis branding, local/remote AI and Android-agent integration;
- `jarvis-live.patch` — v0.2.0 keyless search and turn-based Jarvis Live;
- `jarvis-screen-v0.6.patch` — previous release layer;
- `jarvis-knowledge-v0.7.patch` — ZIM archive search, simplified Jarvis chat, turn-based Live, web search fallback;
- `brand_launcher.py` — Jarvis launcher branding;
- `RELEASE_NOTES.md` — exact device-test scope and limitations.

The build workflow is `.github/workflows/jarvis-pocketpal-build.yml` and runs from `main`.

Local reconstruction:

```bash
git clone https://github.com/a-ghorbani/pocketpal-ai.git jarvis-src
cd jarvis-src
git checkout 4ebab32771df7d9e2554cd88a376bbda7d833da1
git apply ../pocketpal/jarvis-pocketpal.patch
git apply ../pocketpal/jarvis-live.patch
# Apply each remaining patch in the same order as the build workflow.
```

Current device-test line: `v0.18.0-pocketpal`. The final reliability patch adds the actual Android control bridge and aligns the APK version with CI. The repository must remain private while repository-owned signing material is present. Wikipedia content is a separate ZIM download; it is not embedded in the APK.


The v0.16 layer removes the Agent drawer screen, adds Work inside Chat, selected ZIM packs, GGUF-tokenizer context budgeting, a structured action protocol for Mamay/Gemma 3, native bounded HTTP, foreground inference with a partial wake lock, and verifiable project checks. See [WORK.md](WORK.md) for exact behavior, tests and limits.

The v0.18 layer clears the structured sampler generation prefix and native chat parser while retaining the complete GGUF-formatted input prompt and eager JSON schema. This fixes the native grammar-prefill failure whose message was sliced to `std::exception`. `verify_structured_sampler.cpp` reproduces the rejection and tests valid Ukrainian JSON/HTML actions against the pinned llama.cpp implementation without loading a GGUF. It does not measure device inference performance.

Native regression (the APK workflow also runs it):

```bash
python3 pocketpal/verify_structured_sampler.py \
  --source jarvis-src/node_modules/llama.rn/vendor/llama.cpp \
  --build-dir /tmp/jarvis-native-sampler
```
