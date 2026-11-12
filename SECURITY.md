# Security policy

Thank you for helping keep WrtCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/WrtCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of WrtCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

- Your router password, ubus session, Wi-Fi passwords or other data leaking to anyone other than your own OpenWrt router
- WrtCtrl accepting a router certificate you didn't confirm, or sending anything over plain HTTP that you didn't allow
- Ways around the app lock (including from the widget, the Quick Settings tile, app shortcuts or notifications), or protected screens showing up in screenshots when they shouldn't
- A change to your router (Wi-Fi, firewall, port forwards, interfaces, reboot, packages) happening without your confirmation, or a risky change skipping OpenWrt's rollback
- Another app on the phone being able to make WrtCtrl do something you didn't ask for
- Anything that lets someone run code or read files they shouldn't

Problems in OpenWrt itself (rpcd, uhttpd, LuCI) should go to that project.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
0C:3B:15:A4:11:D7:41:83:BA:F8:06:2B:31:24:29:66:4E:09:22:E4:BC:06:BE:6B:8B:02:65:4A:4E:AD:80:CB
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.

Each release's notes list the APK's SHA-256, and each release APK has a signed build provenance attestation: `gh attestation verify <apk> -R VoidedKN0X/WrtCtrl` proves it was built by this repo's workflow from the tagged commit. Every library and plugin the build downloads is checked against the checksums in `gradle/verification-metadata.xml`.
