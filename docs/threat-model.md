# Threat model

## Implemented boundary

The first network feature sends only current theme metadata and the selected Omarchy wallpaper from the computer to a paired OmaApp device. The only privileged action available through the protocol is revoking that same device's token. There are no remote command, terminal, file-write, clipboard, notification, media, workspace, or desktop-streaming routes.

The plugin binds HTTPS to the computer's local interfaces only after pairing starts or a saved device needs to reconnect. It uses a self-signed P-256 certificate and TLS 1.2+. The Android app accepts only a QR with a private/loopback numeric IPv4 URL, validates the QR lifetime and schema, pins the certificate's SHA-256 fingerprint, disables redirects, and uses bounded response reads. The pin, rather than a public CA, establishes the computer identity; a previously approved phone can scan a fresh QR to follow a changed private address.

The first pairing has two user approvals: the phone user scans the computer's QR and confirms its displayed identity, then the computer user approves the phone name in the widget. A random bearer token is generated only after the host approval. The computer stores its SHA-256 hash in a mode-0600 file within a mode-0700 state directory. Android stores the token and certificate pin encrypted by an AES-GCM key in Android Keystore. The Android manifest disables backup.

The pairing secret lasts three minutes and is one-use. Pair attempts are rate-limited per source address, pending requests and stored devices are bounded, request JSON and wallpaper sizes are capped, and responses are marked no-store. Device tokens can be revoked from the computer widget or OmaApp when reachable. Offline revocation is not possible over a disconnected link; the widget also exposes host-side revocation.

## Assets and threats

| Asset or boundary | Threat | Current control / remaining check |
| --- | --- | --- |
| Pairing identity | A nearby host impersonates the computer | QR-scanned SHA-256 certificate pin, local address validation, user confirmation, and no redirects. Verify the TLS pin path on Android hardware. |
| One-time pairing secret | Guessing, replay, or unauthorized registration | 256-bit secret, three-minute lifetime, one use, per-source attempt limit, both-side approval, and expiry tests. |
| Device token | Token theft or reuse after revocation | Random 256-bit token; only a hash is stored on the computer; Android Keystore AES-GCM; per-device authentication; revocation tests. |
| Theme and wallpaper | Disclosure or resource exhaustion on a hostile local network | TLS pin, token authentication, bounded JSON/image size, private app cache, and no-store responses. Review file type support on target Android builds. |
| Omarchy plugin process | Malformed network data becomes code execution | No shell command is built from network input; JSON values are parsed as data; requests are rate/size-limited. The Quickshell plugin itself remains trusted, unsandboxed code. |
| Offline/reconnected phone | Stale or revoked token remains in use | The client retries the saved URL; the server checks the current token hash on every request. A fresh QR updates a trusted device's private address. |

## Remaining verification

Automated Python tests exercise TLS endpoints, first approval, rejection, expiry, rate/size boundaries, reconnection, theme parsing, and revocation. CI must compile the latest Java, C++, QML, and shader changes. The current environment has no Android SDK, `adb`, emulator, or attached Android device, so the TLS pin, scanner, Keystore, Android HOME role, and end-to-end pairing still need an emulator or physical-device smoke test.

Do not add remote execution or any endpoint that changes computer state until there is an explicit per-feature permission model, a clear user prompt, authorization tests, revocation behavior, and an updated threat review.
