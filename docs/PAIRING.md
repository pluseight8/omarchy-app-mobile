# OmaApp ↔ Omarchy pairing and theme sync

The direct connection uses one Quickshell bar widget and an Android QR scanner. The Python helper and QR encoder ship inside the plugin package; no second installer or system service is added.

## Pairing flow

1. Install and enable the OmaApp Omarchy plugin. The helper process starts with the widget but opens no network port until the user requests a pairing code. If trusted devices already exist, it starts the listener so they can reconnect.
2. Select **Pair Android device** in the widget. The plugin creates a short-lived (three-minute), one-use QR code and binds an HTTPS listener to the computer's network interfaces.
3. In OmaApp, scan the QR and review the computer name and SHA-256 certificate fingerprint before tapping **Trust computer**.
4. On first pairing, the phone sends a one-time request over HTTPS. The computer displays the phone name and waits for an explicit approval in the widget.
5. Only after approval does the service issue a random per-device bearer token. OmaApp encrypts the token and pinned certificate fingerprint with an Android Keystore AES-GCM key, then acknowledges receipt. An approved token that the phone never claims expires and is removed from the trusted-device list.

For a computer that is already paired, scan a fresh QR and tap **Reconnect**. The saved per-device token plus the new one-use QR secret allow OmaApp to update the local address without repeating the first-pairing approval. The client pins the exact certificate fingerprint from the QR, so the computer identity remains the same even when its router changes its private IPv4 address. A different certificate fingerprint is rejected until the old pairing is forgotten and a new pairing is approved.

The listener requires TLS 1.2 or later. The Android client pins the exact self-signed certificate scanned from the QR code, checks the certificate validity period, and rejects redirects. Pairing secrets, request IDs, device names, JSON payloads, and wallpaper downloads have length or rate limits. The computer stores only the SHA-256 hash of each device token in a mode-0600 file inside a mode-0700 state directory. A device token can be revoked from either OmaApp (while the computer is reachable) or the widget.

The QR is the local-network discovery step. Both devices need to be able to reach each other on the same network, and the network must permit inbound TCP on port `44892`. The phone retries its saved URL automatically. If the computer's IP address changes, show a new QR in the widget and scan it from the already paired phone. A new computer identity requires forgetting the old one; if the computer is offline, revoke its saved token in the widget before pairing that phone with another computer.

## Theme snapshot

While OmaApp is in the foreground, it waits for authenticated theme and workspace events. The plugin watches active Omarchy theme files with inotify and listens to Hyprland socket2 events for configuration reloads and workspace changes. A 30-second foreground fallback refresh handles missed events and feature metrics. The plugin reads Omarchy's active `colors.toml`, `shell.toml`, and `current/background` symlink, and asks Hyprland for active/inactive border colors and angle, border size, rounding, rounding power, inner/outer gaps, animation entries, and Bezier curves.

OmaApp applies the supported theme colors and `shell.toml` tokens, border gradient, wallpaper, spacing, border width, radius, rounding power, and live animation list. The last successful JSON snapshot and wallpaper stay in the app's private storage when the computer is offline. Reconnection retries the saved endpoint and refreshes the snapshot.

## Optional computer features

Each paired phone starts with every feature disabled. The Omarchy widget grants or revokes clipboard, file send, file receive, MPRIS, system metrics, workspace control, and configured command access independently. Requests validate their paths, identifiers, payload sizes, workspace numbers, player actions, and command IDs. The PC helper never accepts an arbitrary command line: optional commands must be listed by the user in `~/.config/omaapp/commands.toml` as an absolute executable plus an argument vector and run under a timeout without a shell.

File transfers are capped at 100 MiB and PC-side files stay inside `~/Downloads/OmaApp`. Android uses the system document picker for both upload and download. Clipboard accepts plain text only, up to 64 KiB, and transfers only on an explicit tap while OmaApp is foregrounded. MPRIS uses `playerctl`; workspace switching uses a validated numeric ID with `hyprctl`.

The plugin service has Linux integration tests for TLS startup, persistence, theme inotify events, permission events, and revocation after restart. Protocol tests exercise per-feature permissions, files, clipboard, MPRIS, workspaces, commands, shell theme fields, and Hyprland animations. The real Omarchy plugin was installed and restarted in the development Omarchy 4.0.4 / Quickshell 0.3.1 session. Android physical-device and Pixel 10 GrapheneOS testing remains to be performed with the guide in [GRAPHENEOS.md](GRAPHENEOS.md).
