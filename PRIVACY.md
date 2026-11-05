# Privacy and permissions

Applies to the v0.7.3 release candidate. This independent local-network app has no account service, cloud relay, advertising or analytics SDK in its published source. GitHub downloads and Windows/browser software have their own policies.

## Local data and sharing

The app stores chat text, file metadata/links, device identities/addresses, approvals, preferences and reviewed pending items locally. Peers receive the name, protocol/network identity and content you deliberately share. Use trusted local networks and intended destinations.

History is app-private SQLite, not separately database-encrypted. Device storage protection follows Android settings. Android backup is disabled. Public Downloads/IPMsg files and selected folders follow Android/provider permissions. Clearing history does not delete public downloads.

Clipboard content is read after Send clipboard and a preview, without monitoring. Selected shared files may require temporary private copies while URI grants remain usable. Public download deletion is a separate storage-management action.

## Camera

The owner must start/approve foreground camera sharing. A temporary HTTPS server sends frames directly to the selected viewer. The camera link contains a secret fragment; keep it private. After authentication, the browser removes the fragment and uses a secure session cookie.

Built-in receiver captures create new media only in that browser. The phone receives activity alerts and a live recording indicator, without saving a new captured media file. Independent screen recording cannot be detected. There is no live audio.

## Permissions

- Network/Wi-Fi/multicast: discovery, direct messages/files and local HTTPS camera viewing.
- Foreground service/notifications: user-controlled listening and message/file/camera alerts.
- Camera: optional owner-started capture/sharing.
- Wake lock: bounded support during active transfers.
- Vibration: notifications, subject to Android settings.

System file pickers grant access to selected files/folders. There is no remote phone-storage browsing. Android/providers control URI access, notification display and public storage.

## Controls

Stop listening, revoke/block peers in Nearby → Manage, stop the camera, clear history or remove downloads in Settings → Storage. Uninstall removes app-private data but may leave public downloads. Do not post keys, camera links, private addresses, chats or personal media in reports.
