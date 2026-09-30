# Jarvis | AuthorAi — PocketPal release layer

This directory contains the reproducible PocketPal-based Android release layer for the single canonical `main` branch.

The release is reconstructed from PocketPal AI commit `4ebab32771df7d9e2554cd88a376bbda7d833da1`, then applies `jarvis-pocketpal.patch` and `jarvis-live.patch`. The same `main` branch also keeps the native Kotlin/Compose agent implementation used to develop and port Android-agent capabilities.

Included here:

- `jarvis-pocketpal.patch` — base Jarvis branding, local/remote AI and Android-agent integration;
- `jarvis-live.patch` — v0.2.0 keyless search and turn-based Jarvis Live;
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
```

Current device-test line: `v0.2.0-pocketpal`. The repository must remain private while repository-owned signing material is present.
