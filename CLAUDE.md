# WrtCtrl

Android app for managing an OpenWrt router through its ubus JSON-RPC API (`/ubus` via uhttpd + rpcd, the API LuCI uses). Part of the VKNXWorks Ctrl series: same look, structure and libraries as ProxCtrl, AdCtrl and the other Ctrl apps (Kotlin, Compose, Material 3, manual `AppContainer` DI, OkHttp + kotlinx.serialization, Ctrl / Signal look with a floating tab bar). No Firebase, Google Play Services, WebView or any other Google library: it must build for F-Droid.

## Working on this repo

- Push fixes and updates straight to `main` without waiting to be asked, with no branches or pull requests.
- Every push needs a detailed `CHANGELOG.md` entry.
- Every user-visible string goes in `app/src/main/res/values/strings.xml`.
- Check ubus objects, methods and rpcd ACL names against the router (`ubus -v list <object>`) or the OpenWrt / LuCI source; never guess them. Parse responses leniently (missing fields must never crash).
- Every change that could cut the phone off (Wi-Fi, interfaces, firewall) goes through `uci apply` with rollback and an explicit confirm. Test write actions on something harmless first (a disabled test port forward or a test static lease), never on the WAN or main LAN interface.
- AdGuard Home runs on the same router and is managed by AdCtrl: don't duplicate DNS filtering here.
- Never read the Wi-Fi SSID, and never switch or fall back between addresses: one address per router.
- Never install packages on the router without the user's explicit confirmation.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "WrtCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.
- CI signs with the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` and `KEY_PASSWORD` secrets and the `KEY_ALIAS` variable (`wrt`), and checks the APK is signed with that key. CI also runs a Google-free dependency audit on every build; keep it passing. Never commit a keystore, its base64 or its passwords.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.

## Encryption at rest

- The device nicknames and notes (`filesDir/client_notes/<router id>.json`, `data/router/NotesStore` behind `ClientNotes`) and the background checks' saved state (`noBackupFilesDir/notify/<router id>.json`, `CheckStore`, which lists every MAC address seen) are sealed with AES-256-GCM (`data/crypto/SealedFile`, marker `WRTCTRL-REST1`) under the data key (`DataKeyStore`: 32 random bytes in `filesDir/data.key`, wrapped by the Keystore key `wrtctrl_data_at_rest` through `KeystoreWrapper`/`KeystoreAes`; no user authentication, so background checks work while locked). `DataKeys` holds the one key store and `SealedCache` of the process. The router list, the settings, the ntfy push address and everything else in SharedPreferences/DataStore are not encrypted (read synchronously at start); passwords, sessions and the app lock vault keep their own encryption and keys and must not be touched from here.
- Readers must accept both sealed and ordinary data (`SealedFile.isSealed`). Without a data key the app reads and writes as before encryption (notes in the `client_notes` preferences, check state as ordinary files).
- Nicknames and notes can't be made again: never delete or overwrite a file that can't be opened, never replace an existing `data.key`, and never create a Keystore key on the decrypt path (`KeystoreAes.existing`). Notes leave the old preferences only through `SealedFile.writeSealedChecked` (temp file, sync, read back, compare, atomic rename) with the old copy removed last; when both copies exist they are merged and the preferences win. Unreadable notes put the router in `ClientNotes.unreadable`: `set` refuses, and `NotesUnreadableCard` offers "Try again" and "Start empty" (renames to `.unreadable`).
- The check state can be made again (`SealedCache`): unreadable means dropped, and the next check starts over. Ordinary files from an older version are sealed at start through `SealedFile.sealInPlace`.
- Key unwrapping, sealing and migration run off the main thread (`ClientNotes` and `CheckStore` calls go through `Dispatchers.IO`); don't call them from the main thread.
- Configuration backups stream straight to the user's chosen file and stay in OpenWrt's format.
- `SealedFileTest` and `NotesStoreTest` cover the format, the migration, the cache and the key rules (`./gradlew :app:testDebugUnitTest`; CI doesn't run them).

## Languages

English (`values/strings.xml`) and Dutch (`values-nl/strings.xml`, Belgian Dutch, informal "je"). Android picks by the phone's language (`res/xml/locales_config.xml` lists both for the per-app language setting); there is no in-app switch. A new string needs both files.

## Dependency verification

Gradle checks every downloaded library and plugin against `gradle/verification-metadata.xml` (sha256 only, no PGP). When a dependency or plugin changes, start the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/WrtCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), decode the file from its log (gzip + base64 between the BEGIN/END markers; it already trusts `-sources.jar` and `-javadoc.jar` for Android Studio), check the diff and commit it in the same push. Never turn verification off or make it lenient in the real build.

`settings.gradle.kts` lists only `google()` and `mavenCentral()` for plugins, not `gradlePluginPortal()`: the portal serves different files for the Kotlin plugin markers, which made verification fail whenever Maven Central had a hiccup. Don't add it back unfiltered (see the comment in that file).

## Build and signing (CI)

`.github/workflows/build.yml` has four jobs: `version` (is this a release?), `build` (Gradle, **no secrets**, `UNSIGNED_RELEASE=true` gives `app-release-unsigned.apk`; runs every check), `sign` (no checkout, no Gradle: `zipalign` + `apksigner` with the release key, then checks the signer against `EXPECTED_CERT_SHA256` and that the APK's contents didn't change) and `release` (attestation, release notes, publish).

- Never give the `build` job a secret, and never run Gradle or check out the repo in `sign`. New build checks (tests, lint, APK checks) go in `build`.
- `EXPECTED_CERT_SHA256` in `build.yml` must match the fingerprint in `SECURITY.md` and `README.md`; the build fails if it doesn't.
- No Gradle cache in any workflow (`cache-disabled: true`): a cache saved by a failed run used to break every later build.
