# Threat model

## Implemented boundary

The plugin exposes theme reads plus optional clipboard, file, media, system, workspace, and configured-command operations. Every optional feature starts disabled and has its own per-device grant in the Omarchy widget. A paired device does not receive authority to other paired devices or plugin configuration.

The plugin binds HTTPS to the computer's local interfaces only after pairing starts or a saved device needs to reconnect. It uses a self-signed P-256 certificate and TLS 1.2+. The Android app accepts only a QR with a private/loopback numeric IPv4 URL, validates the QR lifetime and schema, pins the certificate's SHA-256 fingerprint, disables redirects, and uses bounded response reads. The pin, rather than a public CA, establishes the computer identity; a previously approved phone can scan a fresh QR to follow a changed private address.

The first pairing has two user approvals: the phone user scans the computer's QR and confirms its displayed identity, then the computer user approves the phone name in the widget. A random bearer token is generated only after the host approval. The computer stores its SHA-256 hash in a mode-0600 file within a mode-0700 state directory. Android stores the token and certificate pin encrypted by an AES-GCM key in Android Keystore. The Android manifest disables backup.

The pairing secret lasts three minutes and is one-use. Pair attempts are rate-limited per source address, pending requests and stored devices are bounded, request JSON and wallpaper sizes are capped, and responses are marked no-store. Device tokens can be revoked from the computer widget or OmaApp when reachable. Offline revocation is not possible over a disconnected link; the widget also exposes host-side revocation.

## Optional operation boundaries

- **Clipboard:** plain UTF-8 text only, capped at 64 KiB. A user taps a foreground OmaApp button to send or receive; there is no passive clipboard monitor. Android's system clipboard may display copied text for user awareness.
- **Files:** each direction has a separate grant. Transfers are capped at 100 MiB. Phone-side access uses Android's system document picker. PC-side transfers are confined to `~/Downloads/OmaApp`, reject unsafe names, do not overwrite an existing file, and use private file/directory permissions.
- **MPRIS:** only the listed player identifier and play/pause/next/previous/stop actions reach `playerctl`; there is no free-form playerctl argument.
- **System metrics:** read-only load, memory, and battery state are returned to the paired phone after a separate grant.
- **Hyprland workspace control:** numeric workspace IDs are range-checked before `hyprctl dispatch workspace`; arbitrary Hyprland commands are not accepted.
- **Configured commands:** the phone sends only a constrained command ID from the host's `~/.config/omaapp/commands.toml`. The host user configures an absolute executable, argument vector, and timeout. Execution uses an argv vector without a shell, requires a separate grant, and can be disabled or revoked per device. A granted command may still cause the effects explicitly configured by the host user.
- **Event stream:** authenticated long-poll responses contain only a revision and an allowlisted event kind. Waits are bounded, and the client disconnects the listener while the app is backgrounded.

## Assets and threats

| Asset or boundary | Threat | Current control / remaining check |
| --- | --- | --- |
| Pairing identity | A nearby host impersonates the computer | QR-scanned SHA-256 certificate pin, local address validation, user confirmation, and no redirects. Verify the TLS pin path on Android hardware. |
| One-time pairing secret | Guessing, replay, or unauthorized registration | 256-bit secret, three-minute lifetime, one use, per-source attempt limit, both-side approval, and expiry tests. |
| Device token | Token theft or reuse after revocation | Random 256-bit token; only a hash is stored on the computer; Android Keystore AES-GCM; per-device authentication; revocation tests. |
| Theme and wallpaper | Disclosure or resource exhaustion on a hostile local network | TLS pin, token authentication, bounded JSON/image size, private app cache, and no-store responses. Review file type support on target Android builds. |
| Clipboard text | Secret disclosure or oversized input | Independent grant, explicit foreground action, plain text only, 64 KiB cap, and no background listener. Users should treat a transfer as sharing the current text with the paired computer/phone. |
| Transferred files | Path traversal, overwrite, or storage exhaustion | Independent direction grants, strict names, 100 MiB limit, symlink-resistant destination, atomic no-overwrite writes, and Android SAF. Test cancellation and partial-transfer cleanup on devices. |
| PC controls | A modified phone sends arbitrary commands or actions | Independent grants; strict MPRIS actions, workspace ID validation, and host-configured command IDs only. Test disabled-grant rejection and revocation for every route. |
| System metrics | Device fingerprinting through load, memory, or battery values | Read-only information is sent only after the dedicated system grant. |
| User command config | A host-defined command has consequential effects | Absolute executable plus argv and timeout; no shell interpolation; the host user controls the config and the phone permission separately. Review the configured command itself before granting access. |
| Omarchy plugin process | Malformed network data becomes code execution | No arbitrary shell command is built from network input; JSON is parsed as data; requests are rate/size-limited. User-configured command IDs are resolved only against host-owned config. The Quickshell plugin itself remains trusted, unsandboxed code. |
| Offline/reconnected phone | Stale or revoked token remains in use | The client retries the saved URL; the server checks the current token hash on every request. A fresh QR updates a trusted device's private address. |

## Remaining verification

Automated Python tests exercise TLS endpoints, first approval, rejection, expiry, rate/size boundaries, reconnection, theme parsing, independent feature grants, file transfer, clipboard, MPRIS, workspaces, configured commands, and revocation. A Linux subprocess integration test starts the helper, changes a watched Omarchy theme fixture, and checks that an authenticated event wakes a waiter. The actual plugin has been installed and restarted in Omarchy 4.0.4 / Quickshell 0.3.1. Current PR #17 CI [run #270](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40212669660) compiled the current app and passed launcher/pairing/theme and native widget picker/interaction/process-restoration/removal smoke tests on OpenGL and Vulkan. These emulator checks do not verify every optional feature flow or physical-device behavior; Pixel/GrapheneOS testing has not been run.

Do not add notifications, terminal access, desktop streaming, arbitrary commands, or other routes without a separate grant, bounded input contract, revocation tests, and an updated threat review.
