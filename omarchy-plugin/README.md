# OmaApp Omarchy plugin

This is the Omarchy-side component of OmaApp. It provides one Quickshell bar widget and a small Python HTTPS helper shipped inside the same plugin folder. It does not install another product or system service.

The helper starts with the widget. It opens a listener only when the user requests pairing or a trusted phone needs reconnection, and retries after a network outage. Pairing uses a three-minute, one-use QR secret, a pinned self-signed TLS certificate, a per-device token, and explicit approval in the widget. The widget can revoke phones and independently grant clipboard, file, MPRIS, system, workspace, and configured-command permissions.

## Install

Requirements: Omarchy with Quickshell, Python 3.11+, and OpenSSL on `PATH`.

From a checkout of the OmaApp repository, run:

```sh
./omarchy-plugin/install.sh
```

The installer validates the manifest, stages the plugin in the Omarchy user plugin directory, and enables it. To remove it, run `./omarchy-plugin/uninstall.sh`; the script disables the plugin and moves its source to a timestamped backup under `~/.local/state/omarchy/plugin-backups`, outside Quickshell's plugin scan.

## Pair a phone

1. Open the OmaApp widget and choose **Pair Android device**.
2. In OmaApp, scan the QR code and review the computer identity.
3. Tap **Trust computer** in OmaApp.
4. Approve the displayed phone name in the computer's widget.

The phone and computer must be reachable on the same local network, and that network must allow inbound TCP on port `44892`. The service exposes authenticated theme, wallpaper, clipboard, file, MPRIS, system, workspace, and optional configured-command endpoints. Optional features are off by default and each has a separate grant. Arbitrary shell commands are never accepted.

## Theme data

The helper reads active Omarchy `colors.toml`, `shell.toml`, and wallpaper, plus Hyprland border colors, gradient angle, border size, rounding, rounding power, gaps, animations, and Bezier curves. Linux inotify and Hyprland socket2 events notify the phone when configuration or workspace state changes; OmaApp uses a slow foreground fallback for other state. The phone retains its last good snapshot and wallpaper while offline.

The host stores the TLS private key and hashes of trusted device tokens under `~/.local/state/omarchy/omaapp` with private directory/file permissions. Revoke devices in the widget before permanently removing that state directory.

For protocol details and the Pixel 10 test procedure, see [the pairing guide](../docs/PAIRING.md), [GrapheneOS guide](../docs/GRAPHENEOS.md), and [threat model](../docs/threat-model.md). Automated Linux helper tests are included; physical Android verification remains open.
