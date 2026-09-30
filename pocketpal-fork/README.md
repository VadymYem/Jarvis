# Jarvis | AuthorAi — PocketPal fork

This directory contains the reproducible PocketPal-based Jarvis Android build requested by AuthorChe.

The full PocketPal application lives on `pocketpal-fork`. The smaller native Kotlin/Compose prototype on `main` is a separate app and its APK is not this release. The `pocketpal-fork` branch adds:

- `jarvis-pocketpal.patch` — a binary-safe Git patch against PocketPal AI commit `4ebab32771df7d9e2554cd88a376bbda7d833da1`;
- `.github/workflows/jarvis-pocketpal-build.yml` — reconstructs the source, runs checks, and builds a signed arm64 APK artifact and private device-test prerelease.

The patch includes the single repository-owned release keystore and signing configuration by the project owner's explicit decision. The repository must remain private while this key is present.

Local reconstruction:

```bash
git clone https://github.com/a-ghorbani/pocketpal-ai.git jarvis-src
cd jarvis-src
git checkout 4ebab32771df7d9e2554cd88a376bbda7d833da1
git apply ../pocketpal-fork/jarvis-pocketpal.patch
```

Full project documentation is created inside the reconstructed tree under `docs/`.

## Device test release

Download [v0.1.0-pocketpal](https://github.com/AuthorGramProject/aaa/releases/tag/v0.1.0-pocketpal) after the Android workflow succeeds. [Release notes](RELEASE_NOTES.md) list the exact included tools and current limits. The compact 0.3.1 APK from `main` has a different signing certificate; back up its data and uninstall it before installing this build.
