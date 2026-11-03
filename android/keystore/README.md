# Sideload signing key

`sideload.jks` signs GitHub Release APKs so updates stay on the same signature.

This is a **public sideload identity**, not a Play Store upload key. The store
password is `meridian-sideload`, alias `meridian`. Anyone with the repo can
rebuild a matching signature. Do not use this key for Play Console.

| | |
|---|---|
| **Store / key password** | `meridian-sideload` |
| **Alias** | `meridian` |
| **Cert SHA-256** | `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9` |

Restore from text if the binary is missing:

```bash
base64 -d android/keystore/sideload.jks.b64 > android/keystore/sideload.jks
```

The published [v1.3.6](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6) APK (**versionCode 150**) is signed with this key.
