# Jarvis | AuthorAi — PocketPal fork

This directory contains the reproducible PocketPal-based Jarvis Android build requested by AuthorChe.

The repository's existing native Kotlin/Compose portfolio snapshot remains untouched on `main`. The `pocketpal-fork` branch adds:

- `jarvis-pocketpal.patch` — a binary-safe Git patch against PocketPal AI commit `4ebab32771df7d9e2554cd88a376bbda7d833da1`;
- `.github/workflows/jarvis-pocketpal-build.yml` — reconstructs the source, runs checks, and builds signed arm64 APK/AAB artifacts.

The patch includes the single repository-owned release keystore and signing configuration by the project owner's explicit decision. The repository must remain private while this key is present.

Local reconstruction:

```bash
git clone https://github.com/a-ghorbani/pocketpal-ai.git jarvis-src
cd jarvis-src
git checkout 4ebab32771df7d9e2554cd88a376bbda7d833da1
git apply ../pocketpal-fork/jarvis-pocketpal.patch
```

Full project documentation is created inside the reconstructed tree under `docs/`.
