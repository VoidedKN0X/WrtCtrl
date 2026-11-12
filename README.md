# WrtCtrl

**Manage your OpenWrt router from your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/WrtCtrl?label=download)](https://github.com/VoidedKN0X/WrtCtrl/releases/latest) ![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

WrtCtrl is an Android app for [OpenWrt](https://openwrt.org): a fast, touch-first alternative to LuCI for what you do on a phone. See who's connected, check bandwidth, switch Wi-Fi and the guest network on or off, manage DHCP leases and port forwards, and reboot. It talks to the same ubus API LuCI uses.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

## Features

- **Overview**: model, OpenWrt version, kernel, uptime, load, CPU, memory, temperature and storage; WAN status, public IP (read from the router itself), gateway, DNS and IPv6; a live WAN throughput graph; Wi-Fi radios; and an attention list for WAN down, a radio off, low memory, storage almost full, package updates and unapplied changes
- **Clients**: every device on your network, from DHCP leases, host hints, the neighbour table and Wi-Fi, with vendor (from a list built into the app, no online lookup), connection type, signal and lease expiry; sort and filter by name, IP, online, band and signal. Each device's page has its signal and rate history, data use (with nlbwmon), and actions: make its lease static, disconnect it from Wi-Fi, block it from the internet, wake it (Wake-on-LAN with etherwake), your own nickname and notes, and **Open in AdCtrl** when AdCtrl is installed
- **Network**: Wi-Fi networks and a **guest network** switch, DHCP leases and static leases, **port forwards**, and interfaces with their traffic counters
- **System**: services, package updates (with `apk` or `opkg`, whichever your router has), the system log, reboot and config backups
- **Changes that could cut you off are undone automatically**: Wi-Fi, DHCP and firewall changes (including port forwards and blocking a device) are applied with OpenWrt's rollback. If WrtCtrl can't reach the router afterwards, the router puts the old settings back by itself. Restarting an interface, rebooting and package upgrades can't be rolled back, so WrtCtrl asks first (for the LAN, the WAN and a reboot you type the name to confirm)
- **Notifications**: router unreachable, WAN down or back up, a new device, and a daily package-update digest, from WrtCtrl's own background checks, woken at once by your **self-hosted ntfy** (push from the router's hotplug scripts)
- **Quick Settings tile** for guest Wi-Fi, a **home screen widget** (WAN status, throughput, online devices) and **app shortcuts** (Clients, Guest Wi-Fi, Reboot)
- **Several routers**, such as a main router and a dumb access point
- **App lock** with its own PIN and optional fingerprint unlock, a **read-only mode** per router, certificate pinning, and optional **Hide in screenshots** and **Hide in recent apps**

## Requirements

- Android 15 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- OpenWrt with LuCI's ubus API (`uhttpd`, `rpcd` and `luci-base`, installed with LuCI)
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google libraries, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

## Install

1. Open the [latest release](https://github.com/VoidedKN0X/WrtCtrl/releases/latest) on your phone. Scroll down to **Assets** and tap the file that ends in `.apk` (for example `WrtCtrl-v1.0.5.apk`). The "Source code" zip and tar.gz files are not the app.
2. Open the downloaded file. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds WrtCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/WrtCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/WrtCtrl` in Obtainium by hand.

## Is it safe to install?

- **Open source:** all the code is in this repo, under the GPL-3.0 licence.
- **Built by GitHub Actions, not on a personal computer:** every release on the Releases page is built from this code by [the build workflow](.github/workflows/build.yml), which also checks that no Google library ended up in the app.
- **Always signed with the same release key.** Android refuses an update signed with a different key, and the CtrlF-Droid repo only accepts APKs signed with this key. Its SHA-256 certificate fingerprint is:

  ```
  0C:3B:15:A4:11:D7:41:83:BA:F8:06:2B:31:24:29:66:4E:09:22:E4:BC:06:BE:6B:8B:02:65:4A:4E:AD:80:CB
  ```

  Advanced users can compare it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).
- **Checksum:** each release's notes list the APK's SHA-256. Compare it with `sha256sum WrtCtrl-v*.apk`.
- **Built by this repo:** every release APK has a signed build provenance attestation. With the [GitHub CLI](https://cli.github.com), `gh attestation verify WrtCtrl-v1.0.7.apk -R VoidedKN0X/WrtCtrl` proves it was built by this repo's workflow from the tagged commit.

**Permissions**

| Permission | What WrtCtrl uses it for |
| --- | --- |
| Internet (`INTERNET`) | Talking to your own routers, and nothing else |
| Network state (`ACCESS_NETWORK_STATE`) | Waiting for a network before background checks and widget refreshes run. It never reads the Wi-Fi name |
| Biometrics (`USE_BIOMETRIC`) | Optional fingerprint unlock for the app lock, and for **Confirm changes** |
| Hide other apps' floating windows (`HIDE_OVERLAY_WINDOWS`) | Hide other apps' floating windows over the PIN screen: while the lock, PIN, a password or key, or a confirmation shows, no chat head or overlay can sit on top of it. Granted at install, no prompt; overlays work as usual everywhere else |
| Notifications (`POST_NOTIFICATIONS`) | Router alerts, only if you turn them on |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Putting the background checks and widget refreshes back on schedule after the phone restarts. Added by Android's background work library (WorkManager) |
| Keep awake (`WAKE_LOCK`) | Keeping the phone awake for the few seconds a background check or widget refresh runs. Added by WorkManager |

`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is an internal permission Android adds to every app built for Android 13 and newer; it lets WrtCtrl keep its own broadcasts private. WrtCtrl has no location, camera, microphone, contacts, storage or foreground-service permission, and can't read the name of your Wi-Fi network.

Router passwords never leave the phone except to sign in to that router. See [Privacy](#privacy) for more.

## Recommended router setup

WrtCtrl signs in to rpcd, OpenWrt's ubus login service, like LuCI does. Root works, but a **dedicated login with only the permissions WrtCtrl needs** is safer: if your phone is ever lost, that login can't do anything else on the router.

The permissions below were checked against OpenWrt 25.12 and LuCI's own permission files. WrtCtrl shows what your login may do when you add the router, and greys out everything else.

### 1. Create the permission file

Copy this to the router as `/usr/share/rpcd/acl.d/wrtctrl.json`. It holds two profiles: **wrtctrl-read** (look, change nothing) and **wrtctrl-operator** (the changes WrtCtrl makes). Step 2 picks which ones the login gets.

```json
{
  "wrtctrl-read": {
    "description": "WrtCtrl: read-only access",
    "read": {
      "ubus": {
        "system": [
          "board",
          "info"
        ],
        "network.interface": [
          "dump"
        ],
        "network.device": [
          "status"
        ],
        "luci-rpc": [
          "getBoardJSON",
          "getHostHints",
          "getDHCPLeases",
          "getNetworkDevices",
          "getWirelessDevices"
        ],
        "luci": [
          "getVersion",
          "getMountPoints",
          "getInitList"
        ],
        "iwinfo": [
          "devices",
          "info",
          "assoclist"
        ],
        "rc": [
          "list"
        ],
        "log": [
          "read"
        ],
        "uci": [
          "get",
          "changes"
        ],
        "file": [
          "read",
          "stat",
          "exec"
        ]
      },
      "uci": [
        "system",
        "network",
        "wireless",
        "dhcp",
        "firewall"
      ],
      "file": {
        "/proc/stat": [
          "read"
        ],
        "/sys/class/thermal/thermal_zone*/temp": [
          "read"
        ],
        "/sys/class/thermal/thermal_zone*/type": [
          "read"
        ],
        "/usr/bin/apk": [
          "list"
        ],
        "/usr/sbin/nlbw": [
          "list"
        ],
        "/usr/bin/etherwake": [
          "list"
        ],
        "/usr/sbin/collectd": [
          "list"
        ],
        "/sbin/ip -4 -j neigh show": [
          "exec"
        ],
        "/sbin/ip -6 -j neigh show": [
          "exec"
        ],
        "/usr/bin/apk list --upgradable": [
          "exec"
        ],
        "/bin/opkg list-upgradable": [
          "exec"
        ],
        "/usr/sbin/nlbw -c json -g mac": [
          "exec"
        ]
      }
    }
  },
  "wrtctrl-operator": {
    "description": "WrtCtrl: Wi-Fi, DHCP, port forwards, blocking, interfaces, services, packages, backup and reboot",
    "read": {
      "cgi-io": [
        "backup"
      ]
    },
    "write": {
      "ubus": {
        "system": [
          "reboot"
        ],
        "network.interface.*": [
          "up",
          "down"
        ],
        "hostapd.*": [
          "del_client"
        ],
        "rc": [
          "init"
        ],
        "uci": [
          "set",
          "add",
          "delete",
          "rename",
          "order",
          "apply",
          "confirm",
          "rollback",
          "revert"
        ],
        "file": [
          "exec"
        ]
      },
      "uci": [
        "wireless",
        "dhcp",
        "firewall"
      ],
      "file": {
        "/usr/libexec/package-manager-call update": [
          "exec"
        ],
        "/usr/libexec/package-manager-call upgrade *": [
          "exec"
        ],
        "/usr/libexec/package-manager-call install *": [
          "exec"
        ],
        "/usr/bin/etherwake -D -i * *": [
          "exec"
        ]
      }
    }
  }
}
```

`package-manager-call` is LuCI's wrapper around `apk` (or `opkg` on older OpenWrt), so the same permissions work with either. The `ip neigh` commands give the neighbour table (who's online), `/proc/stat` the CPU use, and the `list` entries let WrtCtrl see which optional packages are installed. Without Wi-Fi on the router, the Wi-Fi entries simply do nothing.

**Updating from an earlier WrtCtrl version?** Copy this file again: 1.0 needs more permissions than 0.2 (Wi-Fi, the neighbour table, packages, blocking and backups), then run `/etc/init.d/rpcd restart`.

### 2. Add the login

Make a password hash on the router (type a long, random password):

```sh
uhttpd -m 'your-long-random-password'
```

Then add the login to `/etc/config/rpcd`, with the hash it printed:

```
config login
	option username 'wrtctrl'
	option password '$1$...the hash...'
	list read 'wrtctrl-read'
	list write 'wrtctrl-operator'
```

For read-only, leave out the `list write` line. Then restart rpcd: `/etc/init.d/rpcd restart`, and sign in to WrtCtrl with username `wrtctrl` and the password you typed.

### 3. HTTP or HTTPS

OpenWrt serves LuCI and its API over plain HTTP unless you set up HTTPS. Plain HTTP is fine on your home network or over a VPN; WrtCtrl asks before the first unencrypted connection. With HTTPS and the router's self-signed certificate, WrtCtrl shows the certificate's SHA-256 fingerprint the first time it connects (on the router: `openssl x509 -in /etc/uhttpd.crt -noout -fingerprint -sha256`); tap **Trust** and WrtCtrl accepts only that exact certificate for that router.

WrtCtrl only ever connects to the one address you enter for each router: no second address, no switching based on the Wi-Fi network you're on.

## Optional packages

WrtCtrl checks which of these are installed and only shows what they make possible. It never installs anything without asking you first.

| Package | What it adds |
| --- | --- |
| `nlbwmon` | Data use per device on each client's page |
| `etherwake` | **Wake-on-LAN** for wired devices |
| `luci-app-statistics` (`collectd`) | Detected and installable from WrtCtrl; history graphs from it are planned (WrtCtrl shows live graphs without it) |

## Notifications

Tap your profile picture › **Settings › Notifications** and turn on **Check in the background** (every 15 to 120 minutes; Android doesn't allow less). WrtCtrl then notifies when a router can't be reached, the WAN goes down or comes back, a new device joins, and once a day when package updates are available. Each kind has its own switch. The checks only read from your routers.

### Push through ntfy (optional)

With [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) connected to your self-hosted ntfy server, the router can wake WrtCtrl the moment something happens instead of waiting for the next check. A push is only a wake-up call: WrtCtrl ignores what it says, checks your routers and notifies about what it finds, at most once a minute.

1. In WrtCtrl: **Settings › Notifications**, turn on **Check in the background** and **Check when ntfy pushes**, then tap **Push address** to copy it. Treat it like a password.
2. On the router, put the push address in these two hotplug scripts (replace the example address).

**WAN down and back up**, as `/etc/hotplug.d/iface/99-wrtctrl`:

```sh
#!/bin/sh
PUSH='https://ntfy.example.com/upAbC123?up=1'
[ "$INTERFACE" = wan ] || exit 0
case "$ACTION" in
  ifup|ifdown) uclient-fetch -q -O /dev/null --post-data="wan $ACTION" "$PUSH" ;;
esac
```

**A device got a DHCP lease**, as `/etc/hotplug.d/dhcp/99-wrtctrl`:

```sh
#!/bin/sh
PUSH='https://ntfy.example.com/upAbC123?up=1'
[ "$ACTION" = add ] || exit 0
uclient-fetch -q -O /dev/null --post-data="lease" "$PUSH"
```

`uclient-fetch` is built into OpenWrt; with curl installed, `curl -fsS -d check "$PUSH" >/dev/null` does the same. A WAN-down push can only go out once the router reaches your ntfy server again, so if ntfy runs outside your home, WAN down is caught by the regular background check instead. Allow ntfy to run unrestricted in the background (WrtCtrl shows a row for it under **Battery** when it isn't).

## Widget, tile and shortcuts

- **Widget:** add **Router** from your home screen's widget list: WAN status, WAN throughput, online devices and the WAN address of the router you have selected. It refreshes when you open WrtCtrl, with every background check and at least every 30 minutes, and never changes anything.
- **Quick Settings tile:** **Guest Wi-Fi** switches every guest network of the selected router on or off (applied with rollback). With the app lock's **Confirm changes** on, in read-only mode, or while WrtCtrl is locked, it opens WrtCtrl instead (and asks for your PIN first).
- **App shortcuts** (long-press the icon): **Clients**, **Guest Wi-Fi** (opens the Network tab) and **Reboot** (opens the confirmation; you still type the hostname).

## App lock

Turn it on: tap your profile picture › **Settings › Privacy and security › App lock**. It's off until you turn it on.

- **Its own app PIN** of 6 to 12 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your router logins are encrypted with the PIN while WrtCtrl is locked.** The key only exists in memory while the app is unlocked and is wiped the moment it locks, and nothing is sent to your routers until you unlock. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Opening a link or Android's settings from WrtCtrl doesn't count as leaving, unless you stay away for more than 5 minutes. Once the time has run out, the widget, the Quick Settings tile and the background checks treat WrtCtrl as locked straight away, without waiting for you to open it again.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your router logins. The lock screen offers to remove your routers from the phone, so you can add them again; nothing changes on your routers.
- **Confirm changes** (optional) asks for your fingerprint or app PIN before anything that changes a router: Wi-Fi, DHCP, port forwards, services, reboots and upgrades. One confirmation covers the next minute.
- **The Quick Settings tile** never switches anything while WrtCtrl is locked: it opens WrtCtrl, which asks for your PIN first. With **Confirm changes** on it always opens WrtCtrl. **App shortcuts** and the widget only open WrtCtrl, behind the lock screen; the Reboot shortcut then still asks you to type the hostname.
- **Background checks while locked** (optional): notifications, the widget and the tile keep working while WrtCtrl is locked, but your router logins are then only encrypted with the Android Keystore, as without the app lock. Leave it off to keep them sealed with your PIN; the widget then only says that WrtCtrl is locked.

**Hide in screenshots** and **Hide in recent apps** (in the same section, also without the app lock, both off by default): the first keeps WrtCtrl out of screenshots and screen recordings (and blanks its recent-apps preview), the second shows a blank card for WrtCtrl in the recent-apps view while screenshots still work. With the app lock on, the recent-apps view always shows a blank card. Signing in to a router, the Wi-Fi password dialog, your ntfy push address, the lock screen and the PIN screens are always kept out of screenshots, and a copied push address is marked as sensitive so it doesn't show in clipboard previews.

## Privacy

**Telemetry-free and Google-free.** WrtCtrl has no analytics, crash reporting, usage stats, ads or tracking of any kind, no update checks, and no Google libraries or services. It never reads the name of the Wi-Fi network you're on. Device vendors are looked up in a list built into the app ([oui-data](https://github.com/silverwind/oui-data), from the IEEE registry), and your public IP is read from the router itself, so nothing about your network is sent anywhere else. Nicknames and notes for devices stay on the phone. Router passwords are encrypted with the Android Keystore and never written to a log, and nothing is included in Android backups or moved to a new phone.

Everything WrtCtrl connects to:

- **Your OpenWrt routers**, at exactly the address you entered for each one. Redirects are never followed, plain HTTP only goes to a router you allowed it for, and a self-signed certificate is only accepted when it's the exact one you confirmed for that router.
- **Nothing else.** With push turned on, the ntfy app on your phone delivers wake-up calls to WrtCtrl; WrtCtrl itself never connects to your ntfy server.

Links that open in your browser only when you tap them (the app itself doesn't contact these sites):

- **About:** Support (GitHub Sponsors), Report a bug and Source code (github.com), and Licence (gnu.org).
- **Install ntfy** on the Notifications page (f-droid.org).

## How WrtCtrl is made

WrtCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/WrtCtrl.git
   cd WrtCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

**Dependency verification:** Gradle checks every library and plugin it downloads against the SHA-256 checksums in `gradle/verification-metadata.xml` and refuses anything that doesn't match. When you add or update a dependency or plugin, run the **Dependency verification metadata** workflow (Actions tab, *Run workflow*), copy the file from its log (between the BEGIN/END markers, gzipped and base64-encoded), check the diff and commit it in the same push.

## Support the project

WrtCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/WrtCtrl/issues/new/choose) (pick **Bug report** or **Feature request**) helps too.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

In the app, the **About** section at the bottom of the **You** page (tap your profile picture) has **Support WrtCtrl** and **Report a bug** rows that open these pages.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same "Ctrl / Signal" design, dark or light with your phone.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [CameraCtrl](https://github.com/VoidedKN0X/CameraCtrl) | Private camera with RAW (DNG) on every lens |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [ClockCtrl](https://github.com/VoidedKN0X/ClockCtrl) | Alarms, timers, stopwatch and world clock |
| [ContactCtrl](https://github.com/VoidedKN0X/ContactCtrl) | Contacts: browse, edit, import and export |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [DateCtrl](https://github.com/VoidedKN0X/DateCtrl) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [GalleryCtrl](https://github.com/VoidedKN0X/GalleryCtrl) | Private, offline gallery with an encrypted vault |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LauncherCtrl](https://github.com/VoidedKN0X/LauncherCtrl) | Minimal, private home screen |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [LumenCtrl](https://github.com/VoidedKN0X/LumenCtrl) | Private, offline photo and video editor |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| [MapsCtrl](https://github.com/VoidedKN0X/MapsCtrl) | Private, offline maps and navigation |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [ShutterCtrl](https://github.com/VoidedKN0X/ShutterCtrl) | Remote control and photo transfer for Canon cameras |
| [SoundCtrl](https://github.com/VoidedKN0X/SoundCtrl) | JBL headphones and speakers, without the account |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| [SynapseCtrl](https://github.com/VoidedKN0X/SynapseCtrl) | Administer your Matrix Synapse server |
| [TalkCtrl](https://github.com/VoidedKN0X/TalkCtrl) | Cardo intercom settings, radio and updates |
| [TapoCtrl](https://github.com/VoidedKN0X/TapoCtrl) | Tapo plugs and bulbs on your home network |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| [WakeCtrl](https://github.com/VoidedKN0X/WakeCtrl) | Keep the screen awake, per app or while charging |
| **WrtCtrl** (this app) | Manage your OpenWrt router |

## License

WrtCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The bundled vendor list is from [oui-data](https://github.com/silverwind/oui-data) (BSD-2-Clause, see `app/src/main/assets/licenses/oui-data-LICENSE.txt`). The bundled fonts are licensed under the SIL Open Font License 1.1: [Space Grotesk](https://github.com/floriankarsten/space-grotesk) (see `FONT-LICENSE-SpaceGrotesk.txt`) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) (see `FONT-LICENSE-JetBrainsMono.txt`).

WrtCtrl is an independent app and isn't made by or affiliated with the OpenWrt project. OpenWrt is a registered trademark of Software Freedom Conservancy.
