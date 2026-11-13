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

## Dependency verification

Gradle checks every downloaded library and plugin against `gradle/verification-metadata.xml` (sha256 only, no PGP). When a dependency or plugin changes, start the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/WrtCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), decode the file from its log (gzip + base64 between the BEGIN/END markers; it already trusts `-sources.jar` and `-javadoc.jar` for Android Studio), check the diff and commit it in the same push. Never turn verification off or make it lenient in the real build.
