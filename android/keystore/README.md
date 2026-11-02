# Sideload signing key

`sideload.jks` signs GitHub Release APKs so updates stay on the same signature.

This is a **public sideload identity**, not a Play Store upload key. The store
password is `meridian-sideload`, alias `meridian`. Anyone with the repo can
rebuild a matching signature. Do not use this key for Play Console.

Restore from text if the binary is missing:

```bash
base64 -d android/keystore/sideload.jks.b64 > android/keystore/sideload.jks
```
