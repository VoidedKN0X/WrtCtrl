# WrtCtrl

**Manage your OpenWrt router from your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/WrtCtrl?label=download)](https://github.com/VoidedKN0X/WrtCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

WrtCtrl is an Android app for [OpenWrt](https://openwrt.org): a fast, touch-first alternative to LuCI for what you do on a phone. See who's connected, check bandwidth, switch Wi-Fi and the guest network on or off, manage DHCP leases and port forwards, and reboot. It talks to the same ubus API LuCI uses.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

> **Early development.** WrtCtrl 0.2.0 signs in to your router and shows what your login may do; the tabs fill up in the next versions. See [CHANGELOG.md](CHANGELOG.md) for what's in each one.

## Features

- **Overview**: model, OpenWrt version, kernel, uptime, load, CPU, memory, temperature and storage; WAN status, public IP (read from the router itself), gateway, DNS and IPv6; a live WAN throughput graph; Wi-Fi radios; and an attention list for WAN down, a radio off, low memory, storage almost full, package updates and unapplied changes
- **Clients**: every device on your network, from DHCP leases, host hints, the neighbour table and Wi-Fi, with vendor (from a list built into the app, no online lookup), connection type, signal and lease expiry. Make a lease static, disconnect a device from Wi-Fi, block it from the internet, or wake it (Wake-on-LAN)
- **Network**: Wi-Fi networks and a **guest network** switch, DHCP leases and static leases, **port forwards**, and interfaces with their traffic counters
- **System**: services, package updates (with `apk` or `opkg`, whichever your router has), the system log, reboot and config backups
- **Changes that could cut you off are undone automatically**: Wi-Fi, interface and firewall changes are applied with OpenWrt's rollback. If WrtCtrl can't reach the router afterwards, the router puts the old settings back by itself
- **Notifications** through ntfy: router unreachable, WAN down or back up, a new unknown device, and package updates
- **Quick Settings tile** for the guest network, a **home screen widget** and **app shortcuts**
- **Several routers**, such as a main router and a dumb access point
- **App lock** with its own PIN and optional fingerprint unlock, a **read-only mode** per router, certificate pinning, and optional **Hide in screenshots** and **Hide in recent apps**

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- OpenWrt with LuCI's ubus API (`uhttpd`, `rpcd` and `luci-base`, installed with LuCI)
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google libraries, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/WrtCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds WrtCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/WrtCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/WrtCtrl` in Obtainium by hand.

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
        "system": [ "board", "info" ],
        "network.interface": [ "dump" ],
        "network.device": [ "status" ],
        "luci-rpc": [ "getBoardJSON", "getHostHints", "getDHCPLeases", "getNetworkDevices" ],
        "luci": [ "getVersion", "getMountPoints", "getInitList" ],
        "rc": [ "list" ],
        "log": [ "read" ],
        "uci": [ "get", "changes" ],
        "file": [ "read", "exec" ]
      },
      "uci": [ "system", "network", "dhcp", "firewall" ],
      "file": {
        "/sys/class/thermal/thermal_zone*/temp": [ "read" ],
        "/sys/class/thermal/thermal_zone*/type": [ "read" ],
        "/usr/libexec/package-manager-call list-installed": [ "exec" ],
        "/usr/libexec/package-manager-call list-available": [ "exec" ]
      }
    }
  },
  "wrtctrl-operator": {
    "description": "WrtCtrl: DHCP, port forwards, interfaces, services, packages, backup and reboot",
    "write": {
      "ubus": {
        "system": [ "reboot" ],
        "network.interface.*": [ "up", "down" ],
        "rc": [ "init" ],
        "uci": [ "set", "add", "delete", "rename", "order", "apply", "confirm", "rollback", "revert" ],
        "file": [ "exec" ]
      },
      "uci": [ "dhcp", "firewall" ],
      "file": {
        "/usr/libexec/package-manager-call update": [ "exec" ],
        "/usr/libexec/package-manager-call upgrade *": [ "exec" ]
      },
      "cgi-io": [ "backup" ]
    }
  }
}
```

`package-manager-call` is LuCI's wrapper around `apk` (or `opkg` on older OpenWrt), so the same permissions work with either. Wi-Fi permissions are added once WrtCtrl manages Wi-Fi.

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
| `luci-app-statistics` (`collectd`) | History graphs beyond the live ones |

## Notifications from the router (ntfy)

WrtCtrl checks your routers in the background, and can also get a push through [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) the moment something happens on the router. These two small scripts post to an ntfy topic of yours; replace the address with your ntfy server and topic.

**WAN down and back up**, as `/etc/hotplug.d/iface/99-wrtctrl`:

```sh
#!/bin/sh
NTFY='https://ntfy.example.com/your-topic'
[ "$INTERFACE" = wan ] || exit 0
case "$ACTION" in
  ifup)   msg="WAN is up" ;;
  ifdown) msg="WAN is down" ;;
  *) exit 0 ;;
esac
uclient-fetch -q -O /dev/null --post-data="$msg" "$NTFY"
```

**A new DHCP lease** (a device joined), as `/etc/hotplug.d/dhcp/99-wrtctrl`:

```sh
#!/bin/sh
NTFY='https://ntfy.example.com/your-topic'
[ "$ACTION" = add ] || exit 0
uclient-fetch -q -O /dev/null --post-data="New device: ${HOSTNAME:-unknown} ($IPADDR, $MACADDR)" "$NTFY"
```

`uclient-fetch` is built into OpenWrt; with curl installed, `curl -fsS -d "$msg" "$NTFY" >/dev/null` does the same. A WAN-down message only goes out once the router can reach ntfy again, if your ntfy server is outside your home.

## Privacy

**Telemetry-free.** WrtCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to the routers you add, and never reads the name of the Wi-Fi network you're on. Device vendors are looked up in a list built into the app, and your public IP is read from the router itself, so nothing about your network is sent anywhere else. Router passwords are encrypted with the Android Keystore and never written to a log.

- **Links:** the Support, Report a bug, Source code and Licence rows in About open in your browser; the app itself doesn't contact GitHub.

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

## Support the project

WrtCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/WrtCtrl/issues) helps too.

In the app, the **About** section at the bottom of the **You** page (tap your profile picture) has **Support WrtCtrl** and **Report a bug** rows that open these pages.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same "Ctrl / Signal" design, dark or light with your phone.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| **WrtCtrl** (this app) | Manage your OpenWrt router |

## License

WrtCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The bundled fonts are licensed under the SIL Open Font License 1.1: [Space Grotesk](https://github.com/floriankarsten/space-grotesk) (see `FONT-LICENSE-SpaceGrotesk.txt`) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) (see `FONT-LICENSE-JetBrainsMono.txt`).

WrtCtrl is not affiliated with or endorsed by the OpenWrt project. OpenWrt is a registered trademark of Software Freedom Conservancy.
