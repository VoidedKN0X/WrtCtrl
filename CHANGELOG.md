# Changelog

What changed in each WrtCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/WrtCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.1.0 (2026-10-31)

The first build of WrtCtrl: the foundation the router features are built on. It doesn't connect to a router yet; router setup arrives in the next version.

### New

- **The Ctrl / Signal design**, the same as every Ctrl app: dark and high-contrast with a violet accent, and a light mode that follows your phone's light or dark theme automatically.
- **Four tabs: Overview, Clients, Network and System.** Each one says what it will show once router setup is in.
- **Settings open from your profile picture** at the top right of every tab, on the **You** page, which also has the About section, **Report a bug**, **Source code**, the licence and **Support WrtCtrl**. There's no settings tab or menu.
- **App lock** with its own PIN (at least 6 digits) and optional fingerprint unlock, the same lock as the other Ctrl apps. Choose when it locks again (right away up to 1 hour, or only when the app restarts), and turn on **Confirm changes** to be asked for your PIN or fingerprint before anything changes on a router, once router features exist. Wrong PINs make you wait longer each time, and **Forgot PIN?** removes your routers so you can add them again.
- **Hide in screenshots** and **Hide in recent apps**: two separate switches under **Settings › Privacy and security**, both off by default.
- **Warnings** under **Settings**: from how much memory use and storage (overlay) use the Overview will flag your router.
- **A new app icon**: a router with two antennas on the Ctrl keycap.

### Security

- Router logins will be stored encrypted with a key in the phone's secure hardware (Android Keystore), and with the app lock on, also with your PIN while WrtCtrl is locked. Nothing is backed up or moved to another phone.
- Plain HTTP is only ever used for a router address you chose after a warning, and an HTTPS connection is never downgraded. Self-signed certificates are only accepted by their exact fingerprint, per router.
- Google-free and telemetry-free: no Google libraries, no WebView, no analytics or crash reporting. Fonts are built in.
- 64-bit ARM phones only (arm64-v8a), like every Ctrl app.
