# OmaApp Omarchy plugin

This is the Omarchy-side component of OmaApp. It provides one Quickshell bar widget and a small Python HTTPS helper shipped inside the same plugin folder. It does not install another product or system service.

The helper starts with the widget. It does not open a network listener until the user requests pairing; after pairing, it keeps HTTPS available for the trusted phone and retries starting the listener after a network outage. Pairing uses a three-minute, one-use QR secret, a pinned self-signed TLS certificate, a per-device token, and an explicit approval in the widget. The widget can revoke trusted phones.

## Install

Requirements: Omarchy with Quickshell, Python 3.11+, and OpenSSL on `PATH`.

From a checkout of the OmaApp repository, run:

```sh
./omarchy-plugin/install.sh
```

The installer validates the manifest, stages the plugin in the Omarchy user plugin directory, and enables it. To remove it, run `./omarchy-plugin/uninstall.sh`; the script disables the plugin and moves its source to a timestamped backup.

## Pair a phone

1. Open the OmaApp widget and choose **Pair Android device**.
2. In OmaApp, scan the QR code and review the computer identity.
3. Tap **Trust computer** in OmaApp.
4. Approve the displayed phone name in the computer's widget.

The phone and computer must be reachable on the same local network, and that network must allow inbound TCP on port `44892`. The service exposes authenticated theme and wallpaper reads plus pairing and revocation endpoints. It does not accept remote commands.

## Theme data

The helper reads the active `~/.local/state/omarchy/current/theme/colors.toml`, `shell.toml`, and `background` symlink. It queries Hyprland through `hyprctl -j getoption` for active/inactive border colors, gradient angle, border size, rounding, rounding power, and inner/outer gaps. OmaApp polls every four seconds and applies supported colors, gradient, spacing, geometry, and wallpaper. The phone retains its last good snapshot and wallpaper while offline.

The host stores the TLS private key and hashes of trusted device tokens under `~/.local/state/omarchy/omaapp` with private directory/file permissions. Revoke devices in the widget before permanently removing that state directory.

For protocol details and current device-testing status, see [the pairing guide](../docs/PAIRING.md) and [the threat model](../docs/threat-model.md). The Android client and device experience have not yet passed an emulator or phone smoke test.
