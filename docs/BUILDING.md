# Build and signing

Use JDK 17, Android SDK Platform 35 and Build Tools 35.0.0. The wrapper pins Gradle 8.11.1 with a distribution checksum. AGP 8.9.1 and Kotlin 2.1.20 are declared in build files. Minimum SDK is 29, application ID `app.lantern.lan`.

Set JAVA_HOME/ANDROID_HOME for your machine or an untracked local.properties with sdk.dir. Do not commit local paths or signing keys.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Linux/macOS: use `./gradlew`. Debug output: app/build/outputs/apk/debug/app-debug.apk. Release shrinking is enabled; its unsigned output is app/build/outputs/apk/release/app-release-unsigned.apk.

Distribution signing happens outside this tree. Official certificate SHA-256:

`00c5f3e2275a3f8a231ca1c9a21110296f37c2a24baa627b2dedadafb0249222`

The APK retains its existing signing lineage. Private signing keys/passwords are not included. A fork needs its own key, usually a separate application ID, and cannot upgrade this distributed APK with an unrelated key. Never post keys in issues or logs. A signed APK cannot be reproduced byte-for-byte without its private signing material.

The published APK is the existing signed v0.7.3 artifact; repository documentation additions do not change application code. Compilation/signature checks are not runtime validation.

## Validation status

No new runtime tests were run for 0.7.1–0.7.3 at the owner's request. Existing test sources include obsolete cases for earlier camera behavior; do not claim them as current coverage. Update relevant cases before future validation. Windows 5.9.1 and physical Android acceptance, including Samsung S25 Ultra / OnePlus Ace 5, remain pending.

No CI workflow is configured or claimed green for this initial publication. See TESTING.md and CONTRIBUTING.md.
