# Installation and troubleshooting

Download the signed APK from Releases. Verify its SHA-256 against SHA256SUMS.txt if desired, then open it from Android Files. Allow installation from that file manager when requested. Android 10+ is required.

Install over your current IPMsg/Lantern installation to retain history and identity. If Android rejects an update signature, retain the old app and report it. Uninstalling erases app-private history. An unrelated developer key cannot update this APK.

## Local network and Windows Firewall

Use the same router, with the phone on Wi-Fi. Keep Windows IP Messenger running. Allow the Windows application through Firewall on a trusted **Private** network: UDP 2425 for discovery/messages and TCP 2425 for IPMsg files. Do not disable the firewall globally.

Allow Android notifications and Start listening. Refresh Nearby, then approve the intended PC. If discovery fails, find its IPv4 address with `ipconfig` and add it manually. Settings shows My IP and troubleshooting. Stop any other Android IPMsg listener using port 2425.

Listening is user-controlled and does not restart automatically after boot/process termination. Keep its ongoing notification enabled. Android battery and notification settings affect background receiving and heads-up alerts.

## Storage

Accept files in the source chat. Downloads default to Downloads/IPMsg. Settings → Storage contains Change folder, Use default, Manage storage and Browse storage. Re-select a folder if its permission is revoked. Open/Folder actions depend on installed apps/providers.

Restart begins at zero. Keep the sender and source file available; expired offers must be shared again. Wait for Saved and a usable file link. Duplicate names are intended to receive unique destinations rather than overwrite files.

## Common problems

- No devices: check listener, Wi-Fi, Firewall, Refresh/manual IP. Guest Wi-Fi/client isolation, VPN routing or separate subnets can prevent direct connections.
- Missing alerts: allow notifications and the relevant channel. Android/Do Not Disturb control banners and sound.
- Timeout or early connection end: keep the sender online and retry an eligible offer. Report the exact error and whether a small file also fails.
- Save failure: check free space and folder access; re-select a revoked destination.
- Changed key: verify the intended peer before approving. Do not reset trust merely to remove a warning.

Do not post camera URLs/tokens, keys, private addresses, chats or personal media in public issues.
