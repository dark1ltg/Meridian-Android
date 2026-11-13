# Patches

1.3.6 source lives on `main` (engine + Android UI + docs). The Release APK
workflow (`.github/workflows/release-apk.yml`) builds from the tree, stamps
versionCode and SHA-256 into the current-release docs, and publishes
[v1.3.6](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6)
(current **versionCode 160**).

Packed/part patch files from the earlier MCP upload path are no longer required.
