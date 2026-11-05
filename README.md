# IPMsg for Android

A lightweight, unofficial Android companion for Windows IP Messenger. Messages and individual files over your local network, without an account, cloud server or separate Windows companion.

**Android 10+ · Kotlin / Jetpack Compose · MIT application code · Beta**

> **Release candidate v0.7.3-rc.1:** Android version 0.7.3, code 11. The APK was compiled, shrunk and signed. Latest UI/camera changes have not been runtime-tested. Physical-phone and current Windows/browser acceptance remain pending. See [validation status](TESTING.md).

## Download and start

1. Download **IPMsg-v0.7.3-rc1.apk** from [Releases](https://github.com/mobideck-web/ipmsg-android/releases/tag/v0.7.3-rc.1). Source archives are not installable apps.
2. Install on Android 10+. Install over your existing IPMsg/Lantern app to retain history; do not uninstall first.
3. Put the phone and Windows PC on the same router. Keep Windows IP Messenger running. Allow UDP/TCP **2425** through Windows Firewall on the **Private** network profile.
4. Allow Android notifications, tap **Start listening**, then **Refresh** in Nearby. Add the PC's IPv4 address if needed.
5. Approve the intended device, then open its chat to send messages or accept files.

[Installation and troubleshooting](docs/INSTALLATION.md) · [User guide](docs/USER_GUIDE.md) · [Live camera](docs/LIVE_CAMERA.md)

## Features

- Nearby discovery, explicit approval, favorites, local nicknames and device management.
- Unicode messages, delivery acknowledgments, local history and database-backed search.
- Individual photos/files, batches up to 30, progress and explicit acceptance. Downloads default to **Downloads/IPMsg**; change the folder in Settings → Storage.
- Restart actions for eligible interrupted downloads. Restart begins at zero; cross-process resume is not supported.
- Android Share menu, deliberate clipboard sharing, message actions, notification replies and a review-before-send outbox.
- User-controlled background listening with an ongoing notification.
- Owner-started HTTPS live camera, live quality percentage and receiver-only JPEG/WebM downloads with sender activity alerts. Wide view fills the receiver viewport with centered cropping and natural proportions.

Four tabs: **Nearby, Chats, Files and Settings**. No widget, folder transfer, internet relay, calls, live audio, Member Master or remote phone-storage browsing.

## Security and privacy

Secure peers are identified by public-key fingerprints; verify the intended device. Changed keys require approval. Legacy plaintext sharing requires consent. Classic IPMsg file encryption does not authenticate file contents.

Camera viewing uses HTTPS with a local certificate. Compare its entire fingerprint with the phone before a site-specific exception. Never disable certificate checking globally. There is no HTTP fallback; browser policy may prevent setup. Camera access expires and ends when the owner leaves the camera screen or the session is revoked.

The published app has no account service or analytics SDK. History is local; public downloads remain until deleted. See [Privacy](PRIVACY.md) and [Security](SECURITY.md).

## Build and contribute

Requires **JDK 17, Android SDK 35** and the included Gradle wrapper. See [Build instructions](docs/BUILDING.md) and [Contributing](CONTRIBUTING.md).

```powershell
.\gradlew.bat :app:assembleDebug
```

The distributed APK uses a private signing key excluded from the repository. Your own unrelated key cannot update it in place.

## License and attribution

Application code is [MIT](LICENSE). The reused original IP Messenger icon and its monochrome adaptation retain the upstream BSD notice. Dependencies retain their own terms: [Third-party notices](THIRD_PARTY_NOTICES.md), [License texts](LICENSES/README.md).

This is an independent project, not endorsed by IP Messenger, SHIROUZU Hiroaki or FastCopy Lab. Names/artwork belong to their respective owners. References: [published protocol](https://github.com/shirouzu/ipmsg/blob/master/protocol.txt), [older Windows source](https://github.com/shirouzu/ipmsg). Windows **5.9.1** is the acceptance target; current physical interoperability is not asserted as verified.
