# Changelog

What changed in each WrtCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/WrtCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Unreleased

Built and checked by CI only: none of this had been run on a phone or against a router when it was written. The ubus calls and commands were checked against OpenWrt 25.12's packages and LuCI's own code, but please try each new screen once on your router.

**Copy the permission file from the README to the router again** (`/usr/share/rpcd/acl.d/wrtctrl.json`, then `/etc/init.d/rpcd restart`). The new screens need new permissions, also when you sign in as root: rpcd only lets a login run commands that a permission file names.

### New

- **Tailscale** (Network tab, shown when Tailscale is installed on the router): whether it's connected, the router's Tailscale name and addresses, the tailnet, anything Tailscale itself reports as wrong, and every device of your tailnet with online or offline. **Exit node**: pick a device that offers to be an exit node and the router sends its internet traffic through it, or pick "No exit node" to go back. WrtCtrl asks before switching and keeps the local network reachable (`--exit-node-allow-lan-access`), so you can always switch back from home. This isn't rolled back automatically.
- **WireGuard** (Network tab, shown when an interface uses WireGuard): every WireGuard interface with its port, and every peer with its allowed addresses, last handshake, endpoint and traffic. Each peer has an on/off switch, applied with rollback: if your phone was connected through that peer and loses the router, the router puts the old setting back.
- **Open in AdCtrl** in a new DNS section on the Network tab. It only shows when AdGuard Home is found on the router (its service or its program) and AdCtrl is installed on the phone.
- **Firmware check** (System tab): shows the installed OpenWrt version and, when you tap "Check for a newer release", whether a newer release exists (newer point releases of your branch, and the newest release of a newer branch). The router asks OpenWrt's upgrade server for the list; your phone still only talks to the router. WrtCtrl never installs firmware. Snapshots and custom builds are recognised and not compared.
- **Firewall** (Network tab): the default policies, every zone with its policies, networks, masquerading and the zones it forwards to, and every traffic rule with a one-line summary and an on/off switch (applied with rollback). Adding and editing rules stays in LuCI.
- **Local hostnames** (Network tab): the DNS entries the router answers itself (`domain` entries in `/etc/config/dhcp`). Add, edit and remove them; names and IPv4 or IPv6 addresses are checked before saving.
- **DHCP options** (Network › DHCP): each address pool now lists its DHCP options with a plain name for the common ones (gateway, DNS servers, domain, time servers), and you can add or remove one. Useful for handing out a different DNS server, for example AdGuard Home's address with `6,<address>`.
- **Dynamic DNS status** (Network tab, shown when ddns-scripts is installed): per service whether it's running, the hostname, the provider, the address registered now, and the last and next update. Status only.
- **Ping and traceroute** (Network tab): run from the router, over IPv4 or IPv6, with the same commands LuCI's Diagnostics page uses. The output can be selected and copied. What you type is checked so it can only ever be a hostname or an address.
- **Multi-WAN status** (Network tab, shown when mwan3 is installed): every connection mwan3 tracks with online or offline and for how long, its test addresses with latency and loss, and how each policy shares new connections.

- **Export and import settings** (profile picture › Settings › Export and import, and "Import settings from a file" on the first-start screen): saves WrtCtrl's own settings (theme, warning levels, which notifications and how often) and your routers' names, addresses, usernames and read-only switch to a JSON file, and reads them back on another phone. Passwords, sessions, the app lock and its PIN, trusted certificates, the push address and device nicknames and notes are never written. Before anything changes, an import shows which routers it would add and which you already have; routers you already have are left exactly as they are, new ones come without a password (enter it under Routers) and without a trusted certificate, an `http://` router is only added after the usual "unencrypted connection" question, and hiding in screenshots or recent apps can only be switched on by a file, never off. Whether background checks run is not changed by an import.

### Changed

- The "Open in AdCtrl" row on a device's page works as before; its code is now shared with the new DNS section.
- README: the permission file has the new entries, and the Optional packages table says what Tailscale, WireGuard, dynamic DNS, mwan3 and AdGuard Home add.

## v1.4.0 (2026-11-21)

Built and checked by CI only: nothing in this release had been run on a phone when it was published. It is the first release built with the new build tools (Android Gradle Plugin 9, stricter code shrinking), so update over your installed version and check that your data and settings are still there.

### Fixed

- **Fixed before release: the app would have crashed at start with the new build tools.** WrtCtrl uses WorkManager for its background work, and WorkManager stores its jobs in a small Room database that is created by name when the app starts. The shrinker (R8) in Android Gradle Plugin 9 is stricter than the old one and removed the constructor that needs, which makes the app stop right at launch (a known problem with Room 2.6.1 under Android Gradle Plugin 9). A keep rule for that constructor, the same one newer Room versions ship themselves, is now in `app/proguard-rules.pro`. No released version was affected: the new build tools have not been in any release. Built by CI only; not yet run on a phone.

### Changed

- **The system log downloads far less while it's open.** The log screen used to download the last 1,000 lines again every 5 seconds. It now downloads all 1,000 once and after that only the last 100 lines per refresh, and joins the new ones onto what it already shows. OpenWrt's log service numbers every line, and the join is only made when the newly read lines connect to the shown ones without any doubt: same line numbers, and the lines both lists share must be identical (number, time, priority and text). If they don't connect (100 or more new lines within one refresh, a router or log service restart, a cleared log, or a router that sends no line numbers), WrtCtrl reads all 1,000 lines again, as before. What you see is the same list in the same order, still the newest 1,000 lines at most. One small difference: on a router whose log memory holds fewer than 1,000 lines, lines the router has already dropped can stay on screen (up to 1,000 in total) until you leave the log and open it again. When nothing new was logged, the screen is no longer redrawn.
- **WrtCtrl signs in to the router less often.** Until now WrtCtrl signed in with the saved password every time it made a connection to the active router: at every start, every unlock of the app lock, every switch between routers and every change to a router's settings. Each of those left one more session on the router to time out, and sent the password once more (readable on the network when the router is reached over plain HTTP). It now keeps using the saved session when that is safe:
  - **While the app stays in memory**, WrtCtrl remembers what the current session may do (the router fixes that at sign-in), so after an unlock, a switch back to a router or a settings change it carries on with that session without asking the router anything extra. Only a short fingerprint of the session ID is kept for this, not the ID itself, so nothing new is in memory while the app is locked.
  - **After a restart of the app**, the saved session is tried only when it was issued less than its timeout ago (5 minutes by default), which is the time the router is sure to still know it. WrtCtrl then asks the router what that session may do (`session access`). That works for root and for logins whose permissions include `session access`; the restricted login from the README doesn't include it, gets "access denied" and signs in as before (one small extra request).
  - **Every other case signs in exactly as before**, and so does **Retry** on the Overview. A kept session is always checked right away by the request that reads the router's model and version: if the router no longer knows the session, that request signs in again automatically, and what the login may do is taken from that sign-in.
  - What you might notice: if the router's password was changed, or the login's permissions were edited on the router without restarting rpcd, WrtCtrl can keep working with the old session until that session ends (or until you tap Retry on the Overview's error card or restart the app after a few minutes), instead of noticing at the next start.
- **Newer build tools and libraries; nothing changes in how WrtCtrl looks or works.** WrtCtrl is now built with the current stable toolchain: Android Gradle Plugin 8.11.1 → 9.5.1, Gradle 8.14.3 → 9.9.2, Kotlin 2.2.0 → 2.4.21 (with its Compose and serialization compiler plugins) and Compose BOM 2025.07.00 → 2026.09.00 (Compose 1.12.1, Material 3 1.4.0). The libraries move to the versions the newest Ctrl apps use: activity-compose 1.10.1 → 1.13.0, core-ktx 1.16.0 → 1.18.0, lifecycle 2.9.1 → 2.10.0, navigation-compose 2.9.1 → 2.9.8, datastore-preferences 1.1.7 → 1.3.0, kotlinx-serialization-json 1.9.0 → 1.13.0 and kotlinx-coroutines-android 1.10.2 → 1.11.0. Everything else stays at its version. No library was added, and CI's Google-free dependency audit still runs on every build.
- **Compiled against the Android 17 SDK.** Compose 1.12 needs `compileSdk` 37, so that goes from 36 to 37. The Android versions WrtCtrl runs on and targets are unchanged (`minSdk` 35, `targetSdk` 36), so Android treats the app exactly as before.
- **The app is shrunk by a newer R8.** Android Gradle Plugin 9 compiles Kotlin itself (the separate Kotlin Android plugin is gone from the build files) and brings the R8 version that understands Kotlin 2.4. WrtCtrl's own keep rules are unchanged.
- The checksums Gradle verifies every download against (`gradle/verification-metadata.xml`) were regenerated on CI for the new versions. Built and checked by CI only; not yet run on a phone.

### Security

- **The signing key is no longer available to the build.** Until now the release key and its passwords were present in the same GitHub Actions job that runs Gradle, so any build plugin or library could in principle have read them. The build job now has no secrets at all and produces an unsigned APK. A separate job, which checks out no code and runs no Gradle, signs it with `apksigner`, and then refuses to hand it on unless it is signed by exactly the certificate published in SECURITY.md and README.md and its contents are identical to what the build job made. The key and the signature scheme (v2) are the same as before, so updates install as usual. Nothing in the app changed.
- **The build checks that the published fingerprint and the enforced one are the same.** If `build.yml`, SECURITY.md and README.md ever disagree about the signing certificate, the build fails.

### Project

- **Builds no longer use a saved Gradle cache.** Ctrl apps had builds fail with "Dependency verification failed ... from repository Gradle Central Plugin Repository", and keep failing until the caches were deleted by hand. The cause was reproduced on DateCtrl: with a restored cache Gradle still fetches the plugin files from Maven Central on every build; when Maven Central can't be reached for a moment, Gradle takes the Kotlin plugin "marker" file from the Gradle plugin portal instead, the portal serves a different file under the same name, verification rightly refuses it, and the failed run then saved that state as the cache for every later run. Now every build and the weekly code scan start from a clean Gradle home (builds take a minute or two longer), and the plugin portal is no longer a plugin source at all (`settings.gradle.kts`): everything the build needs is on Maven Central and Google's Maven. Dependency verification itself is unchanged and as strict as before.

## v1.3.0 (2026-11-20)

**This release encrypts some of what the app stores on the phone. An older version cannot read it afterwards, so export or back up anything you need before you update (details below).** Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### Security

- **Device nicknames and notes are now encrypted on the phone.** The nicknames and notes you type for devices used to sit as readable text in the app's private settings (`client_notes`). They are now kept in one encrypted file per router (`client_notes/<router id>.json` in the app's private storage, AES-256-GCM).
- **The background checks' saved state is now encrypted too.** To announce only new devices, the background checks remember every device (MAC address) they have seen, per router, next to the last check's result (`notify/<router id>.json`). That file is now encrypted the same way.
- **How the key works.** WrtCtrl makes one random 256-bit data key and stores it in `data.key` in its private storage, itself encrypted by a key in the Android Keystore that can't be taken off the phone (in the StrongBox security chip when the phone has one). That Keystore key is separate from the ones that protect your router logins and the app lock, which are untouched. It needs no fingerprint or PIN, so background checks keep working while the phone and WrtCtrl are locked. What this protects against: someone reading WrtCtrl's files copied off the phone. It does not depend on the app lock, and it does not protect against something that controls the running app.
- **Your existing data is moved, not rewritten in place.** The first start after updating seals what's there, in the background. Nicknames and notes: an encrypted copy is written to a temporary file, read back from disk, decrypted and compared with the original, and only then put in place; the old readable copy is removed last. If the app is stopped half-way, either the old copy is still there or both are (and say the same), and the next start finishes the job. The background checks' state is sealed with the same write, check and swap. Both the old and the new form are read, now and in later versions.
- **If the key can't be had** (the phone's Keystore doesn't work), nothing changes: WrtCtrl keeps reading and saving nicknames, notes and check state the way it did before, unencrypted, and tries again at the next start.
- **If encrypted nicknames and notes can't be opened** (the Keystore key is gone or the file is damaged), WrtCtrl never deletes or overwrites them. The Clients tab and a device's page show **Can't open your nicknames and notes** with **Try again** and **Start empty**; until then devices show without nicknames and saving a note says it wasn't saved. **Start empty** asks first, then renames the unreadable file to `<router id>.json.unreadable` (kept on the phone, not deleted) so you can save new ones. An unusable `data.key` is never replaced, so after that new nicknames and notes are saved unencrypted, as before this change. Removing the router from WrtCtrl still removes everything kept about it, including that set-aside file.
- **If the encrypted check state can't be opened**, it is dropped and the next background check starts over, as if it were the first one: it records the devices it sees without announcing them as new, and it may notify once more about something you were already told about (package updates, or a router that still can't be reached).
- **Going back to an older version of WrtCtrl:** an older version can't read the encrypted files. It would show your devices without nicknames and notes (they come back when you update again; anything saved in the older version in the meantime is merged in, and wins for a device that has both) and start the background checks over. WrtCtrl has no export for nicknames and notes, so don't install an older version over this one if you rely on them.

### Not encrypted, and why

- **The list of routers** (name, address, user name, last seen model and OpenWrt version) and **the app's settings** stay in the app's private settings as before. They are read the moment the app starts, to decide which screen to show; encrypting them would mean asking the Keystore on the main thread at every start (which was just removed to make the app start faster) or showing a wrong "no router yet" screen while the key loads. Router passwords and sessions were already encrypted and are unchanged, as are the app lock's own files.
- **The ntfy push address and the push registration** stay as they are: the push receiver reads them the moment a push arrives.
- **Configuration backups** are written straight to the file you pick in Android's file picker, in OpenWrt's own format, exactly as before. WrtCtrl keeps no copy.
- WrtCtrl keeps no other lists on the phone: the Overview, the client list, the logs and the graphs are read from the router each time and only held in memory.

### Changed

- Saving a nickname or note now happens on a background thread (it used to be written from the main thread), and reading them while the client list loads does too.

## v1.2.0 (2026-11-20)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### Removed

- **The home screen widget is gone.** The **Router** widget (WAN status, throughput, online devices) has been removed from WrtCtrl, together with its 30-minute background refresh. If you had it on your home screen, Android shows an empty or "can't load" box in its place after updating: remove that box by hand. WrtCtrl stops the widget's leftover refresh job and deletes the small file with the widget's last numbers the next time you open the app.
- **The Guest Wi-Fi Quick Settings tile is gone.** Guest Wi-Fi is switched from the Network tab, or opened quickly with the **Guest Wi-Fi** app shortcut (long-press the icon). App shortcuts (Clients, Guest Wi-Fi, Reboot) are unchanged.
- **Jetpack Glance is no longer in the app.** It was only used to draw the widget, so the library is removed and the app gets a little smaller.
- "Background checks while locked" now only covers notifications; its description no longer mentions the widget and the tile. When you turn the app lock on, the option is suggested only when notifications are in use (a placed widget used to count too).

### Changed

- **Faster start.** Decrypting the saved router login (up to four Android Keystore operations, slow on phones with a security chip) and setting up the connection for a pinned certificate used to happen on the main thread while the app was starting, for every start of the app and of its background checks. Both now happen on a background thread, right before the first request to the router.
- **One sign-in at start instead of two.** WrtCtrl made the connection to the active router twice while starting, and each one signed in to the router again, leaving an extra session on the router to time out. It now makes it once. Locking and unlocking the app still makes a fresh connection, as before.
- **The Overview asks the router for less.** It refreshes every 2 seconds for the live graph, CPU, memory and interfaces, and used to ask for storage use, the Wi-Fi radios and the number of unsaved changes at the same pace. Those three are now read every 15 seconds (three requests fewer on most refreshes), and straight away when you pull to refresh, tap Retry, come back to the Overview, or apply or undo a change.
- **Permission checks are cheaper.** With a restricted login, every request first checks the login's permission patterns; each pattern was compiled again for every check. They are now compiled once.
- **The system log scrolls more smoothly.** The log lines were filtered again and a new date formatter was made with every redraw of the screen; now only when the log refreshes or the search or filter changes.

### Fixed

- The live throughput graph's saved samples could be read while they were being updated (when opening the Overview during a refresh). They're now read from a finished copy.

## v1.1.1 (2026-11-20)

### Changed

- **The app icon matches the other Ctrl apps.** WrtCtrl's icon was drawn in a darker set of greys than the rest of the series: a near-black background, a darker keycap and a front bar you could hardly see. It now uses the same background, keycap and bar colours as the other Ctrl apps, so the icons look like one family side by side. The purple symbol and the themed (monochrome) icon are unchanged.

## Project updates (2026-11-19)

### Project

- **The weekly code scan no longer fails at random.** CodeQL has to watch the app being compiled, but its build could take the compiled code from Gradle's build cache instead, and then the scan stopped with "CodeQL detected code written in Java/Kotlin but this run didn't build any of it" (and GitHub mailed a failed run). The scan's build now runs with `--no-build-cache`, so the code is always compiled. No app changes, so no new version.

## v1.1.0 (2026-11-13)

### New

- **Bandwidth graph per device:** each device's page now shows its data use. With nlbwmon on the router, it's a bar per accounting period (download and upload, the last 12 periods): per month by default, per day if you set nlbwmon's database interval to one day. Without nlbwmon, WrtCtrl shows a live rate measured every 3 seconds from the router's connection tracking table. When neither is available, the page says what to install or which permission to add.
- **Block on a schedule:** blocking a device from the internet now lets you choose **Reject** (the device is told at once) or **Drop** (its traffic is silently thrown away), and optionally only certain times on certain days, like 22:00 to 07:00 on school nights. The times use the router's clock. A block that's there can be changed later (**Change the block**).
- **Blocked devices:** Clients shows how many devices are blocked; tap it for the list, with each block's type and times, whether it's blocking right now, and **Allow the internet again**.
- **Undo:** after blocking or unblocking a device, **Undo** puts it back the way it was.
- **This phone is protected:** WrtCtrl recognises the phone it's running on (by its address on your network) and asks a second time before blocking it.

### Changed

- **Only WrtCtrl's own firewall rules are ever changed or removed:** the block, change and unblock actions refuse any rule whose name doesn't start with "WrtCtrl block", before anything is sent to the router.
- **New permissions for the bandwidth graphs:** the permission file in the README adds nlbwmon's history (`nlbw -c list` and `nlbw -c json -g mac -t …`) and LuCI's connection list (`luci getConntrackList`). Copy it to the router again and run `/etc/init.d/rpcd restart` to use them; without them the page explains what's missing.
## v1.0.10 (2026-11-13)

A security and privacy check-up. Nothing works differently, and you don't need to sign in again.

### Security

- **Router logins in the phone's security chip:** on phones with StrongBox (a separate security chip, such as Titan M on Pixels), the key that encrypts your saved router passwords is now made inside that chip, and falls back to the normal Android Keystore on phones without one. This applies when the key is first made (a new install, or after clearing WrtCtrl's data); logins you already saved stay encrypted with the key they have.
- **The widget only takes updates from Android:** other apps can no longer ask the router widget to refresh; only Android's own widget service can. It looks and works the same.
- **Checked build tools:** the build now also checks the SHA-256 of the Gradle build tool it downloads, as it already did for every library.

### Performance

- Reading a saved router password no longer opens the Android Keystore again each time, so background checks, the widget and the tile connect a little sooner.

## v1.0.9 (2026-11-12)

### Changed

- **Slightly smaller download.** The bundled JetBrains Mono font (numbers, readouts and labels) no longer carries the Cyrillic, Vietnamese and maths-alphabet letters WrtCtrl never shows itself, which takes about 0.1 MB off the download and 0.2 MB off the installed app. Everything looks the same; if a name or a log line shown in that font uses one of those scripts, those letters are now drawn with your phone's own font.

## v1.0.8 (2026-11-05)

### Changed

- **Needs Android 15 or newer.** Android 13 no longer gets security updates and Android 14's updates end in early 2027, so WrtCtrl now supports Android 15, 16 and 17 only. On older phones the version you have keeps working, but it won't get updates.

## v1.0.7 (2026-11-05)

### Fixed

- **Password managers can fill your router and Wi-Fi passwords again.** Since 1.0.6 the router sign-in and the Wi-Fi password editor had been hidden from every accessibility service that isn't a screen reader, which also kept password managers that fill through accessibility from filling them. Only the lock and PIN screens are hidden from those apps now. Other apps' floating windows stay hidden over all of these screens, as in 1.0.6.

## v1.0.6 (2026-11-05)

A security update. Nothing changes in how WrtCtrl works.

### Security

- **No other app's floating window over the PIN and confirmation screens.** While the lock screen, PIN entry or change, "Forgot PIN?", **Confirm changes**, the router sign-in, the Wi-Fi password editor or a "can't be undone" question is showing, Android hides other apps' floating windows (chat heads, screen filters, overlays), so none can cover the screen or trick you into tapping the wrong button. They come back as soon as that screen closes and work as usual everywhere else. This uses Android's "hide other apps' floating windows" permission, which is granted at install without a prompt.
- **PIN and password screens are hidden from apps that misuse accessibility** (Android 14 and newer). Real accessibility tools such as TalkBack still read them; other apps with accessibility access can't see the PIN pad, the router password or the Wi-Fi password fields.
- **Stricter intent matching** (Android 16 and newer). Other apps can only start WrtCtrl through the actions it declares. The launcher, the three long-press shortcuts (Clients, Guest Wi-Fi, Reboot), the widget, the Quick Settings tile and push from ntfy work as before.

### Project

- **Verified dependencies:** every library and plugin the build downloads is now checked against SHA-256 checksums in the repo, and the build stops if anything doesn't match.
- **Provable releases:** from this release on, the release notes list the APK's SHA-256, and each APK has a signed build provenance attestation from GitHub. The README's "Is it safe to install?" section shows how to check both.

## Project updates (2026-11-05)

Changes to how the app is built and released. Nothing changes in the app.

- **Every release is now provable.** From the next release on, each APK gets a signed build provenance attestation from GitHub, showing it was built by this repo's workflow from the tagged commit, and the release notes list the APK's SHA-256 checksum.
- **The build workflows are audited.** A new check runs [zizmor](https://docs.zizmor.sh) on every workflow change, and every workflow now starts with no permissions and gives each job only what it needs.
- **Code scanning.** CodeQL now analyses the app's code every week and on every app change; results go to the repo's Security tab.
- **Groundwork for verified dependencies:** a manually started workflow writes the checksums of every library and plugin the build downloads, so the build can refuse anything that doesn't match.

## v1.0.5 (2026-11-05)

A small security update. Nothing changes in how WrtCtrl works.

### Security

- **Other apps can no longer poke WrtCtrl's start-up profile.** An AndroidX library added a receiver that lets other apps ask WrtCtrl to write or skip its start-up performance profile. WrtCtrl doesn't need it, so it's removed.

## v1.0.4 (2026-11-05)

A privacy and security check-up: two permissions fewer, the app lock's timeout now also covers the widget, the tile and the background checks, and a README that lists every permission and everything WrtCtrl connects to.

### Security

- **The "Lock again" time now also counts while WrtCtrl isn't on screen.** With **Lock again** set to 1, 5 or 15 minutes or 1 hour, WrtCtrl only noticed the time had run out once you opened it again. Until then the key that unlocks your router logins stayed in memory, the widget kept showing your router and the background checks kept signing in, even with **Background checks while locked** off. The widget, the Quick Settings tile and the background checks now check the time first: once it has run out, WrtCtrl locks straight away, the logins are sealed with your PIN again, and the widget and the tile only say that WrtCtrl is locked.
- **Your ntfy push address stays out of screenshots.** While **Settings › Notifications** shows the push address (which works like a password), the page is kept out of screenshots, screen recordings and the recent apps preview, whatever **Hide in screenshots** is set to.

### Privacy

- **Two permissions fewer.** WrtCtrl no longer has the old "use fingerprint hardware" permission, which only Android 8 and older use (fingerprint unlock keeps working through the biometric permission), or the "run foreground service" permission, which Android's background work library added for long jobs that show a notification. WrtCtrl's background checks and widget refreshes are short jobs that never need it, so it's gone together with the library's unused foreground service. Notifications, the widget, the tile and push through ntfy work exactly as before.
- **Fewer log messages:** release builds now also strip debug and info log messages from the libraries WrtCtrl uses (WrtCtrl itself writes nothing to the system log).

### Project

- README: the permissions table now lists every permission the app has, with what each one is for (including the ones Android's background work library adds), and the Privacy section lists everything WrtCtrl connects to (only your routers) and every link it can open. A new **App lock** section explains the PIN, fingerprint unlock, **Lock again**, **Confirm changes**, **Background checks while locked** and which screens are always kept out of screenshots.
- README: the Ctrl series list now shows all 28 apps, with CallCtrl, ClockCtrl, ContactCtrl, DateCtrl, LijnCtrl, MailCtrl, RecorderCtrl and TextCtrl added.
- The build now checks that the Gradle wrapper in the repo is an official Gradle release before building, and `.gitignore` also keeps `google-services.json` out of the repo.

## Project updates (2026-11-03)

### Project

- **README:** the Ctrl series list now links to ChatCtrl, the Matrix chat app in the series. No app changes, so no new version.
- **README:** the Ctrl series list now includes SumCtrl, the offline calculator, unit converter and work-tools app. No app changes, so no new version.

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
