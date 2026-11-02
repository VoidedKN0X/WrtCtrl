# Changelog

What changed in each WrtCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/WrtCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v1.0.3 (2026-11-02)

Menus no longer stay on top of the lock screen.

### Security

- **Menus close behind the lock screen.** If you left WrtCtrl with a menu open (such as the ⋮ menu of a service under **System > Services**) and the app locked, that menu stayed visible and could be tapped on top of the lock screen. Start, Reload and Enable could then act on your router without your PIN. Menus are now hidden while WrtCtrl is locked or waiting for **Confirm changes**, and come back after you unlock. Stop, Disable and Restart already asked first and were never reachable this way.

## v1.0.2 (2026-11-02)

A security update: the Guest Wi-Fi tile always waits for your PIN while WrtCtrl is locked, and the Wi-Fi password dialog stays out of screenshots. Plus a security policy, issue forms and a clearer README.

### Security

- **The Guest Wi-Fi tile no longer switches anything while WrtCtrl is locked.** With **Background checks while locked** on, a tap on the Quick Settings tile could switch the guest network on or off without your PIN, even though the README promised it opens WrtCtrl while it's locked. Now it always opens WrtCtrl while the app lock is locked, and the app asks for your PIN first. That includes the time after **Lock again** has run out while WrtCtrl was in the background, before you open it again. Once WrtCtrl is unlocked, the tile works as before.
- **On a locked phone, the tile asks you to unlock the phone first** before it does anything (switching, or opening WrtCtrl).
- **The Wi-Fi edit dialog (network name and password) is always kept out of screenshots, screen recordings and the Recents preview.** It already asked for this, but a dialog is a window of its own, so on some phones the protection could miss it. The dialog now sets it on its own window, whatever **Hide in screenshots** says.

### Project

- New **SECURITY.md**: how to report a security problem privately (through GitHub's **Report a vulnerability**), what counts, and the SHA-256 fingerprint of the release signing key.
- New issue forms for bug reports and feature requests, with a reminder to leave out router addresses, passwords and other personal details. Blank issues are off, and security problems point to the private report.
- README: clearer install steps for first-time GitHub users (which file to tap on the Releases page), a new **Is it safe to install?** section (open source, built by GitHub Actions, signing key fingerprint, every permission explained), **How WrtCtrl is made**, the full Ctrl series list, and the empty Screenshots section removed. Two claims were corrected: interface restarts have no rollback (they ask first instead), and WrtCtrl detects `luci-app-statistics` but doesn't draw its history graphs yet.

## v1.0.1 (2026-11-02)

### Fixed

- **Popup menus no longer show a grey box.** Menus used Material's default shadow and tonal tint, which Android draws inside the popup window, so they showed a grey, square-ish edge (most visible in light mode). They now use the flat Signal surface (#141417 dark, #FFFFFF light) with 16dp corners, a thin 1dp border in the card border colour (#232328 dark, #DEDED7 light) and no shadow, in both themes. Menu text uses the text colour, icons the muted colour and disabled items the stopped grey.
- Menus touched: the service actions menu (start, restart, reload, stop, start at boot) on **System > Services**. Every popup menu now goes through one shared `CtrlDropdownMenu`, so new menus get the same look. WrtCtrl has no other popup menus, tooltips or custom popups; dialogs and bottom sheets already used the Signal surface. Nothing else changed.

## v1.0.0 (2026-10-31)

WrtCtrl is complete: everything you do with your OpenWrt router from the phone, with every risky change protected by OpenWrt's rollback. It includes 0.1.0 and 0.2.0, which were never released on their own.

**Copy the permission file from the README to your router again** (and restart rpcd): 1.0 needs more permissions than 0.2 for Wi-Fi, the neighbour table, package updates, blocking devices and backups. WrtCtrl greys out whatever your login can't do.

### New

- **Overview.** Your router's hostname, OpenWrt version and uptime; WAN download and upload speeds with a live graph while the screen is open; CPU, memory and storage (overlay) cards; the WAN card with its public IP (read from the router itself), gateway, DNS, uptime and IPv6; the Wi-Fi radios; router details (model, target, kernel, load, temperature, swap); and **Needs a look** for the WAN down, a radio off, memory or storage over your warning level, package updates and unapplied changes. Pull down to refresh.
- **Clients.** Every device the router knows, merged from DHCP leases, host hints, the neighbour table and every Wi-Fi network's connected devices: name, IP addresses, MAC address with its vendor (looked up on the phone in a built-in list of about 40,000 vendors; private Wi-Fi addresses are marked as such), wired or Wi-Fi with band, signal and rate, online state and lease expiry. Search, sort by name, IP, online or signal, and filter by online, wired or Wi-Fi band.
- **A page for every device** with its addresses and connection, signal and rate graphs while it's open, data use (with nlbwmon), and:
  - **Make the lease static** with an IP address and hostname you can change, or change and remove an existing static lease.
  - **Disconnect from Wi-Fi** (it can't rejoin for a minute).
  - **Block from the internet**: a firewall rule that keeps it on your local network but off the internet, and **Allow the internet again**.
  - **Wake up** with Wake-on-LAN (with etherwake).
  - **Nickname and notes**, kept on the phone only.
  - **Open in AdCtrl** when AdCtrl is installed, for DNS filtering (WrtCtrl doesn't duplicate it).
- **Network tab** with a **Guest Wi-Fi** switch (when the router has a guest network) and:
  - **Wi-Fi**: every radio and network with on/off switches, band, channel, width, power and encryption, and changing a network's name or password after a strong warning.
  - **DHCP**: active leases, static leases (add, change, delete) and the address pools.
  - **Port forwards**: list, add, change, delete, and switch them on or off, with checks on ports (single or ranges) and the device address.
  - **Interfaces**: state, addresses, traffic and errors, and restarting one (the LAN and WAN need their name typed).
- **System tab**:
  - **Services**: running and starting at boot, with start, stop, restart, reload, and start at boot on or off. Stopping or restarting asks first, with an extra warning for services like network, firewall, dnsmasq and AdGuard Home.
  - **Packages**: updates through apk (or opkg on older OpenWrt), refreshing the package lists, upgrading selected packages or all of them after a warning (with an extra one for core packages), and the package manager's output.
  - **System log** with search and filters for the kernel, dnsmasq, hostapd and errors, refreshing every 5 seconds.
  - **Download config backup** (the same archive as LuCI's) to a file you choose.
  - **Optional packages**: nlbwmon, etherwake and collectd, what each one adds, and installing one after you confirm.
  - **Reboot**, after typing the router's hostname.
- **Changes are applied with rollback.** Every Wi-Fi, DHCP and firewall change is applied the way LuCI does it: WrtCtrl shows **Confirm changes within 30 s** with a countdown, waits a moment for services to restart, and confirms once it can still reach the router. If the change cut the phone off, the router puts the old settings back by itself when the countdown runs out. **Undo now** puts them back at once.
- **Notifications** from WrtCtrl's own background checks (every 15 to 120 minutes): router unreachable (after two missed checks), WAN down, WAN back up with its address, a new device, and once a day the package updates. Each one can be switched off, **Check now** runs a check at once, and the lock screen only shows that something needs a look.
- **Push through ntfy**: anything that posts to WrtCtrl's push address starts a check right away. The README has hotplug scripts for the router that push when the WAN goes down or up and when a device gets a DHCP lease.
- **Home screen widget**: WAN status, WAN throughput, online devices and the WAN address. Tap it to open WrtCtrl.
- **Quick Settings tile**: guest Wi-Fi on or off, applied with rollback. With **Confirm changes** on, in read-only mode or while WrtCtrl is locked, it opens WrtCtrl instead.
- **App shortcuts**: Clients, Guest Wi-Fi and Reboot (which still asks for the hostname).
- **Background checks while locked** under **Settings › Privacy and security**, so notifications, the widget and the tile keep working with the app lock on.

### Security

- Every change is checked against the login's rpcd permissions and the router's read-only mode before anything is sent, and **Confirm changes** asks for your PIN or fingerprint first.
- Removing a router also removes its notifications, its last check and your device nicknames and notes from the phone.
- The Wi-Fi password dialog is kept out of screenshots and the recent-apps view.

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
