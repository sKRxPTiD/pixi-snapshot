# Validation

## 0.1.2 creator credit

Signed release build and release lint passed. APK signature verified with the existing release certificate. The main screen was opened on the Android 15/API 35 emulator and visually checked; the requested creator credit appears centered below the status panel. The README screenshot was refreshed from that clean emulator screen. No backup, restore, or SFTP behavior changed, and the release was not installed on the physical phone.

## Run archive and lifecycle checks

Use a disposable Android emulator. The custom instrumentation runner deliberately refuses physical devices because it resets the test app's preferences. It uses only platform instrumentation APIs; no Google test service is required.

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s EMULATOR install -r app/build/outputs/apk/debug/app-debug.apk
adb -s EMULATOR install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s EMULATOR shell am instrument -w org.nullprotocol.pixisnapshot.test/org.nullprotocol.pixisnapshot.ReleaseChecks
```

The runner checks archive round trips and exact Nova export, consecutive file creation, invalid format/package/path rejection, the manifest size bound, exclusion of server credentials from the manifest, and persistence of selected files across activity restarts. A pass prints `ALL RELEASE CHECKS PASSED`.

## Optional SFTP integration checks

Run a temporary OpenSSH test server on the emulator host's loopback address, port 22222, with a test-only key and existing writable folder. Copy the test client key and trusted known_hosts file into the app's external-files directory as `client-key` and `known_hosts`. Do not use production credentials. The host's known_hosts entry uses `[10.0.2.2]:22222`. Run:

```sh
adb -s EMULATOR shell am instrument -w \
  -e remoteFolder /absolute/path/to/test-upload-folder \
  -e user TEST_USER \
  org.nullprotocol.pixisnapshot.test/org.nullprotocol.pixisnapshot.ReleaseChecks
```

This adds authenticated SFTP upload/remote size verification, rejection of an unknown host key, pre-start cancellation, persisted unmetered scheduling, a destination frozen independently of changed preferences, forced background JobService execution, and queue cancellation. Compare the resulting file on the host with the app's ZIP to independently confirm byte integrity. The forced scheduler command verifies the worker; it does not prove Android will promptly run a background job under every battery/network condition.

Release instrumentation can be built with `PIXI_TEST_BUILD_TYPE=release`, `PIXI_SNAPSHOT_SIGNING_PROPERTIES` configured, and `:app:assembleReleaseAndroidTest`. Test and app APKs must use the same certificate. Keep the signing files outside the checkout.

## Coverage for 0.1.0

Final results are recorded below after the release checks complete. Earlier development smoke checks opened and created QuickSnaps on an Android 8/API 26 AOSP emulator, Android 15/API 35 AOSP emulator, and Pixel 9 on LineageOS/Android 16/API 36. The physical Pixel check inventoried 77 apps, saved a 2.5 KB ZIP using the system document picker, and reopened it with zero missing apps on the same phone.

Physical validation covers one phone. Automatic Nova import/widget reconstruction, a real device wipe, different launcher implementations, real Dolo/Swervo/Novo transfers, metered-to-unmetered transitions during a transfer, reboot scheduling, and byte-for-byte reproducible builds are not established by those checks.

Final validation on October 25, 2026:

- Debug build and lint: pass (zero lint errors; remaining warnings include English-only UI strings and newer-toolchain suggestions). Full package visibility is intentionally declared for inventory and documented beside the permission.
- Signed, optimized release build and lint: pass. APK signature verified with Android apksigner; RSA-4096 certificate fingerprint is in SECURITY.md.
- Signed release instrumentation: **20/20 checks passed on API 26/Android 8 and API 35/Android 15**, including actual SFTP and background JobService transfer through a temporary loopback OpenSSH server. The uploaded ZIP was independently opened on the server and its expected manifest, notes, and Nova fixture inspected.
- Pixel 9/API 36: official signed release installed and opened. Three existing internal QuickSnaps and saved settings were preserved from the development installation and individually SHA-256 verified before replacing the temporary migration build with the non-debuggable official release. The main screen sees the preserved latest QuickSnap.
- APK contains only bundled open-source notices and privacy text; no personal host addresses, credentials, signing keys, or QuickSnaps are in the source release. The public screenshot is from a clean emulator install.

The forced background-worker test and persisted-job inspection do not establish reboot recovery or every real-world Wi-Fi/cellular/VPN transition. Those remain explicitly unverified. There has been no destructive device-wipe test. The same signed APK was used on both emulator API versions and the physical phone.

## 0.1.1 naming update

The app label and displayed backup wording are now PiXi QuickSnaps / QuickSnap / QuickSnaps. The signed update keeps the existing application ID, signing certificate, private-storage paths, notification-channel identifier, ZIP manifest name, and format version for compatibility. New backup filenames use `pixi-quicksnap-`.

Signed release build, lint, and APK signature checks passed. Version 0.1.1 installed over 0.1.0 on the Pixel 9 without uninstalling; the home screen displayed the saved latest backup with the new QuickSnap wording. The updated home screen was visually checked for fit and legibility. No SFTP implementation changed; the previous transfer-test results remain recorded under 0.1.0 rather than being claimed as new tests of 0.1.1.

On the signed 0.1.1 APK, all 11 archive/lifecycle instrumentation checks passed on the Android 15/API 35 emulator. A real exported 0.1.0 ZIP from the phone opened successfully with 77 app entries. Creating a new backup displayed “QuickSnap is ready” and offered the new `pixi-quicksnap-` filename through Android's document picker. The refreshed public screenshot is from the clean emulator install.
