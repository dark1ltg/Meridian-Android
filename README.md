Meridian for Android 8+ (GPL-3.0-only). Mood map, listen matrix, local playback.

This GitHub repo is the home for the Android tree. To get the full history from the Cloud Agent onto your Linux machine:

1. Open https://cursor.com/agents/bc-89acf348-36bc-47d6-96ca-87ee4b5acf21
2. Artifacts → download `meridian_android.bundle`
3. Then:

```bash
cd ~
git clone ~/Downloads/meridian_android.bundle meridian-android
cd meridian-android
git remote add origin https://github.com/dark1ltg/Meridian-Android.git
git push -u origin main
```

The debug APK is a separate artifact: `meridian_1_3_5_debug.apk`.
