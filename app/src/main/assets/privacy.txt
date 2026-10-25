# Privacy and backup limits

PiXi Snapshot 0.1.0 — effective October 25, 2026.

PiXi Snapshot works offline. It has no ads, analytics, telemetry, account system, or Google Play Services dependency. The developers do not receive your inventory, snapshots, files, or SSH credentials.

## What stays on your device

When you create a snapshot, the app records the apps visible to your Android user: names, package IDs, versions, reported installer package, and your edited source labels/URLs. It also records your profile name, notes, device model, Android version, and visible Android user/profile identifiers. Optional files are included only when you select them: a Nova backup, wallpaper, and screenshots. No APKs, app-private data, account passwords, SSH keys, or downloaded maps are collected automatically. Avoid choosing a sensitive file as an attachment or putting passwords in notes.

Selected document references, server settings, edited source labels, and snapshots are stored in the app's private storage. SSH keys and known_hosts files are read from the documents you select; the app retains permission to reopen them for queued exports. Keys are handled in memory during transfer and are not bundled into snapshots. Clearing app storage or uninstalling removes the internal snapshots and settings. Export a copy to a folder or server first. Files you exported remain until you delete them.

## When the network is used

Network access is used when you ask to export to your own SFTP server, or open an app/store/source link. SFTP uses SSH encryption and strict host-key verification against the known_hosts file you provide. The server operator can see the uploaded snapshot. Store/source links may open a third-party browser or app whose own privacy policy applies.

Exports show the archive size and offer send now or wait for an unmetered connection. Android determines when queued work runs; Wi-Fi or Tailscale reachability to your server is still required. A queued export keeps its selected destination even if you later edit the active server settings. Setup includes cancellation. Notification permission is optional; transfer status also appears in the app.

## Protect exported copies

Snapshot ZIP files are not password-encrypted. Their contents can reveal installed apps, screenshots, launcher setup, and personal notes. Store and share them accordingly. SSH encrypts the upload connection, not the stored ZIP on your server.

## Permissions

- Installed-package visibility: build the app inventory and find missing apps.
- Internet and network state: optional SFTP transfer and network selection.
- Boot completed: preserve scheduled exports across a reboot.
- Notifications: optional export progress and results.
- Set wallpaper: only when you explicitly choose the restore action.
- Document picker: access only the files and destinations you choose; no broad storage permission.

## Restore boundaries

This is a setup recorder and assisted restore guide. It does not reinstall apps silently, recreate accounts, copy private app data, recover a wiped internal snapshot, or rebuild Android profiles. Exported Nova backups must be imported through Nova; some widget bindings, permissions, and settings require manual work. Nova is optional, is a separate third-party product, and is not bundled. Choose a fresh Nova backup yourself; PiXi does not trigger Nova's backup process automatically.

The first preview limits snapshots to 256 MiB of uncompressed contents, 100 screenshots, and 2 MiB of inventory/notes. It rejects unsupported formats and malformed inventories. It is intended for lightweight setup recovery rather than full-device disaster recovery.

Source and issue tracker: https://github.com/sKRxPTiD/pixi-snapshot
