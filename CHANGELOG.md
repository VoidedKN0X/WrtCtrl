# Changelog

What changed in each WrtCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/WrtCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.2.0 (2026-10-31)

WrtCtrl now signs in to your OpenWrt router. It includes everything from 0.1.0, which was never released on its own. The four tabs still show what's coming; they fill up in the next versions.

### New

- **Router setup.** On first start WrtCtrl asks for your router's address, a name (optional; it uses the router's hostname otherwise), the username and the password. It signs in to the router's ubus API, the same one LuCI uses, then shows the model, OpenWrt version and who you're signed in as before you save.
- **What this login can do.** After signing in, and on the **You** page, WrtCtrl lists what your login is allowed to do on the router: status, connected devices, DHCP, port forwards and blocking, restarting interfaces, services, the system log, package updates and reboot. It reads this from the login's rpcd permissions, so anything it can't do will be greyed out instead of failing. A login that can't change anything starts in read-only mode.
- **Several routers.** Tap your profile picture › **Routers** to add more routers (such as an access point), switch between them, change one's address or sign-in, turn read-only mode on per router, or remove one.
- **Stays signed in.** The router ends a session after a few minutes without use; WrtCtrl then signs in again on its own with the saved password, so you don't see it.
- **The profile picture** shows your router's first letter, with a small violet square while WrtCtrl is connected. Connection problems show on the **You** page.
- **README:** a ready-to-copy least-privilege setup (a dedicated `wrtctrl` login with read-only and operator permissions), checked against OpenWrt 25.12.

### Security

- **An unencrypted connection only after you agree.** OpenWrt serves its API over plain HTTP unless you set up HTTPS. For an http:// address, WrtCtrl first asks **Use an unencrypted connection?** and sends nothing, not even the username, until you tap **Use HTTP**. The router is then marked **Not encrypted (HTTP)** wherever it's shown.
- **Self-signed certificates by fingerprint.** For an https:// address with a certificate the phone doesn't trust, WrtCtrl shows its SHA-256 fingerprint to compare with the router's before sending anything, then accepts only that exact certificate for that router. If it ever changes, WrtCtrl stops and asks again.
- **Your password stays with its router.** It's stored encrypted (Android Keystore, and your app PIN while WrtCtrl is locked) and only reused for the same address and username: change either and you have to type it again. The setup screen is always kept out of screenshots and the recent-apps view.
- **Read-only mode** blocks every change before anything is sent to the router.
- One address per router, which you set: WrtCtrl never reads your Wi-Fi network's name and never switches addresses on its own.

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
